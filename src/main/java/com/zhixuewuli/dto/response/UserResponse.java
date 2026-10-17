package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户响应DTO
 * 
 * 用于向客户端返回用户信息的数据传输对象。
 * 包含用户的基本信息和系统管理字段。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Data
@Builder
@Schema(description = "用户信息响应")
public class UserResponse {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户名", example = "student001")
    private String username;

    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Schema(description = "班级名称", example = "高三(1)班")
    private String className;

    @Schema(description = "用户类型", example = "STUDENT")
    private String userType;

    @Schema(description = "账户状态", example = "true")
    private Boolean active;

    @Schema(description = "创建时间", example = "2026-01-01T10:00:00")
    private LocalDateTime createTime;

    @Schema(description = "最后登录时间", example = "2026-01-15T14:30:00")
    private LocalDateTime lastLogin;
}