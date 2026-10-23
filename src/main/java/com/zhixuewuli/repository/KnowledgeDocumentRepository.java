package com.zhixuewuli.repository;
import com.zhixuewuli.entity.KnowledgeDocument;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument,Long> {
    @org.springframework.data.jpa.repository.Query("select new com.zhixuewuli.repository.ActiveKnowledgeIndexRow(d.id,d.classId,r.id,r.indexVersion) from KnowledgeDocument d join KnowledgeRevision r on r.id=d.activeRevisionId where d.classId=:classId and d.withdrawnAt is null and r.status=com.zhixuewuli.entity.KnowledgeRevision.Status.READY and r.indexVersion is not null")
    java.util.List<ActiveKnowledgeIndexRow> findActiveIndexesByClassId(@org.springframework.data.repository.query.Param("classId") Long classId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from KnowledgeDocument d where d.id=:id")
    java.util.Optional<KnowledgeDocument> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    Page<KnowledgeDocument> findByClassIdAndWithdrawnAtIsNull(Long classId,Pageable page);
    long countByClassIdAndWithdrawnAtIsNull(Long classId);
}
