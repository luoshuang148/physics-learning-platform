package com.zhixuewuli.repository;
import com.zhixuewuli.entity.KnowledgeDocument;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument,Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from KnowledgeDocument d where d.id=:id")
    java.util.Optional<KnowledgeDocument> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    Page<KnowledgeDocument> findByClassIdAndWithdrawnAtIsNull(Long classId,Pageable page);
    long countByClassIdAndWithdrawnAtIsNull(Long classId);
}
