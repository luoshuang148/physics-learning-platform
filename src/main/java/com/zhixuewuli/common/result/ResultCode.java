package com.zhixuewuli.common.result;

import lombok.Getter;

/**
 * API响应状态码枚举类
 * 
 * 定义系统中所有可能的响应状态码及其对应的描述信息。
 * 采用HTTP状态码标准结合业务需求进行扩展。
 * 
 * <p>
 * 状态码分类：
 * </p>
 * <ul>
 * <li><strong>2xx系列</strong>: 成功状态码</li>
 * <li><strong>4xx系列</strong>: 客户端错误状态码</li>
 * <li><strong>5xx系列</strong>: 服务器错误状态码</li>
 * <li><strong>4000+系列</strong>: 业务逻辑错误码</li>
 * <li><strong>5000+系列</strong>: 技术异常错误码</li>
 * </ul>
 * 
 * <p>
 * 使用规范：
 * </p>
 * <ul>
 * <li>严格按照HTTP状态码语义使用</li>
 * <li>新增状态码需遵循现有编号规则</li>
 * <li>错误消息应简洁明了，便于前端展示</li>
 * <li>避免过度细分状态码导致维护困难</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see Result 统一响应结果类
 */
@Getter
public enum ResultCode {

    // ==================== 通用状态码 (x000-x099) ==================

    /** 业务逻辑错误 */
    BUSINESS_ERROR(4001, "业务逻辑错误"),

    /** 会话状态不正确 */
    SESSION_STATUS_INVALID(4002, "会话状态不正确"),

    /** 会话不存在 */
    SESSION_NOT_FOUND(4003, "会话不存在"),

    /** 技术异常错误 */
    TECHNICAL_ERROR(5001, "系统内部错误"),

    /** 数据库连接失败 */
    DATABASE_CONNECTION_FAILED(5002, "数据库连接失败"),

    /** 数据库访问失败 */
    DATABASE_ACCESS_FAILED(5003, "数据库访问失败"),

    /** 数据库约束违反 */
    DATABASE_CONSTRAINT_VIOLATION(5004, "数据库约束违反"),

    /** 第三方服务调用失败 */
    THIRD_PARTY_SERVICE_ERROR(5005, "第三方服务调用失败"),

    /** 文件系统错误 */
    FILE_SYSTEM_ERROR(5006, "文件系统错误"),

    /** 网络通信错误 */
    NETWORK_COMMUNICATION_ERROR(5007, "网络通信错误"),

    /** 系统资源不足 */
    SYSTEM_RESOURCE_INSUFFICIENT(5008, "系统资源不足"),

    // ==================== 用户模块状态码 (x100-x199) ====================

    /** 用户状态不正确 */
    USER_STATUS_INVALID(5101, "用户状态不正确"),

    /** 用户不存在 */
    USER_NOT_FOUND(4102, "用户不存在"),

    /** 用户已存在 */
    USER_ALREADY_EXISTS(4103, "用户已存在"),

    /** 用户账户已锁定 */
    USER_ACCOUNT_LOCKED(4104, "用户账户已锁定"),

    /** 用户密码错误 */
    USER_PASSWORD_INCORRECT(4105, "密码错误"),

    /** 用户已被禁用 */
    USER_DISABLED(4106, "用户已被禁用"),

    /** 用户名称重复 */
    USER_NAME_DUPLICATE(4107, "用户名称重复"),

    /** 真实姓名重复 */
    REAL_NAME_DUPLICATE(4108, "真实姓名重复"),

    /** 用户名称不合法 */
    USER_NAME_INVALID(4109, "用户名称不合法"),

    /** 真实姓名不合法 */
    REAL_NAME_INVALID(4110, "真实姓名不合法"),

    /** 令牌无效或已过期 */
    INVALID_TOKEN(4111, "令牌无效或已过期"),

    // ==================== 题目模块状态码 (x200-x299) ====================

    /** 题目状态不正确 */
    QUESTION_STATUS_INVALID(5201, "题目状态不正确"),

    /** 题目不存在 */
    QUESTION_NOT_FOUND(4202, "题目不存在"),

    /** 题目内容重复 */
    QUESTION_CONTENT_DUPLICATE(4203, "题目内容重复"),

    /** 题目关联视频不存在 */
    QUESTION_VIDEO_NOT_FOUND(4204, "题目关联视频不存在"),

    // ==================== 视频模块状态码 (x300-x399) ====================

    /** 视频状态不正确 */
    VIDEO_STATUS_INVALID(5301, "视频状态不正确"),

    /** 视频不存在 */
    VIDEO_NOT_FOUND(4302, "视频不存在"),

    /** 视频已存在 */
    VIDEO_ALREADY_EXISTS(4303, "视频已存在"),

    /** 视频上传失败 */
    VIDEO_UPLOAD_FAILED(5304, "视频上传失败"),

    /** 视频格式不支持 */
    VIDEO_FORMAT_UNSUPPORTED(4305, "视频格式不支持"),

    // ==================== 答题记录模块状态码 (x400-x499) ====================

    /** 答题记录状态不正确 */
    ANSWER_RECORD_STATUS_INVALID(5401, "答题记录状态不正确"),

    /** 答题记录不存在 */
    ANSWER_RECORD_NOT_FOUND(4402, "答题记录不存在"),

    /** 答题记录处理失败 */
    ANSWER_RECORD_PROCESSING_FAILED(5403, "答题记录处理失败"),

    // ==================== AI模块状态码 (x500-x599) ====================

    /** AI服务不可用 */
    AI_SERVICE_UNAVAILABLE(5501, "AI服务不可用"),

    /** AI响应超时 */
    AI_TIMEOUT(5502, "AI响应超时"),

    /** AI响应解析失败 */
    AI_PARSE_ERROR(5503, "AI响应解析失败"),

    /** AI配额不足 */
    AI_QUOTA_EXCEEDED(5504, "AI配额不足"),

    /** AI模型不支持 */
    AI_MODEL_UNSUPPORTED(5505, "AI模型不支持"),

    /** 会话操作失败 */
    SESSION_OPERATION_FAILED(5506, "会话操作失败"),

    /** Token计算错误 */
    TOKEN_CALCULATION_ERROR(5507, "Token计算错误"),

    /** 会话已失效 */
    SESSION_EXPIRED(4508, "会话已失效"),

    /** 题目ID为空 */
    INVALID_QUESTION_ID(4509, "题目ID为空"),

    // ==================== 错题分析模块状态码 (x600-x699) ================

    /** 错题分析服务不可用 */
    QUESTION_ERROR_ANALYSIS_SERVICE_UNAVAILABLE(5601, "错题分析服务不可用"),

    /** 词云分析失败 */
    WORD_CLOUD_ANALYSIS_FAILED(5602, "词云分析失败"),

    /** 分词处理失败 */
    SEGMENTATION_FAILED(5603, "分词处理失败"),

    // ==================== 教师事件模块与实验模块状态码 (x700-x799) ====================

    /** 教师事件不正确 */
    TEACHER_EVENT_STATUS_INVALID(5701, "教师事件不正确"),

    /** 教师事件已存在 */
    TEACHER_EVENT_ALREADY_EXISTS(4702, "教师事件已存在"),

    /** 教师事件不存在 */
    TEACHER_EVENT_NOT_FOUND(4703, "教师事件不存在"),



    /** 实验状态不正确 */
    EXPERIMENT_STATUS_INVALID(5711, "实验状态不正确"),

    /** 实验不存在 */
    EXPERIMENT_NOT_FOUND(4712, "实验不存在"),

    /** 实验名称已存在 */
    EXPERIMENT_NAME_DUPLICATE(4713, "实验名称已存在"),

    /** 实验序号已存在 */
    EXPERIMENT_ORDER_NUMBER_DUPLICATE(4714, "实验序号已存在"),

    /** 题目ID列表为空 */
    INVALID_QUESTION_IDS_FORMAT(4715, "题目ID列表为空"),

    // ==================== 权限模块状态码 (4800-4899) ====================

    /** 权限不足 */
    PERMISSION_DENIED(4801, "权限不足"),

    /** 教师权限不足 */
    INSUFFICIENT_TEACHER_PERMISSION(4802, "教师权限不足"),

    /** 学生权限不足 */
    INSUFFICIENT_STUDENT_PERMISSION(4803, "学生权限不足"),

    /** 管理员权限不足 */
    INSUFFICIENT_ADMIN_PERMISSION(4804, "管理员权限不足"),

    // ==================== 参数校验模块状态码 (4900-4999) ====================

    /** 参数校验失败 */
    VALIDATION_FAILED(4900, "参数校验失败"),

    /** 请求参数缺失 */
    MISSING_PARAMETER(4901, "缺少必需参数"),

    /** 参数类型不匹配 */
    PARAMETER_TYPE_MISMATCH(4902, "参数类型不匹配"),

    /** 参数格式错误 */
    PARAMETER_FORMAT_INVALID(4903, "参数格式错误"),

    /** 参数值超出范围 */
    PARAMETER_OUT_OF_RANGE(4904, "参数值超出允许范围"),

    /** 参数长度不合法 */
    PARAMETER_LENGTH_INVALID(4905, "参数长度不合法"),

    /** 参数内容不合法 */
    PARAMETER_CONTENT_INVALID(4906, "参数内容不合法"),

    /** 请求体解析失败 */
    REQUEST_BODY_PARSE_FAILED(4907, "请求体解析失败"),

    /** 绑定异常 */
    BINDING_EXCEPTION(4908, "数据绑定异常"),

    /** 约束违反 */
    CONSTRAINT_VIOLATION(4909, "约束条件违反");

    /** 状态码值 */
    private final int code;

    /** 状态码描述信息 */
    private final String message;

    /**
     * 构造函数
     * 
     * @param code    状态码
     * @param message 描述信息
     */
    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
