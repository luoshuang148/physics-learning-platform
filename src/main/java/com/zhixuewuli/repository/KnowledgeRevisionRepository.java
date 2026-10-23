package com.zhixuewuli.repository;
import com.zhixuewuli.entity.KnowledgeRevision;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
public interface KnowledgeRevisionRepository extends JpaRepository<KnowledgeRevision,Long> {
    Optional<KnowledgeRevision> findFirstByDocumentIdOrderByIdDesc(Long documentId);
    @Query("select r from KnowledgeRevision r where r.id in (select max(x.id) from KnowledgeRevision x where x.documentId in :ids group by x.documentId)")
    List<KnowledgeRevision> latestForDocuments(@Param("ids") Collection<Long> documentIds);

    @Modifying
    @Transactional
    @Query("update KnowledgeRevision r set r.status=:indexing, r.leaseOwner=:owner, r.leaseExpiresAt=:expires, r.retryCount=r.retryCount+1, r.errorCode=null " +
            "where r.id=:id and (r.status=:uploaded or (r.status=:indexing and (r.leaseExpiresAt is null or r.leaseExpiresAt<:now)))")
    int claimForIndexing(@Param("id") Long id, @Param("owner") String owner,
                         @Param("now") LocalDateTime now, @Param("expires") LocalDateTime expires,
                         @Param("uploaded") KnowledgeRevision.Status uploaded,
                         @Param("indexing") KnowledgeRevision.Status indexing);

    @Modifying
    @Transactional
    @Query("update KnowledgeRevision r set r.leaseExpiresAt=:expires where r.id=:id and r.leaseOwner=:owner and r.status=:indexing")
    int renewIndexingLease(@Param("id") Long id, @Param("owner") String owner,
                           @Param("expires") LocalDateTime expires,
                           @Param("indexing") KnowledgeRevision.Status indexing);

    @Modifying
    @Transactional
    @Query("update KnowledgeRevision r set r.status=:failed, r.leaseOwner=null, r.leaseExpiresAt=null, r.errorCode=:errorCode " +
            "where r.id=:id and r.leaseOwner=:owner and r.status=:indexing")
    int failClaimedRevision(@Param("id") Long id, @Param("owner") String owner,
                            @Param("indexing") KnowledgeRevision.Status indexing,
                            @Param("failed") KnowledgeRevision.Status failed,
                            @Param("errorCode") String errorCode);

    @Query("select r.id from KnowledgeRevision r where r.status=:uploaded or " +
            "(r.status=:indexing and (r.leaseExpiresAt is null or r.leaseExpiresAt<:now)) order by r.id")
    List<Long> findRunnableRevisionIds(@Param("uploaded") KnowledgeRevision.Status uploaded,
                                       @Param("indexing") KnowledgeRevision.Status indexing,
                                       @Param("now") LocalDateTime now, Pageable pageable);
}
