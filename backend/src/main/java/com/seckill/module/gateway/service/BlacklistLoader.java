package com.seckill.module.gateway.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 黑名单冷加载器。
 *
 * <p>应用启动时执行，将 {@code sys_user} 中 {@code ban_status = 'banned'} 的用户 ID
 * 全量加载到 Redis {@code seckill:blacklist} Set。
 *
 * <p>策略：每次先 DEL 清空旧数据再 SADD 全量——启动时脏数据概率极低，全量 reload 成本可控。
 * 定时对账（SchedulerX）见{@code注意事项.md}，采用差量 SADD/SREM，不做 DEL。
 */
@Component
public class BlacklistLoader implements ApplicationRunner {

    private static final String BLACKLIST_KEY = "seckill:blacklist";

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public BlacklistLoader(StringRedisTemplate redisTemplate, JdbcTemplate jdbcTemplate) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        load();
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
