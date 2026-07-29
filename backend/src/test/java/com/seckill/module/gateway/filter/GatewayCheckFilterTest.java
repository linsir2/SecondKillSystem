package com.seckill.module.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.constant.UserRole;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.gateway.model.dto.GatewayCheckResult;
import com.seckill.module.gateway.service.GatewayCheckService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayCheckFilter")
class GatewayCheckFilterTest {

    @Mock
    private GatewayCheckService gatewayCheckService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GatewayCheckFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new GatewayCheckFilter(gatewayCheckService, objectMapper);
        request = new MockHttpServletRequest("POST", "/api/v1/seckill/execute");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    @Nested
    @DisplayName("校验通过")
    class Pass {

        @Test
        @DisplayName("非 seckill 路径直接放行（路径匹配由 Spring 注册控制，filter 不自己做路径判断）")
        void anyPathWithValidUser() throws Exception {
            SecurityContext.set(new CurrentUser(1L, "用户", UserRole.user));
            when(gatewayCheckService.check(1L)).thenReturn(GatewayCheckResult.pass());

            boolean result = filter.preHandle(request, response, null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("GatewayCheckService 抛出异常 → 传播不拦截")
        void serviceThrows() throws Exception {
            SecurityContext.set(new CurrentUser(1L, "用户", UserRole.user));
            when(gatewayCheckService.check(1L)).thenThrow(new RuntimeException("Redis down"));

            assertThatThrownBy(() -> filter.preHandle(request, response, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Redis down");
        }
    }

    @Nested
    @DisplayName("拦截返回 false")
    class Blocked {

        @Test
        @DisplayName("未登录 → 401 + JSON 响应")
        void noAuth() throws Exception {
            boolean result = filter.preHandle(request, response, null);

            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
            Result<?> r = objectMapper.readValue(response.getContentAsString(), Result.class);
            assertThat(r.getCode()).isEqualTo(401);
            assertThat(r.getMessage()).contains("未登录");
        }

        @Test
        @DisplayName("黑名单 → 403 + JSON 响应")
        void blacklisted() throws Exception {
            SecurityContext.set(new CurrentUser(1L, "用户", UserRole.user));
            when(gatewayCheckService.check(1L)).thenReturn(GatewayCheckResult.blacklisted());

            boolean result = filter.preHandle(request, response, null);

            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
            Result<?> r = objectMapper.readValue(response.getContentAsString(), Result.class);
            assertThat(r.getCode()).isEqualTo(403);
            assertThat(r.getMessage()).contains("封禁");
        }

        @Test
        @DisplayName("限流 → 429 + JSON 响应")
        void rateLimited() throws Exception {
            SecurityContext.set(new CurrentUser(1L, "用户", UserRole.user));
            when(gatewayCheckService.check(1L)).thenReturn(GatewayCheckResult.rateLimited());

            boolean result = filter.preHandle(request, response, null);

            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(429);
            Result<?> r = objectMapper.readValue(response.getContentAsString(), Result.class);
            assertThat(r.getCode()).isEqualTo(429);
            assertThat(r.getMessage()).contains("频繁");
        }
    }
}
