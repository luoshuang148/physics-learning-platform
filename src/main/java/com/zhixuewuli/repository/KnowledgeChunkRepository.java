package com.zhixuewuli.repository;
import com.zhixuewuli.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk,Long> {
    List<KnowledgeChunk> findByRevisionIdOrderByOrdinalNo(Long revisionId);
    long countByRevisionId(Long revisionId);
}
