package com.zhixuewuli.controller;

import com.zhixuewuli.common.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.dto.request.LoginRequest;
import com.zhixuewuli.dto.request.RegisterRequest;
import com.zhixuewuli.dto.response.LoginResponse;
import com.zhixuewuli.dto.response.RegisterResponse;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 认证管理控制器
 * 
 * 提供用户身份认证相关的RESTful API接口。
 * 实现基于JWT的无状态认证机制，支持用户登录和令牌管理。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>用户登录认证</li>
 * <li>JWT令牌生成</li>
 * <li>用户身份验证</li>
 * <li>会话管理</li>
 * </ul>
 * 
 * <p>
 * 安全特性：
 * </p>
 * <ul>
 * <li>基于JWT的无状态认证</li>
 * <li>令牌过期时间控制</li>
 * <li>密码安全验证</li>
 * <li>防暴力破解机制</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AuthServiceImpl 认证服务实现
 * @see Result 统一响应结果类
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "认证管理", description = "用户身份认证相关接口")
public class AuthController {

    private final AuthService authService;
    private final com.zhixuewuli.common.security.LoginAttemptLimiter limiter;

    /**
     * 用户登录接口
     * 
     * 验证用户提供的用户名和密码，如果验证成功则返回JWT访问令牌。
     * 
     * @param request 登录请求参数，包含用户名和密码
     * @return Result<LoginResponse> 登录结果，包含令牌和用户信息
     * 
     * @implSpec 使用BCrypt验证密码安全性
     * @implSpec 生成包含用户信息的JWT令牌
     * @implSpec 更新用户最后登录时间
     * @implSpec 返回标准化的登录响应格式
     */
    @PostMapping("/login")
    @Operation(
        summary = "用户登录",
        description = "通过用户名和密码进行身份认证，获取访问令牌",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误（用户名或密码为空）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "认证失败（用户名或密码错误）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "账户已被禁用")
        }
    )
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,jakarta.servlet.http.HttpServletRequest servletRequest) {
        log.debug("用户登录请求，用户名: {}", request.getUsername());

        // 执行用户认证
        String peer=servletRequest.getRemoteAddr();limiter.check(peer,request.getUsername());
        User user;
        try {user=authService.authenticate(request.getUsername(),request.getPassword());limiter.succeeded(peer,request.getUsername());}
        catch(com.zhixuewuli.common.exception.auth.AuthenticationException failure){throw new com.zhixuewuli.common.exception.auth.InvalidCredentialsException();}

        // 生成JWT访问令牌
        String token = authService.generateAccessToken(user);

        // 构建登录响应
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setUserType(user.getUserType().name());
        response.setRealName(user.getRealName());

        log.debug("用户登录成功，用户ID: {}, 用户名: {}", user.getId(), user.getUsername());

        return Result.success(response);
    }

    /**
     * 用户注册接口
     * 
     * 处理新用户注册请求，创建用户账户并返回注册结果。
     * 
     * @param request 注册请求参数，包含用户名、密码等基本信息
     * @return Result<RegisterResponse> 注册结果
     * 
     * @implSpec 验证用户名唯一性
     * @implSpec 使用BCrypt加密存储密码
     * @implSpec 设置默认账户为激活状态
     * @implSpec 返回简洁的注册成功信息
     */
    @PostMapping("/register")
    @Operation(
        summary = "用户注册",
        description = "创建新的用户账户",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "注册成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误或用户名已存在")
        }
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("收到用户注册请求，用户名: {}", request.getUsername());

        // 执行用户注册
        User registeredUser = authService.registerUser(request);

        // 构建注册响应
        RegisterResponse response = new RegisterResponse(
                registeredUser.getUsername(),
                registeredUser.getUserType().name());

        log.debug("用户注册成功，用户ID: {}, 用户名: {}",
                registeredUser.getId(), registeredUser.getUsername());

        return Result.created(response);
    }

    /**
     * 用户登出接口
     * 
     * 处理用户登出请求，在JWT无状态认证模式下主要用于客户端清理。
     * 
     * @return Result<String> 登出结果
     * 
     * @implSpec 在JWT模式下主要由客户端清理令牌
     * @implSpec 可用于记录登出日志
     * @implSpec 支持黑名单机制（未来扩展）
     */
    @PostMapping("/logout")
    @Operation(
        summary = "用户登出",
        description = "退出当前登录会话",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登出成功")
        }
    )
    public Result<String> logout(@AuthenticationPrincipal AuthenticatedUser user) {
        authService.logout(user.id());
        // 在JWT无状态模式下，服务器端不需要特殊处理
        // 客户端应负责删除本地存储的令牌
        log.debug("用户登出请求");
        return Result.success("登出成功");
    }
}