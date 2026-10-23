package com.zhixuewuli.repository;
import com.zhixuewuli.entity.KnowledgeRevision;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface KnowledgeRevisionRepository extends JpaRepository<KnowledgeRevision,Long> {
    Optional<KnowledgeRevision> findFirstByDocumentIdOrderByIdDesc(Long documentId);
    @Query("select r from KnowledgeRevision r where r.id in (select max(x.id) from KnowledgeRevision x where x.documentId in :ids group by x.documentId)")
    List<KnowledgeRevision> latestForDocuments(@Param("ids") Collection<Long> documentIds);
}
