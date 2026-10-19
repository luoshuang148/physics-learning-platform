package com.zhixuewuli.service.reportbatch;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.result.PageResult;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.service.classroom.ClassAccessService;
import com.zhixuewuli.dto.request.ReportBatchCreateRequest;
import com.zhixuewuli.dto.response.ReportBatchItemReportResponse;
import com.zhixuewuli.dto.response.ReportBatchItemResponse;
import com.zhixuewuli.dto.response.ReportBatchResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 批量学生报告：创建、查询、手动重试。
 *
 * <p>创建时对权限、任务状态、名单规模与并发批次做校验；批次、子任务与发件箱
 * 在同一数据库事务内写入。幂等键通过数据库唯一约束保证并发安全。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportBatchService {

    private static final int MAX_PAGE_SIZE = 50;

    private final TeacherEventRepository events;
    private final TeachingClassRepository classes;
    private final TaskRecipientRepository recipients;
    private final AnswerRecordRepository answerRecords;
    private final ClassAccessService classAccess;
    private final ReportBatchRepository batches;
    private final ReportBatchItemRepository items;
    private final StudentReportRepository studentReports;
    private final ReportBatchOutboxService outbox;
    private final ReportOutboxRepository deliveries;
    private final com.zhixuewuli.common.config.properties.ReportBatchProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    // ========== 创建 ==========

    public ReportBatchResponse createBatch(Long teacherId, ReportBatchCreateRequest request) {
        String key = request.getIdempotencyKey() == null ? "" : request.getIdempotencyKey().trim();
        if (key.isEmpty()) throw new BusinessException("幂等键不能为空");
        if (key.length() > 100) throw new BusinessException("幂等键长度不能超过100字符");

        TeacherEvent event = events.findById(request.getEventId())
                .orElseThrow(() -> new BusinessException("教学任务不存在"));
        Long classId = event.getClassId() != null ? event.getClassId() : classAccess.defaultClass().getId();
        classAccess.requireTeacher(classId, teacherId);
        requireClosedTask(event);
        String requestHash = sha256(classId + ":" + event.getId());

        Optional<ReportBatch> replay = batches.findByTeacherIdAndIdempotencyKey(teacherId, key);
        if (replay.isPresent()) return replay(replay.get(), requestHash);

        try {
            return transactionTemplate.execute(status -> doCreate(teacherId, event, classId, key, requestHash));
        } catch (DataIntegrityViolationException concurrent) {
            // 同一幂等键并发创建：唯一约束裁决后返回既有批次。
            ReportBatch raced = batches.findByTeacherIdAndIdempotencyKey(teacherId, key).orElseThrow(() -> concurrent);
            return replay(raced, requestHash);
        }
    }

    private ReportBatchResponse replay(ReportBatch existing, String requestHash) {
        if (!requestHash.equals(existing.getRequestHash())) {
            throw new BusinessException("该幂等键已用于不同的任务参数，请更换幂等键");
        }
        return describe(existing, countsOf(existing.getId()));
    }

    private ReportBatchResponse doCreate(Long teacherId, TeacherEvent event, Long classId,
                                         String key, String requestHash) {
        long active = batches.countByTeacherIdAndStatusIn(teacherId,
                List.of(ReportBatch.Status.PENDING, ReportBatch.Status.PROCESSING));
        if (active >= properties.getMaxActiveBatchesPerTeacher()) {
            throw new BusinessException("进行中的批次过多（最多 " + properties.getMaxActiveBatchesPerTeacher()
                    + " 个），请等待完成后再试");
        }
        List<User> roster = recipients.students(event.getId());
        if (roster.isEmpty()) throw new BusinessException("该任务还没有应参与学生，无法批量生成");
        if (roster.size() > properties.getMaxStudentsPerBatch()) {
            throw new BusinessException("应参与名单数量（" + roster.size() + "）超过单批上限 "
                    + properties.getMaxStudentsPerBatch() + "，请缩小任务范围");
        }
        Set<Long> answered = answerRecords.findUserIdsWithRecords(event.getId());

        ReportBatch batch = new ReportBatch();
        batch.setClassId(classId);
        batch.setEventId(event.getId());
        batch.setTeacherId(teacherId);
        batch.setIdempotencyKey(key);
        batch.setRequestHash(requestHash);
        batch.setStatus(ReportBatch.Status.PENDING);
        batch.setTotalCount(roster.size());
        batches.saveAndFlush(batch);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        boolean anyPending = false;
        for (User student : roster) {
            ReportBatchItem item = new ReportBatchItem();
            item.setBatchId(batch.getId());
            item.setUserId(student.getId());
            item.setStudentName(student.getRealName());
            item.setMaxAttempts(properties.getItemMaxAttempts());
            if (answered.contains(student.getId())) {
                item.setStatus(ReportBatchItem.Status.PENDING);
                anyPending = true;
            } else {
                item.setStatus(ReportBatchItem.Status.SKIPPED);
                item.setSkipReason("该任务下无作答记录");
                item.setFinishedAt(now);
            }
            items.save(item);
            if (item.getStatus() == ReportBatchItem.Status.PENDING) {
                outbox.enqueueItem(batch.getId(), item.getId(), now);
            }
        }
        if (!anyPending) {
            finalizeIfComplete(batch.getId(), now);
            entityManager.refresh(batch);
        }
        log.info("批量报告批次创建完成 - batchId: {}, teacherId: {}, 名单: {}, 待生成: {}",
                batch.getId(), teacherId, roster.size(), anyPending ? "有" : "无");
        return describe(batch, countsOf(batch.getId()));
    }

    private void requireClosedTask(TeacherEvent event) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        boolean closed = event.getStatus() == TeacherEvent.Status.CLOSED
                || event.getStatus() == TeacherEvent.Status.ARCHIVED
                || event.isEndedAt(now);
        if (!closed) throw new BusinessException("任务尚未截止，截止后才能批量生成报告");
    }

    // ========== 查询 ==========

    @Transactional(readOnly = true)
    public PageResult<ReportBatchResponse> listBatches(Long teacherId, int page, int size) {
        return listBatches(teacherId, page, size, null);
    }

    @Transactional(readOnly = true)
    public PageResult<ReportBatchResponse> listBatches(Long teacherId, int page, int size, Long eventId) {
        Page<ReportBatch> result = batches.findAccessible(teacherId, eventId, pageRequest(page, size));
        Map<Long, Map<ReportBatchItem.Status, Long>> counts = countsOf(result.getContent().stream()
                .map(ReportBatch::getId).toList());
        Map<Long, String> eventTitles = eventTitles(result.getContent());
        Map<Long, String> classNames = classNames(result.getContent());
        List<ReportBatchResponse> records = result.getContent().stream()
                .map(batch -> describe(batch, counts.getOrDefault(batch.getId(), Map.of()),
                        eventTitles.get(batch.getEventId()), classNames.get(batch.getClassId())))
                .toList();
        return PageResult.<ReportBatchResponse>builder()
                .total(result.getTotalElements())
                .pages(result.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .records(records)
                .build();
    }

    @Transactional(readOnly = true)
    public ReportBatchResponse getBatch(Long teacherId, Long batchId) {
        ReportBatch batch = requireBatch(teacherId, batchId);
        return describe(batch, countsOf(batchId));
    }

    @Transactional(readOnly = true)
    public PageResult<ReportBatchItemResponse> listItems(Long teacherId, Long batchId, String status,
                                                         int page, int size) {
        requireBatch(teacherId, batchId);
        Page<ReportBatchItem> result = (status == null || status.isBlank())
                ? items.findByBatchIdOrderByIdAsc(batchId, pageRequest(page, size))
                : items.findByBatchIdAndStatusOrderByIdAsc(batchId, parseStatus(status), pageRequest(page, size));
        return PageResult.<ReportBatchItemResponse>builder()
                .total(result.getTotalElements())
                .pages(result.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .records(result.getContent().stream().map(this::toItemResponse).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public ReportBatchItemReportResponse getItemReport(Long teacherId, Long batchId, Long itemId) {
        ReportBatch batch = requireBatch(teacherId, batchId);
        ReportBatchItem item = requireItem(batchId, itemId);
        if (item.getStatus() != ReportBatchItem.Status.SUCCEEDED || item.getReportId() == null) {
            throw new BusinessException("该学生报告尚未生成完成");
        }
        StudentReport report = studentReports.findById(item.getReportId())
                .orElseThrow(() -> new BusinessException("报告数据不存在"));
        ReportBatchItemReportResponse response = new ReportBatchItemReportResponse();
        response.setBatchId(batchId);
        response.setItemId(itemId);
        response.setEventId(batch.getEventId());
        response.setStudentName(item.getStudentName());
        response.setContent(report.getReportContent());
        response.setEvidenceJson(report.getEvidenceJson());
        response.setGeneratedAt(report.getUpdatedAt() != null ? report.getUpdatedAt() : report.getCreatedAt());
        return response;
    }

    // ========== 手动重试 ==========

    public ReportBatchItemResponse retryItem(Long teacherId, Long batchId, Long itemId) {
        requireBatch(teacherId, batchId);
        requireItem(batchId, itemId);
        transactionTemplate.executeWithoutResult(status -> {
            int updated = items.markManualRetry(itemId, batchId,
                    ReportBatchItem.Status.FAILED, ReportBatchItem.Status.PENDING);
            if (updated == 0) throw new BusinessException("仅失败状态的子任务可以手动重试");
            batches.reopen(batchId, ReportBatch.Status.PENDING,
                    List.of(ReportBatch.Status.SUCCEEDED, ReportBatch.Status.PARTIAL_FAILED, ReportBatch.Status.FAILED));
            outbox.enqueueItem(batchId, itemId, LocalDateTime.now(ZoneOffset.UTC));
        });
        log.info("手动重试已入队 - batchId: {}, itemId: {}, teacherId: {}", batchId, itemId, teacherId);
        return toItemResponse(items.findById(itemId).orElseThrow());
    }

    /** Called in the same transaction as terminalizing an Outbox delivery. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void failUndeliverableItem(ReportOutboxMessage delivery) {
        if (delivery.getWorkItemId() == null) return; // DLQ records are not work deliveries.
        var item = items.lockById(delivery.getWorkItemId()).orElse(null);
        if (item == null || item.getStatus() != ReportBatchItem.Status.PENDING) return;
        // A locking read must see deliveries committed after this transaction's RR snapshot began.
        var latest = deliveries.lockLatestForWorkItem(item.getId(), PageRequest.of(0, 1));
        if (latest.isEmpty() || !latest.getFirst().getId().equals(delivery.getId())) return;
        item.setStatus(ReportBatchItem.Status.FAILED);
        item.setErrorCode("DELIVERY_EXHAUSTED");
        item.setErrorMessage("消息投递失败，修复消息服务后可手动重试");
        item.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        items.saveAndFlush(item);
        finalizeIfComplete(item.getBatchId(), item.getFinishedAt());
    }

    // ========== 共享：终态汇总 ==========

    /**
     * 当批次内没有 PENDING/PROCESSING 子任务时收敛为终态。
     * SKIPPED 计入已处理；成败比例只由子任务状态聚合得出，重复消费不会多计。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void finalizeIfComplete(Long batchId, LocalDateTime now) {
        jdbc.update("""
                UPDATE report_batches b SET b.status = CASE
                    WHEN (SELECT COUNT(*) FROM report_batch_items i WHERE i.batch_id = b.id AND i.status = 'FAILED') = 0
                        THEN 'SUCCEEDED'
                    WHEN (SELECT COUNT(*) FROM report_batch_items i WHERE i.batch_id = b.id AND i.status IN ('SUCCEEDED','SKIPPED')) = 0
                        THEN 'FAILED'
                    ELSE 'PARTIAL_FAILED' END,
                    b.finished_at = ?
                WHERE b.id = ? AND b.status IN ('PENDING','PROCESSING')
                  AND NOT EXISTS (SELECT 1 FROM report_batch_items i
                                  WHERE i.batch_id = b.id AND i.status IN ('PENDING','PROCESSING'))
                """, now, batchId);
    }

    // ========== 内部工具 ==========

    public ReportBatch requireBatch(Long teacherId, Long batchId) {
        ReportBatch batch = batches.findById(batchId)
                .orElseThrow(() -> new BusinessException("批次不存在"));
        classAccess.requireTeacher(batch.getClassId(), teacherId);
        return batch;
    }

    private ReportBatchItem requireItem(Long batchId, Long itemId) {
        return items.findById(itemId)
                .filter(item -> item.getBatchId().equals(batchId))
                .orElseThrow(() -> new BusinessException("子任务不存在"));
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private ReportBatchItem.Status parseStatus(String status) {
        try {
            return ReportBatchItem.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new BusinessException("不支持的状态筛选值");
        }
    }

    private Map<ReportBatchItem.Status, Long> countsOf(Long batchId) {
        Map<ReportBatchItem.Status, Long> counts = new EnumMap<>(ReportBatchItem.Status.class);
        for (ReportBatchItemRepository.StatusCount row : items.countsByBatch(batchId)) {
            counts.put(row.getStatus(), row.getTotal());
        }
        return counts;
    }

    private Map<Long, Map<ReportBatchItem.Status, Long>> countsOf(Collection<Long> batchIds) {
        Map<Long, Map<ReportBatchItem.Status, Long>> result = new HashMap<>();
        if (batchIds.isEmpty()) return result;
        for (ReportBatchRepository.StatusCount row : batches.countsByBatchIds(batchIds)) {
            result.computeIfAbsent(row.getBatchId(), id -> new EnumMap<>(ReportBatchItem.Status.class))
                    .put(row.getStatus(), row.getTotal());
        }
        return result;
    }

    private Map<Long, String> eventTitles(List<ReportBatch> batchList) {
        Set<Long> ids = new HashSet<>();
        batchList.forEach(batch -> ids.add(batch.getEventId()));
        Map<Long, String> titles = new HashMap<>();
        events.findAllById(ids).forEach(event -> titles.put(event.getId(), event.getEventTitle()));
        return titles;
    }

    private Map<Long, String> classNames(List<ReportBatch> batchList) {
        Set<Long> ids = new HashSet<>();
        batchList.forEach(batch -> ids.add(batch.getClassId()));
        Map<Long, String> names = new HashMap<>();
        classes.findAllById(ids).forEach(teachingClass -> names.put(teachingClass.getId(), teachingClass.getName()));
        return names;
    }

    private ReportBatchResponse describe(ReportBatch batch, Map<ReportBatchItem.Status, Long> counts) {
        ReportBatchResponse response = describe(batch, counts,
                events.findById(batch.getEventId()).map(TeacherEvent::getEventTitle).orElse(null),
                classes.findById(batch.getClassId()).map(TeachingClass::getName).orElse(null));
        return response;
    }

    private ReportBatchResponse describe(ReportBatch batch, Map<ReportBatchItem.Status, Long> counts,
                                         String eventTitle, String className) {
        ReportBatchResponse response = new ReportBatchResponse();
        response.setId(batch.getId());
        response.setClassId(batch.getClassId());
        response.setClassName(className);
        response.setEventId(batch.getEventId());
        response.setEventTitle(eventTitle);
        response.setStatus(batch.getStatus().name());
        response.setTotalCount(batch.getTotalCount());
        response.setSucceededCount(count(counts, ReportBatchItem.Status.SUCCEEDED));
        response.setFailedCount(count(counts, ReportBatchItem.Status.FAILED));
        response.setSkippedCount(count(counts, ReportBatchItem.Status.SKIPPED));
        response.setPendingCount(count(counts, ReportBatchItem.Status.PENDING));
        response.setProcessingCount(count(counts, ReportBatchItem.Status.PROCESSING));
        response.setFinishedCount(response.getSucceededCount() + response.getFailedCount() + response.getSkippedCount());
        response.setCreatedAt(batch.getCreatedAt());
        response.setStartedAt(batch.getStartedAt());
        response.setFinishedAt(batch.getFinishedAt());
        return response;
    }

    private int count(Map<ReportBatchItem.Status, Long> counts, ReportBatchItem.Status status) {
        return counts.getOrDefault(status, 0L).intValue();
    }

    private ReportBatchItemResponse toItemResponse(ReportBatchItem item) {
        ReportBatchItemResponse response = new ReportBatchItemResponse();
        response.setId(item.getId());
        response.setUserId(item.getUserId());
        response.setStudentName(item.getStudentName());
        response.setStatus(item.getStatus().name());
        response.setAttempts(item.getAttempts());
        response.setMaxAttempts(item.getMaxAttempts());
        response.setManualRetryCount(item.getManualRetryCount());
        response.setErrorCode(item.getErrorCode());
        response.setErrorMessage(item.getErrorMessage());
        response.setSkipReason(item.getSkipReason());
        response.setReportId(item.getReportId());
        response.setFinishedAt(item.getFinishedAt());
        return response;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception error) {
            throw new IllegalStateException("参数摘要计算失败", error);
        }
    }
}
