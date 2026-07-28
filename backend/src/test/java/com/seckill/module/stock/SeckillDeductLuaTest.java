package com.seckill.module.stock;

import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 秒杀库存预扣 Lua 脚本 — TDD 测试（RED => GREEN）。
 *
 * <pre>
 * 被测对象: classpath:lua/seckill_deduct.lua
 *
 * 设计思路（10 年测试老师傅视角）：
 *   ┌──────────────────────────────────────────────────────────┐
 *   │ Category 1: 正常路径   — 确保基本功能可用                │
 *   │    T1  标准扣减          stock=10 limit=5 buy=2 → 1     │
 *   │    T2  满限购            buy=limit=5          → 1       │
 *   │    T3  清库存            buy=stock=2          → 1       │
 *   │                                                          │
 *   │ Category 2: 重复拦截   — 每人一单，不改集合就不变语义    │
 *   │    T4  同一用户再来 → -1    不二次扣减                   │
 *   │    T5  A 重复不影响 B     B 正常放行                     │
 *   │    T6  补偿后可重买        SREM + INCRBY 后重新放行       │
 *   │                                                          │
 *   │ Category 3: 单次超限购 — 新逻辑，single-shot 检查        │
 *   │    T7  buy(3) > limit(2)          → -3                   │
 *   │    T8  limit key 不存在           → 跳过限购检查         │
 *   │                                                          │
 *   │ Category 4: 售罄        — stock < buyCount 即不通过      │
 *   │    T9  stock=0                   → -2                    │
 *   │    T10 stock=2 buy=3             → -2                    │
 *   │    T11 stock key 不存在          → -2                    │
 *   │                                                          │
 *   │ Category 5: 多用户      — 共享 stock，相互独立           │
 *   │    T12 stock=2, A=1 B=1 → C=-2                          │
 *   │                                                          │
 *   │ Category 6: 异常值      — 防御性编码验证                  │
 *   │    T13 负库存                   → -2                     │
 *   │    T14 stock="corrupted"        → -2                     │
 *   └──────────────────────────────────────────────────────────┘
 *
 * 前置条件: docker-compose Redis（cd docker && docker compose up -d）
 * 直连 localhost:6379，纯 Jedis + Lua，无 Spring 上下文。
 * &#64;BeforeEach FLUSHALL 保证用例隔离。
 * </pre>
 */
@Tag("integration")
@DisplayName("SeckillDeductLua — buyCount 支持")
class SeckillDeductLuaTest {

    static Jedis jedis;
    static String luaScript;

    static final String STOCK_KEY = "seckill:stock:100:200";
    static final String USERS_KEY = "seckill:users:100:200";
    static final String LIMIT_KEY = "seckill:limit:100:200";
    static final long USER_A = 10001L;
    static final long USER_B = 10002L;
    static final long USER_C = 10003L;

    @BeforeAll
    static void beforeAll() throws IOException {
        jedis = new Jedis("localhost", 6379);
        assertEquals("PONG", jedis.ping(),
                "Redis 未启动，请先: cd docker && docker compose up -d");
        luaScript = Files.readString(Path.of("src/main/resources/lua/seckill_deduct.lua"));
    }

    @AfterAll
    static void afterAll() {
        if (jedis != null) jedis.close();
    }

    @BeforeEach
    void setUp() {
        jedis.flushAll();
    }

    // ================================================================
    // Category 1: 正常路径
    // ================================================================

    @Test
    @DisplayName("T1 标准扣减: stock=10 limit=5 buy=2 → 1, stock→8, SISMEMBER true")
    void shouldDeductWithBuyCount() {
        jedis.set(STOCK_KEY, "10");
        jedis.set(LIMIT_KEY, "5");

        assertEquals(1L, eval(USER_A, 2));
        assertEquals("8", jedis.get(STOCK_KEY));
        assertTrue(jedis.sismember(USERS_KEY, String.valueOf(USER_A)));
    }

    @Test
    @DisplayName("T2 满限购: buy=limit=5 → 1（边界：刚好等于限购数）")
    void shouldSucceedWhenBuyCountEqualsLimit() {
        jedis.set(STOCK_KEY, "10");
        jedis.set(LIMIT_KEY, "5");

        assertEquals(1L, eval(USER_A, 5));
        assertEquals("5", jedis.get(STOCK_KEY));
    }

    @Test
    @DisplayName("T3 清库存: buy=stock=2 → 1（边界：刚好买完）")
    void shouldSucceedWhenBuyCountEqualsStock() {
        jedis.set(STOCK_KEY, "2");
        jedis.set(LIMIT_KEY, "5");

        assertEquals(1L, eval(USER_A, 2));
        assertEquals("0", jedis.get(STOCK_KEY));
    }

    // ================================================================
    // Category 2: 重复拦截
    // ================================================================

    @Test
    @DisplayName("T4 重复: A 买 2 → A 再买 1 → 第二次 -1，stock 不二次扣减")
    void shouldRejectDuplicateBuyer() {
        jedis.set(STOCK_KEY, "10");
        jedis.set(LIMIT_KEY, "5");

        // A 首次
        assertEquals(1L, eval(USER_A, 2));
        assertEquals("8", jedis.get(STOCK_KEY));

        // A 再次请求 → -1
        assertEquals(-1L, eval(USER_A, 1));
        assertEquals("8", jedis.get(STOCK_KEY)); // 未二次扣减
    }

    @Test
    @DisplayName("T5 A 重复不影响 B: A=-1 B=1, stock=7")
    void shouldNotAffectOtherUsersOnDuplicate() {
        jedis.set(STOCK_KEY, "10");
        jedis.set(LIMIT_KEY, "5");

        eval(USER_A, 2); // A 买走 2 件

        assertEquals(-1L, eval(USER_A, 1)); // A 重复 → -1
        assertEquals(1L, eval(USER_B, 1));  // B 正常 → 1
        assertEquals("7", jedis.get(STOCK_KEY)); // 10-2-1=7
        assertTrue(jedis.sismember(USERS_KEY, String.valueOf(USER_B)));
    }

    @Test
    @DisplayName("T6 补偿后重买: SREM + INCRBY 后重新放行")
    void shouldAllowRePurchaseAfterCompensation() {
        jedis.set(STOCK_KEY, "10");
        jedis.set(LIMIT_KEY, "5");

        // A 买 2
        assertEquals(1L, eval(USER_A, 2));
        assertEquals("8", jedis.get(STOCK_KEY));

        // 模拟补偿
        jedis.srem(USERS_KEY, String.valueOf(USER_A));
        jedis.incrBy(STOCK_KEY, 2);

        // A 重买 2
        assertEquals(1L, eval(USER_A, 2));
        assertEquals("8", jedis.get(STOCK_KEY));
        assertTrue(jedis.sismember(USERS_KEY, String.valueOf(USER_A)));
    }

    // ================================================================
    // Category 3: 单次超限购
    // ================================================================

    @Test
    @DisplayName("T7 超限购: limit=2 buy=3 → -3，无副作用")
    void shouldRejectWhenBuyCountExceedsLimit() {
        jedis.set(STOCK_KEY, "10");
        jedis.set(LIMIT_KEY, "2");

        assertEquals(-3L, eval(USER_A, 3));
        // decrby 未执行，stock 不变
        assertEquals("10", jedis.get(STOCK_KEY));
        assertFalse(jedis.sismember(USERS_KEY, String.valueOf(USER_A)));
    }

    @Test
    @DisplayName("T8 limit key 不存在 → 跳过限购检查，正常放行")
    void shouldSkipLimitCheckWhenLimitKeyNotExists() {
        jedis.set(STOCK_KEY, "5");
        // 不设置 LIMIT_KEY

        assertEquals(1L, eval(USER_A, 5));
        assertEquals("0", jedis.get(STOCK_KEY));
    }

    // ================================================================
    // Category 4: 售罄
    // ================================================================

    @Test
    @DisplayName("T9 stock=0 → -2")
    void shouldRejectWhenStockZero() {
        jedis.set(STOCK_KEY, "0");
        jedis.set(LIMIT_KEY, "5");

        assertEquals(-2L, eval(USER_A, 1));
        assertEquals("0", jedis.get(STOCK_KEY));
        assertFalse(jedis.sismember(USERS_KEY, String.valueOf(USER_A)));
    }

    @Test
    @DisplayName("T10 stock=2 buy=3 → -2（stock < buyCount）")
    void shouldRejectWhenBuyCountExceedsStock() {
        jedis.set(STOCK_KEY, "2");
        jedis.set(LIMIT_KEY, "5");

        assertEquals(-2L, eval(USER_A, 3));
        assertEquals("2", jedis.get(STOCK_KEY)); // 未扣减
    }

    @Test
    @DisplayName("T11 stock key 不存在（nil）→ 视为 0，返回 -2")
    void shouldRejectWhenStockKeyNotExists() {
        jedis.set(LIMIT_KEY, "5");

        assertEquals(-2L, eval(USER_A, 1));
        assertNull(jedis.get(STOCK_KEY));
    }

    // ================================================================
    // Category 5: 多用户
    // ================================================================

    @Test
    @DisplayName("T12 stock=2, A=1 B=1 → C=-2")
    void shouldAllowMultipleBuyersUntilSoldOut() {
        jedis.set(STOCK_KEY, "2");
        jedis.set(LIMIT_KEY, "5");

        assertEquals(1L, eval(USER_A, 1));
        assertEquals(1L, eval(USER_B, 1));
        assertEquals(-2L, eval(USER_C, 1));

        assertEquals("0", jedis.get(STOCK_KEY));
        assertTrue(jedis.sismember(USERS_KEY, String.valueOf(USER_A)));
        assertTrue(jedis.sismember(USERS_KEY, String.valueOf(USER_B)));
        assertFalse(jedis.sismember(USERS_KEY, String.valueOf(USER_C)));
    }

    // ================================================================
    // Category 6: 异常值
    // ================================================================

    @Test
    @DisplayName("T13 负库存 → -2")
    void shouldRejectWhenStockIsNegative() {
        jedis.set(STOCK_KEY, "-1");
        jedis.set(LIMIT_KEY, "5");

        assertEquals(-2L, eval(USER_A, 1));
    }

    @Test
    @DisplayName("T14 非法库存值（非数字）→ tonumber nil → -2")
    void shouldTreatNonNumericStockAsZero() {
        jedis.set(STOCK_KEY, "corrupted");
        jedis.set(LIMIT_KEY, "5");

        assertEquals(-2L, eval(USER_A, 1));
    }

    // ================================================================
    // helper
    // ================================================================

    private long eval(long userId, int buyCount) {
        return (long) jedis.eval(luaScript,
                List.of(STOCK_KEY, USERS_KEY, LIMIT_KEY),
                List.of(String.valueOf(userId), String.valueOf(buyCount)));
    }
}
