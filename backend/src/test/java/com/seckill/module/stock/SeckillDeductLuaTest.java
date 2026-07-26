package com.seckill.module.stock;

import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 秒杀库存预扣 Lua 脚本 — TDD 测试（RED 阶段）。
 *
 * <pre>
 * 被测对象: classpath:lua/seckill_deduct.lua
 *
 * 前置条件: docker-compose Redis（cd docker && docker compose up -d）
 *
 * 直连 localhost:6379，纯 Jedis + Lua，无 Spring 上下文。
 * &#64;BeforeEach FLUSHALL 保证用例隔离。
 *
 * Testcontainers 暂不可用——docker-java 与 Docker Engine 29.4.0 不兼容（BadRequest 400）。
 * Docker Engine 升级或 docker-java 修复后切回 Testcontainers 即可，测试逻辑不变。
 * </pre>
 */
@Tag("integration")
@DisplayName("秒杀库存预扣 Lua 脚本")
class SeckillDeductLuaTest {

    static Jedis jedis;
    static String luaScript;

    static final String STOCK_KEY = "seckill:stock:100:200";
    static final String USERS_KEY = "seckill:users:100:200";
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
    // 正常路径
    // ================================================================

    @Test
    @DisplayName("库存充足 → 返回 1，库存 -1，用户入集合")
    void shouldDeductSuccessfullyForNewUser() {
        jedis.set(STOCK_KEY, "10");

        assertEquals(1L, eval(STOCK_KEY, USERS_KEY, USER_A));
        assertEquals("9", jedis.get(STOCK_KEY));
        assertTrue(jedis.sismember(USERS_KEY, String.valueOf(USER_A)));
    }

    @Test
    @DisplayName("库存恰好为 1 → 仍成功，库存变 0")
    void shouldSucceedWhenStockIsExactlyOne() {
        jedis.set(STOCK_KEY, "1");

        assertEquals(1L, eval(STOCK_KEY, USERS_KEY, USER_A));
        assertEquals("0", jedis.get(STOCK_KEY));
    }

    // ================================================================
    // 重复购买
    // ================================================================

    @Test
    @DisplayName("同一用户再次抢购 → 返回 -1，库存不变")
    void shouldRejectDuplicateBuyer() {
        jedis.set(STOCK_KEY, "10");
        eval(STOCK_KEY, USERS_KEY, USER_A);

        assertEquals(-1L, eval(STOCK_KEY, USERS_KEY, USER_A));
        assertEquals("9", jedis.get(STOCK_KEY)); // 未二次扣减
    }

    @Test
    @DisplayName("A 重复不影响 B 正常抢购")
    void shouldNotAffectOtherUsersOnDuplicate() {
        jedis.set(STOCK_KEY, "10");
        eval(STOCK_KEY, USERS_KEY, USER_A);

        assertEquals(1L, eval(STOCK_KEY, USERS_KEY, USER_B));
        assertEquals("8", jedis.get(STOCK_KEY));
    }

    // ================================================================
    // 售罄
    // ================================================================

    @Test
    @DisplayName("库存为 0 → 返回 -2，无副作用")
    void shouldRejectWhenStockZero() {
        jedis.set(STOCK_KEY, "0");

        assertEquals(-2L, eval(STOCK_KEY, USERS_KEY, USER_A));
        assertEquals("0", jedis.get(STOCK_KEY));
        assertFalse(jedis.sismember(USERS_KEY, String.valueOf(USER_A)));
    }

    @Test
    @DisplayName("库存 key 不存在（nil）→ 视为 0，返回 -2")
    void shouldRejectWhenStockKeyNotExists() {
        assertEquals(-2L, eval(STOCK_KEY, USERS_KEY, USER_A));
        assertNull(jedis.get(STOCK_KEY));
    }

    @Test
    @DisplayName("负库存 → 返回 -2")
    void shouldRejectWhenStockIsNegative() {
        jedis.set(STOCK_KEY, "-1");

        assertEquals(-2L, eval(STOCK_KEY, USERS_KEY, USER_A));
    }

    // ================================================================
    // 多用户
    // ================================================================

    @Test
    @DisplayName("stock=2，AB 成功 C 售罄")
    void shouldAllowMultipleBuyersUntilSoldOut() {
        jedis.set(STOCK_KEY, "2");

        assertEquals(1L, eval(STOCK_KEY, USERS_KEY, USER_A));
        assertEquals(1L, eval(STOCK_KEY, USERS_KEY, USER_B));
        assertEquals(-2L, eval(STOCK_KEY, USERS_KEY, USER_C));

        assertEquals("0", jedis.get(STOCK_KEY));
        assertTrue(jedis.sismember(USERS_KEY, String.valueOf(USER_A)));
        assertTrue(jedis.sismember(USERS_KEY, String.valueOf(USER_B)));
        assertFalse(jedis.sismember(USERS_KEY, String.valueOf(USER_C)));
    }

    @Test
    @DisplayName("补偿（SREM+INCRBY）后用户可再次抢购")
    void shouldAllowRePurchaseAfterCompensation() {
        jedis.set(STOCK_KEY, "10");
        eval(STOCK_KEY, USERS_KEY, USER_A);

        // 模拟补偿
        jedis.srem(USERS_KEY, String.valueOf(USER_A));
        jedis.incrBy(STOCK_KEY, 1);

        assertEquals(1L, eval(STOCK_KEY, USERS_KEY, USER_A));
        assertEquals("9", jedis.get(STOCK_KEY));
        assertTrue(jedis.sismember(USERS_KEY, String.valueOf(USER_A)));
    }

    // ================================================================
    // 边界
    // ================================================================

    @Test
    @DisplayName("非法库存值（非数字）→ tonumber nil → return -2")
    void shouldTreatNonNumericStockAsZero() {
        jedis.set(STOCK_KEY, "corrupted");

        assertEquals(-2L, eval(STOCK_KEY, USERS_KEY, USER_A));
    }

    // ================================================================
    // helper
    // ================================================================

    private Long eval(String stockKey, String usersKey, long userId) {
        List<String> keys = List.of(stockKey, usersKey);
        List<String> args = List.of(String.valueOf(userId));
        return (Long) jedis.eval(luaScript, keys, args);
    }
}
