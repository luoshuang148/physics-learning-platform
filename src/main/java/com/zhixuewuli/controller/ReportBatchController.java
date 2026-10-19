package com.zhixuewuli.controller;

import com.zhixuewuli.common.result.PageResult;
import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.common.security.AuthenticatedUser;
import com.zhixuewuli.common.security.TeacherOnly;
import com.zhixuewuli.dto.request.ReportBatchCreateRequest;
import com.zhixuewuli.dto.response.ReportBatchItemReportResponse;
import com.zhixuewuli.dto.response.ReportBatchItemResponse;
import com.zhixuewuli.dto.response.ReportBatchResponse;
import com.zhixuewuli.service.reportbatch.ReportBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 批量学生报告控制器。
 *
 * <p>教师对已截止任务发起批量报告生成，查看进度与逐条结果，读取报告并手动重试失败项。
 * 所有接口都重新校验教师对批次所属班级的权限，不能仅凭 batchId/itemId/reportId 访问。</p>
 */
@RestController
@RequestMapping("/teacher/report-batches")
@Tag(name = "批量学生报告", description = "基于 RabbitMQ 的批量报告生成与进度查询")
@RequiredArgsConstructor
@Slf4j
public class ReportBatchController {

    private final ReportBatchService reportBatchService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @TeacherOnly
    @Operation(summary = "创建批量报告批次",
            description = "校验班级权限与任务截止状态后创建批次；同教师同幂等键同参数返回原批次，"
                    + "同键不同参数会被拒绝。名单取任务已持久化的应参与名单。")
    public Result<ReportBatchResponse> create(
            @RequestBody @Valid ReportBatchCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        ReportBatchResponse response = reportBatchService.createBatch(user.id(), request);
        return Result.created("批量报告任务已创建", response);
    }

    @GetMapping
    @TeacherOnly
    @Operation(summary = "可访问的批量报告批次", description = "按当前班级权限分页，可按教学任务筛选")
    public Result<PageResult<ReportBatchResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long eventId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(reportBatchService.listBatches(user.id(), page, size, eventId));
    }

    @GetMapping("/{batchId}")
    @TeacherOnly
    @Operation(summary = "批次详情", description = "批次进度汇总；只允许批次所属班级的教师访问")
    public Result<ReportBatchResponse> detail(
            @PathVariable Long batchId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(reportBatchService.getBatch(user.id(), batchId));
    }

    @GetMapping("/{batchId}/items")
    @TeacherOnly
    @Operation(summary = "批次子任务列表", description = "分页返回名单内每名学生的生成状态、失败原因与报告引用")
    public Result<PageResult<ReportBatchItemResponse>> items(
            @PathVariable Long batchId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(reportBatchService.listItems(user.id(), batchId, status, page, size));
    }

    @PostMapping("/{batchId}/items/{itemId}/retry")
    @TeacherOnly
    @Operation(summary = "手动重试失败子任务",
            description = "仅失败项可重试；事务内改状态并写入新的发件箱记录，重复点击不会并行执行")
    public Result<ReportBatchItemResponse> retry(
            @PathVariable Long batchId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success("已加入重试队列", reportBatchService.retryItem(user.id(), batchId, itemId));
    }

    @GetMapping("/{batchId}/items/{itemId}/report")
    @TeacherOnly
    @Operation(summary = "读取批量生成的报告", description = "仅批次所属班级的教师可读；报告来自数据库中已持久化的内容")
    public Result<ReportBatchItemReportResponse> report(
            @PathVariable Long batchId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(reportBatchService.getItemReport(user.id(), batchId, itemId));
    }
}
