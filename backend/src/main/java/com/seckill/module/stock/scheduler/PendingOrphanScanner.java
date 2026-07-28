package com.seckill.module.stock.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seckill.module.order.mapper.SeckillOrderMapper;
import com.seckill.module.order.model.entity.SeckillOrder;
import com.seckill.module.stock.model.dto.PendingOrderMeta;
import com.seckill.module.stock.service.SeckillStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SchedulerX 悬空死账兜底扫描 —— 终极防线。
 *
 * <p>每 3 分钟扫描 {@code seckill:pending:*}</span> ZSET，对每个活动中
 * 3~10 分钟窗口内的排队凭证，批量对比 MySQL {@code seckill_order} 表：
 * <ul>
 *   <li>Redis 有 &amp; MySQL 有 → 正常订单，跳过</li>
 *   <li>Redis 有 &amp; MySQL 无 → 悬空死账，执行 Lua 原子补偿</li>
 * </ul>
 *
 * <p>补偿所需字段从 {@code seckill:pending:meta:{orderToken}} 读取，
 * 格式 {@code "userId:seckillGoodsId:buyCount"}。</p>
 */
@Component
public class PendingOrphanScanner {

    private static final Logger log = LoggerFactory.getLogger(PendingOrphanScanner.class);

    /** 扫描窗口上界：仅处理 10 分钟以内的排队凭证 */
    private static final long WINDOW_MAX_SEC = 600;

    /** 扫描窗口下界：跳过最近 3 分钟内的凭证（给 MQ 消费留足时间） */
    private static final long WINDOW_MIN_SEC = 180;

    /** 每次 MySQL IN 查询的 token 数量上限 */
    private static final int MYSQL_BATCH_SIZE = 50;

    /** Redis pending ZSET key 前缀 */
    private static final String PENDING_KEY_PREFIX = "seckill:pending:";

    /** Redis meta key 前缀 */
    private static final String META_KEY_PREFIX = "seckill:pending:meta:";

    private final StringRedisTemplate redisTemplate;
    private final SeckillOrderMapper orderMapper;
    private final SeckillStockService seckillStockService;

    public PendingOrphanScanner(StringRedisTemplate redisTemplate,
                                SeckillOrderMapper orderMapper,
                                SeckillStockService seckillStockService) {
        this.redisTemplate = redisTemplate;
        this.orderMapper = orderMapper;
        this.seckillStockService = seckillStockService;
    }

    /**
     * 定时任务入口 —— 每 3 分钟执行一次。
     */
    @Scheduled(fixedRate = 180_000)
    public void scanAllOrphans() {
        doScan(System.currentTimeMillis());
    }

    // ====================================================================
    // Package-private — 测试入口
    // ====================================================================

    /**
     * 扫描所有活动，处理悬空死账。
     *
     * @param nowMillis 当前系统时间戳（毫秒），测试时可注入固定值
     */
    void doScan(long nowMillis) {
        Set<String> pendingKeys;
        try {
            pendingKeys = redisTemplate.keys(PENDING_KEY_PREFIX + "*");
        } catch (Exception e) {
            log.error("PendingOrphanScanner: failed to scan KEYS {}", PENDING_KEY_PREFIX + "*", e);
            return;
        }

        if (pendingKeys == null || pendingKeys.isEmpty()) {
            return;
        }

        double minScore = nowMillis - WINDOW_MAX_SEC * 1000L;
        double maxScore = nowMillis - WINDOW_MIN_SEC * 1000L;

        for (String key : pendingKeys) {
            try {
                scanActivity(key, minScore, maxScore);
            } catch (Exception e) {
                log.error("PendingOrphanScanner: failed to scan activity key={}", key, e);
            }
        }
    }

    // ====================================================================
    // Activity 级扫描
    // ====================================================================

    /**
     * 扫描单个活动的 pending ZSET 窗口，补偿悬空死账。
     */
    void scanActivity(String pendingKey, double minScore, double maxScore) {
        Long activityId = parseActivityId(pendingKey);
        if (activityId == null) {
            return;
        }

        Set<String> tokens = redisTemplate.opsForZSet()
                .rangeByScore(pendingKey, minScore, maxScore);
        if (tokens == null || tokens.isEmpty()) {
            return;
        }

        // ---- 批次查询 MySQL 中已存在的 orderToken ----
        List<String> tokenList = new ArrayList<>(tokens);
        Set<String> existingTokens = new HashSet<>();

        for (int i = 0; i < tokenList.size(); i += MYSQL_BATCH_SIZE) {
            int end = Math.min(i + MYSQL_BATCH_SIZE, tokenList.size());
            List<String> batch = tokenList.subList(i, end);
            try {
                List<SeckillOrder> found = orderMapper.selectList(
                        new QueryWrapper<SeckillOrder>()
                                .select("order_token")
                                .in("order_token", batch));
                if (found != null) {
                    found.stream()
                            .map(SeckillOrder::getOrderToken)
                            .forEach(existingTokens::add);
                }
            } catch (Exception e) {
                log.error("PendingOrphanScanner: MySQL batch query failed for activityId={}, " +
                        "batch from {}-{}, skipping {} tokens this cycle",
                        activityId, i, end, batch.size(), e);
                // 无法确定这批 token 的 orphan 状态 → 标记为 "existing" 跳过补偿，
                // 下轮重试（保持窗口内即可）
                existingTokens.addAll(batch);
            }
        }

        // ---- 差集 = orpahns ----
        for (String token : tokenList) {
            if (existingTokens.contains(token)) {
                continue;
            }
            compensateOrphan(activityId, token);
        }
    }

    // ====================================================================
    // 单个 Orphan 补偿
    // ====================================================================

    /**
     * 尝试补偿单个悬空死账。
     *
     * <p>读 meta → 解析 → 调 {@code compensateByTimeout} → DEL meta。
     * meta 不存在 → ZREM 清理 pending 记录（避免死循环扫描）。</p>
     */
    void compensateOrphan(Long activityId, String orderToken) {
        String metaKey = META_KEY_PREFIX + orderToken;
        String meta = null;
        try {
            meta = redisTemplate.opsForValue().get(metaKey);
        } catch (Exception e) {
            log.warn("PendingOrphanScanner: failed to GET meta for token={}", orderToken, e);
            return;
        }

        if (meta == null) {
            log.warn("PendingOrphanScanner: meta missing for token={}, removing pending entry", orderToken);
            redisTemplate.opsForZSet().remove(PENDING_KEY_PREFIX + activityId, orderToken);
            return;
        }

        PendingOrderMeta parsed;
        try {
            parsed = PendingOrderMeta.parse(meta);
        } catch (IllegalArgumentException e) {
            log.error("PendingOrphanScanner: corrupted meta for token={}, meta={}, removing pending", orderToken, meta);
            redisTemplate.opsForZSet().remove(PENDING_KEY_PREFIX + activityId, orderToken);
            return;
        }

        try {
            seckillStockService.compensateByTimeout(
                    activityId,
                    parsed.seckillGoodsId(),
                    parsed.userId(),
                    parsed.buyCount(),
                    orderToken);
            // 清理 meta（补偿完成后，双重 DEL 幂等无害）
            redisTemplate.delete(META_KEY_PREFIX + orderToken);
        } catch (Exception e) {
            log.error("PendingOrphanScanner: failed to compensate orphan token={}", orderToken, e);
            // 不清理 meta → 下轮重试
        }
    }

    // ====================================================================
    // 辅助方法
    // ====================================================================

    /**
     * 从 pending key 中提取 activityId。
     * {@code "seckill:pending:42"} → {@code 42L}
     */
    Long parseActivityId(String key) {
        if (key == null || !key.startsWith(PENDING_KEY_PREFIX)) {
            log.warn("PendingOrphanScanner: unexpected key format: {}", key);
            return null;
        }
        String suffix = key.substring(PENDING_KEY_PREFIX.length());
        try {
            return Long.parseLong(suffix);
        } catch (NumberFormatException e) {
            log.warn("PendingOrphanScanner: cannot parse activityId from key={}", key, e);
            return null;
        }
    }
}
