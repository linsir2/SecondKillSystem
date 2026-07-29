package com.seckill.module.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.gateway.model.dto.GatewayCheckResult;
import com.seckill.module.gateway.service.GatewayCheckService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 网关防护拦截器 —— 黑名单校验 + 1s 限流。
 *
 * <p>注册路径 {@code /api/v1/seckill/**}，在 Controller 执行前检查：
 * <ol>
 *   <li>SecurityContext 是否有用户（未登录 401）</li>
 *   <li>用户是否在黑名单（被封禁 403）</li>
 *   <li>用户是否触发 1s 限流（操作频繁 429）</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class GatewayCheckFilter implements HandlerInterceptor {

    private final GatewayCheckService gatewayCheckService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            writeError(response, HttpStatus.UNAUTHORIZED.value(), "未登录");
            return false;
        }

        GatewayCheckResult result = gatewayCheckService.check(user.getUserId());
        if (result.code() == GatewayCheckResult.CODE_BLACKLISTED) {
            writeError(response, HttpStatus.FORBIDDEN.value(), "用户已被封禁");
            return false;
        }
        if (result.code() == GatewayCheckResult.CODE_RATE_LIMITED) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS.value(), "操作过于频繁，请稍后重试");
            return false;
        }

        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.error(status, message));
    }
}
