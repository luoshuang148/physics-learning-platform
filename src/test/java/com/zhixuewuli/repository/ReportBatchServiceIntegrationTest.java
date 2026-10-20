package com.zhixuewuli.repository;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.dto.response.ReportBatchItemResponse;
import com.zhixuewuli.dto.response.ReportBatchResponse;
import com.zhixuewuli.entity.ReportBatch;
import com.zhixuewuli.entity.ReportBatchItem;
import com.zhixuewuli.entity.TeachingClass;
import com.zhixuewuli.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** 批量报告服务语义：名单快照、跳过、幂等、权限、上限与手动重试。 */
@DisplayName("批量报告创建与查询语义")
class ReportBatchServiceIntegrationTest extends ReportBatchTestSupport {

    @Test
    @DisplayName("创建时快照名单，无作答学生直接标记跳过并立即收敛成功")
    void marksStudentsWithoutAnswersAsSkippedAndFinalizes() {
        TeachingClass clazz = newClass();
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, com.zhixuewuli.entity.ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), STUDENT_ID);
        TaskFixture task = publishTask(clazz.getId());
        events.closeTask(task.eventId());

        ReportBatchResponse batch = createBatch(task.eventId(), "skip-" + UUID.randomUUID());

        assertThat(batch.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(batch.getTotalCount()).isEqualTo(1);
        assertThat(batch.getSkippedCount()).isEqualTo(1);
        assertThat(batch.getFinishedCount()).isEqualTo(1);
        assertThat(batch.getFinishedAt()).isNotNull();
        ReportBatchItem item = itemOf(batch.getId(), STUDENT_ID);
        assertThat(item.getStatus()).isEqualTo(ReportBatchItem.Status.SKIPPED);
        assertThat(item.getSkipReason()).contains("无作答记录");
        assertThat(item.getAttempts()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    @DisplayName("同键同参重复创建返回原批次且不重复入队")
    void isIdempotentForSameKey() {
        TeachingClass clazz = newClass();
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, com.zhixuewuli.entity.ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), STUDENT_ID);
        TaskFixture task = publishTask(clazz.getId());
        submitAnswer(task, STUDENT_ID);
        events.closeTask(task.eventId());

        String key = "idem-" + UUID.randomUUID();
        ReportBatchResponse first = createBatch(task.eventId(), key);
        ReportBatchResponse replay = createBatch(task.eventId(), key);

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(first.getStatus()).isEqualTo("PENDING");
        assertThat(first.getPendingCount()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("同键不同任务参数被拒绝")
    void rejectsSameKeyWithDifferentParameters() {
        TeachingClass clazz = newClass();
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, com.zhixuewuli.entity.ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), STUDENT_ID);
        TaskFixture first = publishTask(clazz.getId());
        TaskFixture second = publishTask(clazz.getId());
        events.closeTask(first.eventId());
        events.closeTask(second.eventId());

        String key = "conflict-" + UUID.randomUUID();
        createBatch(first.eventId(), key);
        assertThatThrownBy(() -> createBatch(second.eventId(), key))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("幂等键");
    }

    @Test
    @DisplayName("未截止任务不允许生成")
    void requiresClosedTask() {
        TeachingClass clazz = newClass();
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, com.zhixuewuli.entity.ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), STUDENT_ID);
        TaskFixture open = publishTask(clazz.getId());

        assertThatThrownBy(() -> createBatch(open.eventId(), "open-" + UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未截止");
    }

    @Test
    @DisplayName("非本班教师无法创建、查询或手动重试")
    void requiresTeacherMembership() {
        TeachingClass clazz = newClass();
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, com.zhixuewuli.entity.ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), STUDENT_ID);
        TaskFixture task = publishTask(clazz.getId());
        events.closeTask(task.eventId());
        ReportBatchResponse batch = createBatch(task.eventId(), "perm-" + UUID.randomUUID());

        assertThatThrownBy(() -> batchService.getBatch(OTHER_TEACHER_ID, batch.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该班级");
        assertThatThrownBy(() -> batchService.listItems(OTHER_TEACHER_ID, batch.getId(), null, 1, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该班级");
        assertThatThrownBy(() -> batchService.retryItem(OTHER_TEACHER_ID, batch.getId(),
                itemOf(batch.getId(), STUDENT_ID).getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该班级");
    }

    @Test
    void removedTeacherCannotSeeBatchListOrPaginationTotals() {
        var clazz = newClass();
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, com.zhixuewuli.entity.ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), STUDENT_ID);
        var task = publishTask(clazz.getId()); events.closeTask(task.eventId());
        var batch = createBatch(task.eventId(), UUID.randomUUID().toString());
        jdbc.update("DELETE FROM class_teachers WHERE class_id=? AND teacher_id=?", clazz.getId(), TEACHER_ID);
        var page = batchService.listBatches(TEACHER_ID, 1, 10);
        assertThat(page.getRecords()).extracting(ReportBatchResponse::getId).doesNotContain(batch.getId());
        assertThat(page.getTotal()).isZero();
    }

    @Test
    @DisplayName("空名单任务不允许批量生成")
    void requiresCapturedRoster() {
        TeachingClass empty = newClass();
        classAccess.addTeacher(empty.getId(), TEACHER_ID, com.zhixuewuli.entity.ClassTeacher.Role.OWNER);
        TaskFixture task = publishTask(empty.getId());
        events.closeTask(task.eventId());

        assertThatThrownBy(() -> createBatch(task.eventId(), "empty-" + UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应参与学生");
    }

    @Test
    @DisplayName("名单规模上限与并发批次上限都会拒绝创建")
    void enforcesRosterAndActiveBatchLimits() {
        TeachingClass clazz = newClass();
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, com.zhixuewuli.entity.ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), STUDENT_ID);
        TaskFixture task = publishTask(clazz.getId());
        submitAnswer(task, STUDENT_ID);
        events.closeTask(task.eventId());

        int originalRosterLimit = properties.getMaxStudentsPerBatch();
        int originalActiveLimit = properties.getMaxActiveBatchesPerTeacher();
        try {
            properties.setMaxStudentsPerBatch(0);
            assertThatThrownBy(() -> createBatch(task.eventId(), "roster-limit-" + UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("单批上限");

            properties.setMaxStudentsPerBatch(originalRosterLimit);
            properties.setMaxActiveBatchesPerTeacher(1);
            createBatch(task.eventId(), "active-1-" + UUID.randomUUID());
            assertThatThrownBy(() -> createBatch(task.eventId(), "active-2-" + UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("进行中的批次过多");
        } finally {
            properties.setMaxStudentsPerBatch(originalRosterLimit);
            properties.setMaxActiveBatchesPerTeacher(originalActiveLimit);
        }
    }

    @Test
    @DisplayName("手动重试只接受失败子任务，并重新打开已终结批次")
    void retryItemOnlyAcceptsFailedState() {
        TeachingClass clazz = newClass();
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, com.zhixuewuli.entity.ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), STUDENT_ID);
        TaskFixture task = publishTask(clazz.getId());
        submitAnswer(task, STUDENT_ID);
        events.closeTask(task.eventId());
        ReportBatchResponse batch = createBatch(task.eventId(), "retry-" + UUID.randomUUID());
        ReportBatchItem item = itemOf(batch.getId(), STUDENT_ID);

        assertThatThrownBy(() -> batchService.retryItem(TEACHER_ID, batch.getId(), item.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅失败状态");

        forceItemStatus(item, ReportBatchItem.Status.FAILED);
        ReportBatch batchRow = batches.findById(batch.getId()).orElseThrow();
        batchRow.setStatus(ReportBatch.Status.FAILED);
        batchRow.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        batches.saveAndFlush(batchRow);

        ReportBatchItemResponse retried = batchService.retryItem(TEACHER_ID, batch.getId(), item.getId());
        assertThat(retried.getStatus()).isEqualTo("PENDING");
        assertThat(retried.getManualRetryCount()).isEqualTo(1);
        assertThat(freshBatch(batch.getId()).getStatus()).isEqualTo(ReportBatch.Status.PENDING);
        assertThat(outbox.count()).isEqualTo(2);

        assertThatThrownBy(() -> batchService.getItemReport(TEACHER_ID, batch.getId(), item.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未生成完成");
    }

    @Test
    @DisplayName("子任务列表支持状态筛选，非法状态被拒绝")
    void listItemsSupportsStatusFilter() {
        TeachingClass clazz = newClass();
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, com.zhixuewuli.entity.ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), STUDENT_ID);
        TaskFixture task = publishTask(clazz.getId());
        submitAnswer(task, STUDENT_ID);
        events.closeTask(task.eventId());
        ReportBatchResponse batch = createBatch(task.eventId(), "filter-" + UUID.randomUUID());

        assertThat(batchService.listItems(TEACHER_ID, batch.getId(), "PENDING", 1, 10).getRecords()).hasSize(1);
        assertThat(batchService.listItems(TEACHER_ID, batch.getId(), "SKIPPED", 1, 10).getRecords()).isEmpty();
        assertThatThrownBy(() -> batchService.listItems(TEACHER_ID, batch.getId(), "NOT_A_STATUS", 1, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态筛选");
    }

    @Test
    @DisplayName("终态汇总只在全部子任务终结后结算：成功/部分失败/失败")
    void finalizeAggregatesOnlyTerminalItems() {
        TeachingClass clazz = newClass();
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, com.zhixuewuli.entity.ClassTeacher.Role.OWNER);
        User second = user("rb-second-student", User.UserType.STUDENT);
        classAccess.ensureStudent(clazz.getId(), STUDENT_ID);
        classAccess.ensureStudent(clazz.getId(), second.getId());
        TaskFixture task = publishTask(clazz.getId());
        submitAnswer(task, STUDENT_ID);
        events.closeTask(task.eventId());

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        ReportBatchResponse partial = createBatch(task.eventId(), "finalize-a-" + UUID.randomUUID());
        batchService.finalizeIfComplete(partial.getId(), now);
        assertThat(freshBatch(partial.getId()).getStatus())
                .as("仍有 PENDING 子任务时不得收敛")
                .isEqualTo(ReportBatch.Status.PENDING);
        forceItemStatus(itemOf(partial.getId(), STUDENT_ID), ReportBatchItem.Status.FAILED);
        batchService.finalizeIfComplete(partial.getId(), now);
        assertThat(freshBatch(partial.getId()).getStatus())
                .isEqualTo(ReportBatch.Status.PARTIAL_FAILED);

        ReportBatchResponse allFailed = createBatch(task.eventId(), "finalize-b-" + UUID.randomUUID());
        forceItemStatus(itemOf(allFailed.getId(), STUDENT_ID), ReportBatchItem.Status.FAILED);
        forceItemStatus(itemOf(allFailed.getId(), second.getId()), ReportBatchItem.Status.FAILED);
        batchService.finalizeIfComplete(allFailed.getId(), now);
        assertThat(freshBatch(allFailed.getId()).getStatus())
                .isEqualTo(ReportBatch.Status.FAILED);

        ReportBatchResponse allSkipped = createBatch(task.eventId(), "finalize-c-" + UUID.randomUUID());
        forceItemStatus(itemOf(allSkipped.getId(), STUDENT_ID), ReportBatchItem.Status.SKIPPED);
        forceItemStatus(itemOf(allSkipped.getId(), second.getId()), ReportBatchItem.Status.SKIPPED);
        batchService.finalizeIfComplete(allSkipped.getId(), now);
        assertThat(freshBatch(allSkipped.getId()).getStatus())
                .isEqualTo(ReportBatch.Status.SUCCEEDED);
    }
}
