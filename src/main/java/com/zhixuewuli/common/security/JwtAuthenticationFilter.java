package com.zhixuewuli.common.security;

import com.zhixuewuli.common.exception.auth.AccountDisabledException;
import com.zhixuewuli.common.exception.auth.AuthenticationException;
import com.zhixuewuli.common.exception.auth.InvalidTokenException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.util.JwtUtil;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 解析 Bearer 令牌并写入 SecurityContext。
 *
 * <p>只负责“你是谁”：令牌无效时不直接拒绝，而是保持匿名并记录原因，
 * 由授权规则决定该路径是否需要登录，公开接口因此不受过期令牌影响。
 * 每次请求都会回库读取用户，以便即时生效的令牌撤销（tokenVersion）、停用和角色变更。</p>
 *
 * <p>不注册为 Spring Bean，避免被 Servlet 容器重复注册为普通过滤器。</p>
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证失败原因，供 AuthenticationEntryPoint 生成响应。 */
    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            try {
                AuthenticatedUser principal = authenticate(token);
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.authorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AuthenticationException e) {
                log.debug("令牌认证失败: {} - {}", request.getRequestURI(), e.getMessage());
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    private AuthenticatedUser authenticate(String token) {
        Claims claims;
        try {
            claims = jwtUtil.parseClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
        Long userId = claims.get("userId", Long.class);
        if (userId == null) {
            throw new InvalidTokenException();
        }
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        long currentVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        if (JwtUtil.tokenVersion(claims) != currentVersion) {
            throw new InvalidTokenException();
        }
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new AccountDisabledException();
        }
        return AuthenticatedUser.from(user);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
