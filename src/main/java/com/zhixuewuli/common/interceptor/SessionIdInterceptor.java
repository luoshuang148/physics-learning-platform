package com.zhixuewuli.common.interceptor;

import com.zhixuewuli.common.util.SessionUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 会话ID拦截器
 * 
 * 处理请求中的会话ID，确保每个请求都有有效的会话标识。
 * 如果请求中没有提供会话ID，则自动生成一个新的。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>从请求头提取会话ID</li>
 * <li>验证会话ID格式有效性</li>
 * <li>生成新的会话ID（当需要时）</li>
 * <li>将会话ID存储到请求属性中</li>
 * </ul>
 * 
 * <p>
 * 处理逻辑：
 * </p>
 * <ol>
 * <li>检查请求头中的X-Session-Id</li>
 * <li>如果不存在，生成新的会话ID</li>
 * <li>如果存在但格式无效，生成新的会话ID</li>
 * <li>将会话ID存储到request.setAttribute中</li>
 * <li>在响应头中返回会话ID（便于客户端使用）</li>
 * </ol>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see SessionUtil 会话工具类
 * @see HandlerInterceptor Spring MVC拦截器接口
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionIdInterceptor implements HandlerInterceptor {

    /**
     * 请求预处理
     * 
     * 在请求到达控制器之前处理会话ID。
     * 
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象
     * @return boolean true表示继续处理，false表示中断
     * @throws Exception 处理过程中可能发生的异常
     * 
     * @implSpec 优先使用请求头中的会话ID
     * @implSpec 自动生成新的会话ID当需要时
     * @implSpec 验证会话ID格式和安全性
     * @implSpec 通过响应头返回会话ID
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 从请求头中提取会话ID
        String sessionId = request.getHeader("X-Session-Id");

        // 处理会话ID逻辑
        if (sessionId == null || sessionId.isEmpty()) {
            // 没有提供会话ID，生成新的
            sessionId = SessionUtil.generateSecureSessionId();
            log.debug("生成新的会话ID: {}", sessionId);
        } else {
            // 验证现有会话ID的有效性
            if (!SessionUtil.isValidSessionId(sessionId)) {
                log.warn("无效的会话ID格式: {}, 生成新的会话ID", sessionId);
                sessionId = SessionUtil.generateSecureSessionId();
            } else {
                // 清理和标准化会话ID
                sessionId = SessionUtil.sanitizeSessionId(sessionId);
                log.debug("使用现有会话ID: {}", sessionId);
            }
        }

        // 将会话ID存储到请求属性中，供后续处理使用
        request.setAttribute("sessionId", sessionId);

        // 在响应头中返回会话ID，便于客户端使用
        response.setHeader("X-Session-Id", sessionId);

        return true;
    }

    /**
     * 请求后处理
     * 
     * 在请求处理完成后执行清理工作。
     * 
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象
     * @param ex       异常对象（如果有）
     * @throws Exception 处理过程中可能发生的异常
     * 
     * @implSpec 记录会话使用情况
     * @implSpec 清理临时资源
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) throws Exception {

        String sessionId = (String) request.getAttribute("sessionId");

        // 记录会话使用情况和性能信息
        if (sessionId != null) {
            log.info("会话请求处理完成 - 会话ID: {}, URI: {}, Status: {}",
                    sessionId,
                    request.getRequestURI(),
                    response.getStatus());
        }

        // 记录异常情况
        if (ex != null) {
            log.error("会话处理异常 - 会话ID: {}, URI: {}, Exception: {}",
                    sessionId, request.getRequestURI(), ex.getMessage(), ex);
        }
    }
}