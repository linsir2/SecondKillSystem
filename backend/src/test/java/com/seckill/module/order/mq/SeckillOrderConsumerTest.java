package com.seckill.module.order.mq;

import com.seckill.common.exception.BusinessException;
import com.seckill.module.order.service.OrderService;
import com.seckill.module.stock.model.dto.SeckillDeductedEvent;
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
 * {@link SeckillOrderConsumer} 单元测试。
 *
 * <p>纯 Mockito 测试，不启动 Spring 上下文。
 * 验证 onMessage 对不同异常的 ack/retry 决策正确。</p>
 *
 * @see SeckillOrderConsumer
 */
@ExtendWith(MockitoExtension.class)
class SeckillOrderConsumerTest {

    @Mock
    private OrderService orderService;

    private SeckillOrderConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SeckillOrderConsumer(orderService);
    }

    @Nested
    @DisplayName("正常路径 → ack，不抛异常")
    class NormalPath {

        @Test
        void C1_正常消费() {
            SeckillDeductedEvent event = new SeckillDeductedEvent("t", 1L, 1L, 1L, 1);

            assertDoesNotThrow(() -> consumer.onMessage(event));
            verify(orderService).createOrder(event);
        }
    }

    @Nested
    @DisplayName("永久失败 → catch + ack，不抛异常（RocketMQ 不重试）")
    class PermanentFailure {

        @Test
        void C3_BusinessException_秒杀商品不存在() {
            SeckillDeductedEvent event = new SeckillDeductedEvent("t", 1L, 1L, 1L, 1);
            doThrow(new BusinessException("秒杀商品不存在")).when(orderService).createOrder(event);

            assertDoesNotThrow(() -> consumer.onMessage(event));
            verify(orderService).createOrder(event);
        }

        @Test
        void C4_IllegalArgumentException_消息格式异常() {
            SeckillDeductedEvent event = new SeckillDeductedEvent("", 1L, 1L, 1L, 1);
            doThrow(new IllegalArgumentException("orderToken must not be blank"))
                    .when(orderService).createOrder(event);

            assertDoesNotThrow(() -> consumer.onMessage(event));
            verify(orderService).createOrder(event);
        }
    }

    @Nested
    @DisplayName("临时失败 → throw，RocketMQ 重试")
    class TransientFailure {

        @Test
        void C5_RuntimeException_DB不可用() {
            SeckillDeductedEvent event = new SeckillDeductedEvent("t", 1L, 1L, 1L, 1);
            doThrow(new RuntimeException("DB connection error"))
                    .when(orderService).createOrder(event);

            assertThrows(RuntimeException.class, () -> consumer.onMessage(event));
            verify(orderService).createOrder(event);
        }
    }
}
