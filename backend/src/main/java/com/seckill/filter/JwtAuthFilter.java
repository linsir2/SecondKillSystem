package com.seckill.filter;

import com.seckill.common.constant.BanStatus;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.JwtUtil;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.user.model.dto.UserInfo;
import com.seckill.module.user.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器 —— 读取 {@code Authorization: Bearer <token>} 完成认证。
 *
 * <p>成功时同时设置：
 * <ul>
 *   <li>Spring Security {@link SecurityContextHolder}（框架级）</li>
 *   <li>自定义 {@link SecurityContext}（旧代码兼容）</li>
 * </ul>
 *
 * <p>开发环境（dev 且 {@code jwt.dev-auth-fallback=true}）下，无 JWT 时
 * 回退读取 {@code X-User-Id} header（方便 curl 调试）。</p>
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BLACKLIST_KEY = "seckill:blacklist";

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final StringRedisTemplate redisTemplate;
    private final boolean devAuthFallback;

    public JwtAuthFilter(JwtUtil jwtUtil,
                         UserService userService,
                         StringRedisTemplate redisTemplate,
                         @Value("${jwt.dev-auth-fallback:false}") boolean devAuthFallback) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.redisTemplate = redisTemplate;
        this.devAuthFallback = devAuthFallback;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            // ---- 1. 优先尝试 JWT ----
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                Long userId = authenticateByJwt(token);
                if (userId != null) {
                    // Token 撤销检查
                    if (isAccessTokenBlacklisted(token)) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":403,\"message\":\"token已被撤销\"}");
                        return;
                    }
                    // 封禁检查（覆盖全部认证路径）
                    if (isBlacklisted(userId)) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":403,\"message\":\"用户已被封禁\"}");
                        return;
                    }
                    chain.doFilter(request, response);
                    return;
                }
                // JWT 无效 → 返回 401
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"token无效或已过期\"}");
                return;
            }

            // ---- 2. Dev 回退：X-User-Id ----
            if (devAuthFallback) {
                String uid = request.getHeader("X-User-Id");
                if (uid != null && !uid.isBlank()) {
                    authenticateByHeader(uid);
                }
            }

            chain.doFilter(request, response);
        } finally {
            SecurityContext.clear();
        }
    }

    /**
     * 通过 JWT 认证。
     *
     * @return userId 认证成功，null token 无效
     */
    private Long authenticateByJwt(String token) {
        try {
            Claims claims = jwtUtil.validateAccessToken(token);
            Long userId = jwtUtil.getUserId(claims);
            String userName = jwtUtil.getUserName(claims);
            String role = jwtUtil.getRole(claims).name();

            setSecurityContexts(userId, userName, role);
            return userId;
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 通过 X-User-Id header 认证（仅 dev）。
     */
    private void authenticateByHeader(String uid) {
        try {
            Long userId = Long.valueOf(uid);
            UserInfo info = userService.getUserInfo(userId);
            if (info != null && info.getRole() != null && info.getBanStatus() != BanStatus.banned) {
                setSecurityContexts(info.getUserId(), info.getUserName(), info.getRole().name());
            }
        } catch (NumberFormatException e) {
            log.debug("Invalid X-User-Id header: {}", uid);
        }
    }

    private void setSecurityContexts(Long userId, String userName, String role) {
        // Spring Security 上下文
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 自定义上下文（兼容旧代码）
        SecurityContext.set(new CurrentUser(userId, userName, com.seckill.common.constant.UserRole.valueOf(role)));
    }

    /**
     * 检查用户是否在 Redis 黑名单中。
     * <p>异常降级：Redis 不可用时视为不在黑名单，不阻塞认证流程。</p>
     */
    private boolean isBlacklisted(Long userId) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForSet().isMember(BLACKLIST_KEY, String.valueOf(userId)));
        } catch (Exception e) {
            log.debug("Blacklist check failed for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * 检查 access token 是否已被撤销（logout 时加入黑名单）。
     * <p>异常降级：Redis 不可用时视为未撤销，不阻塞认证流程。</p>
     */
    private boolean isAccessTokenBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey("seckill:abl:" + JwtUtil.sha256Hex(token)));
        } catch (Exception e) {
            log.debug("Access token blacklist check failed: {}", e.getMessage());
            return false;
        }
    }
}
