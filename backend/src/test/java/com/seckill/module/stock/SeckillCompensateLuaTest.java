package com.seckill.module.stock;

import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 秒杀库存补偿 Lua 脚本 — TDD 测试。
 *
 * <pre>
 * 被测对象: classpath:lua/seckill_compensate.lua
 *
 * 设计思路：
 *   ┌───────────────────────────────────────────────────────────────────────┐
 *   │ CL1 标准补偿     A 买了 2 → stock+2, SREM, ZREM                     │
 *   │ CL2 补偿不存在用户 → SREM 幂等, INCRBY 照常                         │
 *   │ CL3 补偿不存在 token → ZREM 幂等                                     │
 *   │ CL4 buyCount=0 → INCRBY 0, 纯幂等                                    │
 *   └───────────────────────────────────────────────────────────────────────┘
 * </pre>
 */
@Tag("integration")
@DisplayName("SeckillCompensateLua — 库存补偿")
class SeckillCompensateLuaTest {

    static Jedis jedis;
    static String luaScript;

    static final String STOCK_KEY = "seckill:stock:100:200";
    static final String USERS_KEY = "seckill:users:100:200";
    static final String PENDING_KEY = "seckill:pending:100";
    static final long USER_A = 10001L;
    static final String TOKEN_1 = "token-111-aaaa";
    static final String TOKEN_2 = "token-222-bbbb";

    @BeforeAll
    static void beforeAll() throws IOException {
        jedis = new Jedis("localhost", 6379);
        assertEquals("PONG", jedis.ping(),
                "Redis 未启动，请先: cd docker && docker compose up -d");
        luaScript = Files.readString(Path.of("src/main/resources/lua/seckill_compensate.lua"));
    }

    @AfterAll
    static void afterAll() {
        if (jedis != null) jedis.close();
    }

    @BeforeEach
    void setUp() {
        jedis.flushAll();
    }

    @Test
    @DisplayName("CL1 标准补偿: A 买 2 后补偿 → INCRBY 2 + SREM A + ZREM token")
    void shouldCompensateSuccessfully() {
        // 模拟扣减后的 Redis 状态
        jedis.set(STOCK_KEY, "8");
        jedis.sadd(USERS_KEY, String.valueOf(USER_A));
        jedis.zadd(PENDING_KEY, 1000, TOKEN_1);

        eval(USER_A, 2, TOKEN_1);

        assertEquals("10", jedis.get(STOCK_KEY));          // INCRBY 2
        assertFalse(jedis.sismember(USERS_KEY, String.valueOf(USER_A))); // SREM
        assertNull(jedis.zscore(PENDING_KEY, TOKEN_1));    // ZREM
    }

    @Test
    @DisplayName("CL2 补偿不存在的用户 → SREM 幂等, INCRBY 照常")
    void shouldBeIdempotentForNonExistentUser() {
        jedis.set(STOCK_KEY, "8");
        jedis.zadd(PENDING_KEY, 1000, TOKEN_1);
        // 不 SADD USER_A——模拟用户不在集合的情况

        eval(USER_A, 2, TOKEN_1);

        assertEquals("10", jedis.get(STOCK_KEY));          // 仍恢复
        assertFalse(jedis.sismember(USERS_KEY, String.valueOf(USER_A)));
    }

    @Test
    @DisplayName("CL3 补偿不存在的 token → ZREM 返回 0 → 跳过全部（幂等安全）")
    void shouldHandleNonExistentToken() {
        jedis.set(STOCK_KEY, "8");
        jedis.sadd(USERS_KEY, String.valueOf(USER_A));
        jedis.zadd(PENDING_KEY, 1000, TOKEN_2); // 另一个 token 存在

        eval(USER_A, 2, "nonexistent-token");

        // ZREM 返回 0 → 跳过 INCRBY 和 SREM（幂等安全，防止双倍补偿）
        assertEquals("8", jedis.get(STOCK_KEY));                 // INCRBY 未执行
        assertTrue(jedis.sismember(USERS_KEY, String.valueOf(USER_A))); // SREM 未执行
        assertNotNull(jedis.zscore(PENDING_KEY, TOKEN_2));      // TOKEN_2 不受影响
    }

    @Test
    @DisplayName("CL4 buyCount=0 → INCRBY 0, 纯幂等不影响数据")
    void shouldHandleZeroBuyCount() {
        jedis.set(STOCK_KEY, "8");

        eval(USER_A, 0, TOKEN_1);

        assertEquals("8", jedis.get(STOCK_KEY)); // stock 不变
    }

    // ================================================================
    // helper
    // ================================================================

    private void eval(long userId, int buyCount, String orderToken) {
        jedis.eval(luaScript,
                List.of(STOCK_KEY, USERS_KEY, PENDING_KEY),
                List.of(String.valueOf(userId), String.valueOf(buyCount), orderToken));
    }
}
