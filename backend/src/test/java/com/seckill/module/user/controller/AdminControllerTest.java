package com.seckill.module.user.controller;

import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController")
class AdminControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminController controller;

    private final CurrentUser admin    = new CurrentUser(1L, "管理员", UserRole.admin);
    private final CurrentUser merchant = new CurrentUser(2L, "商家A",  UserRole.merchant);

    @BeforeEach
    void clearSecurityContext() {
        SecurityContext.clear();
    }

    @Nested
    @DisplayName("POST /api/v1/admin/users/{userId}/ban")
    class BanUser {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            assertThatThrownBy(() -> controller.banUser(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(userService, never()).banUser(anyLong(), anyLong());
        }

        @Test
        @DisplayName("非管理员 → BusinessException")
        void notAdmin() {
            SecurityContext.set(merchant);

            assertThatThrownBy(() -> controller.banUser(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅管理员");
            verify(userService, never()).banUser(anyLong(), anyLong());
        }

        @Test
        @DisplayName("管理员封禁 → 200")
        void success() {
            SecurityContext.set(admin);

            Result<Void> result = controller.banUser(100L);

            assertThat(result.getCode()).isEqualTo(200);
            verify(userService).banUser(1L, 100L);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/users/{userId}/unban")
    class UnbanUser {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            assertThatThrownBy(() -> controller.unbanUser(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(userService, never()).unbanUser(anyLong(), anyLong());
        }

        @Test
        @DisplayName("非管理员 → BusinessException")
        void notAdmin() {
            SecurityContext.set(merchant);

            assertThatThrownBy(() -> controller.unbanUser(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅管理员");
            verify(userService, never()).unbanUser(anyLong(), anyLong());
        }

        @Test
        @DisplayName("管理员解封 → 200")
        void success() {
            SecurityContext.set(admin);

            Result<Void> result = controller.unbanUser(100L);

            assertThat(result.getCode()).isEqualTo(200);
            verify(userService).unbanUser(1L, 100L);
        }
    }
}
