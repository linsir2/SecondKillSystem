package com.seckill.module.gateway.service;

import com.seckill.module.gateway.model.dto.GatewayCheckResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 网关防护领域服务。
 *
 * <p>执行黑名单校验 + 单用户 1s 限流，对应 DDD 抢购上下文（Seckill）的网关层。
 *
 * <p>检查顺序（重要）:
 * <ol>
 *   <li>黑名单校验 — SISMEMBER seckill:blacklist {userId}</li>
 *   <li>1s 限流 — SET seckill:rate:{userId} 1 EX 1 NX</li>
 * </ol>
 *
 * <p>黑名单的冷加载（从 sys_user.ban_status 同步到 Redis Set）由 Identity 上下文
 * 的 {@code UserBanned} / {@code UserUnbanned} 事件驱动，不在本服务职责内。
 */
@Service
public class GatewayCheckService {

    private static final String BLACKLIST_KEY = "seckill:blacklist";
    private static final String RATE_LIMIT_PREFIX = "seckill:rate:";

    private final StringRedisTemplate redisTemplate;

    public GatewayCheckService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 网关检查: 黑名单 → 限流 → 放行。
     *
     * @param userId 用户 ID（不可 null）
     * @return 检查结果
     */
    public GatewayCheckResult check(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        // 1. 黑名单校验
        if (isBlacklisted(userId)) {
            return GatewayCheckResult.blacklisted();
        }

        // 2. 单用户 1s 限流 —— SET NX EX 原子指令
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(RATE_LIMIT_PREFIX + userId, "1", 1, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            return GatewayCheckResult.rateLimited();
        }

        return GatewayCheckResult.pass();
    }

    /**
     * 检查用户是否在黑名单中。
     * <p>异常降级：如果 Redis 返回 WRONGTYPE（key 被误覆盖等），捕获异常视为"不在黑名单"，
     * 不阻塞网关整体流程。
     */
    private boolean isBlacklisted(Long userId) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForSet().isMember(BLACKLIST_KEY, String.valueOf(userId)));
        } catch (Exception e) {
            // 降级：黑名单异常不阻塞放行
            return false;
        }
    }
}
