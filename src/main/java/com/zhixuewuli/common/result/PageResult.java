package com.zhixuewuli.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 分页结果响应包装类
 * <p>
 * 统一封装分页查询的返回格式，包含总数、页码、页大小和当前页数据。
 * 与前端约定的标准分页响应结构，便于前端进行分页渲染和导航。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 在 Controller 中返回分页结果
 * @GetMapping("/questions")
 * public Result<PageResult<QuestionDTO>> getQuestions(PageRequestDTO request) {
 *     Page<Question> page = questionRepository.findAll(
 *         PaginationUtils.createPageable(request)
 *     );
 *     return Result.success(
 *         PaginationUtils.toPageResult(page, request, QuestionMapper::toDTO)
 *     );
 * }
 * }</pre>
 *
 * <h3>响应示例：</h3>
 * <pre>{@code
 * {
 *     "code": 200,
 *     "message": "操作成功",
 *     "data": {
 *         "total": 156,
 *         "pages": 16,
 *         "currentPage": 1,
 *         "pageSize": 10,
 *         "records": [...]
 *     },
 *     "timestamp": 1710000000000
 * }
 * }</pre>
 *
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Schema(description = "分页结果响应")
@Data
@Builder
public class PageResult<T> {

    /**
     * 数据总条数
     * <p>
     * 表示符合条件的全部记录数量，用于前端计算总页数和显示"共X条记录"。
     * 与 pages 字段配合实现完整的分页导航功能。
     * </p>
     *
     * @example 156
     */
    @Schema(description = "数据总条数")
    private long total;

    /**
     * 总页数
     * <p>
     * 根据 total 和 pageSize 自动计算得出。
     * 用于前端渲染分页导航栏，如"共16页"。
     * </p>
     *
     * @example 16
     */
    @Schema(description = "总页数")
    private int pages;

    /**
     * 当前页码
     * <p>
     * 从1开始的页码，与前端约定保持一致。
     * 注意：数据库分页从0开始，本字段进行了+1转换。
     * </p>
     *
     * @example 1
     */
    @Schema(description = "当前页码（从1开始）")
    private int currentPage;

    /**
     * 每页数据条数
     * <p>
     * 表示每页返回的记录数量，应与请求中的 size 参数保持一致。
     * 用于前端确认每页展示的数据量。
     * </p>
     *
     * @example 10
     */
    @Schema(description = "每页数据条数")
    private int pageSize;

    /**
     * 当前页的数据列表
     * <p>
     * 泛型列表，包含当前页的所有数据记录。
     * 类型 T 由具体业务决定，如 QuestionDTO、UserDTO 等。
     * </p>
     *
     * @example [{"id": 1, "content": "题目内容..."}]
     */
    @Schema(description = "当前页数据列表")
    private List<T> records;
}
