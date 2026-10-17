package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 答题记录批量响应DTO
 * 
 * 专门用于封装批量答题记录查询的结果，包含记录列表和统计信息。
 * 与单个答题记录响应DTO分离，避免循环嵌套问题。
 * 
 * <p>
 * 数据结构组成：
 * </p>
 * <ul>
 * <li><strong>记录列表</strong>: 答题记录响应对象列表</li>
 * <li><strong>统计信息</strong>: 总数、分页信息等</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>专为批量操作设计，职责单一</li>
 * <li>支持分页和统计信息</li>
 * <li>与AnswerRecordResponse组合使用</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AnswerRecordResponse 单个答题记录响应DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "答题记录批量响应")
public class AnswerRecordBatchResponse {

    /**
     * 答题记录列表
     * 
     * 批量查询时返回的答题记录响应对象列表。
     * 按答题时间倒序排列。
     */
    @Schema(description = "答题记录列表")
    private List<AnswerRecordResponse> records;

    /**
     * 总记录数
     * 
     * 批量查询结果的总记录数。
     */
    @Schema(description = "总记录数", example = "15")
    private Long totalCount;

    /**
     * 当前页码
     * 
     * 分页查询时的当前页码（从1开始）。
     */
    @Schema(description = "当前页码", example = "1")
    private Integer currentPage;

    /**
     * 每页大小
     * 
     * 分页查询时每页显示的记录数。
     */
    @Schema(description = "每页大小", example = "10")
    private Integer pageSize;

    /**
     * 总页数
     * 
     * 根据总记录数和每页大小计算得出的总页数。
     */
    @Schema(description = "总页数", example = "2")
    private Long totalPages;
}