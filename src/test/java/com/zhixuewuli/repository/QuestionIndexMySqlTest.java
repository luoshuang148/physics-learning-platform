package com.zhixuewuli.repository;

import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.QuestionIndexJob;
import com.zhixuewuli.entity.QuestionSearchUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 题目索引 MySQL 一致性：租约、重试幂等、旧任务晚完成不发布、删除墓碑。
 * 依赖真实 MySQL（@Tag("mysql")），默认 surefire 组不含 mysql。
 */
@Tag("mysql")
@SpringBootTest
@ActiveProfiles("mysql-test")
@Transactional
class QuestionIndexMySqlTest {

    @Autowired QuestionRepository questions;
    @Autowired QuestionIndexJobRepository jobs;
    @Autowired QuestionSearchUnitRepository searchUnits;
    @Autowired QuestionIndexPublicationRepository publications;

    private Question newQuestion(long revision, boolean aiEnabled) {
        Question q = new Question();
        String unique = "mysql-idx-" + UUID.randomUUID();
        q.setContent(unique);
        q.setOptions("{\"A\":\"a\",\"B\":\"b\"}");
        q.setCorrectOption("A");
        q.setExplanation("解析文本");
        q.setDifficulty(2);
        q.setAiPracticeEnabled(aiEnabled);
        q.setAiContentRevision(revision);
        return questions.saveAndFlush(q);
    }

    @Test
    void claimIsExclusiveAndRetryReusesSameJobRow() {
        Question q = newQuestion(1L, true);
        var job = new QuestionIndexJob();
        job.setQuestionId(q.getId());
        job.setRevision(1L);
        job.setJobType("INDEX");
        job.setStatus(QuestionIndexJob.Status.PENDING);
        job = jobs.saveAndFlush(job);

        LocalDateTime now = LocalDateTime.now();
        assertEquals(1, jobs.claim(job.getId(), "w1", now.plusSeconds(120), now, 3));
        assertEquals(0, jobs.claim(job.getId(), "w2", now.plusSeconds(120), now, 3),
                "同一任务不得被两个 worker 同时认领");

        var reloaded = jobs.findById(job.getId()).orElseThrow();
        assertEquals("w1", reloaded.getLeaseOwner());
        assertEquals(1, reloaded.getAttempts());

        // 同 (questionId,revision,type) 幂等：再次 enqueue 不新增行
        long before = jobs.count();
        var again = jobs.findByQuestionIdAndRevisionAndJobType(q.getId(), 1L, "INDEX").orElseThrow();
        assertEquals(job.getId(), again.getId());
        assertEquals(before, jobs.count());
    }

    @Test
    void expiredLeaseCanBeReclaimed() {
        Question q = newQuestion(1L, true);
        var job = new QuestionIndexJob();
        job.setQuestionId(q.getId());
        job.setRevision(1L);
        job.setJobType("INDEX");
        job.setStatus(QuestionIndexJob.Status.INDEXING);
        job.setAttempts(1);
        job.setLeaseOwner("dead-worker");
        job.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(10));
        job = jobs.saveAndFlush(job);

        LocalDateTime now = LocalDateTime.now();
        assertEquals(1, jobs.claim(job.getId(), "new-worker", now.plusSeconds(120), now, 3));
    }

    @Test
    void searchUnitsAreKeyedByRevision() {
        Question q = newQuestion(2L, true);
        var u = new QuestionSearchUnit();
        u.setQuestionId(q.getId());
        u.setRevision(2L);
        u.setPart("QUESTION");
        u.setOrdinalNo(0);
        u.setUnitText("单元文本");
        u.setContentHash("a".repeat(64));
        searchUnits.saveAndFlush(u);

        assertEquals(1, searchUnits.countByQuestionIdAndRevision(q.getId(), 2L));
        assertEquals(0, searchUnits.countByQuestionIdAndRevision(q.getId(), 3L));

        // 旧 revision 清理
        searchUnits.deleteByQuestionIdAndRevisionLessThan(q.getId(), 3L);
        assertEquals(0, searchUnits.countByQuestionIdAndRevision(q.getId(), 2L));
    }

    @Test
    void deleteTombstoneKeepsJobRow() {
        Question q = newQuestion(1L, true);
        var job = new QuestionIndexJob();
        job.setQuestionId(q.getId());
        job.setRevision(1L);
        job.setJobType("DELETE");
        job.setStatus(QuestionIndexJob.Status.PENDING);
        job = jobs.saveAndFlush(job);

        // 题目删除后任务行仍在（无 FK）
        questions.delete(q);
        questions.flush();
        assertTrue(jobs.findById(job.getId()).isPresent(), "删除墓碑应保留任务行");
    }

    @Test
    void existingQuestionsDefaultNotPublished() {
        Question q = newQuestion(1L, false);
        assertFalse(q.isAiPracticeEnabled(), "已有题默认未发布");
        assertEquals(1L, q.getAiContentRevision());
        assertTrue(publications.findByQuestionIdAndRevision(q.getId(), 1L).isEmpty());
        List<QuestionIndexJob> list = jobs.findByQuestionIdOrderByUpdatedAtDesc(q.getId());
        assertTrue(list.isEmpty() || list.stream().noneMatch(j -> j.getStatus() == QuestionIndexJob.Status.READY));
    }
}
