package com.zhixuewuli.repository;

import com.zhixuewuli.entity.ReportBatch;
import com.zhixuewuli.entity.ReportBatchItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface ReportBatchRepository extends JpaRepository<ReportBatch, Long> {

    Optional<ReportBatch> findByTeacherIdAndIdempotencyKey(Long teacherId, String idempotencyKey);

    @Query("select b from ReportBatch b where (:eventId is null or b.eventId=:eventId) "
            + "and exists (select c.id from ClassTeacher c where c.classId=b.classId and c.teacherId=:teacherId) order by b.id desc")
    Page<ReportBatch> findAccessible(@Param("teacherId") Long teacherId, @Param("eventId") Long eventId, Pageable pageable);

    long countByTeacherIdAndStatusIn(Long teacherId, Collection<ReportBatch.Status> statuses);

    @Modifying
    @Query("update ReportBatch b set b.status=:processing, b.startedAt=coalesce(b.startedAt, :now) "
            + "where b.id=:id and b.status=:pending")
    int markProcessing(@Param("id") Long id, @Param("pending") ReportBatch.Status pending,
                       @Param("processing") ReportBatch.Status processing, @Param("now") LocalDateTime now);

    /** 终态批次在人工重试后重新打开。 */
    @Modifying
    @Query("update ReportBatch b set b.status=:pending, b.finishedAt=null "
            + "where b.id=:id and b.status in :terminal")
    int reopen(@Param("id") Long id, @Param("pending") ReportBatch.Status pending,
               @Param("terminal") Collection<ReportBatch.Status> terminal);

    interface StatusCount { Long getBatchId(); ReportBatchItem.Status getStatus(); long getTotal(); }

    @Query("select b.id as batchId, i.status as status, count(i) as total from ReportBatch b "
            + "join ReportBatchItem i on i.batchId=b.id where b.id in :ids group by b.id, i.status")
    java.util.List<StatusCount> countsByBatchIds(@Param("ids") Collection<Long> ids);
}
