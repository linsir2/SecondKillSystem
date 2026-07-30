package com.seckill.module.stock.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.module.stock.model.dto.SeckillDeductResult;
import com.seckill.module.stock.model.dto.SeckillDeductedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SeckillStockService} meta 生命周期单元测试。
 *
 * <p>Pure Mockito，验证：
 * <ul>
 *   <li>{@code deduct()} 成功后 SET meta</li>
 *   <li>{@code compensateByTimeout()} 调用后 DEL meta</li>
 *   <li>{@code deduct()} MQ 失败 → 自动补偿 DEL meta</li>
 *   <li>二次补偿（ZREM 已执行）静默跳过，DEL meta 仍执行（无害）</li>
 * </ul>
 */
@DisplayName("SeckillStockService — meta 生命周期")
class SeckillStockServiceMetaTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ZSetOperations<String, String> zsetOps = mock(ZSetOperations.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private SeckillStockService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // deduct() → execute(script, keys, uid, count)        4 个参数
        // compensateByTimeout() → execute(script, keys, uid, count, token)  5 个参数
        // Mockito varargs 匹配要求精确 mock 调用签名，不能用 any() 代替
        when(redisTemplate.execute(any(), any(), anyString(), anyString()))
                .thenReturn(1L);
        when(redisTemplate.execute(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        service = new SeckillStockService(redisTemplate, eventPublisher);
    }

    // ================================================================
    // M — Meta 生命周期
    // ================================================================

    @Test
    @DisplayName("M1 deduct 成功 → SET meta，格式 uid:sgId:cnt，TTL=15min")
    void deductSetsMeta() {
        when(zsetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        var result = service.deduct(1L, 10L, 100L, 3);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, result.code());
        assertNotNull(result.orderToken());

        // meta SET: key 以 seckill:pending:meta: 开头
        var keyCaptor = ArgumentCaptor.forClass(String.class);
        var valCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), valCaptor.capture(), eq(Duration.ofMinutes(15)));

        assertTrue(keyCaptor.getValue().startsWith("seckill:pending:meta:"),
                "meta key prefix wrong: " + keyCaptor.getValue());
        assertEquals("100:10:3", valCaptor.getValue(),
                "meta value format must be userId:seckillGoodsId:buyCount");
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("M2 compensateByTimeout → DEL meta")
    void compensateByTimeoutDeletesMeta() {
        service.compensateByTimeout(1L, 10L, 100L, 1, "test-token");

        verify(redisTemplate).delete("seckill:pending:meta:test-token");
    }

    @Test
    @DisplayName("M3 deduct 事件发布同步异常 → compensate + DEL meta")
    void deductEventPublishFailCleansUpMeta() {
        // 事件发布抛出同步异常
        doThrow(new RuntimeException("Event bus unavailable"))
                .when(eventPublisher).publishEvent(any(SeckillDeductedEvent.class));

        assertThrows(BusinessException.class,
                () -> service.deduct(1L, 10L, 100L, 2));

        // meta SET 发生过
        verify(valueOps).set(anyString(), anyString(), any(Duration.class));
        // meta 被 DEL
        verify(redisTemplate).delete(argThat((String k) -> k.startsWith("seckill:pending:meta:")));
    }

    @Test
    @DisplayName("M4 二次补偿（ZREM 已执行）→ DEL meta 仍执行（幂等无害）")
    void secondCompensateStillDeletesMeta() {
        // 模拟 compensateScript 返回 0（ZREM 已被他人执行）
        when(redisTemplate.execute(any(), any(), anyString(), anyString(), anyString())).thenReturn(0L);

        service.compensateByTimeout(1L, 10L, 100L, 1, "test-token");

        // DEL meta 仍应执行（幂等）
        verify(redisTemplate).delete("seckill:pending:meta:test-token");
    }
}
