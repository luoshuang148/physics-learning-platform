package com.zhixuewuli.controller;

import com.zhixuewuli.common.security.TeacherOnly;
import com.zhixuewuli.common.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.zhixuewuli.common.mapstruct.UserMapper;
import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.dto.request.UserAdminUpdateRequest;
import com.zhixuewuli.dto.request.UserSelfUpdateRequest;
import com.zhixuewuli.dto.response.UserResponse;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 * 
 * 提供用户信息管理的RESTful API接口。
 * 实现权限分离：普通用户只能操作自己的信息，教师可以管理所有用户。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see UserService 用户服务接口
 * @see UserResponse 用户响应DTO
 */
@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "用户信息管理相关接口")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * 获取当前用户信息
     * 
     * 获取当前登录用户的完整信息，所有认证用户均可访问。
     * 即使账户被禁用，用户仍然可以查看自己的信息。
     * 
     * @param request HTTP请求对象，包含认证信息
     * @return Result<UserResponse> 用户信息响应
     * 
     * @permission 所有认证用户
     */
    @GetMapping("/me")
    @Operation(
        summary = "获取当前用户信息",
        description = "获取当前登录用户的完整信息，所有认证用户均可访问",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证")
        }
    )
    public Result<UserResponse> getCurrentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(UserMapper.INSTANCE.toResponse(userService.getCurrentUser(user.id())));
    }

    /**
     * 更新当前用户信息
     * 
     * 用户更新自己的基本信息，可更新用户名、密码、真实姓名、班级。
     * 支持增量更新，只更新提供的字段。
     * 
     * @param request       HTTP请求对象，包含认证信息
     * @param updateRequest 用户自我更新请求DTO
     * @return Result<UserResponse> 更新后的用户信息响应
     * 
     * @permission 所有认证用户
     */
    @PutMapping("/me")
    @Operation(
        summary = "更新当前用户信息",
        description = "用户更新自己的基本信息，可更新用户名、密码、真实姓名、班级",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证")
        }
    )
    public Result<UserResponse> updateCurrentUser(
            @AuthenticationPrincipal AuthenticatedUser user,
            @io.swagger.v3.oas.annotations.Parameter(description = "用户自我更新请求参数", required = true) @Valid @RequestBody UserSelfUpdateRequest updateRequest) {
        Long currentUserId = user.id();
        User updatedUser = userService.updateCurrentUser(currentUserId, updateRequest);
        return Result.success(UserMapper.INSTANCE.toResponse(updatedUser));
    }

    /**
     * 根据用户ID获取用户信息（教师权限）
     */
    @GetMapping("/teacher/{id}")
    @Operation(
        summary = "根据用户 ID 获取用户信息",
        description = "教师根据用户ID查询用户详细信息",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
        }
    )
    @TeacherOnly
    public Result<UserResponse> getUserById(
            @io.swagger.v3.oas.annotations.Parameter(description = "用户 ID", required = true) @PathVariable Long id) {
        User user = userService.getUserById(id);
        return Result.success(UserMapper.INSTANCE.toResponse(user));
    }

    /**
     * 根据真实姓名获取用户信息（教师权限）
     */
    @GetMapping("/teacher/realname/{realName}")
    @Operation(
        summary = "根据真实姓名获取用户",
        description = "教师根据学生真实姓名查询用户信息",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
        }
    )
    @TeacherOnly
    public Result<UserResponse> getUserByRealName(
            @io.swagger.v3.oas.annotations.Parameter(description = "学生真实姓名", required = true) @PathVariable String realName) {
        User user = userService.getUserByRealName(realName);
        return Result.success(UserMapper.INSTANCE.toResponse(user));
    }

    /**
     * 获取所有用户列表
     * 
     * 教师获取系统中所有用户的列表信息。
     * 
     * @return Result<List<UserResponse>> 用户列表响应
     * 
     * @permission 教师权限
     */
    @GetMapping("/teacher/all")
    @Operation(
        summary = "获取所有用户列表",
        description = "教师获取系统中所有用户的列表信息",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    @TeacherOnly
    public Result<List<UserResponse>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        List<UserResponse> userResponses = UserMapper.INSTANCE.toResponseList(users);
        return Result.success(userResponses);
    }

    /**
     * 更新指定用户信息
     * 
     * 教师更新指定用户的信息，可更新真实姓名、班级、账户状态、用户类型。
     * 支持增量更新，只更新提供的字段。
     * 目标用户ID从请求DTO中获取。
     * 
     * @param request       HTTP请求对象，包含认证信息
     * @param updateRequest 管理员更新请求DTO（包含目标用户ID）
     * @return Result<UserResponse> 更新后的用户信息响应
     * 
     * @permission 教师权限
     */
    @PutMapping("/teacher/update")
    @Operation(
        summary = "更新指定用户信息",
        description = "教师更新指定用户的信息，可更新真实姓名、班级、账户状态、用户类型",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "目标用户不存在")
        }
    )
    @TeacherOnly
    public Result<UserResponse> updateUserByAdmin(
            @AuthenticationPrincipal AuthenticatedUser user,
            @io.swagger.v3.oas.annotations.Parameter(description = "管理员更新请求参数（包含目标用户 ID）", required = true) @Valid @RequestBody UserAdminUpdateRequest updateRequest) {
        Long currentUserId = user.id();
        User updatedUser = userService.updateUserByAdmin(updateRequest, currentUserId);
        return Result.success(UserMapper.INSTANCE.toResponse(updatedUser));
    }

    /**
     * 删除指定用户
     * 
     * 教师删除指定用户账户。
     * 
     * @param id      用户ID
     * @param request HTTP请求对象，包含认证信息
     * @return Result<Void> 删除结果响应
     * 
     * @permission 教师权限
     */
    @DeleteMapping("/teacher/{id}")
    @Operation(
        summary = "删除指定用户",
        description = "教师删除指定用户账户",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
        }
    )
    @TeacherOnly
    public Result<Void> deleteUser(
            @io.swagger.v3.oas.annotations.Parameter(description = "用户 ID", required = true) @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Long currentUserId = user.id();
        userService.deleteUser(id, currentUserId);
        return Result.success();
    }
}