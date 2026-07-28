package com.seckill.module.stock.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.config.mq.SeckillProducerConfig;
import com.seckill.module.stock.model.dto.SeckillDeductResult;
import com.seckill.module.stock.model.dto.SeckillDeductedEvent;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 秒杀库存扣减服务 — MQ 发送与补偿逻辑，9 场景。
 *
 * <pre>
 *   ┌──────────────────────────────────────────────────────────────────────────┐
 *   │ M1 发送调用    成功扣减 → asyncSend 被调用                              │
 *   │ M2 事件字段    事件体字段全对                                            │
 *   │ M3 发送成功    onSuccess → 不补偿                                        │
 *   │ M4 发送失败    onException → 恢复库存 + SREM + ZREM                     │
 *   │ M5 同步异常    asyncSend 同步抛异常 → 补偿 + BusinessException          │
 *   │ M6 补偿后可重买   补偿后同用户重新购买 → 成功                            │
 *   │ M7 重复不发送   重复购买 → asyncSend 只调 1 次（首次）                   │
 *   │ M8 超限购不发送   limit=2 buy=5 → asyncSend 未调用                          │
 *   │ M9 售罄不发送     stock=0 → asyncSend 未调用                                │
 *   └──────────────────────────────────────────────────────────────────────────┘
 * </pre>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
    }
)
@Tag("integration")
@DisplayName("SeckillStockService — MQ 发送与补偿")
class SeckillStockServiceMqTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SeckillStockService seckillStockService;

    @MockBean
    private RocketMQTemplate rocketMQTemplate;

    private static final Long ACTIVITY_ID = 100L;
    private static final Long GOODS_ID = 200L;
    private static final Long USER_A = 10001L;
    private static final Long USER_B = 10002L;

    private String stockKey;
    private String usersKey;
    private String limitKey;
    private String pendingKey;

    @BeforeEach
    void setUp() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });

        stockKey = "seckill:stock:" + ACTIVITY_ID + ":" + GOODS_ID;
        usersKey = "seckill:users:" + ACTIVITY_ID + ":" + GOODS_ID;
        limitKey = "seckill:limit:" + ACTIVITY_ID + ":" + GOODS_ID;
        pendingKey = "seckill:pending:" + ACTIVITY_ID;
    }

    private void warmup(int stock, int limit) {
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        redisTemplate.opsForValue().set(limitKey, String.valueOf(limit));
    }

    // ================================================================
    // M1: asyncSend 被调用
    // ================================================================

    @Test
    @DisplayName("M1 发送调用: 成功扣减 → asyncSend 被调用，destination 正确")
    void shouldSendMqOnSuccess() {
        warmup(10, 5);

        SeckillDeductResult r = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, r.code());

        verify(rocketMQTemplate)
                .asyncSend(eq(SeckillProducerConfig.DESTINATION_STOCK_DEDUCTED),
                        any(Object.class),
                        any(SendCallback.class));
    }

    // ================================================================
    // M2: 事件体字段完整
    // ================================================================

    @Test
    @DisplayName("M2 事件字段: 事件体 5 个字段全匹配")
    void shouldContainAllEventFields() {
        warmup(10, 5);

        SeckillDeductResult r = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2);
        assertNotNull(r.orderToken());

        ArgumentCaptor<SeckillDeductedEvent> eventCaptor = ArgumentCaptor.forClass(SeckillDeductedEvent.class);
        verify(rocketMQTemplate).asyncSend(anyString(), eventCaptor.capture(), any(SendCallback.class));

        SeckillDeductedEvent event = eventCaptor.getValue();
        assertEquals(r.orderToken(), event.orderToken());
        assertEquals(USER_A, event.userId());
        assertEquals(ACTIVITY_ID, event.activityId());
        assertEquals(GOODS_ID, event.seckillGoodsId());
        assertEquals(2, event.buyCount());
    }

    // ================================================================
    // M3: onSuccess → 不补偿
    // ================================================================

    @Test
    @DisplayName("M3 发送成功: onSuccess → Redis 状态不变（不补偿）")
    void shouldNotCompensateOnSuccess() throws Exception {
        warmup(10, 5);

        SeckillDeductResult r = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, r.code());

        // 记录扣减后的 Redis 快照
        String stockAfter = redisTemplate.opsForValue().get(stockKey);
        Boolean userInSet = redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A));
        Double pendingScore = redisTemplate.opsForZSet().score(pendingKey, r.orderToken());

        // 触发 onSuccess 回调
        ArgumentCaptor<SendCallback> cb = ArgumentCaptor.forClass(SendCallback.class);
        verify(rocketMQTemplate).asyncSend(anyString(), any(Object.class), cb.capture());
        cb.getValue().onSuccess(mock(SendResult.class));

        // Redis 状态不变
        assertEquals(stockAfter, redisTemplate.opsForValue().get(stockKey));
        assertEquals(userInSet, redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A)));
        assertEquals(pendingScore, redisTemplate.opsForZSet().score(pendingKey, r.orderToken()));
    }

    // ================================================================
    // M4: onException → 补偿
    // ================================================================

    @Test
    @DisplayName("M4 发送失败: onException → INCRBY stock + SREM + ZREM")
    void shouldCompensateOnException() throws Exception {
        warmup(10, 5);

        SeckillDeductResult r = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, r.code());

        // 触发 onException
        ArgumentCaptor<SendCallback> cb = ArgumentCaptor.forClass(SendCallback.class);
        verify(rocketMQTemplate).asyncSend(anyString(), any(Object.class), cb.capture());
        cb.getValue().onException(new RuntimeException("send failed"));

        // Redis 已补偿
        assertEquals("10", redisTemplate.opsForValue().get(stockKey)); // 恢复
        assertFalse(redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A))); // SREM
        assertNull(redisTemplate.opsForZSet().score(pendingKey, r.orderToken())); // ZREM
    }

    // ================================================================
    // M5: 同步异常 → 补偿 + BusinessException
    // ================================================================

    @Test
    @DisplayName("M5 同步异常: asyncSend 抛异常 → 补偿 + BusinessException")
    void shouldCompensateAndThrowOnSyncFailure() {
        warmup(10, 5);

        doThrow(new RuntimeException("Broker unavailable"))
                .when(rocketMQTemplate)
                .asyncSend(anyString(), any(Object.class), any(SendCallback.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2));
        assertTrue(ex.getMessage().contains("系统繁忙"), "异常消息应包含'系统繁忙'提示");

        // 补偿已执行
        assertEquals("10", redisTemplate.opsForValue().get(stockKey));
        assertFalse(redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A)));
        assertEquals(0L, redisTemplate.opsForZSet().size(pendingKey));
    }

    // ================================================================
    // M6: 补偿后可重新购买
    // ================================================================

    @Test
    @DisplayName("M6 补偿后重买: onException 补偿后同用户再买 → 成功")
    void shouldAllowRePurchaseAfterMqCompensation() throws Exception {
        warmup(10, 5);

        // 第 1 次购买 → 模拟 MQ 失败
        SeckillDeductResult r1 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, r1.code());

        ArgumentCaptor<SendCallback> cb1 = ArgumentCaptor.forClass(SendCallback.class);
        verify(rocketMQTemplate, times(1)).asyncSend(anyString(), any(Object.class), cb1.capture());
        cb1.getValue().onException(new RuntimeException("send failed"));

        // 第 2 次购买 → 应成功（补偿后 Redis 已还原）
        SeckillDeductResult r2 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, r2.code());
        assertNotNull(r2.orderToken());
        assertNotEquals(r1.orderToken(), r2.orderToken());

        assertEquals("8", redisTemplate.opsForValue().get(stockKey)); // 10-2=8
        assertTrue(redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A))); // 重新 SADD
        assertEquals(1L, redisTemplate.opsForZSet().size(pendingKey)); // 新 token

        // asyncSend 被调了 2 次（首次 + 重买）
        verify(rocketMQTemplate, times(2)).asyncSend(anyString(), any(Object.class), any(SendCallback.class));
    }

    // ================================================================
    // M7: 重复购买 → 不调第二次 asyncSend
    // ================================================================

    @Test
    @DisplayName("M7 重复不发送: A 重复购买 → asyncSend 只调 1 次（首次）")
    void shouldNotSendMqOnDuplicate() {
        warmup(10, 5);

        // 第 1 次
        seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2);
        verify(rocketMQTemplate, times(1)).asyncSend(anyString(), any(Object.class), any(SendCallback.class));

        // 第 2 次 → 重复
        seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 1);
        verify(rocketMQTemplate, times(1)).asyncSend(anyString(), any(Object.class), any(SendCallback.class));
    }

    // ================================================================
    // M8/M9: 拒绝场景 → asyncSend 未被调用
    // ================================================================

    @Test
    @DisplayName("M8 超限购不发送: limit=2 buy=5 → asyncSend 未被调用")
    void shouldNotSendMqOnOverLimit() {
        warmup(10, 2);
        seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 5);
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    @DisplayName("M9 售罄不发送: stock=0 buy=1 → asyncSend 未被调用")
    void shouldNotSendMqOnSoldOut() {
        warmup(0, 5);
        seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_B, 1);
        verifyNoInteractions(rocketMQTemplate);
    }
}
