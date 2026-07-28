package com.seckill.module.gateway.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 黑名单加载器。
 *
 * <p>将 {@code sys_user} 中 {@code ban_status = 'banned'} 的用户 ID
 * 全量加载到 Redis {@code seckill:blacklist} Set。
 *
 * <p>不再通过 {@code ApplicationRunner} 启动时加载，改为活动预热定时任务触发。
 * 增量封禁/解封仍通过 {@code UserServiceImpl} 实时 SADD/SREM 同步。</p>
 */
@Component
public class BlacklistLoader {

    private static final String BLACKLIST_KEY = "seckill:blacklist";

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public BlacklistLoader(StringRedisTemplate redisTemplate, JdbcTemplate jdbcTemplate) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行黑名单全量加载（可被测试直接调用，无需构造 ApplicationArguments）。
     */
    public void load() {
        List<Long> bannedIds = jdbcTemplate.queryForList(
                "SELECT user_id FROM sys_user WHERE ban_status = 'banned'", Long.class);

        // DEL + SADD 全量 reload
        redisTemplate.delete(BLACKLIST_KEY);
        if (!bannedIds.isEmpty()) {
            bannedIds.forEach(id ->
                    redisTemplate.opsForSet().add(BLACKLIST_KEY, String.valueOf(id)));
        }
    }
}
