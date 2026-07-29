package com.seckill.module.order.controller;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.order.model.dto.CancelOrderRequest;
import com.seckill.module.order.model.vo.OrderStatusVO;
import com.seckill.module.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController")
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController controller;

    private final CurrentUser testUser = new CurrentUser(10001L, "买家A", null);

    @BeforeEach
    void clearSecurityContext() {
        SecurityContext.clear();
    }

    @Nested
    @DisplayName("GET /api/v1/order/status")
    class GetOrderStatus {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            assertThatThrownBy(() -> controller.getStatus("token-abc"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(orderService, never()).getOrderStatusVO(anyString(), anyLong());
        }

        @Test
        @DisplayName("订单存在 → 200 + status + orderNo")
        void found() {
            SecurityContext.set(testUser);
            when(orderService.getOrderStatusVO("token-abc", 10001L))
                    .thenReturn(new OrderStatusVO("UNPAID", 10001L));

            Result<OrderStatusVO> result = controller.getStatus("token-abc");

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isNotNull();
            assertThat(result.getData().status()).isEqualTo("UNPAID");
            assertThat(result.getData().orderNo()).isEqualTo(10001L);
        }

        @Test
        @DisplayName("订单不存在 → 200 + data 字段全 null")
        void notFound() {
            SecurityContext.set(testUser);
            when(orderService.getOrderStatusVO("token-not-exist", 10001L))
                    .thenReturn(null);

            Result<OrderStatusVO> result = controller.getStatus("token-not-exist");

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("token 为 blank → IllegalArgumentException")
        void blankToken() {
            assertThatThrownBy(() -> controller.getStatus("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("token");
        }

        @Test
        @DisplayName("token 为 null → IllegalArgumentException")
        void nullToken() {
            assertThatThrownBy(() -> controller.getStatus(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("token");
        }
    }

    @Nested
    @DisplayName("POST /api/v1/order/cancel")
    class CancelOrder {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            assertThatThrownBy(() -> controller.cancel(new CancelOrderRequest(9001L)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(orderService, never()).cancel(any(), any());
        }

        @Test
        @DisplayName("已登录 → 调用 service.cancel + 200")
        void cancelled() {
            SecurityContext.set(testUser);

            Result<Void> result = controller.cancel(new CancelOrderRequest(9001L));

            assertThat(result.getCode()).isEqualTo(200);
            verify(orderService).cancel(9001L, 10001L);
        }
    }
}
