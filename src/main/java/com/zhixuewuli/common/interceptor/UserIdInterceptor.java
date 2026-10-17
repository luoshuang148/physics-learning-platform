package com.zhixuewuli.common.interceptor;

import com.zhixuewuli.common.exception.auth.AccountDisabledException;
import com.zhixuewuli.common.exception.auth.InvalidTokenException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.util.JwtUtil;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.auth.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import jakarta.servlet.http.HttpServletResponse;

import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户ID拦截器
 * 
 * 解析JWT令牌获取用户身份信息，并将用户上下文存储到请求属性中。
 * 实现基于JWT的无状态认证机制。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>解析Authorization头部的JWT令牌</li>
 * <li>验证令牌有效性和用户身份</li>
 * <li>提取用户ID和用户名信息</li>
 * <li>将用户上下文存储到请求属性中</li>
 * </ul>
 * 
 * <p>
 * 认证流程：
 * </p>
 * <ol>
 * <li>检查Authorization头部是否存在</li>
 * <li>验证Bearer Token格式</li>
 * <li>使用JWT工具类验证令牌签名和过期时间</li>
 * <li>从令牌中提取用户ID和用户名</li>
 * <li>将用户信息存储到request.setAttribute中</li>
 * </ol>
 * 
 * <p>
 * 安全考虑：
 * </p>
 * <ul>
 * <li>支持JWT令牌的完整验证</li>
 * <li>用户状态检查（激活状态）</li>
 * <li>详细的日志记录用于安全审计</li>
 * <li>优雅的错误处理机制</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see JwtUtil JWT工具类
 * @see AuthService 认证服务
 * @see HandlerInterceptor Spring MVC拦截器接口
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserIdInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    /**
     * 请求预处理
     * 
     * 在请求到达控制器之前解析JWT令牌并验证用户身份。
     * 
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象
     * @return boolean true表示继续处理，false表示中断
     * @throws Exception 处理过程中可能发生的异常
     * 
     * @implSpec 只处理Controller方法
     * @implSpec 支持白名单路径跳过认证
     * @implSpec 验证JWT令牌格式和有效性
     * @implSpec 提取并存储用户上下文信息
     * @implSpec 未认证请求返回401状态码
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        log.info("=== UserIdInterceptor.preHandle 开始执行 ===");
        log.info("请求URI: {}", request.getRequestURI());
        log.info("请求方法: {}", request.getMethod());
        log.info("Handler类型: {}", handler.getClass().getSimpleName());

        // 只处理Controller方法
        if (!(handler instanceof HandlerMethod)) {
            log.info("不是Controller方法，跳过处理");
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        log.info("处理的方法: {}.{}",
                handlerMethod.getBeanType().getSimpleName(),
                handlerMethod.getMethod().getName());

        // 检查是否需要跳过认证
        if (shouldSkipAuthentication(request)) {
            log.debug("跳过认证检查，路径: {}", request.getRequestURI());
            log.info("=== UserIdInterceptor.preHandle 结束(跳过) ===");
            return true;
        }

        try {
            log.info("开始JWT令牌验证流程");

            // 提取并验证JWT令牌
            String token = extractToken(request);
            log.info("提取到的令牌: {}", token != null ? "[存在]" : "[不存在]");

            if (token == null) {
                log.warn("未找到有效的JWT令牌");
                throw new InvalidTokenException();
            }

            // 验证JWT令牌有效性
            log.info("开始验证JWT令牌");
            if (!jwtUtil.validateToken(token)) {
                log.warn("JWT令牌验证失败");
                throw new InvalidTokenException();
            }
            log.info("JWT令牌验证成功");

            // 从令牌中提取用户ID并查询用户信息
            log.info("开始提取用户ID");
            Long userId = jwtUtil.getUserIdFromToken(token);
            log.info("提取到的用户ID: {}", userId);

            // 根据用户ID查询用户信息
            log.info("开始查询用户信息");
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("用户不存在，ID: {}", userId);
                throw new UserNotFoundException();
            }

            User user = userOpt.get();
            log.info("查询到用户: {} ({})", user.getUsername(), user.getUserType());

            // 检查用户账户状态
            if (!Boolean.TRUE.equals(user.getActive())) {
                log.warn("用户账户已被禁用: {}", user.getUsername());
                throw new AccountDisabledException();
            }

            // 将用户信息存储到请求属性中
            request.setAttribute("userId", userId);
            request.setAttribute("currentUser", user);
            request.setAttribute("username", user.getUsername());
            request.setAttribute("userType", user.getUserType());

            log.info("设置请求属性 - userId: {}, username: {}, userType: {}",
                    userId, user.getUsername(), user.getUserType());

            log.debug("用户认证成功，用户ID: {}, 用户名: {}, 路径: {}",
                    userId, user.getUsername(), request.getRequestURI());
            log.info("=== UserIdInterceptor.preHandle 成功结束 ===");

            return true;

        } catch (Exception e) {
            log.error("认证过程中发生异常: {}", e.getMessage(), e);

            // 处理认证异常，直接返回401状态码
            if (e instanceof com.zhixuewuli.common.exception.auth.AuthenticationException) {
                log.info("认证失败: {}", e.getMessage());
                response.setStatus(SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                try {
                    String jsonResponse = "{\"code\":401,\"message\":\"认证失败: " + e.getMessage()
                            + "\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}";
                    response.getWriter().write(jsonResponse);
                    log.info("已发送401响应");
                } catch (Exception ioException) {
                    log.error("写入响应失败", ioException);
                }
                log.info("=== UserIdInterceptor.preHandle 失败结束 ===");
                return false;
            }
            // 其他未知异常转换为技术异常
            log.error("发生未预期的技术异常");
            throw new com.zhixuewuli.common.exception.TechnicalException("认证服务暂时不可用");
        }
    }

    /**
     * 判断是否应该跳过认证检查
     * 
     * 对于公开接口和白名单路径，不需要进行用户认证。
     * 
     * @param request HTTP 请求对象
     * @return boolean true 表示跳过认证，false 表示需要认证
     * 
     * @implSpec 包含常见的公开接口路径
     * @implSpec 包含 Swagger 文档相关路径
     * @implSpec 包含健康检查路径
     */
    private boolean shouldSkipAuthentication(HttpServletRequest request) {
        String uri = request.getRequestURI();
    
        // 白名单路径 - 不需要认证
        // 使用精确路径匹配，与 WebConfig 中的配置保持一致
        return "/auth/login".equals(uri) ||
                "/auth/register".equals(uri) ||
                uri.startsWith("/videos/") ||
                (uri.startsWith("/questions/teacher/get/") && !uri.equals("/questions/teacher/get/all")) || // 允许访问题目详情接口（公开），但排除获取所有题目
                // Swagger/OpenAPI文档相关路径（考虑 context-path）
                uri.contains("/swagger") ||
                uri.contains("/v3/api-docs") ||
                uri.contains("/webjars/") ||
                // Actuator 监控端点
                uri.contains("/actuator") ||
                // 健康检查和视频相关公开接口
                "/answers/health".equals(uri) ||
                "/answers/next-video".equals(uri) ||
                "/answers/submit-with-next-video".equals(uri);
    }

    /**
     * 从请求中提取JWT令牌
     * 
     * 解析Authorization头部，提取Bearer Token格式的JWT令牌。
     * 
     * @param request HTTP请求对象
     * @return String JWT令牌字符串，如果不存在返回null
     * 
     * @implSpec 支持标准Bearer Token格式
     * @implSpec 自动去除"Bearer "前缀
     * @implSpec 处理大小写不敏感
     */
    private String extractToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7); // 去除"Bearer "前缀
        }

        if (authorizationHeader != null && authorizationHeader.startsWith("bearer ")) {
            return authorizationHeader.substring(7); // 处理小写bearer
        }

        return null;
    }

    /**
     * 请求后处理
     * 
     * 在请求处理完成后执行清理和日志记录工作。
     * 
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象
     * @param ex       异常对象（如果有）
     * @throws Exception 处理过程中可能发生的异常
     * 
     * @implSpec 记录认证使用情况
     * @implSpec 清理临时认证信息
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) throws Exception {

        Long userId = (Long) request.getAttribute("userId");

        // 记录请求处理的完整信息
        if (userId != null) {
            log.info("用户请求处理完成 - 用户ID: {}, URI: {}, Status: {}",
                    userId,
                    request.getRequestURI(),
                    response.getStatus());
        }

        // 记录异常情况
        if (ex != null) {
            log.error("用户请求处理异常 - 用户ID: {}, URI: {}, Exception: {}",
                    userId, request.getRequestURI(), ex.getMessage(), ex);
        }
    }
}