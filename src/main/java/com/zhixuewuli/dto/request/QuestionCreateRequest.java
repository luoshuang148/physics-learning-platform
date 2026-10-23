package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 题目创建请求DTO
 * 
 * 封装创建新题目时所需的各项参数信息。
 * 用于题目管理接口的请求数据传输对象。
 * 
 * <p>
 * 参数说明：
 * </p>
 * <ul>
 * <li><strong>content</strong>: 必填项，题目内容（LaTeX 格式）</li>
 * <li><strong>options</strong>: 必填项，题目选项（JSON 格式 Map 结构）</li>
 * <li><strong>correctOption</strong>: 必填项，正确答案选项标识</li>
 * <li><strong>explanation</strong>: 可选项，题目解析说明（LaTeX 格式）</li>
 * <li><strong>difficulty</strong>: 可选项，题目难度等级（1-5）</li>
 * </ul>
 * 
 * <p>
 * 验证规则：
 * </p>
 * <ul>
 * <li>视频ID不能为空且必须为正数</li>
 * <li>题目内容不能为空且使用LaTeX格式</li>
 * <li>选项必须为有效的JSON格式</li>
 * <li>正确答案必须是A/B/C/D之一</li>
 * <li>难度等级限定在1-5范围内</li>
 * </ul>
 * 
 * <p>
 * 业务场景：
 * </p>
 * <ul>
 * <li>教师创建新的练习题目</li>
 * <li>题库内容的扩充和维护</li>
 * <li>教学资源的数字化管理</li>
 * <li>个性化学习内容配置</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see com.zhixuewuli.controller.QuestionController 题目控制器
 * @see com.zhixuewuli.service.question.QuestionService 题目服务
 */
@Schema(description = "题目创建请求参数")
@Data
public class QuestionCreateRequest {

    /**
     * 题目内容
     * 
     * 必须填写，不能为空白字符串。
     * 统一使用LaTeX格式存储物理量、单位和数学表达式。
     * 例如：质量为$2\,\mathrm{kg}$的物体，速度$v=3\,\mathrm{m/s}$
     */
    @NotBlank(message = "题目内容不能为空")
    @Schema(description = "题目内容（LaTeX格式）", example = "质量为$2\\,\\mathrm{kg}$的物体，受到$10\\,\\mathrm{N}$的水平力作用，求加速度$a$。")
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
    @Schema(description = "题目选项（JSON格式Map结构）", example = "{\"A\": \"$2\\\\,\\\\mathrm{m/s^2}$\", \"B\": \"$5\\\\,\\\\mathrm{m/s^2}$\", \"C\": \"$10\\\\,\\\\mathrm{m/s^2}$\", \"D\": \"$20\\\\,\\\\mathrm{m/s^2}$\"}")
    private String options;

    /**
     * 正确答案选项标识
     * 
     * 必须填写，且必须是A、B、C、D中的一个。
     * 表示该题目正确的选项标识符。
     */
    @NotBlank(message = "正确答案不能为空")
    @Pattern(regexp = "^[A-D]$", message = "正确答案必须是A/B/C/D之一")
    @Schema(description = "正确答案选项标识", example = "B")
    private String correctOption;

    /**
     * 题目解析说明
     * 
     * 可选参数，用于提供题目的详细解析过程。
     * 统一使用LaTeX格式，支持复杂的数学推导和公式展示。
     */
    @Schema(description = "题目解析说明（LaTeX格式）", example = "根据牛顿第二定律$F=ma$，可得$a=\\frac{F}{m}=\\frac{10}{2}=5\\,\\mathrm{m/s^2}$")
    private String explanation;

    /**
     * 题目难度等级
     * 
     * 可选参数，表示题目的难易程度。
     * 取值范围为1-5，数值越大表示难度越高。
     * 1: 基础入门级
     * 2: 简单应用级
     * 3: 中等掌握级
     * 4: 较难题目级
     * 5: 高难度挑战级
     */
    @Schema(description = "题目难度等级（1-5）", example = "3")
    private Integer difficulty;

    @Schema(description = "教师维护的知识点代码")
    @jakarta.validation.constraints.Size(max = 15, message = "每题最多选择15个知识点")
    private java.util.List<@jakarta.validation.constraints.Pattern(regexp = "^[a-z0-9.-]{1,100}$") String> knowledgePointCodes = java.util.List.of();

    /**
     * 题目图片Base64编码数据（仅支持PNG格式）
     * 
     * 可选参数，用于存储与题目相关的图片信息。
     * 格式要求：data:image/png;base64,[编码数据]
     * 示例：data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==
     */
    @Schema(description = "题目图片Base64编码数据", example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==")
    private String imageBase64;
}
