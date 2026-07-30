package com.seckill.module.stock.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.config.mq.SeckillProducerConfig;
import com.seckill.module.stock.model.dto.SeckillDeductResult;
import com.seckill.module.stock.model.dto.SeckillDeductedEvent;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
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
 *   │ M1 发送调用    成功扣减 → syncSend 被调用                              │
 *   │ M2 事件字段    事件体字段全对                                            │
 *   │ M3 发送失败    syncSend 抛异常 → 补偿 + BusinessException                │
 *   │ M4 补偿后可重买   syncSend 失败补偿后同用户再买 → 成功                   │
 *   │ M5 重复不发送   重复购买 → syncSend 只调 1 次（首次）                    │
 *   │ M6 超限购不发送  limit=2 buy=5 → syncSend 未被调用                       │
 *   │ M7 售罄不发送    stock=0 → syncSend 未被调用                             │
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
    // M1: syncSend 被调用
    // ================================================================

    @Test
    @DisplayName("M1 发送调用: 成功扣减 → syncSend 被调用，destination 正确")
    void shouldSendMqOnSuccess() {
        warmup(10, 5);

        SeckillDeductResult r = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, r.code());

        verify(rocketMQTemplate)
                .syncSend(eq(SeckillProducerConfig.DESTINATION_STOCK_DEDUCTED),
                        any(Object.class));
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
        verify(rocketMQTemplate).syncSend(anyString(), eventCaptor.capture());

        SeckillDeductedEvent event = eventCaptor.getValue();
        assertEquals(r.orderToken(), event.orderToken());
        assertEquals(USER_A, event.userId());
        assertEquals(ACTIVITY_ID, event.activityId());
        assertEquals(GOODS_ID, event.seckillGoodsId());
        assertEquals(2, event.buyCount());
    }

    // ================================================================
    // M3: 发送失败（同步） → 补偿 + BusinessException
    // ================================================================

    @Test
    @DisplayName("M3 发送失败: syncSend 抛异常 → 补偿 + BusinessException")
    void shouldCompensateAndThrowOnSyncFailure() {
        warmup(10, 5);

        doThrow(new RuntimeException("Broker unavailable"))
                .when(rocketMQTemplate)
                .syncSend(anyString(), any(Object.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2));
        assertTrue(ex.getMessage().contains("系统繁忙"), "异常消息应包含'系统繁忙'提示");

        // 补偿已执行
        assertEquals("10", redisTemplate.opsForValue().get(stockKey));
        assertFalse(redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A)));
        assertEquals(0L, redisTemplate.opsForZSet().size(pendingKey));
    }

    // ================================================================
    // M4: 补偿后可重新购买
    // ================================================================

    @Test
    @DisplayName("M4 补偿后重买: syncSend 失败补偿后同用户再买 → 成功")
    void shouldAllowRePurchaseAfterMqCompensation() {
        warmup(10, 5);

        // 第 1 次购买 → 模拟 MQ 失败（syncSend 抛异常 → 补偿 + BusinessException）
        doThrow(new RuntimeException("Broker unavailable"))
                .when(rocketMQTemplate)
                .syncSend(anyString(), any(Object.class));

        assertThrows(BusinessException.class,
                () -> seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2));

        // 补偿已执行：stock 恢复，user 移除
        assertEquals("10", redisTemplate.opsForValue().get(stockKey));
        assertFalse(redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A)));

        // 恢复 mock：第 2 次购买允许通过
        Mockito.reset(rocketMQTemplate);

        SeckillDeductResult r2 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, r2.code());
        assertNotNull(r2.orderToken());

        assertEquals("8", redisTemplate.opsForValue().get(stockKey)); // 10-2=8
        assertTrue(redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A))); // 重新 SADD
        assertEquals(1L, redisTemplate.opsForZSet().size(pendingKey)); // 新 token

        // syncSend 被调了 1 次（重买，首次抛异常未调用成功）
        verify(rocketMQTemplate, times(1)).syncSend(anyString(), any(Object.class));
    }

    // ================================================================
    // M5: 重复购买 → 不调第二次 syncSend
    // ================================================================

    @Test
    @DisplayName("M5 重复不发送: A 重复购买 → syncSend 只调 1 次（首次）")
    void shouldNotSendMqOnDuplicate() {
        warmup(10, 5);

        // 第 1 次
        seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2);
        verify(rocketMQTemplate, times(1)).syncSend(anyString(), any(Object.class));

        // 第 2 次 → 重复
        seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 1);
        verify(rocketMQTemplate, times(1)).syncSend(anyString(), any(Object.class));
    }

    // ================================================================
    // M6/M7: 拒绝场景 → syncSend 未被调用
    // ================================================================

    @Test
    @DisplayName("M6 超限购不发送: limit=2 buy=5 → syncSend 未被调用")
    void shouldNotSendMqOnOverLimit() {
        warmup(10, 2);
        seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 5);
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    @DisplayName("M7 售罄不发送: stock=0 buy=1 → syncSend 未被调用")
    void shouldNotSendMqOnSoldOut() {
        warmup(0, 5);
        seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_B, 1);
        verifyNoInteractions(rocketMQTemplate);
    }
}
