package com.zhixuewuli.repository;

import com.zhixuewuli.entity.ReportOutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发件箱仓储。认领条件同时约束状态、到期时间、重试上限与租约，
 * 多实例并发扫描时只有一台能取得投递权。
 */
public interface ReportOutboxRepository extends JpaRepository<ReportOutboxMessage, Long> {

    @Query("select o.id from ReportOutboxMessage o where o.status=:pending and o.attempts<:maxAttempts "
            + "and o.nextAttemptAt<=:now and (o.leaseExpiresAt is null or o.leaseExpiresAt < :now) order by o.id")
    List<Long> findDueIds(@Param("pending") ReportOutboxMessage.Status pending, @Param("maxAttempts") int maxAttempts,
                          @Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("update ReportOutboxMessage o set o.attempts=o.attempts+1, o.leaseOwner=:owner, o.leaseExpiresAt=:leaseUntil "
            + "where o.id=:id and o.status=:pending and o.attempts<:maxAttempts and o.nextAttemptAt<=:now "
            + "and (o.leaseExpiresAt is null or o.leaseExpiresAt < :now)")
    int claim(@Param("id") Long id, @Param("pending") ReportOutboxMessage.Status pending,
              @Param("owner") String owner, @Param("leaseUntil") LocalDateTime leaseUntil,
              @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Query("update ReportOutboxMessage o set o.status=:published, o.publishedAt=:now, "
            + "o.leaseOwner=null, o.leaseExpiresAt=null, o.lastError=null where o.id=:id and o.status=:pending and o.leaseOwner=:owner")
    int markPublished(@Param("id") Long id, @Param("pending") ReportOutboxMessage.Status pending,
                      @Param("published") ReportOutboxMessage.Status published, @Param("now") LocalDateTime now, @Param("owner") String owner);

    @Modifying
    @Query("update ReportOutboxMessage o set o.nextAttemptAt=:nextAttempt, o.lastError=:error, "
            + "o.leaseOwner=null, o.leaseExpiresAt=null where o.id=:id and o.status=:pending and o.leaseOwner=:owner")
    int markRetry(@Param("id") Long id, @Param("pending") ReportOutboxMessage.Status pending,
                  @Param("nextAttempt") LocalDateTime nextAttempt, @Param("error") String error, @Param("owner") String owner);

    @Modifying
    @Query("update ReportOutboxMessage o set o.status=:failed, o.lastError=:error, "
            + "o.leaseOwner=null, o.leaseExpiresAt=null where o.id=:id and o.status=:pending and o.leaseOwner=:owner")
    int markFailed(@Param("id") Long id, @Param("pending") ReportOutboxMessage.Status pending,
                   @Param("failed") ReportOutboxMessage.Status failed, @Param("error") String error, @Param("owner") String owner);

    java.util.Optional<ReportOutboxMessage> findFirstByWorkItemIdOrderByIdDesc(Long workItemId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ReportOutboxMessage o where o.workItemId=:itemId order by o.id desc")
    List<ReportOutboxMessage> lockLatestForWorkItem(@Param("itemId") Long itemId, Pageable page);

    @Query("select o.id from ReportOutboxMessage o where o.status=:pending and o.attempts>=:maxAttempts "
            + "and (o.leaseExpiresAt is null or o.leaseExpiresAt < :now) order by o.id")
    List<Long> findExhaustedIds(@Param("pending") ReportOutboxMessage.Status pending,
                              @Param("maxAttempts") int maxAttempts, @Param("now") LocalDateTime now, Pageable page);

    @Modifying
    @Query("update ReportOutboxMessage o set o.status=:failed, o.lastError='消息投递次数已耗尽', "
            + "o.leaseOwner=null, o.leaseExpiresAt=null where o.id=:id and o.status=:pending "
            + "and o.attempts>=:maxAttempts and (o.leaseExpiresAt is null or o.leaseExpiresAt < :now)")
    int failExhausted(@Param("id") Long id, @Param("pending") ReportOutboxMessage.Status pending,
                      @Param("failed") ReportOutboxMessage.Status failed, @Param("maxAttempts") int maxAttempts,
                      @Param("now") LocalDateTime now);

    long countByStatus(ReportOutboxMessage.Status status);

    long countByStatusAndNextAttemptAtBefore(ReportOutboxMessage.Status status, LocalDateTime now);
}
