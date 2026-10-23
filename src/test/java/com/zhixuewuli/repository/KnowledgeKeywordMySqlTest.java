package com.zhixuewuli.repository;

import com.zhixuewuli.common.redis.RedisTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql") @SpringBootTest @ActiveProfiles("mysql-test")
class KnowledgeKeywordMySqlTest extends RedisTestSupport {
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired JdbcTemplate jdbc;
    private Long classId, otherClassId, documentId, withdrawnDocumentId, otherDocumentId;

    @Test void chinese_ngram_search_is_scoped_to_active_ready_nonwithdrawn_class_documents() {
        classId=insert("INSERT INTO classes(name,status,created_at,updated_at) VALUES(?,'ACTIVE',NOW(6),NOW(6))","kw_"+UUID.randomUUID());
        otherClassId=insert("INSERT INTO classes(name,status,created_at,updated_at) VALUES(?,'ACTIVE',NOW(6),NOW(6))","kw_"+UUID.randomUUID());
        documentId=document(classId,"活跃资料"); withdrawnDocumentId=document(classId,"撤回资料");
        otherDocumentId=document(otherClassId,"异班资料");
        revision(documentId,"牛顿第二定律指出，物体加速度与合外力成正比。",false);
        revision(withdrawnDocumentId,"牛顿第二定律撤回版本。",true);
        revision(otherDocumentId,"牛顿第二定律其他班内容。",false);
        var found=chunks.searchActiveByClassId(classId,"牛顿第二定律",10);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getChunkText()).contains("加速度");
        assertThat(found.get(0).getScore()).isPositive();
    }

    private Long document(Long cls,String title){return insert("INSERT INTO knowledge_documents(class_id,owner_id,title,storage_key,media_type,content_sha256,lock_version,created_at,updated_at) VALUES(?,1,?,?,'text/plain',?,0,NOW(6),NOW(6))",cls,title,UUID.randomUUID()+".bin","a".repeat(64));}
    private void revision(Long doc,String text,boolean withdrawn){
        Long rev=insert("INSERT INTO knowledge_revisions(document_id,content_sha256,status,index_version,retry_count,lock_version,created_at,updated_at) VALUES(?,?,'READY',?,0,0,NOW(6),NOW(6))",doc,"b".repeat(64),"idx_"+UUID.randomUUID().toString().replace("-",""));
        insert("INSERT INTO knowledge_chunks(revision_id,ordinal_no,page_no,section_title,chunk_text,content_sha256,created_at,updated_at) VALUES(?,0,1,'力学',?,?,NOW(6),NOW(6))",rev,text,"c".repeat(64));
        jdbc.update("UPDATE knowledge_documents SET active_revision_id=?,withdrawn_at=? WHERE id=?",rev,withdrawn?java.time.LocalDateTime.now():null,doc);
    }
    private Long insert(String sql,Object...values){var holder=new org.springframework.jdbc.support.GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<values.length;i++)ps.setObject(i+1,values[i]);return ps;},holder);return holder.getKey().longValue();}
    @AfterEach void cleanup(){if(documentId!=null)jdbc.update("DELETE FROM knowledge_documents WHERE id=?",documentId);if(withdrawnDocumentId!=null)jdbc.update("DELETE FROM knowledge_documents WHERE id=?",withdrawnDocumentId);if(otherDocumentId!=null)jdbc.update("DELETE FROM knowledge_documents WHERE id=?",otherDocumentId);if(classId!=null)jdbc.update("DELETE FROM classes WHERE id=?",classId);if(otherClassId!=null)jdbc.update("DELETE FROM classes WHERE id=?",otherClassId);}
}
