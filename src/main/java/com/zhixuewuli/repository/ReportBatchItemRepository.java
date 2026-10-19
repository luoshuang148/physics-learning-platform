package com.zhixuewuli.repository;

import com.zhixuewuli.entity.ReportBatchItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 子任务仓储。所有状态流转都是带条件的 UPDATE（认领语义），
 * 并发执行者中只有一个能把行从 PENDING 推进到 PROCESSING，重复消息不会重复计数。
 */
public interface ReportBatchItemRepository extends JpaRepository<ReportBatchItem, Long> {

    Page<ReportBatchItem> findByBatchIdOrderByIdAsc(Long batchId, Pageable pageable);

    Page<ReportBatchItem> findByBatchIdAndStatusOrderByIdAsc(Long batchId, ReportBatchItem.Status status, Pageable pageable);

    interface StatusCount { ReportBatchItem.Status getStatus(); long getTotal(); }

    @Query("select i.status as status, count(i) as total from ReportBatchItem i where i.batchId=:batchId group by i.status")
    List<StatusCount> countsByBatch(@Param("batchId") Long batchId);

    @Modifying
    @Query("update ReportBatchItem i set i.status=:processing, i.executionId=:executionId, i.leaseOwner=:owner, "
            + "i.leaseExpiresAt=:leaseUntil, i.attempts=i.attempts+1, i.startedAt=:now, i.errorCode=null, i.errorMessage=null "
            + "where i.id=:id and i.status=:pending and (i.leaseExpiresAt is null or i.leaseExpiresAt < :now)")
    int claim(@Param("id") Long id, @Param("pending") ReportBatchItem.Status pending,
              @Param("processing") ReportBatchItem.Status processing, @Param("executionId") String executionId,
              @Param("owner") String owner, @Param("leaseUntil") LocalDateTime leaseUntil, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update ReportBatchItem i set i.status=:succeeded, i.reportId=:reportId, i.inputDigest=:digest, "
            + "i.inputRevision=:revision, i.leaseOwner=null, i.leaseExpiresAt=null, i.finishedAt=:now "
            + "where i.id=:id and i.status=:processing and i.executionId=:executionId")
    int markSucceeded(@Param("id") Long id, @Param("processing") ReportBatchItem.Status processing,
                      @Param("succeeded") ReportBatchItem.Status succeeded, @Param("executionId") String executionId,
                      @Param("reportId") Long reportId, @Param("digest") String digest,
                      @Param("revision") String revision, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update ReportBatchItem i set i.status=:failed, i.errorCode=:code, i.errorMessage=:message, "
            + "i.leaseOwner=null, i.leaseExpiresAt=null, i.finishedAt=:now "
            + "where i.id=:id and i.status=:processing and i.executionId=:executionId")
    int markFailed(@Param("id") Long id, @Param("processing") ReportBatchItem.Status processing,
                   @Param("failed") ReportBatchItem.Status failed, @Param("executionId") String executionId,
                   @Param("code") String code, @Param("message") String message, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update ReportBatchItem i set i.status=:pending, i.errorCode=:code, i.errorMessage=:message, "
            + "i.leaseOwner=null, i.leaseExpiresAt=null "
            + "where i.id=:id and i.status=:processing and i.executionId=:executionId")
    int releaseForRetry(@Param("id") Long id, @Param("processing") ReportBatchItem.Status processing,
                        @Param("pending") ReportBatchItem.Status pending, @Param("executionId") String executionId,
                        @Param("code") String code, @Param("message") String message);

    @Modifying
    @Query("update ReportBatchItem i set i.status=:skipped, i.skipReason=:reason, "
            + "i.leaseOwner=null, i.leaseExpiresAt=null, i.finishedAt=:now "
            + "where i.id=:id and i.status=:processing and i.executionId=:executionId")
    int markSkipped(@Param("id") Long id, @Param("processing") ReportBatchItem.Status processing,
                    @Param("skipped") ReportBatchItem.Status skipped, @Param("executionId") String executionId,
                    @Param("reason") String reason, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update ReportBatchItem i set i.status=:pending, i.leaseOwner=null, i.leaseExpiresAt=null, "
            + "i.errorCode=:code, i.errorMessage=:message "
            + "where i.id=:id and i.status=:processing and i.leaseExpiresAt < :now and i.attempts < :maxAttempts")
    int recoverExpiredLease(@Param("id") Long id, @Param("processing") ReportBatchItem.Status processing,
                            @Param("pending") ReportBatchItem.Status pending, @Param("code") String code,
                            @Param("message") String message, @Param("now") LocalDateTime now,
                            @Param("maxAttempts") int maxAttempts);

    /** 租约过期且执行次数已耗尽的子任务直接判定失败（不重新投递）。 */
    @Modifying
    @Query("update ReportBatchItem i set i.status=:failed, i.errorCode=:code, i.errorMessage=:message, "
            + "i.leaseOwner=null, i.leaseExpiresAt=null, i.finishedAt=:now "
            + "where i.id=:id and i.status=:processing and i.leaseExpiresAt < :now and i.attempts >= :maxAttempts")
    int markExhaustedExpiredLease(@Param("id") Long id, @Param("processing") ReportBatchItem.Status processing,
                                  @Param("failed") ReportBatchItem.Status failed, @Param("code") String code,
                                  @Param("message") String message, @Param("now") LocalDateTime now,
                                  @Param("maxAttempts") int maxAttempts);

    @Modifying(clearAutomatically = true)
    @Query("update ReportBatchItem i set i.status=:pending, i.manualRetryCount=i.manualRetryCount+1, "
            + "i.leaseOwner=null, i.leaseExpiresAt=null, i.finishedAt=null, i.errorCode=null, i.errorMessage=null "
            + "where i.id=:id and i.batchId=:batchId and i.status=:failed")
    int markManualRetry(@Param("id") Long id, @Param("batchId") Long batchId,
                        @Param("failed") ReportBatchItem.Status failed, @Param("pending") ReportBatchItem.Status pending);

    @Query("select i.id from ReportBatchItem i where i.status=:processing and i.leaseExpiresAt < :now order by i.id")
    List<Long> findExpiredLeaseIds(@Param("processing") ReportBatchItem.Status processing,
                                   @Param("now") LocalDateTime now, Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ReportBatchItem i where i.id=:id")
    java.util.Optional<ReportBatchItem> lockById(@Param("id") Long id);

    @Query("select i.id from ReportBatchItem i where i.status=:pending "
            + "and not exists (select o.id from ReportOutboxMessage o where o.workItemId=i.id and o.status=:outboxPending) "
            + "and (not exists (select o.id from ReportOutboxMessage o where o.workItemId=i.id) "
            + "or exists (select o.id from ReportOutboxMessage o where o.workItemId=i.id "
            + "and o.id=(select max(n.id) from ReportOutboxMessage n where n.workItemId=i.id) "
            + "and (o.status=:failed or (o.status=:published and o.publishedAt<=:cutoff)))) order by i.id")
    List<Long> findUnclaimedDeliveryIds(@Param("pending") ReportBatchItem.Status pending,
            @Param("outboxPending") com.zhixuewuli.entity.ReportOutboxMessage.Status outboxPending,
            @Param("failed") com.zhixuewuli.entity.ReportOutboxMessage.Status failed,
            @Param("published") com.zhixuewuli.entity.ReportOutboxMessage.Status published,
            @Param("cutoff") LocalDateTime cutoff, Pageable page);

    long countByStatusAndLeaseExpiresAtBefore(ReportBatchItem.Status status, LocalDateTime now);

    long countByStatus(ReportBatchItem.Status status);
}
