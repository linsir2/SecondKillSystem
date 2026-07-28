package com.seckill.module.stock.scheduler;

import com.seckill.module.order.mapper.SeckillOrderMapper;
import com.seckill.module.order.model.entity.SeckillOrder;
import com.seckill.module.stock.service.SeckillStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link PendingOrphanScanner} 单元测试。
 *
 * <p>Pure Mockito，无 Spring 上下文。覆盖正常/异常/极端场景。
 *
 * <p>Scanner 隔离策略：模拟 Redis SCAN → ZRANGEBYSCORE → MySQL batch query →
 * 差集 orphans → GET meta → compensateByTimeout → DEL meta。</p>
 */
@DisplayName("PendingOrphanScanner — 悬空死账兜底扫描")
class PendingOrphanScannerTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ZSetOperations<String, String> zsetOps = mock(ZSetOperations.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final SeckillOrderMapper orderMapper = mock(SeckillOrderMapper.class);
    private final SeckillStockService seckillStockService = mock(SeckillStockService.class);

    private PendingOrphanScanner scanner;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        scanner = new PendingOrphanScanner(redisTemplate, orderMapper, seckillStockService);
    }

    // ==================== helpers ====================

    private static SeckillOrder order(String token) {
        var o = new SeckillOrder();
        o.setOrderToken(token);
        return o;
    }

    private static String metaKey(String token) {
        return "seckill:pending:meta:" + token;
    }

    // ================================================================
    // N — 正常路径
    // ================================================================

    @Test
    @DisplayName("N1 无 pending key → 无事发生")
    void noPendingKeys() {
        when(redisTemplate.keys(anyString())).thenReturn(Collections.emptySet());

        scanner.doScan(0L);

        verifyNoInteractions(zsetOps, orderMapper, seckillStockService);
    }

    @Test
    @DisplayName("N2 pending key 存在但窗口内无 token → skip")
    void windowEmpty() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1"));
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Collections.emptySet());

        scanner.doScan(0L);

        verify(orderMapper, never()).selectList(any());
        verify(seckillStockService, never()).compensateByTimeout(any(), any(), any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("N3 窗口内有 token，全部在 MySQL 中存在 → 无补偿")
    void allExistInMysql() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1"));
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of("t1", "t2"));
        when(orderMapper.selectList(any())).thenReturn(List.of(order("t1"), order("t2")));

        scanner.doScan(0L);

        verify(orderMapper).selectList(any());
        verify(seckillStockService, never()).compensateByTimeout(any(), any(), any(), anyInt(), anyString());
        verifyNoInteractions(valueOps);
    }

    @Test
    @DisplayName("N4 部分 orphan → 补偿 orphan，跳过已有订单的 token")
    void partialOrphans() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1"));
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of("t1", "t2", "t3"));
        when(orderMapper.selectList(any())).thenReturn(List.of(order("t1"))); // t1 exists
        when(valueOps.get(metaKey("t2"))).thenReturn("100:200:2");
        when(valueOps.get(metaKey("t3"))).thenReturn("101:201:1");
        when(redisTemplate.delete(anyString())).thenReturn(true);

        scanner.doScan(0L);

        verify(seckillStockService).compensateByTimeout(1L, 200L, 100L, 2, "t2");
        verify(seckillStockService).compensateByTimeout(1L, 201L, 101L, 1, "t3");
        verify(redisTemplate, times(2)).delete(anyString());
        verify(zsetOps, never()).remove(anyString(), anyString());
    }

    @Test
    @DisplayName("N5 多个 activity 各有 orphan → 独立处理")
    void multipleActivities() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1", "seckill:pending:2"));
        when(zsetOps.rangeByScore(eq("seckill:pending:1"), anyDouble(), anyDouble())).thenReturn(Set.of("t1"));
        when(zsetOps.rangeByScore(eq("seckill:pending:2"), anyDouble(), anyDouble())).thenReturn(Set.of("t2"));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(valueOps.get(metaKey("t1"))).thenReturn("10:20:1");
        when(valueOps.get(metaKey("t2"))).thenReturn("30:40:2");
        when(redisTemplate.delete(anyString())).thenReturn(true);

        scanner.doScan(0L);

        verify(seckillStockService).compensateByTimeout(1L, 20L, 10L, 1, "t1");
        verify(seckillStockService).compensateByTimeout(2L, 40L, 30L, 2, "t2");
    }

    // ================================================================
    // F — 失败/异常
    // ================================================================

    @Test
    @DisplayName("F1 KEYS 抛异常 → 捕获，无事发生")
    void keysThrows() {
        when(redisTemplate.keys(anyString())).thenThrow(new RuntimeException("Redis connection failed"));

        scanner.doScan(0L);

        verifyNoInteractions(zsetOps, orderMapper, seckillStockService);
    }

    @Test
    @DisplayName("F2 某个 activity 的 ZRANGEBYSCORE 抛异常 → 其他 activity 不受影响")
    void rangeByScoreThrowsForOneActivity() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1", "seckill:pending:2"));
        when(zsetOps.rangeByScore(eq("seckill:pending:1"), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Redis error"));
        when(zsetOps.rangeByScore(eq("seckill:pending:2"), anyDouble(), anyDouble()))
                .thenReturn(Set.of("t2"));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(valueOps.get(metaKey("t2"))).thenReturn("30:40:2");
        when(redisTemplate.delete(anyString())).thenReturn(true);

        scanner.doScan(0L);

        verify(seckillStockService).compensateByTimeout(2L, 40L, 30L, 2, "t2");
    }

    @Test
    @DisplayName("F3 MySQL batch query 抛异常 → 该批次跳过，不擅自动作")
    void mysqlQueryThrows() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1"));
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of("t1", "t2"));
        when(orderMapper.selectList(any())).thenThrow(new RuntimeException("MySQL connection lost"));

        scanner.doScan(0L);

        verify(seckillStockService, never()).compensateByTimeout(any(), any(), any(), anyInt(), anyString());
        verifyNoInteractions(valueOps);
    }

    @Test
    @DisplayName("F4 compensateByTimeout 抛异常 → 其他 orphan 不受影响，该 orphan 下轮重试")
    void compensateThrowsForOneOrphan() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1"));
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of("t1", "t2"));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(valueOps.get(metaKey("t1"))).thenReturn("101:201:1");
        when(valueOps.get(metaKey("t2"))).thenReturn("100:200:2");
        doThrow(new RuntimeException("Lua execute failed")).when(seckillStockService)
                .compensateByTimeout(1L, 200L, 100L, 2, "t2");
        when(redisTemplate.delete(anyString())).thenReturn(true);

        scanner.doScan(0L);

        // t1 succeeds → DEL meta
        verify(seckillStockService).compensateByTimeout(1L, 201L, 101L, 1, "t1");
        // t2 throws → skip DEL meta (retry next cycle)
        verify(seckillStockService).compensateByTimeout(1L, 200L, 100L, 2, "t2");
        // only t1's meta deleted
        verify(redisTemplate).delete(metaKey("t1"));
        verify(redisTemplate, never()).delete(metaKey("t2"));
    }

    @Test
    @DisplayName("F5 meta 不存在（GET null）→ ZREM pending，不补偿")
    void metaMissing() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1"));
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of("t1"));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(valueOps.get(metaKey("t1"))).thenReturn(null);

        scanner.doScan(0L);

        verify(zsetOps).remove("seckill:pending:1", "t1");
        verify(seckillStockService, never()).compensateByTimeout(any(), any(), any(), anyInt(), anyString());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("F6 meta 格式损坏 → ZREM pending，不补偿")
    void metaCorrupted() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1"));
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of("t1"));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(valueOps.get(metaKey("t1"))).thenReturn("bad:format");

        scanner.doScan(0L);

        verify(zsetOps).remove("seckill:pending:1", "t1");
        verify(seckillStockService, never()).compensateByTimeout(any(), any(), any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("F7 pending key 格式异常（非数字 activityId）→ skip key")
    void invalidKeyFormat() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:abc"));

        scanner.doScan(0L);

        verifyNoInteractions(zsetOps);
    }

    @Test
    @DisplayName("F8 单个 activity 超过 50 个 token → 分批查询 MySQL")
    void largeBatchMySQL() {
        Set<String> largeSet = new LinkedHashSet<>();
        for (int i = 0; i < 60; i++) largeSet.add("t" + i);

        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1"));
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(largeSet);
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(valueOps.get(anyString())).thenReturn("1:1:1");
        when(redisTemplate.delete(anyString())).thenReturn(true);

        scanner.doScan(0L);

        verify(orderMapper, times(2)).selectList(any());
        verify(seckillStockService, times(60)).compensateByTimeout(any(), any(), any(), anyInt(), anyString());
    }

    // ================================================================
    // E — 边界/极端
    // ================================================================

    @Test
    @DisplayName("E1 混合结果：一个成功一个失败 → 成功 DEL，失败保留")
    void mixedCompensateResult() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1"));
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of("t1", "t2"));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(valueOps.get(metaKey("t1"))).thenReturn("1:10:1");
        when(valueOps.get(metaKey("t2"))).thenReturn("2:20:2");
        doThrow(new RuntimeException("Redis down")).when(seckillStockService)
                .compensateByTimeout(1L, 20L, 2L, 2, "t2");
        when(redisTemplate.delete(anyString())).thenReturn(true);

        scanner.doScan(0L);

        verify(redisTemplate).delete(metaKey("t1"));
        verify(redisTemplate, never()).delete(metaKey("t2"));
    }

    @Test
    @DisplayName("E2 补偿先调用，删除后调用（顺序正确）")
    void compensateBeforeDel() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1"));
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of("t1"));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(valueOps.get(metaKey("t1"))).thenReturn("1:10:1");
        when(redisTemplate.delete(anyString())).thenReturn(true);

        scanner.doScan(0L);

        var inOrder = inOrder(seckillStockService, redisTemplate);
        inOrder.verify(seckillStockService).compensateByTimeout(any(), any(), any(), anyInt(), anyString());
        inOrder.verify(redisTemplate).delete(metaKey("t1"));
    }

    @Test
    @DisplayName("E3 ZRANGEBYSCORE 返回 null → skip")
    void nullTokenSet() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1"));
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(null);

        scanner.doScan(0L);

        verify(orderMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("E4 无 activityId 提取（前缀不匹配）→ skip key")
    void unmatchedKeyPrefix() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("unknown:key"));

        scanner.doScan(0L);

        verifyNoInteractions(zsetOps);
    }

    @Test
    @DisplayName("E5 meta 有额外字段（兼容扩展）→ 只解析前三个，忽略多余")
    void metaExtraFields() {
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("seckill:pending:1"));
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of("t1"));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(valueOps.get(metaKey("t1"))).thenReturn("100:200:3:extra:stuff");
        when(redisTemplate.delete(anyString())).thenReturn(true);

        scanner.doScan(0L);

        verify(seckillStockService).compensateByTimeout(1L, 200L, 100L, 3, "t1");
    }
}
