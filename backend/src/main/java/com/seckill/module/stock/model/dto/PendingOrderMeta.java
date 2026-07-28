package com.seckill.module.stock.model.dto;

/**
 * 悬空死账补偿元数据 —— 从 {@code seckill:pending:meta:{orderToken}} 反序列化。
 *
 * <p>格式 {@code "userId:seckillGoodsId:buyCount"}，支持额外字段（忽略）。
 *
 * @param userId         买家 ID
 * @param seckillGoodsId 秒杀商品 ID
 * @param buyCount       购买数量
 */
public record PendingOrderMeta(long userId, long seckillGoodsId, int buyCount) {

    private static final String SEP = ":";

    /**
     * 从 Redis meta 字符串解析。
     *
     * @param raw meta 值，格式 "uid:sgId:cnt"
     * @return 解析结果
     * @throws IllegalArgumentException raw 为 null/空/格式异常
     */
    public static PendingOrderMeta parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("meta must not be null or blank");
        }
        String[] parts = raw.split(SEP);
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "meta format must be 'uid:sgId:cnt', got: " + raw);
        }
        try {
            return new PendingOrderMeta(
                    Long.parseLong(parts[0]),
                    Long.parseLong(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("meta contains non-numeric value: " + raw, e);
        }
    }
}
