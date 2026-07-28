package com.seckill.filter;

import com.seckill.common.constant.BanStatus;
import com.seckill.common.constant.UserRole;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.user.model.dto.UserInfo;
import com.seckill.module.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthFilter authFilter;

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    private void doFilter(String headerValue, FilterChain chain) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (headerValue != null) {
            request.addHeader("X-User-Id", headerValue);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        authFilter.doFilterInternal(request, response, chain == null ? (req, res) -> {} : chain);
    }

    @Nested
    class ValidHeader {

        @Test
        void setsSecurityContext() throws Exception {
            UserInfo merchant = new UserInfo(1L, "商家A", "a@shop.com",
                    UserRole.merchant, BanStatus.normal);
            when(userService.getUserInfo(1L)).thenReturn(merchant);

            doFilter("1", (req, res) -> {
                CurrentUser user = SecurityContext.get();
                assertThat(user).isNotNull();
                assertThat(user.getUserId()).isEqualTo(1L);
                assertThat(user.getUserName()).isEqualTo("商家A");
                assertThat(user.getRole()).isEqualTo(UserRole.merchant);
            });

            // 请求结束后清理
            assertThat(SecurityContext.get()).isNull();
        }

        @Test
        void bannedUser_stillSetsContext() throws Exception {
            UserInfo banned = new UserInfo(2L, "封禁号", "b@test.com",
                    UserRole.user, BanStatus.banned);
            when(userService.getUserInfo(2L)).thenReturn(banned);

            doFilter("2", (req, res) -> {
                CurrentUser user = SecurityContext.get();
                assertThat(user).isNotNull();
                assertThat(user.getRole()).isEqualTo(UserRole.user);
            });
        }
    }

    @Nested
    class NoAuth {

        @Test
        void noHeader_doesNotSetContext() throws Exception {
            doFilter(null, (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }

        @Test
        void emptyHeader_doesNotSetContext() throws Exception {
            doFilter("", (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }

        @Test
        void blankHeader_doesNotSetContext() throws Exception {
            doFilter("   ", (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }

        @Test
        void nonNumericHeader_skipsGracefully() throws Exception {
            doFilter("abc", (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }

        @Test
        void userNotFound_skips() throws Exception {
            when(userService.getUserInfo(999L)).thenReturn(null);

            doFilter("999", (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }

        @Test
        void userWithNullRole_skips() throws Exception {
            UserInfo noRoleUser = new UserInfo(3L, "norole", "n@test.com",
                    null, BanStatus.normal);
            when(userService.getUserInfo(3L)).thenReturn(noRoleUser);

            doFilter("3", (req, res) ->
                    assertThat(SecurityContext.get()).isNull());
        }
    }

    @Nested
    class Cleanup {

        @Test
        void contextClearedAfterChainCompletes() throws Exception {
            UserInfo merchant = new UserInfo(1L, "商家", "a@shop.com",
                    UserRole.merchant, BanStatus.normal);
            when(userService.getUserInfo(1L)).thenReturn(merchant);

            doFilter("1", (req, res) -> {
                // 请求中上下文存在
                assertThat(SecurityContext.get()).isNotNull();
            });

            // 请求后已清理
            assertThat(SecurityContext.get()).isNull();
        }

        @Test
        void contextCleared_evenWhenChainThrows() {
            UserInfo merchant = new UserInfo(1L, "商家", "a@shop.com",
                    UserRole.merchant, BanStatus.normal);
            when(userService.getUserInfo(1L)).thenReturn(merchant);

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                    doFilter("1", (req, res) -> {
                        throw new RuntimeException("chain爆炸");
                    }));

            assertThat(SecurityContext.get()).isNull();
        }
    }
}
