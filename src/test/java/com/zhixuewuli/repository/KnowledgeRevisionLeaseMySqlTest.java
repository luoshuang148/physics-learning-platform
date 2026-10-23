package com.zhixuewuli.repository;

import com.zhixuewuli.common.redis.RedisTestSupport;
import com.zhixuewuli.entity.KnowledgeRevision;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
@SpringBootTest
@ActiveProfiles("mysql-test")
class KnowledgeRevisionLeaseMySqlTest extends RedisTestSupport {
    @Autowired KnowledgeRevisionRepository revisions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    private Long documentId;
    private Long classId;

    @Test
    void expiredIndexingLeaseCanBeClaimedAgainAfterWorkerRestart() {
        classId = insertAndReturn("INSERT INTO classes(name,status,created_at,updated_at) VALUES(?,'ACTIVE',NOW(6),NOW(6))",
                "lease_fixture_" + UUID.randomUUID());
        documentId = insertAndReturn("INSERT INTO knowledge_documents(class_id,owner_id,title,storage_key,media_type,content_sha256,lock_version,created_at,updated_at) " +
                        "VALUES(?,1,'租约测试',?,'text/plain',?,0,NOW(6),NOW(6))",
                classId, UUID.randomUUID() + ".bin", "a".repeat(64));
        var revision = new KnowledgeRevision();
        revision.setDocumentId(documentId);
        revision.setContentSha256("a".repeat(64));
        revision.setStatus(KnowledgeRevision.Status.UPLOADED);
        revision = revisions.saveAndFlush(revision);
        Long revisionId = revision.getId();
        LocalDateTime now = LocalDateTime.now();

        assertEquals(1, claim(revisionId, "first-worker", now, now.plusMinutes(15)));
        assertEquals(0, claim(revisionId, "duplicate-worker", now, now.plusMinutes(15)));
        assertEquals(1, revisions.renewIndexingLease(revisionId, "first-worker", now.plusMinutes(30),
                KnowledgeRevision.Status.INDEXING));
        assertEquals(0, revisions.renewIndexingLease(revisionId, "duplicate-worker", now.plusMinutes(30),
                KnowledgeRevision.Status.INDEXING));
        entityManager.clear();
        assertTrue(revisions.findRunnableRevisionIds(KnowledgeRevision.Status.UPLOADED,
                KnowledgeRevision.Status.INDEXING, now, org.springframework.data.domain.PageRequest.of(0, 2)).isEmpty());

        jdbc.update("UPDATE knowledge_revisions SET lease_expires_at=DATE_SUB(NOW(6), INTERVAL 1 SECOND) WHERE id=?", revisionId);
        var recovered = revisions.findRunnableRevisionIds(KnowledgeRevision.Status.UPLOADED,
                KnowledgeRevision.Status.INDEXING, LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(0, 2));
        assertTrue(recovered.contains(revisionId));
        assertEquals(1, claim(revisionId, "restarted-worker", LocalDateTime.now(), LocalDateTime.now().plusMinutes(15)));
        entityManager.clear();
        var retried = revisions.findById(revisionId).orElseThrow();
        assertEquals(2, retried.getRetryCount());
        assertEquals("restarted-worker", retried.getLeaseOwner());
    }

    private int claim(Long revisionId, String worker, LocalDateTime now, LocalDateTime expires) {
        return revisions.claimForIndexing(revisionId, worker, now, expires,
                KnowledgeRevision.Status.UPLOADED, KnowledgeRevision.Status.INDEXING);
    }

    private Long insertAndReturn(String sql, Object... values) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    @AfterEach
    void cleanup() {
        if (documentId != null) {
            jdbc.update("DELETE FROM knowledge_documents WHERE id=?", documentId);
        }
        if (classId != null) jdbc.update("DELETE FROM classes WHERE id=?", classId);
    }
}
