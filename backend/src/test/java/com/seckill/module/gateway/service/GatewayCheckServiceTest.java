package com.seckill.module.gateway.service;

import com.seckill.module.gateway.model.dto.GatewayCheckResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 网关防护服务 — TDD RED 阶段。
 *
 * <p>被测对象: {@link GatewayCheckService#check(Long)}
 * <p>职责: 黑名单校验（Redis Set）+ 单用户 1s 限流（SET NX EX）。
 *
 * <p>前置条件: docker-compose Redis（cd docker && docker compose up -d）
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
@DisplayName("网关防护：黑名单 + 1s 限流")
class GatewayCheckServiceTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private GatewayCheckService gatewayCheckService;

    private static final Long USER_A = 10001L;
    private static final Long USER_B = 10002L;

    private static final String BLACKLIST_KEY = "seckill:blacklist";
    private static final String RATE_KEY_PREFIX = "seckill:rate:";

    @BeforeEach
    void setUp() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    // ================================================================
    // 黑名单 (1-6)
    // ================================================================

    @Test
    @DisplayName("1. 用户不在黑名单 → PASS")
    void shouldPassWhenNotBlacklisted() {
        assertEquals(GatewayCheckResult.CODE_PASS,
                gatewayCheckService.check(USER_A).code());
    }

    @Test
    @DisplayName("2. 用户被封禁 → BLACKLISTED")
    void shouldRejectWhenBlacklisted() {
        redisTemplate.opsForSet().add(BLACKLIST_KEY, String.valueOf(USER_A));

        assertEquals(GatewayCheckResult.CODE_BLACKLISTED,
                gatewayCheckService.check(USER_A).code());
    }

    @Test
    @DisplayName("3. 黑名单 SET 不存在（nil）→ PASS")
    void shouldPassWhenBlacklistKeyNotExists() {
        assertEquals(GatewayCheckResult.CODE_PASS,
                gatewayCheckService.check(USER_A).code());
    }

    @Test
    @DisplayName("4. A封禁 B未封禁 → A拒 B放，隔离正确")
    void shouldIsolateBlacklistPerUser() {
        redisTemplate.opsForSet().add(BLACKLIST_KEY, String.valueOf(USER_A));

        assertEquals(GatewayCheckResult.CODE_BLACKLISTED,
                gatewayCheckService.check(USER_A).code());
        assertEquals(GatewayCheckResult.CODE_PASS,
                gatewayCheckService.check(USER_B).code());
    }

    @Test
    @DisplayName("5. 用户被封禁 → 解封（SREM）→ PASS")
    void shouldPassAfterUnban() {
        redisTemplate.opsForSet().add(BLACKLIST_KEY, String.valueOf(USER_A));
        assertEquals(GatewayCheckResult.CODE_BLACKLISTED,
                gatewayCheckService.check(USER_A).code());

        redisTemplate.opsForSet().remove(BLACKLIST_KEY, String.valueOf(USER_A));
        assertEquals(GatewayCheckResult.CODE_PASS,
                gatewayCheckService.check(USER_A).code());
    }

    @Test
    @DisplayName("6. 黑名单拒绝后不留限流 key 垃圾")
    void shouldNotCreateRateLimitKeyForBlacklistedUser() {
        redisTemplate.opsForSet().add(BLACKLIST_KEY, String.valueOf(USER_A));

        gatewayCheckService.check(USER_A);

        assertFalse(Boolean.TRUE.equals(
                redisTemplate.hasKey(RATE_KEY_PREFIX + USER_A)),
                "黑名单拒绝不应创建限流 key");
    }

    // ================================================================
    // 限流 (7-14)
    // ================================================================

    @Test
    @DisplayName("7. 首次请求 → PASS")
    void shouldPassOnFirstRequest() {
        assertEquals(GatewayCheckResult.CODE_PASS,
                gatewayCheckService.check(USER_A).code());
    }

    @Test
    @DisplayName("8. 同一用户 1 秒内第二次 → RATE_LIMITED")
    void shouldRejectWithinOneSecond() {
        gatewayCheckService.check(USER_A); // 首次，占位

        assertEquals(GatewayCheckResult.CODE_RATE_LIMITED,
                gatewayCheckService.check(USER_A).code());
    }

    @Test
    @DisplayName("9. 等待 1 秒后（key 过期）→ 再次 PASS")
    void shouldAllowPassAfterRateLimitExpiresNaturally() {
        gatewayCheckService.check(USER_A);
        assertEquals(GatewayCheckResult.CODE_RATE_LIMITED,
                gatewayCheckService.check(USER_A).code());

        // 等待 TTL 过期（+200ms 缓冲应对调度延迟）
        sleepQuietly(1200);

        assertEquals(GatewayCheckResult.CODE_PASS,
                gatewayCheckService.check(USER_A).code());
    }

    @Test
    @DisplayName("10. AB 同时请求 → 各自 PASS，互不影响")
    void shouldNotRateLimitDifferentUsers() {
        assertEquals(GatewayCheckResult.CODE_PASS,
                gatewayCheckService.check(USER_A).code());
        assertEquals(GatewayCheckResult.CODE_PASS,
                gatewayCheckService.check(USER_B).code());
    }

    @Test
    @DisplayName("11. 同用户 20 线程并发 → 恰好 1 PASS 19 RATE_LIMITED")
    void shouldHandleConcurrentRequestsFromSameUser() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<GatewayCheckResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(
                    () -> gatewayCheckService.check(USER_A)));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        long passCount = futures.stream()
                .map(this::uncheckedGet)
                .filter(r -> r.code() == GatewayCheckResult.CODE_PASS)
                .count();
        long limitedCount = futures.stream()
                .map(this::uncheckedGet)
                .filter(r -> r.code() == GatewayCheckResult.CODE_RATE_LIMITED)
                .count();

        assertEquals(1, passCount, "20 并发应仅 1 人通过");
        assertEquals(threadCount - 1, limitedCount, "其余 19 人被限流");
    }

    @Test
    @DisplayName("12. 精确时间窗口：T=0 通过，T=0.9s 拒绝，T=1.1s 通过")
    void shouldRespectExactTimeWindow() {
        assertEquals(GatewayCheckResult.CODE_PASS,
                gatewayCheckService.check(USER_A).code());

        sleepQuietly(900); // T=0.9s，key 未过期
        assertEquals(GatewayCheckResult.CODE_RATE_LIMITED,
                gatewayCheckService.check(USER_A).code());

        sleepQuietly(250); // T=1.15s，key 已过期
        assertEquals(GatewayCheckResult.CODE_PASS,
                gatewayCheckService.check(USER_A).code());
    }

    @Test
    @DisplayName("13. 限流拒绝不改变限流 key 的 value")
    void shouldNotMutateRateLimitKeyOnReject() {
        gatewayCheckService.check(USER_A);
        assertEquals("1", redisTemplate.opsForValue().get(RATE_KEY_PREFIX + USER_A),
                "首次成功设置 value=1");

        gatewayCheckService.check(USER_A); // 被拒
        assertEquals("1", redisTemplate.opsForValue().get(RATE_KEY_PREFIX + USER_A),
                "拒绝后 value 不变");
    }

    @Test
    @DisplayName("14. 限流拒绝后 TTL 正确（≤ 1s，误差 < 200ms）")
    void shouldHaveCorrectTtlOnRateLimitKey() {
        gatewayCheckService.check(USER_A);

        // 立即检查 TTL（此时应 ≈ 1s）
        Long ttl = redisTemplate.getExpire(RATE_KEY_PREFIX + USER_A, TimeUnit.MILLISECONDS);
        assertNotNull(ttl, "限流 key 应有 TTL");
        assertTrue(ttl >= 800 && ttl <= 1100,
                () -> "TTL 应为 800-1100ms，实际: " + ttl + "ms");

        // 再被拒绝一次
        gatewayCheckService.check(USER_A);
        Long ttlAfterReject = redisTemplate.getExpire(RATE_KEY_PREFIX + USER_A, TimeUnit.MILLISECONDS);
        assertNotNull(ttlAfterReject);
        assertTrue(ttlAfterReject >= 700 && ttlAfterReject <= 1100,
                () -> "拒绝后 TTL 不应被重设为 1s，实际: " + ttlAfterReject + "ms");
    }

    // ================================================================
    // 异常输入 (15-16)
    // ================================================================

    @Test
    @DisplayName("15. check(null) → IllegalArgumentException")
    void shouldRejectNullUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> gatewayCheckService.check(null));
    }

    @Test
    @DisplayName("16. check(-1L) → PASS（负 userId 不拒绝）")
    void shouldAcceptNegativeUserId() {
        assertEquals(GatewayCheckResult.CODE_PASS,
                gatewayCheckService.check(-1L).code());
    }

    // ================================================================
    // 防御编程 (17-19)
    // ================================================================

    @Test
    @DisplayName("17. 黑名单 key 被意外覆写为 String → 降级 PASS，不抛异常")
    void shouldDegradeGracefullyOnCorruptedBlacklistKey() {
        // 把 Set 覆写为 String（模拟误操作：SET seckill:blacklist "corrupted"）
        // 不能再用 opsForSet().add()——String 上执行 SADD 会抛 WRONGTYPE
        redisTemplate.opsForValue().set(BLACKLIST_KEY, "corrupted");

        // SISMEMBER 在非 Set 上会抛 WRONGTYPE——服务应捕获并降级
        assertDoesNotThrow(() -> {
            GatewayCheckResult r = gatewayCheckService.check(USER_A);
            assertNotNull(r);
        }, "黑名单 key 损坏不应导致服务崩溃");
    }

    @Test
    @DisplayName("18. 限流 key 被意外 SET 错误 value + 错误 TTL → 自带 TTL 到期后自愈")
    void shouldSelfHealOnCorruptedRateLimitKey() {
        String rateKey = RATE_KEY_PREFIX + USER_A;
        // 模拟误操作：SET garbage EX 1（有 TTL）
        redisTemplate.opsForValue().set(rateKey, "garbage", 1, TimeUnit.SECONDS);

        // SET NX 只关心 key 是否存在 → 应被限流
        assertEquals(GatewayCheckResult.CODE_RATE_LIMITED,
                gatewayCheckService.check(USER_A).code());

        sleepQuietly(1100);

        // TTL 到期后恢复正常
        assertEquals(GatewayCheckResult.CODE_PASS,
                gatewayCheckService.check(USER_A).code());
    }

    @Test
    @DisplayName("19. 限流 key 自然到期后多次恢复验证（与 9 互补，间隔调用不再是同 1s）")
    void shouldRecoverMultipleTimesAfterRateLimitExpires() {
        gatewayCheckService.check(USER_A);
        gatewayCheckService.check(USER_A);
        assertEquals(GatewayCheckResult.CODE_RATE_LIMITED,
                gatewayCheckService.check(USER_A).code());

        // 等待 TTL 过期后自然恢复
        sleepQuietly(1500);

        // 完全恢复后，每次调用间隔 1.1s（超过窗口），应全部通过
        for (int i = 0; i < 3; i++) {
            assertEquals(GatewayCheckResult.CODE_PASS,
                    gatewayCheckService.check(USER_A).code(),
                    "第 " + (i + 1) + " 次恢复调用应 PASS（不再在同 1s 窗口内）");
            if (i < 2) {
                sleepQuietly(1100);
            }
        }
    }

    // ================================================================
    // helper
    // ================================================================

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private <T> T uncheckedGet(Future<T> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
