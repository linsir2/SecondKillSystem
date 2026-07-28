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
 * 秒杀库存扣减服务 — TDD RED 阶段。
 *
 * <p>被测对象: {@link SeckillStockService#deduct(Long, Long, Long)}
 * <p>该服务封装 Redis+Lua 原子预扣，成功后生成 orderToken 并 ZADD 到 pending 队列。
 *
 * <p>前置条件: docker-compose Redis（cd docker && docker compose up -d）
 *
 * <p><b>4 个高价值场景：</b>
 * <ol>
 *   <li>并发超卖防护 — 生产中最致命的秒杀 bug</li>
 *   <li>重复购买幂等 — 一人多抢只算一次</li>
 *   <li>售罄边界 — stock=0 时全拒绝且无副作用</li>
 *   <li>未预热 — key 不存在时优雅降级，不留垃圾数据</li>
 * </ol>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        // 本测试只依赖 Redis，排除 MySQL / Flyway / MyBatis-Plus / RocketMQ 自动装配
        "spring.autoconfigure.exclude="
            + "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
    }
)
@Tag("integration")
@DisplayName("秒杀库存扣减服务")
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
    private String pendingKey;

    @BeforeEach
    void setUp() {
        // FLUSHALL 保证用例隔离
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });

        stockKey = "seckill:stock:" + ACTIVITY_ID + ":" + GOODS_ID;
        usersKey = "seckill:users:" + ACTIVITY_ID + ":" + GOODS_ID;
        pendingKey = "seckill:pending:" + ACTIVITY_ID;
    }

    // ================================================================
    // 场景 1: 并发超卖防护 — 10 线程抢 3 件，恰好 3 成功
    // ================================================================

    @Test
    @DisplayName("并发超卖防护: 10 线程抢 3 件 → 恰好 3 成功 7 售罄，库存归零")
    void shouldNotOversellUnderConcurrency() throws Exception {
        redisTemplate.opsForValue().set(stockKey, "3");

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<SeckillDeductResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final long userId = 10001L + i; // 不同用户
            futures.add(executor.submit(
                    () -> seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, userId)));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // --- 统计返回值 ---
        int success = 0, duplicate = 0, soldOut = 0;
        Set<String> tokens = new HashSet<>();
        for (Future<SeckillDeductResult> f : futures) {
            SeckillDeductResult r = f.get();
            switch (r.code()) {
                case SeckillDeductResult.CODE_SUCCESS -> {
                    success++;
                    assertNotNull(r.orderToken(), "成功时 orderToken 不能为 null");
                    tokens.add(r.orderToken());
                }
                case SeckillDeductResult.CODE_DUPLICATE -> duplicate++;
                case SeckillDeductResult.CODE_SOLD_OUT -> soldOut++;
            }
        }

        assertEquals(3, success, "恰好 3 人成功");
        assertEquals(0, duplicate, "无重复（每人不同 userId）");
        assertEquals(7, soldOut, "其余 7 人售罄");
        assertEquals(3, tokens.size(), "3 个 orderToken 各不相同");

        // --- Redis 状态一致性 ---
        assertEquals("0", redisTemplate.opsForValue().get(stockKey), "库存归零");
        assertEquals(3L, redisTemplate.opsForSet().size(usersKey),
                "users set 恰好 3 人");
        assertEquals(3L, redisTemplate.opsForZSet().size(pendingKey),
                "pending zset 恰好 3 个 token");

        // pending zset 中每个 token 的 score 应为合理时间戳（非 0）
        redisTemplate.opsForZSet().rangeWithScores(pendingKey, 0, -1)
                .forEach(t -> assertTrue(t.getScore() > 0, "score 应为正时间戳"));
    }

    // ================================================================
    // 场景 2: 重复购买幂等 — 同一用户两次请求，首次成功二次拒绝
    // ================================================================

    @Test
    @DisplayName("重复购买: 同一用户两次请求 → 首次成功二次拒绝，库存只扣 1")
    void shouldRejectDuplicateBuyerAndPreserveConsistency() {
        redisTemplate.opsForValue().set(stockKey, "10");

        // 首次 → 成功
        SeckillDeductResult first = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, first.code());
        assertNotNull(first.orderToken());

        // 二次 → 重复
        SeckillDeductResult second = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A);
        assertEquals(SeckillDeductResult.CODE_DUPLICATE, second.code());
        assertNull(second.orderToken());

        // --- Redis 状态一致性 ---
        assertEquals("9", redisTemplate.opsForValue().get(stockKey), "库存仅扣 1 次");
        assertEquals(1L, redisTemplate.opsForSet().size(usersKey), "users set 仅 1 人");
        assertTrue(redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A)));
        assertEquals(1L, redisTemplate.opsForZSet().size(pendingKey), "pending 仅 1 个 token");

        // B 用户不受影响
        SeckillDeductResult b = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_B);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, b.code());
        assertEquals("8", redisTemplate.opsForValue().get(stockKey));
    }

    // ================================================================
    // 场景 3: 售罄边界 — stock=0 时全拒绝且无副作用
    // ================================================================

    @Test
    @DisplayName("售罄边界: stock=0 → 全返回 SOLD_OUT，Redis 状态不变")
    void shouldRejectAllWhenSoldOut() {
        redisTemplate.opsForValue().set(stockKey, "0");

        SeckillDeductResult r1 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A);
        SeckillDeductResult r2 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_B);

        assertEquals(SeckillDeductResult.CODE_SOLD_OUT, r1.code());
        assertEquals(SeckillDeductResult.CODE_SOLD_OUT, r2.code());
        assertNull(r1.orderToken());
        assertNull(r2.orderToken());

        // 关键: 无副作用
        assertEquals("0", redisTemplate.opsForValue().get(stockKey), "库存仍为 0");
        assertEquals(0L, redisTemplate.opsForSet().size(usersKey), "users set 仍空");
        assertEquals(0L, redisTemplate.opsForZSet().size(pendingKey), "pending zset 仍空");
    }

    // ================================================================
    // 场景 4: 未预热 — stock key 不存在，优雅降级不留垃圾
    // ================================================================

    @Test
    @DisplayName("未预热: stock key 不存在 → 返回 SOLD_OUT，不创建任何 Redis key")
    void shouldRejectGracefullyWhenStockKeyNotExists() {
        // 不 SET stockKey —— 模拟活动预热遗漏

        SeckillDeductResult result = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A);

        assertEquals(SeckillDeductResult.CODE_SOLD_OUT, result.code());
        assertNull(result.orderToken());

        // 关键断言: 没有任何 key 被意外创建（垃圾数据）
        assertNull(redisTemplate.opsForValue().get(stockKey),
                "stockKey 不应被创建");
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(usersKey)),
                "usersKey 不应被创建");
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(pendingKey)),
                "pendingKey 不应被创建");

        // B 用户同理
        SeckillDeductResult r2 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_B);
        assertEquals(SeckillDeductResult.CODE_SOLD_OUT, r2.code());
    }

    // ================================================================
    // 场景 5: 异常输入 — 参数为 null 时应拒绝而不是静默处理
    // ================================================================

    @Test
    @DisplayName("异常输入: activityId/seckillGoodsId/userId 为 null → IllegalArgumentException")
    void shouldRejectNullArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.deduct(null, GOODS_ID, USER_A));
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.deduct(ACTIVITY_ID, null, USER_A));
        assertThrows(IllegalArgumentException.class,
                () -> seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, null));
    }

    // ================================================================
    // 场景 6: 边界库存 = 1 — 单个用户恰好买走最后一件，后面的全部 SOLD_OUT
    // ================================================================

    @Test
    @DisplayName("库存恰好为1: A 成功 → B 售罄 → C 售罄，库存归零，pending 仅 1 个 token")
    void shouldHandleExactlyOneStockBoundary() {
        redisTemplate.opsForValue().set(stockKey, "1");

        // A 买走最后一件
        SeckillDeductResult r1 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, r1.code());
        assertNotNull(r1.orderToken(), "成功后 orderToken 不能为 null");

        // B 售罄
        SeckillDeductResult r2 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_B);
        assertEquals(SeckillDeductResult.CODE_SOLD_OUT, r2.code());
        assertNull(r2.orderToken());

        // C 售罄
        SeckillDeductResult r3 = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, 10003L);
        assertEquals(SeckillDeductResult.CODE_SOLD_OUT, r3.code());
        assertNull(r3.orderToken());

        // —— Redis 状态一致性 ——
        assertEquals("0", redisTemplate.opsForValue().get(stockKey), "库存归零");
        assertEquals(1L, redisTemplate.opsForSet().size(usersKey), "users set 仅 A");
        assertTrue(redisTemplate.opsForSet().isMember(usersKey, String.valueOf(USER_A)));
        assertEquals(1L, redisTemplate.opsForZSet().size(pendingKey), "pending 仅 1 个 token");
    }

    // ================================================================
    // 场景 7: 幂等降级 — stock=1，A 买走后 A 再请求，应返回 DUPLICATE
    // ================================================================

    @Test
    @DisplayName("stock=1，A买走后A再试 → DUPLICATE，库存不变0，不产生额外token")
    void shouldRejectDuplicateAfterBuyingLastItem() {
        redisTemplate.opsForValue().set(stockKey, "1");

        // A 首次成功
        SeckillDeductResult first = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A);
        assertEquals(SeckillDeductResult.CODE_SUCCESS, first.code());
        String token = first.orderToken();
        assertNotNull(token);

        // A 二次：重复
        SeckillDeductResult second = seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A);
        assertEquals(SeckillDeductResult.CODE_DUPLICATE, second.code());
        assertNull(second.orderToken(), "拒绝时不生成 orderToken");

        // —— Redis 状态一致性 ——
        assertEquals("0", redisTemplate.opsForValue().get(stockKey), "库存仍为 0");
        assertEquals(1L, redisTemplate.opsForSet().size(usersKey), "users set 仍为 A 一人");
        assertEquals(1L, redisTemplate.opsForZSet().size(pendingKey), "pending 仍只 1 个 token");

        // 确认 pending 里的是首次的那个 token（没有被重复覆盖）
        Set<String> pendingTokens = redisTemplate.opsForZSet().range(pendingKey, 0, -1);
        assertNotNull(pendingTokens);
        assertTrue(pendingTokens.contains(token),
                "pending 中的 token 应与首次成功返回的一致");
    }

    // ================================================================
    // 场景 8: 同用户并发狂点 — 12 线程发同一 userId，原子性应只允许 1 次成功
    // ================================================================

    @Test
    @DisplayName("同用户并发12次: 恰好1成功11重复，库存只扣1")
    void shouldHandleConcurrentSameUserSpam() throws Exception {
        redisTemplate.opsForValue().set(stockKey, "5");

        int threadCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<SeckillDeductResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(
                    () -> seckillStockService.deduct(ACTIVITY_ID, GOODS_ID, USER_A)));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // —— 计数 ——
        long success = futures.stream()
                .map(f -> uncheckedGet(f))
                .filter(r -> r.code() == SeckillDeductResult.CODE_SUCCESS)
                .count();
        long duplicate = futures.stream()
                .map(f -> uncheckedGet(f))
                .filter(r -> r.code() == SeckillDeductResult.CODE_DUPLICATE)
                .count();

        assertEquals(1, success, "12 个并发请求应只有 1 次成功");
        assertEquals(threadCount - 1, duplicate, "其余均为重复");

        // —— Redis 状态一致性 ——
        assertEquals("4", redisTemplate.opsForValue().get(stockKey), "库存只扣 1 件");
        assertEquals(1L, redisTemplate.opsForSet().size(usersKey), "users set 1 人");
        assertEquals(1L, redisTemplate.opsForZSet().size(pendingKey), "pending 仅 1 个 token");
    }

    // ================================================================
    // helper
    // ================================================================

    /** 安全的 Future.get() 包装，避免 try-catch 噪声。 */
    private static SeckillDeductResult uncheckedGet(Future<SeckillDeductResult> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
