package com.seckill.module.stock.listener;

import com.seckill.module.order.model.dto.OrderTimedOutEvent;
import com.seckill.module.stock.service.SeckillStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * {@link OrderTimedOutEventListener} 单元测试。
 *
 * <p>验证事件收到后调 compensateByTimeout，且异常被 catch 不传播。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderTimedOutEventListenerTest {

    @Mock
    private SeckillStockService seckillStockService;

    private OrderTimedOutEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderTimedOutEventListener(seckillStockService);
    }

    @Test
    @DisplayName("N1 收到事件 -> 调 compensateByTimeout 含全字段")
    void handleNormal() {
        var event = new OrderTimedOutEvent(10001L, "token-x", 10L, 20L, 30001L, 2);

        listener.handleOrderTimedOut(event);

        verify(seckillStockService).compensateByTimeout(10L, 20L, 30001L, 2, "token-x");
    }

    @Test
    @DisplayName("F1 Redis 异常 -> catch + log, 不传播")
    void redisException() {
        var event = new OrderTimedOutEvent(10001L, "token-x", 10L, 20L, 30001L, 2);
        doThrow(new RuntimeException("Redis connection lost"))
                .when(seckillStockService).compensateByTimeout(any(), any(), any(), anyInt(), anyString());

        // 不抛异常
        listener.handleOrderTimedOut(event);

        verify(seckillStockService).compensateByTimeout(10L, 20L, 30001L, 2, "token-x");
    }

    @Test
    @DisplayName("E1 事件字段 null -> IAE 被 catch + log")
    void nullFieldEvent() {
        var event = new OrderTimedOutEvent(null, null, null, null, null, 0);

        listener.handleOrderTimedOut(event);

        // compensateByTimeout 内部抛 IAE → 被 catch, 不传播
        verify(seckillStockService).compensateByTimeout(null, null, null, 0, null);
    }
}
