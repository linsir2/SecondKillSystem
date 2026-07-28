package com.seckill.module.stock.service;

import com.seckill.module.stock.model.dto.SeckillDeductResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 秒杀库存扣减服务 — TDD，8 场景覆盖。
 *
 * <pre>
 * 设计思路（10 年测试老师傅视角）：
 *   ┌───────────────────────────────────────────────────────────────┐
 *   │ S1 并发超卖      10 线程抢 3 件 → 3 成功 7 售罄             │
 *   │ S2 重复幂等      A 买两次 → 1 成功 1 重复，库存只扣 1 次    │
 *   │ S3 售罄边界      stock=0 → 全 SOLD_OUT                     │
 *   │ S4 未预热        key 不存在 → SOLD_OUT，不产生垃圾 key      │
 *   │ S5 非法参数      null → IllegalArgumentException             │
 *   │ S6 边界 1 件     stock=1 → A 成功 B 售罄                    │
 *   │ S7 同用户并发    12 线程同用户 → 1 成功 11 重复             │
 *   │ S8 超限购        limit=2 buy=5 → OVER_LIMIT，无副作用       │
 *   └───────────────────────────────────────────────────────────────┘
 * </pre>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.autoconfigure.exclude="
            + "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
    }
)
@Tag("integration")
@DisplayName("SeckillStockService — buyCount 支持")
class SeckillStockServiceTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SeckillStockService seckillStockService;

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

    /** 快速装配预热数据，各测试按需覆写。 */
    private void warmup(int stock, int limit) {
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        redisTemplate.opsForValue().set(limitKey, String.valueOf(limit));
    }

    // ================================================================
    // S1: 并发超卖防护
    // ================================================================

    @Test
    @DisplayName("S1 并发超卖: 10 线程抢 3 件 → 3 成功 7 售罄")
    void shouldNotOversellUnderConcurrency() throws Exception {
        warmup(3, 5);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<SeckillDeductResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final long userId = 10001L + i;
            futures.add(executor.submit(
                    () -> seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, userId, 1)));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        int success = 0, duplicate = 0, soldOut = 0;
        Set<String> tokens = new HashSet<>();
        for (Future<SeckillDeductResult> f : futures) {
            SeckillDeductResult r = f.get();
            switch (r.code()) {
                case SeckillDeductResult.CODE_SUCCESS -> {
                    success++;
                    assertNotNull(r.orderToken());
                    assertEquals(1, r.buyCount());
                    tokens.add(r.orderToken());
                }
                case SeckillDeductResult.CODE_DUPLICATE -> duplicate++;
                case SeckillDeductResult.CODE_SOLD_OUT -> soldOut++;
            }
        }

        assertEquals(3, success);
        assertEquals(0, duplicate);
        assertEquals(7, soldOut);
        assertEquals(3, tokens.size());

        assertEquals("0", redisTemplate.opsForValue().get(stockKey));
        assertEquals(3L, redisTemplate.opsForSet().size(usersKey));
        assertEquals(3L, redisTemplate.opsForZSet().size(pendingKey));

        redisTemplate.opsForZSet().rangeWithScores(pendingKey, 0, -1)
                .forEach(t -> assertTrue(t.getScore() > 0));
    }

    // ================================================================
    // S2: 重复购买幂等
    // ================================================================

    @Test
    @DisplayName("S2 重复幂等: A 买 2 → A 再买 1 → 1 成功 1 重复，库存只扣 2")
    void shouldRejectDuplicateBuyerAndPreserveConsistency() {
        warmup(10, 5);

        SeckillDeductResult first = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, first.code());
        assertNotNull(first.orderToken());
        assertEquals(2, first.buyCount());

        SeckillDeductResult second = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 1);
        assertEquals(SeckillDeductResult.CODE_DUPLICATE, second.code());
        assertNull(second.orderToken());
        assertEquals(0, second.buyCount());

        assertEquals("8", redisTemplate.opsForValue().get(stockKey));
        assertEquals(1L, redisTemplate.opsForSet().size(usersKey));
        assertTrue(redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A)));
        assertEquals(1L, redisTemplate.opsForZSet().size(pendingKey));

        // B 不受影响
        SeckillDeductResult b = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_B, 1);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, b.code());
        assertEquals("7", redisTemplate.opsForValue().get(stockKey));
    }

    // ================================================================
    // S3: 售罄边界
    // ================================================================

    @Test
    @DisplayName("S3 售罄: stock=0 → 全 SOLD_OUT")
    void shouldRejectAllWhenSoldOut() {
        warmup(0, 5);

        SeckillDeductResult r1 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 1);
        SeckillDeductResult r2 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_B, 1);

        assertEquals(SeckillDeductResult.CODE_SOLD_OUT, r1.code());
        assertEquals(SeckillDeductResult.CODE_SOLD_OUT, r2.code());
        assertNull(r1.orderToken());

        assertEquals("0", redisTemplate.opsForValue().get(stockKey));
        assertEquals(0L, redisTemplate.opsForSet().size(usersKey));
        assertEquals(0L, redisTemplate.opsForZSet().size(pendingKey));
    }

    // ================================================================
    // S4: 未预热
    // ================================================================

    @Test
    @DisplayName("S4 未预热: stock key 不存在 → SOLD_OUT，不创建垃圾 key")
    void shouldRejectGracefullyWhenStockKeyNotExists() {
        redisTemplate.opsForValue().set(limitKey, "5");
        // 不设 stock key

        SeckillDeductResult r = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 1);
        assertEquals(SeckillDeductResult.CODE_SOLD_OUT, r.code());
        assertNull(r.orderToken());

        assertNull(redisTemplate.opsForValue().get(stockKey));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(usersKey)));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(pendingKey)));
    }

    // ================================================================
    // S5: 非法参数
    // ================================================================

    @Test
    @DisplayName("S5 非法参数: null 参数 + buyCount<=0 → IAE")
    void shouldRejectNullArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.deduct(null, GOODS_ID, USER_A, 1));
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.deduct(ACTIVITY_ID, null, USER_A, 1));
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 0));
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, -1));
    }

    // ================================================================
    // S6: 边界 1 件
    // ================================================================

    @Test
    @DisplayName("S6 边界 1 件: stock=1, A 成功 → B 售罄")
    void shouldHandleExactlyOneStockBoundary() {
        warmup(1, 5);

        SeckillDeductResult r1 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 1);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, r1.code());
        assertNotNull(r1.orderToken());
        assertEquals(1, r1.buyCount());

        SeckillDeductResult r2 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_B, 1);
        assertEquals(SeckillDeductResult.CODE_SOLD_OUT, r2.code());

        assertEquals("0", redisTemplate.opsForValue().get(stockKey));
        assertEquals(1L, redisTemplate.opsForSet().size(usersKey));
        assertEquals(1L, redisTemplate.opsForZSet().size(pendingKey));
    }

    // ================================================================
    // S7: 同用户并发狂点
    // ================================================================

    @Test
    @DisplayName("S7 同用户并发 12 次: 1 成功 11 重复，库存只扣 1")
    void shouldHandleConcurrentSameUserSpam() throws Exception {
        warmup(5, 5);

        int threadCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<SeckillDeductResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(
                    () -> seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 1)));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        long success = futures.stream().map(SeckillStockServiceTest::uncheckedGet)
                .filter(r -> r.code() == SeckillDeductResult.CODE_SUCCESS).count();
        long duplicate = futures.stream().map(SeckillStockServiceTest::uncheckedGet)
                .filter(r -> r.code() == SeckillDeductResult.CODE_DUPLICATE).count();

        assertEquals(1, success);
        assertEquals(threadCount - 1, duplicate);

        assertEquals("4", redisTemplate.opsForValue().get(stockKey));
        assertEquals(1L, redisTemplate.opsForSet().size(usersKey));
        assertEquals(1L, redisTemplate.opsForZSet().size(pendingKey));
    }

    // ================================================================
    // S8: 单次超限购
    // ================================================================

    @Test
    @DisplayName("S8 超限购: limit=2 buy=5 → OVER_LIMIT，无副作用")
    void shouldRejectWhenBuyCountExceedsLimit() {
        warmup(10, 2);

        SeckillDeductResult r = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 5);
        assertEquals(SeckillDeductResult.CODE_OVER_LIMIT, r.code());
        assertNull(r.orderToken());
        assertEquals(0, r.buyCount());

        // 无副作用
        assertEquals("10", redisTemplate.opsForValue().get(stockKey));
        assertEquals(0L, redisTemplate.opsForSet().size(usersKey));
        assertEquals(0L, redisTemplate.opsForZSet().size(pendingKey));
    }

    // ================================================================
    // C1-C5: compensateByTimeout
    // ================================================================

    @Test
    @DisplayName("C1 正常补偿: stock++, users SREM, pending ZREM")
    void compensateNormal() {
        warmup(3, 5);
        var result = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 2);
        String token = result.orderToken();

        assertNotNull(redisTemplate.opsForValue().get(stockKey));
        assertEquals(1L, redisTemplate.opsForSet().size(usersKey));
        assertEquals(1L, redisTemplate.opsForZSet().size(pendingKey));

        // 用实际 token 补偿（Lua ZREM-as-lock 要求 token 必须匹配）
        seckillStockService.compensateByTimeout(ACTIVITY_ID, GOODS_ID, USER_A, 2, token);

        // stock: 3(warmup) - 2(deduct) + 2(compensate) = 3
        assertEquals("3", redisTemplate.opsForValue().get(stockKey));
        assertEquals(0L, redisTemplate.opsForSet().size(usersKey));
        assertEquals(0L, redisTemplate.opsForZSet().size(pendingKey));
    }

    @Test
    @DisplayName("C2 补偿后其他用户可正常抢购")
    void compensateThenAnotherCanBuy() {
        warmup(5, 5);
        var r1 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A, 3);

        seckillStockService.compensateByTimeout(ACTIVITY_ID, GOODS_ID, USER_A, 3, r1.orderToken());

        // B 可抢
        SeckillDeductResult br = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_B, 1);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, br.code());

        assertEquals("4", redisTemplate.opsForValue().get(stockKey));
        assertEquals(1L, redisTemplate.opsForSet().size(usersKey));
    }

    // ================================================================
    // V — 参数校验
    // ================================================================

    @Test
    @DisplayName("V1 activityId null -> IAE")
    void compensateNullActivityId() {
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.compensateByTimeout(null, GOODS_ID, USER_A, 1, "t"));
    }

    @Test
    @DisplayName("V2 seckillGoodsId null -> IAE")
    void compensateNullGoodsId() {
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.compensateByTimeout(ACTIVITY_ID, null, USER_A, 1, "t"));
    }

    @Test
    @DisplayName("V3 buyCount <=0 -> IAE")
    void compensateInvalidBuyCount() {
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.compensateByTimeout(ACTIVITY_ID, GOODS_ID, USER_A, 0, "t"));
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.compensateByTimeout(ACTIVITY_ID, GOODS_ID, USER_A, -1, "t"));
    }

    @Test
    @DisplayName("V4 orderToken null -> IAE")
    void compensateNullToken() {
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.compensateByTimeout(ACTIVITY_ID, GOODS_ID, USER_A, 1, null));
    }

    // ================================================================
    // helper
    // ================================================================

    private static SeckillDeductResult uncheckedGet(Future<SeckillDeductResult> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
