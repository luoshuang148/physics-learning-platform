package com.zhixuewuli.common.util;

import com.zhixuewuli.common.result.PageResult;
import com.zhixuewuli.dto.PageRequestDTO;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页工具类
 * <p>
 * 提供统一的分页处理逻辑，封装 Spring Data 分页与前端响应格式之间的转换。
 * 消除业务代码中的重复分页逻辑，保持代码整洁一致。
 * </p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>{@link #createPageable(PageRequestDTO)} - 将请求参数转换为 Spring Data Pageable</li>
 *   <li>{@link #toPageResult(Page, PageRequestDTO, Function)} - 将分页结果转换为统一响应格式</li>
 *   <li>{@link #manualPagination(List, PageRequestDTO, Function)} - 内存级手动分页（适用于预加载场景）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. Repository 层返回 Page 对象
 * Page<Question> page = questionRepository.findAll(
 *     PaginationUtils.createPageable(request)
 * );
 *
 * // 2. 使用工具类转换成分页结果（带实体转 DTO）
 * PageResult<QuestionDTO> result = PaginationUtils.toPageResult(
 *     page,
 *     request,
 *     QuestionMapper::toDTO  // 或 question -> new QuestionDTO(question)
 * );
 *
 * // 3. 返回给前端
 * return Result.success(result);
 * }</pre>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>前端页码从1开始，后端自动转换为0基索引</li>
 *   <li>手动分页({@code manualPagination})仅适用于数据量较小的场景</li>
 *   <li>优先使用数据库分页，性能更优</li>
 * </ul>
 *
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see PageRequestDTO 分页请求参数定义
 * @see PageResult 分页响应包装类
 * @see Pageable Spring Data 分页接口
 */
public class PaginationUtils {

    /**
     * 将分页请求参数转换为 Spring Data 的 Pageable 对象
     * <p>
     * 封装页码转换逻辑（前端1基 → 后端0基），并支持排序参数配置。
     * 用于 Repository 层的分页查询方法。
     * </p>
     *
     * <h3>转换规则</h3>
     * <ul>
     *   <li>前端传入 page=1，后端转换为 page=0</li>
     *   <li>sortDirection 支持 "asc"/"desc"（大小写不敏感）</li>
     *   <li>不指定排序时，返回按主键升序的分页结果</li>
     * </ul>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 请求：page=2, size=10, sortBy=createdAt, sortDirection=desc
     * // 结果：PageRequest.of(1, 10, Sort.by(DESC, "createdAt"))
     *
     * Pageable pageable = PaginationUtils.createPageable(request);
     * Page<Question> page = repository.findAll(pageable);
     * }</pre>
     *
     * @param pageRequest 前端传来的分页请求参数，包含页码、每页大小、排序字段和方向
     * @return Spring Data 可用的 Pageable 对象，可直接传递给 Repository 方法
     * @throws NullPointerException 如果 pageRequest 为 null
     * @see PageRequest#of(int, int, Sort) Spring Data 分页创建方法
     */
    public static Pageable createPageable(PageRequestDTO pageRequest) {
        if (pageRequest.getSortBy() != null && !pageRequest.getSortBy().isEmpty()) {
            // 排序方向转换：字符串 -> Sort.Direction 枚举
            Sort.Direction direction = "desc".equalsIgnoreCase(pageRequest.getSortDirection())
                            ? Sort.Direction.DESC
                            : Sort.Direction.ASC;

            // 构建带排序的分页请求
            // 注意：page 参数 - 1，实现 1基 -> 0基 转换
            return PageRequest.of(
                pageRequest.getPage() - 1,
                pageRequest.getSize(),
                Sort.by(direction, pageRequest.getSortBy())
            );
        }

        // 无排序要求时，返回简单的分页请求
        return PageRequest.of(pageRequest.getPage() - 1, pageRequest.getSize());
    }

    /**
     * 将 Spring Data Page 分页结果转换为统一的 PageResult 响应格式
     * <p>
     * 统一处理实体到 DTO 的转换，封装分页元数据的构建逻辑。
     * 自动从 Page 对象提取总数、总页数，并从请求参数获取当前页码和每页大小。
     * </p>
     *
     * <h3>转换流程</h3>
     * <pre>{@code
     * Page<Question>                          PageResult<QuestionDTO>
     * ┌─────────────────────┐                 ┌─────────────────────────┐
     * │ content: List<Que.>│  ──map(converter)──>  │ records: List<QuestionDTO>│
     * │ totalElements: 156  │                 │ total: 156              │
     * │ totalPages: 16     │                 │ pages: 16               │
     * └─────────────────────┘                 │ currentPage: (from req) │
     *                                         │ pageSize: (from req)    │
     *                                         └─────────────────────────┘
     * }</pre>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 在 Service 或 Controller 层使用
     * @GetMapping("/questions")
     * public Result<PageResult<QuestionDTO>> listQuestions(
     *         @RequestParam(defaultValue = "1") int page,
     *         @RequestParam(defaultValue = "10") int size) {
     *
     *     PageRequestDTO request = new PageRequestDTO(page, size);
     *     Page<Question> page = questionRepository.findByActiveTrue(
     *         PaginationUtils.createPageable(request)
     *     );
     *
     *     // 转换并返回
     *     return Result.success(
     *         PaginationUtils.toPageResult(page, request, QuestionMapper::toDTO)
     *     );
     * }
     * }</pre>
     *
     * @param page      Repository 层返回的 Spring Data 分页结果
     * @param request   原始的分页请求参数（用于获取当前页码和每页大小）
     * @param converter 实体到 DTO 的转换函数，支持方法引用或 lambda 表达式
     * @param <T>       实体类型（Page 中的原始数据类型）
     * @param <R>       DTO 类型（转换后的目标类型）
     * @return 统一格式的分页响应对象，可直接返回给前端
     * @see PageResult 分页响应包装类
     * @see Page#getContent() 获取分页内容
     * @see Page#getTotalElements() 获取总记录数
     */
    public static <T, R> PageResult<R> toPageResult(
            Page<T> page,
            PageRequestDTO request,
            Function<T, R> converter) {

        return PageResult.<R>builder()
                // 从 Spring Data Page 提取分页元数据
                .total(page.getTotalElements())     // 总记录数
                .pages(page.getTotalPages())        // 总页数

                // 从请求参数获取当前页信息
                .currentPage(request.getPage())     // 当前页（保持1基）
                .pageSize(request.getSize())        // 每页大小

                // 转换内容列表：实体 -> DTO
                .records(
                    page.getContent()
                        .stream()
                        .map(converter)             // 执行转换
                        .collect(Collectors.toList())
                )
                .build();
    }

    /**
     * 手动分页处理（适用于预加载查询场景）
     * <p>
     * 当无法使用数据库分页时（如数据已全部加载到内存），提供内存级分页功能。
     * 在列表上执行内存切片，避免一次性返回全部数据导致内存溢出。
     * </p>
     *
     * <h3>适用场景</h3>
     * <ul>
     *   <li>关联查询场景：主表已JOIN其他表，需要在应用层过滤</li>
     *   <li>缓存预热：从 Redis 批量加载数据后进行分页</li>
     *   <li>跨库查询：无法使用统一分页语法的复杂查询</li>
     * </ul>
     *
     * <h3>不适用场景</h3>
     * <ul>
     *   <li>大数据量场景（>10000条）：内存分页效率低，建议使用数据库分页</li>
     *   <li>频繁查询场景：每次查询都需加载全部数据，性能差</li>
     *   <li>生产环境核心接口：优先考虑 SQL 分页或 ES 分页</li>
     * </ul>
     *
     * <h3>性能警告</h3>
     * <p>
     * 手动分页的时间复杂度为 O(n)，数据量大时性能急剧下降。
     * 建议仅在确认数据量较小（<1000条）时使用，或用于非核心的辅助查询。
     * </p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 从缓存批量加载数据
     * List<Question> allQuestions = cacheService.getAllQuestions();
     *
     * // 应用层过滤（假设过滤后数据量不大）
     * List<Question> filtered = allQuestions.stream()
     *     .filter(q -> q.getDifficulty() >= 3)
     *     .collect(Collectors.toList());
     *
     * // 手动分页
     * PageRequestDTO request = new PageRequestDTO(1, 10);
     * PageResult<QuestionDTO> result = PaginationUtils.manualPagination(
     *     filtered,
     *     request,
     *     QuestionMapper::toDTO
     * );
     * }</pre>
     *
     * @param items      完整的数据列表（已在内存中的数据集合）
     * @param pageRequest 分页请求参数，包含页码、每页大小
     * @param converter  实体到 DTO 的转换函数
     * @param <T>       原始数据项类型
     * @param <R>       转换后的 DTO 类型
     * @return 分页后的响应结果，包含当前页数据和分页元信息
     */
    public static <T, R> PageResult<R> manualPagination(
            List<T> items,
            PageRequestDTO pageRequest,
            Function<T, R> converter) {

        // 计算分页边界
        int page = pageRequest.getPage();
        int pageSize = pageRequest.getSize();

        // 计算起始索引（0基）
        int fromIndex = (page - 1) * pageSize;

        // 边界检查：起始索引超出列表长度，返回空结果
        if (fromIndex >= items.size()) {
            return PageResult.<R>builder()
                    .total(items.size())
                    .pages((int) Math.ceil((double) items.size() / pageSize))
                    .currentPage(page)
                    .pageSize(pageSize)
                    .records(List.of())
                    .build();
        }

        // 计算结束索引（不包含）
        int toIndex = Math.min(fromIndex + pageSize, items.size());

        // 内存切片 + 转换
        List<R> pagedRecords = items.subList(fromIndex, toIndex)
                .stream()
                .map(converter)
                .collect(Collectors.toList());

        return PageResult.<R>builder()
                .total(items.size())
                .pages((int) Math.ceil((double) items.size() / pageSize))
                .currentPage(page)
                .pageSize(pageSize)
                .records(pagedRecords)
                .build();
    }
}