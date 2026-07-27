package com.seckill.module.gateway.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 黑名单启动加载器 — TDD RED 阶段。
 *
 * <p>被测对象: {@link BlacklistLoader#load()}
 * <p>将 sys_user.ban_status='banned' 的用户 ID 全量加载到 Redis seckill:blacklist。
 *
 * <p>前置条件: docker-compose MySQL + Redis（cd docker && docker compose up -d）
 *
 * <p>说明: 此测试需要 MySQL（含 Flyway 建表），不可排除 DataSourceAutoConfiguration。
 * RocketMQ 与本测试无关，排除之。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.autoconfigure.exclude="
            + "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        // 显式指定密码，避免被环境变量 ${MYSQL_PWD:root} 中的空 MYSQL_PWD 覆盖
        "spring.datasource.password=root",
        // caching_sha2_password 需要 allowPublicKeyRetrieval=true
        "spring.datasource.url=jdbc:mysql://localhost:3306/seckill_dev"
            + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
            + "&rewriteBatchedStatements=true&allowPublicKeyRetrieval=true&useSSL=false"
    }
)
@Tag("integration")
@DisplayName("黑名单启动加载器")
class BlacklistLoaderTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private BlacklistLoader blacklistLoader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String BLACKLIST_KEY = "seckill:blacklist";

    @BeforeEach
    void setUp() {
        // 清空 Redis
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        // 清空用户表（不走 Flyway，直接 SQL）
        jdbcTemplate.execute("DELETE FROM sys_user");
    }

    // ================================================================
    // 正常路径
    // ================================================================

    @Test
    @DisplayName("封禁用户写入 Redis，普通用户跳过")
    void shouldLoadBannedUsersOnly() {
        insertUser(10001L, "banned", "banned", "b1@t.com");
        insertUser(10002L, "normal", "normal", "n1@t.com");

        blacklistLoader.load();

        assertTrue(redisTemplate.opsForSet().isMember(BLACKLIST_KEY, "10001"),
                "封禁用户应在黑名单");
        assertFalse(Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(BLACKLIST_KEY, "10002")),
                "普通用户不在黑名单");
    }

    @Test
    @DisplayName("多个封禁用户全部加载")
    void shouldLoadAllBannedUsers() {
        for (long id = 1; id <= 5; id++) {
            insertUser(id, "banned", "b" + id, "b" + id + "@t.com");
        }

        blacklistLoader.load();

        assertEquals(5, redisTemplate.opsForSet().size(BLACKLIST_KEY),
                "5 个封禁用户全部加载");
        for (long id = 1; id <= 5; id++) {
            assertTrue(redisTemplate.opsForSet().isMember(BLACKLIST_KEY, String.valueOf(id)),
                    "用户 " + id + " 应在黑名单");
        }
    }

    // ================================================================
    // 边界：空数据 / 脏数据
    // ================================================================

    @Test
    @DisplayName("DB 无封禁用户 → Redis 黑名单不存在或为空")
    void shouldHandleNoBannedUsers() {
        insertUser(10001L, "normal", "normal", "n@t.com");

        blacklistLoader.load();

        // DEL 后无 SADD → key 应不存在或为空
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY)),
                "无封禁用户时不应有黑名单 key");
    }

    @Test
    @DisplayName("加载前清空旧 Redis 脏数据")
    void shouldClearRedisBeforeLoad() {
        redisTemplate.opsForSet().add(BLACKLIST_KEY, "99999", "88888");

        blacklistLoader.load();

        assertEquals(0L, redisTemplate.opsForSet().size(BLACKLIST_KEY),
                "脏数据应在加载前清除");
    }

    @Test
    @DisplayName("DB 有封禁用户 + Redis 有脏数据 → 加载后只保留 DB 中的")
    void shouldReplaceDirtyDataWithDbData() {
        redisTemplate.opsForSet().add(BLACKLIST_KEY, "99999");
        insertUser(10001L, "banned", "real", "real@t.com");

        blacklistLoader.load();

        assertEquals(1, redisTemplate.opsForSet().size(BLACKLIST_KEY),
                "只保留 DB 中的封禁用户");
        assertTrue(redisTemplate.opsForSet().isMember(BLACKLIST_KEY, "10001"));
        assertFalse(Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(BLACKLIST_KEY, "99999")));
    }

    // ================================================================
    // 幂等性
    // ================================================================

    @Test
    @DisplayName("多次调用 run() 幂等")
    void shouldBeIdempotent() {
        insertUser(10001L, "banned", "u1", "u1@t.com");
        insertUser(10002L, "banned", "u2", "u2@t.com");

        blacklistLoader.load();
        blacklistLoader.load(); // 第二次

        assertEquals(2, redisTemplate.opsForSet().size(BLACKLIST_KEY),
                "多次加载结果相同");
    }

    // ================================================================
    // 异常情景：DB 连接异常（可选，暂不实现）
    // ================================================================

    // ================================================================
    // helper
    // ================================================================

    /** 快速插入测试用户（只填必要字段）。 */
    private void insertUser(long id, String banStatus, String name, String email) {
        jdbcTemplate.update("""
            INSERT INTO sys_user (user_id, user_name, email, password, role, ban_status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            """, id, name, email, "dummyPwd", "user", banStatus);
    }
}
