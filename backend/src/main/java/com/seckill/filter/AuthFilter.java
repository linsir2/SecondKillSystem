package com.seckill.filter;

import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.user.model.dto.UserInfo;
import com.seckill.module.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 {@code X-User-Id} 请求头提取用户 ID，查 UserService 后设 {@link SecurityContext}。
 * <p>Header 非法/用户不存在/用户 role 为 null 时跳过，不阻塞请求。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String uid = request.getHeader("X-User-Id");
            if (uid != null && !uid.isBlank()) {
                try {
                    Long userId = Long.valueOf(uid);
                    UserInfo info = userService.getUserInfo(userId);
                    if (info != null && info.getRole() != null) {
                        SecurityContext.set(new CurrentUser(
                                info.getUserId(), info.getUserName(), info.getRole()));
                    }
                } catch (NumberFormatException e) {
                    // 非法 header，不设上下文
                }
            }
            chain.doFilter(request, response);
        } finally {
            SecurityContext.clear();
        }
    }
}
