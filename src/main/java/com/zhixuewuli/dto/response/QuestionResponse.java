package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 题目响应DTO
 * 
 * 用于向前端返回题目的完整信息。
 * 包含题目的基本属性、关联信息和时间戳信息。
 * 
 * <p>
 * 数据结构：
 * </p>
 * <ul>
 * <li>题目基础信息（ID、内容、选项等）</li>
 * <li>关联信息（视频ID、正确答案等）</li>
 * <li>辅助信息（难度、解析等）</li>
 * <li>时间信息（创建时间、更新时间）</li>
 * </ul>
 * 
 * <p>
 * 使用场景：
 * </p>
 * <ul>
 * <li>题目详情查询响应</li>
 * <li>题目列表查询响应</li>
 * <li>题目创建/更新成功响应</li>
 * <li>题目删除前的信息确认</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>使用Builder模式支持灵活构建</li>
 * <li>包含完整的题目信息</li>
 * <li>时间字段便于审计追踪</li>
 * <li>与实体类保持良好的映射关系</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see com.zhixuewuli.entity.Question 题目实体类
 * @see com.zhixuewuli.controller.QuestionController 题目控制器
 */
@Schema(description = "题目响应数据")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {
    private String snapshotOrigin;

    /**
     * 题目ID
     * 
     * 题目的唯一标识符，在题目表中全局唯一。
     * 用于标识和检索特定的题目记录。
     */
    @Schema(description = "题目ID", example = "1001")
    private Long id;
    
    /**
     * 题目内容
     * 
     * 题目的具体内容，统一使用LaTeX格式存储。
     * 包含所有的物理量、单位和数学表达式。
     * 例如：质量为$2\,\mathrm{kg}$的物体，速度$v=3\,\mathrm{m/s}$
     */
    @Schema(description = "题目内容（LaTeX格式）", example = "质量为$2\\,\\mathrm{kg}$的物体，受到$10\\,\\mathrm{N}$的水平力作用，求加速度$a$。")
    private String content;

    /**
     * 题目选项
     * 
     * 题目的选项信息，使用JSON格式的Map结构存储。
     * key为选项标识(A/B/C/D)，value为选项内容(LaTeX格式)。
     * 示例：{"A": "$2\\,\\mathrm{m/s^2}$", "B": "$5\\,\\mathrm{m/s^2}$", "C":
     * "$10\\,\\mathrm{m/s^2}$", "D": "$20\\,\\mathrm{m/s^2}$"}
     */
    @Schema(description = "题目选项（键值对映射）")
    private Map<String, String> options;

    /**
     * 正确答案选项标识
     * 
     * 表示该题目正确答案的选项标识符。
     * 取值为A、B、C、D中的一个。
     */
    @Schema(description = "正确答案选项标识", example = "B")
    private String correctOption;

    /**
     * 题目解析说明
     * 
     * 题目的详细解析过程和解答思路。
     * 统一使用LaTeX格式，支持复杂的数学推导和公式展示。
     * 可能为空，表示暂无解析。
     */
    @Schema(description = "题目解析说明（LaTeX格式）", example = "根据牛顿第二定律$F=ma$，可得$a=\\frac{F}{m}=\\frac{10}{2}=5\\,\\mathrm{m/s^2}$")
    private String explanation;

    /**
     * 题目难度等级
     * 
     * 题目的难易程度评级。
     * 取值范围为1-5，数值越大表示难度越高。
     * 1: 基础入门级
     * 2: 简单应用级
     * 3: 中等掌握级
     * 4: 较难题目级
     * 5: 高难度挑战级
     * 可能为空，表示未设置难度等级。
     */
    @Schema(description = "题目难度等级（1-5）", example = "3")
    private Integer difficulty;

    /**
     * 讲解视频映射
     * 
     * 各选项对应的讲解视频映射关系。
     * 使用Map结构存储，key为选项标识(A/B/C/D)，value为视频ID(Long类型)。
     * 示例：{"A": 123, "B": 456, "C": 789, "D": 101}
     * 可能为空，表示暂无视频映射。
     */
    @Schema(description = "讲解视频映射（选项到视频ID的映射）")
    private Map<String, Long> explanationVideoMapping;

    /**
     * 题目图片Base64编码数据（仅支持PNG格式）
     * 
     * 与题目相关的图片信息，采用Base64编码格式存储。
     * 格式要求：data:image/png;base64,[编码数据]
     * 可能为空，表示该题目没有关联图片。
     */
    @Schema(description = "题目图片Base64编码数据", example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==")
    private String imageBase64;

    /**
     * 创建时间
     * 
     * 题目记录的创建时间戳。
     * 用于记录题目首次入库的时间，便于审计和统计。
     */
    @Schema(description = "创建时间", example = "2026-01-15T10:30:00")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * 
     * 题目记录的最后更新时间戳。
     * 用于记录题目的最新修改时间，便于版本控制和变更追踪。
     */
    @Schema(description = "更新时间", example = "2026-01-15T15:45:00")
    private LocalDateTime updatedAt;
}