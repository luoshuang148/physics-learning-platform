package com.zhixuewuli.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 统一API响应结果封装类
 * 
 * 为所有RESTful API接口提供标准化的响应格式。
 * 支持泛型数据类型，可承载任意业务数据。
 * 
 * <p>
 * 响应格式规范：
 * </p>
 * 
 * <pre>
 * {
 *   "code": 200,
 *   "message": "操作成功",
 *   "data": {},
 *   "timestamp": 1700000000000
 * }
 * </pre>
 * 
 * <p>
 * 设计原则：
 * </p>
 * <ul>
 * <li><strong>统一性</strong>: 所有接口保持一致的响应结构</li>
 * <li><strong>可读性</strong>: 字段命名清晰，含义明确</li>
 * <li><strong>扩展性</strong>: 支持泛型，适应不同数据类型</li>
 * <li><strong>时效性</strong>: 包含时间戳便于调试和监控</li>
 * </ul>
 * 
 * @param <T> 响应数据的具体类型
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see ResultCode 响应状态码枚举类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 响应状态码 */
    private int code;

    /** 响应消息描述 */
    private String message;

    /** 响应数据内容 */
    private T data;

    /** 响应时间戳（毫秒） */
    private long timestamp;

    /** 错误原因 */
    private String reason;

    /**
     * 创建成功的响应结果
     * 
     * 使用标准HTTP 200状态码，适用于标准成功场景。
     * 
     * @param <T>  数据类型泛型
     * @param data 响应数据
     * @return Result<T> 成功响应对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(HttpStatus.OK.value(), "操作成功", data,
                System.currentTimeMillis(), null);
    }

    /**
     * 创建带自定义消息的成功响应
     * 
     * 使用标准HTTP 200状态码，允许指定个性化成功消息。
     * 
     * @param <T>     数据类型泛型
     * @param message 自定义成功消息
     * @param data    响应数据
     * @return Result<T> 成功响应对象
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(HttpStatus.OK.value(), message, data, System.currentTimeMillis(), null);
    }

    /**
     * 创建无数据的成功响应
     * 
     * 使用标准HTTP 200状态码，适用于仅需确认操作成功的场景。
     * 
     * @return Result<Void> 无数据的成功响应
     */
    public static Result<Void> success() {
        return new Result<>(HttpStatus.OK.value(), "操作成功", null,
                System.currentTimeMillis(), null);
    }

    /**
     * 创建资源创建成功的响应结果
     * 
     * 使用HTTP 201 Created状态码，适用于资源创建成功的场景。
     * 
     * @param <T>  数据类型泛型
     * @param data 响应数据
     * @return Result<T> 创建成功响应对象
     */
    public static <T> Result<T> created(T data) {
        return new Result<>(HttpStatus.CREATED.value(), "资源创建成功", data,
                System.currentTimeMillis(), null);
    }

    /**
     * 创建带自定义消息的资源创建成功响应
     * 
     * 使用HTTP 201 Created状态码，允许指定个性化成功消息。
     * 
     * @param <T>     数据类型泛型
     * @param message 自定义成功消息
     * @param data    响应数据
     * @return Result<T> 创建成功响应对象
     */
    public static <T> Result<T> created(String message, T data) {
        return new Result<>(HttpStatus.CREATED.value(), message, data, System.currentTimeMillis(), null);
    }

    /**
     * 创建错误响应
     * 
     * 使用预定义的错误码和对应的标准消息。
     * 
     * @param <T>  数据类型泛型
     * @param code 预定义错误码
     * @return Result<T> 错误响应对象
     */
    public static <T> Result<T> error(ResultCode code) {
        return new Result<>(code.getCode(), code.getMessage(), null, System.currentTimeMillis(), null);
    }

    /**
     * 创建带原因的错误响应
     * 
     * 使用预定义的错误码和对应的标准消息，同时携带业务原因。
     * 
     * @param <T>    数据类型泛型
     * @param code   预定义错误码
     * @param reason 业务原因
     * @return Result<T> 错误响应对象
     */
    public static <T> Result<T> errorWithReason(ResultCode code, String reason) {
        return new Result<>(code.getCode(), code.getMessage(), null, System.currentTimeMillis(), reason);
    }

    /**
     * 创建带自定义消息的错误响应
     * 
     * 在预定义错误码基础上，允许覆盖默认错误消息。
     * 
     * @param <T>     数据类型泛型
     * @param code    预定义错误码
     * @param message 自定义错误消息
     * @return Result<T> 错误响应对象
     */
    public static <T> Result<T> error(ResultCode code, String message) {
        return new Result<>(code.getCode(), message, null, System.currentTimeMillis(), null);
    }

    /**
     * 创建带自定义消息和原因的错误响应
     * 
     * 在预定义错误码基础上，允许覆盖默认错误消息并携带业务原因。
     * 
     * @param <T>     数据类型泛型
     * @param code    预定义错误码
     * @param message 自定义错误消息
     * @param reason  业务原因
     * @return Result<T> 错误响应对象
     */
    public static <T> Result<T> errorWithMessageAndReason(ResultCode code, String message, String reason) {
        return new Result<>(code.getCode(), message, null, System.currentTimeMillis(), reason);
    }

}
