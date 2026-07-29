package com.seckill.module.order.controller;

import com.seckill.common.result.Result;
import com.seckill.module.order.model.vo.OrderStatusVO;
import com.seckill.module.order.service.OrderService;
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

    @Nested
    @DisplayName("GET /api/v1/order/status")
    class GetOrderStatus {

        @Test
        @DisplayName("订单存在 → 200 + status + orderNo")
        void found() {
            when(orderService.getOrderStatusVO("token-abc"))
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
            when(orderService.getOrderStatusVO("token-not-exist"))
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
}
