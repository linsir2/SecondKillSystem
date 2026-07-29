package com.seckill.module.stock.listener;

import com.seckill.module.order.model.dto.OrderPaidEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.mockito.Mockito.*;

/**
 * {@link OrderPaidEventListener} 单元测试。
 *
 * <p>验证支付成功后 ZREM 清理 pending ZSET，且各种异常被 catch 不传播。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderPaidEventListenerTest {

    private static final Long ORDER_NO = 9001L;
    private static final Long ACTIVITY_ID = 100L;
    private static final String ORDER_TOKEN = "test-order-token";
    private static final String PENDING_KEY = "seckill:pending:" + ACTIVITY_ID;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOps;

    private OrderPaidEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderPaidEventListener(redisTemplate);
    }

    @Test
    @DisplayName("P1 收到事件 -> ZREM 清理 pending ZSET")
    void handleNormal() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        var event = new OrderPaidEvent(ORDER_NO, ORDER_TOKEN, ACTIVITY_ID);

        listener.handleOrderPaid(event);

        verify(zSetOps).remove(PENDING_KEY, ORDER_TOKEN);
    }

    @Test
    @DisplayName("F1 ZSET key 不存在 -> ZREM 返回 0, 静默跳过")
    void zsetKeyNotExist() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.remove(PENDING_KEY, ORDER_TOKEN)).thenReturn(0L);
        var event = new OrderPaidEvent(ORDER_NO, ORDER_TOKEN, ACTIVITY_ID);

        listener.handleOrderPaid(event);

        verify(zSetOps).remove(PENDING_KEY, ORDER_TOKEN);
        // 无异常
    }

    @Test
    @DisplayName("F2 Redis 连接异常 -> catch + log, 不传播")
    void redisException() {
        when(redisTemplate.opsForZSet()).thenThrow(new RuntimeException("Redis connection lost"));
        var event = new OrderPaidEvent(ORDER_NO, ORDER_TOKEN, ACTIVITY_ID);

        // 不抛异常
        listener.handleOrderPaid(event);

        verify(redisTemplate).opsForZSet();
    }

    @Test
    @DisplayName("E1 event null -> 不调 Redis, 直接 return")
    void nullEvent() {
        listener.handleOrderPaid(null);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("E2 orderToken null -> ZREM value=null, 不抛 NPE")
    void nullOrderToken() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        var event = new OrderPaidEvent(ORDER_NO, null, ACTIVITY_ID);

        listener.handleOrderPaid(event);

        verify(zSetOps).remove(PENDING_KEY, null);
    }
}
