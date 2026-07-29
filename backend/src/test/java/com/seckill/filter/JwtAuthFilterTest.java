package com.seckill.filter;

import com.seckill.common.constant.BanStatus;
import com.seckill.common.constant.UserRole;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.JwtUtil;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.user.model.dto.UserInfo;
import com.seckill.module.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JwtAuthFilter} 单元测试。
 *
 * <p>覆盖 JWT 认证、开发环境 X-User-Id 回退、无认证、清理等场景。</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserService userService;

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    // ==================== helpers ====================

    /** 用 Authorization: Bearer 头执行过滤器。 */
    private void doFilter(JwtAuthFilter f, String token, FilterChain chain) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        f.doFilterInternal(request, new MockHttpServletResponse(), chain == null ? (req, res) -> {} : chain);
    }

    /** 用 X-User-Id 头执行过滤器（dev fallback）。 */
    private void doFilterWithXUserId(JwtAuthFilter f, String headerValue, FilterChain chain) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (headerValue != null) {
            request.addHeader("X-User-Id", headerValue);
        }
        f.doFilterInternal(request, new MockHttpServletResponse(), chain == null ? (req, res) -> {} : chain);
    }

    private Claims mockClaims(Long userId, String userName, UserRole role) {
        Claims c = mock(Claims.class);
        when(jwtUtil.getUserId(c)).thenReturn(userId);
        when(jwtUtil.getUserName(c)).thenReturn(userName);
        when(jwtUtil.getRole(c)).thenReturn(role);
        return c;
    }

    // ================================================================
    // JWT 认证
    // ================================================================

    @Nested
    class JwtAuth {

        @Test
        void validJwt_setsSecurityContext() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, false);
            Claims claims = mockClaims(1L, "张三", UserRole.user);
            when(jwtUtil.validateAccessToken("valid")).thenReturn(claims);

            doFilter(f, "valid", (req, res) -> {
                CurrentUser user = SecurityContext.get();
                assertThat(user).isNotNull();
                assertThat(user.getUserId()).isEqualTo(1L);
                assertThat(user.getUserName()).isEqualTo("张三");
                assertThat(user.getRole()).isEqualTo(UserRole.user);
            });
        }

        @Test
        void expiredJwt_returns401() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, false);
            when(jwtUtil.validateAccessToken(anyString())).thenThrow(new JwtException("expired"));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer expired");
            f.doFilterInternal(req, resp, (r, s) -> {});

            assertThat(resp.getStatus()).isEqualTo(401);
        }

        @Test
        void invalidJwt_returns401() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, false);
            when(jwtUtil.validateAccessToken(anyString())).thenThrow(new JwtException("bad signature"));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer bad");
            f.doFilterInternal(req, resp, (r, s) -> {});

            assertThat(resp.getStatus()).isEqualTo(401);
        }
    }

    // ================================================================
    // 无认证
    // ================================================================

    @Nested
    class NoAuth {

        @Test
        void noHeader_passesThrough() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, false);

            doFilter(f, null, (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }

        @Test
        void noHeader_withDevFallbackDisabled_passesThrough() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, false);

            doFilter(f, null, (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }
    }

    // ================================================================
    // Dev Fallback（X-User-Id）
    // ================================================================

    @Nested
    class DevFallback {

        @Test
        void validXUserId_setsContext() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, true);
            UserInfo info = new UserInfo(1L, "商家A", "a@shop.com",
                    UserRole.merchant, BanStatus.normal);
            when(userService.getUserInfo(1L)).thenReturn(info);

            doFilterWithXUserId(f, "1", (req, res) -> {
                CurrentUser user = SecurityContext.get();
                assertThat(user).isNotNull();
                assertThat(user.getUserId()).isEqualTo(1L);
                assertThat(user.getUserName()).isEqualTo("商家A");
                assertThat(user.getRole()).isEqualTo(UserRole.merchant);
            });
        }

        @Test
        void noHeader_skips() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, true);

            doFilterWithXUserId(f, null, (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }

        @Test
        void emptyHeader_skips() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, true);

            doFilterWithXUserId(f, "", (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }

        @Test
        void nonNumericHeader_skips() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, true);

            doFilterWithXUserId(f, "abc", (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }

        @Test
        void userNotFound_skips() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, true);
            when(userService.getUserInfo(999L)).thenReturn(null);

            doFilterWithXUserId(f, "999", (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }

        @Test
        void userWithNullRole_skips() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, true);
            UserInfo noRole = new UserInfo(3L, "norole", "n@test.com",
                    null, BanStatus.normal);
            when(userService.getUserInfo(3L)).thenReturn(noRole);

            doFilterWithXUserId(f, "3", (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }
    }

    // ================================================================
    // 清理
    // ================================================================

    @Nested
    class Cleanup {

        @Test
        void contextClearedAfterJwtChainCompletes() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, false);
            Claims claims = mockClaims(1L, "张三", UserRole.user);
            when(jwtUtil.validateAccessToken("t")).thenReturn(claims);

            doFilter(f, "t", (req, res) ->
                    assertThat(SecurityContext.get()).isNotNull());
            assertThat(SecurityContext.get()).isNull();
        }

        @Test
        void contextCleared_evenWhenChainThrows() {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, false);
            Claims claims = mockClaims(1L, "张三", UserRole.user);
            when(jwtUtil.validateAccessToken("t")).thenReturn(claims);

            assertThatThrownBy(() -> doFilter(f, "t", (req, res) -> {
                throw new RuntimeException("chain爆炸");
            })).isInstanceOf(RuntimeException.class);

            assertThat(SecurityContext.get()).isNull();
        }

        @Test
        void contextUnchangedWhenNoAuth() throws Exception {
            JwtAuthFilter f = new JwtAuthFilter(jwtUtil, userService, false);

            doFilter(f, null, (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
            assertThat(SecurityContext.get()).isNull();
        }
    }
}
