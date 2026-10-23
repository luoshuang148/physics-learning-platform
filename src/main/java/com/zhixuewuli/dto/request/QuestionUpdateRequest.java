package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 题目更新请求DTO
 * 
 * 封装更新现有题目时所需的各项参数信息。
 * 采用全量覆盖更新策略，所有字段都会覆盖原数据。
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li><strong>全量覆盖更新</strong>: 更新请求中的所有字段都会覆盖原有数据</li>
 * <li><strong>ID必填</strong>: 必须指定要更新的题目ID</li>
 * <li><strong>字段完整性</strong>: 除了ID外，其他字段与创建请求保持一致</li>
 * <li><strong>空值处理</strong>: 空字段会用null值覆盖原数据</li>
 * </ul>
 * 
 * <p>
 * 更新策略说明：
 * </p>
 * <p>
 * 采用全量覆盖更新而非部分更新的原因：
 * </p>
 * <ul>
 * <li>简化客户端使用，避免复杂的更新逻辑</li>
 * <li>确保数据一致性，避免部分字段残留问题</li>
 * <li>符合教育场景的使用习惯</li>
 * <li>便于版本控制和变更追踪</li>
 * </ul>
 * 
 * <p>
 * 使用示例：
 * </p>
 * 
 * <pre>
 * // 完整更新 - 所有字段都提供新值
 * {
 *   "id": 1001,
 *   "content": "更新后的题目内容...",
 *   "options": "{\"A\": \"新选项A\", \"B\": \"新选项B\"}",
 *   "correctOption": "A",
 *   "explanation": "更新后的解析..."
 * }
 * 
 * // 部分字段清空 - 空字符串会被视为要清空该字段
 * {
 *   "id": 1001,
 *   "content": "题目内容保持不变",
 *   "options": "{\"A\": \"选项A\", \"B\": \"选项B\"}",
 *   "correctOption": "A",
 *   "explanation": "",  // 这会清空原有的解析内容
 *   "difficulty": null  // 这会清空难度等级
 * }
 * </pre>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see QuestionCreateRequest 题目创建请求DTO
 * @see com.zhixuewuli.controller.QuestionController 题目控制器
 */
@Schema(description = "题目更新请求参数")
@Data
public class QuestionUpdateRequest {

    /**
     * 题目ID
     * 
     * 必须填写，不能为空。
     * 指定要更新的目标题目记录。
     */
    @NotNull(message = "题目ID不能为空")
    @Schema(description = "题目ID", example = "1001")
    private Long id;

    /**
     * 题目内容
     * 
     * 必须填写，不能为空白字符串。
     * 统一使用LaTeX格式存储物理量、单位和数学表达式。
     * 例如：质量为$2\,\mathrm{kg}$的物体，速度$v=3\,\mathrm{m/s}$
     */
    @NotBlank(message = "题目内容不能为空")
    @Schema(description = "题目内容（LaTeX格式）", example = "更新后的题目内容...")
    private String content;

    /**
     * 题目选项
     * 
     * 必须填写，不能为空白字符串。
     * 使用JSON格式的Map结构存储，key为选项标识(A/B/C/D)，value为选项内容。
     * 示例：{"A": "$2\\,\\mathrm{m/s^2}$", "B": "$5\\,\\mathrm{m/s^2}$", "C":
     * "$10\\,\\mathrm{m/s^2}$", "D": "$20\\,\\mathrm{m/s^2}$"}
     */
    @NotBlank(message = "题目选项不能为空")
    @Schema(description = "题目选项（JSON格式Map结构）", example = "{\"A\": \"更新后的选项A\", \"B\": \"更新后的选项B\", \"C\": \"更新后的选项C\", \"D\": \"更新后的选项D\"}")
    private String options;

    /**
     * 正确答案选项标识
     * 
     * 必须填写，且必须是A、B、C、D中的一个。
     * 表示该题目正确的选项标识符。
     */
    @NotBlank(message = "正确答案不能为空")
    @Pattern(regexp = "^[A-D]$", message = "正确答案必须是A/B/C/D之一")
    @Schema(description = "正确答案选项标识", example = "C")
    private String correctOption;

    /**
     * 题目解析说明
     * 
     * 可选参数，用于提供题目的详细解析过程。
     * 统一使用LaTeX格式，支持复杂的数学推导和公式展示。
     * 注意：如果传入空字符串或null，会清空原有的解析内容。
     */
    @Schema(description = "题目解析说明（LaTeX格式）", example = "更新后的解析说明...")
    private String explanation;

    /**
     * 题目难度等级
     * 
     * 可选参数，表示题目的难易程度。
     * 取值范围为1-5，数值越大表示难度越高。
     * 注意：如果传入null，会清空原有的难度等级。
     */
    @Schema(description = "题目难度等级（1-5）", example = "4")
    private Integer difficulty;

    @Schema(description = "教师维护的知识点代码")
    @jakarta.validation.constraints.Size(max = 15, message = "每题最多选择15个知识点")
    private java.util.List<@jakarta.validation.constraints.Pattern(regexp = "^[a-z0-9.-]{1,100}$") String> knowledgePointCodes = java.util.List.of();

    /**
     * 题目图片Base64编码数据（仅支持PNG格式）
     * 
     * 可选参数，用于存储与题目相关的图片信息。
     * 格式要求：data:image/png;base64,[编码数据]
     * 注意：如果传入空字符串或null，会清空原有的图片数据。
     * 示例：data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==
     */
    @Schema(description = "题目图片Base64编码数据", example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==")
    private String imageBase64;
}
