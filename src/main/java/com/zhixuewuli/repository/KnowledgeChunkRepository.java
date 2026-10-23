package com.zhixuewuli.repository;
import com.zhixuewuli.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk,Long> {
    @org.springframework.data.jpa.repository.Query(value="select c.id chunkId,d.id documentId,r.id revisionId,d.class_id classId,c.ordinal_no ordinalNo,d.title title,c.page_no pageNo,c.section_title sectionTitle,c.chunk_text chunkText,match(c.chunk_text) against (:query in natural language mode) score from knowledge_chunks c join knowledge_revisions r on r.id=c.revision_id join knowledge_documents d on d.id=r.document_id where d.class_id=:classId and d.withdrawn_at is null and d.active_revision_id=r.id and r.status='READY' and match(c.chunk_text) against (:query in natural language mode) order by score desc,c.id asc limit :limit",nativeQuery=true)
    java.util.List<KeywordKnowledgeRow> searchActiveByClassId(@org.springframework.data.repository.query.Param("classId") Long classId,@org.springframework.data.repository.query.Param("query") String query,@org.springframework.data.repository.query.Param("limit") int limit);
    List<KnowledgeChunk> findByRevisionIdOrderByOrdinalNo(Long revisionId);
    long countByRevisionId(Long revisionId);
    void deleteByRevisionId(Long revisionId);
}
