package com.seckill.module.order.mq;

import com.seckill.common.exception.BusinessException;
import com.seckill.module.order.model.dto.OrderTimeoutMessage;
import com.seckill.module.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * {@link OrderTimeoutConsumer} 单元测试。
 *
 * <p>纯 Mockito，验证 onMessage 对不同异常的 ack/retry 决策。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutConsumerTest {

    @Mock
    private OrderService orderService;

    private OrderTimeoutConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderTimeoutConsumer(orderService);
    }

    @Nested
    @DisplayName("正常路径 → ack，不抛异常")
    class NormalPath {

        @Test
        void N1_正常消费() {
            var msg = new OrderTimeoutMessage("token-1", 10001L);

            assertDoesNotThrow(() -> consumer.onMessage(msg));
            verify(orderService).cancelByTimeout("token-1");
        }
    }

    @Nested
    @DisplayName("永久失败 → catch + ack，不抛异常")
    class PermanentFailure {

        @Test
        void P1_BusinessException_订单不存在() {
            var msg = new OrderTimeoutMessage("token-missing", 99999L);
            doThrow(new BusinessException("订单不存在")).when(orderService).cancelByTimeout("token-missing");

            assertDoesNotThrow(() -> consumer.onMessage(msg));
            verify(orderService).cancelByTimeout("token-missing");
        }

        @Test
        void P2_IllegalArgumentException_格式异常() {
            var msg = new OrderTimeoutMessage("", null);
            doThrow(new IllegalArgumentException("orderToken must not be blank"))
                    .when(orderService).cancelByTimeout("");

            assertDoesNotThrow(() -> consumer.onMessage(msg));
            verify(orderService).cancelByTimeout("");
        }
    }

    @Nested
    @DisplayName("临时失败 → throw，RocketMQ 重试")
    class TransientFailure {

        @Test
        void T1_RuntimeException_DB不可用() {
            var msg = new OrderTimeoutMessage("token-1", 10001L);
            doThrow(new RuntimeException("DB connection error"))
                    .when(orderService).cancelByTimeout("token-1");

            assertThrows(RuntimeException.class, () -> consumer.onMessage(msg));
            verify(orderService).cancelByTimeout("token-1");
        }
    }
}
