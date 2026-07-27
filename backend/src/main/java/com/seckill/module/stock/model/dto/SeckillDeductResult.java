package com.seckill.module.stock.model.dto;

/**
 * 秒杀库存预扣结果值对象。
 *
 * @param code       结果码: 1 成功 / -1 重复 / -2 售罄
 * @param orderToken 排队凭证（仅成功时有值）
 */
public record SeckillDeductResult(int code, String orderToken) {

    public static final int CODE_SUCCESS = 1;
    public static final int CODE_DUPLICATE = -1;
    public static final int CODE_SOLD_OUT = -2;

    public static SeckillDeductResult success(String orderToken) {
        return new SeckillDeductResult(CODE_SUCCESS, orderToken);
    }

    public static SeckillDeductResult duplicate() {
        return new SeckillDeductResult(CODE_DUPLICATE, null);
    }

    public static SeckillDeductResult soldOut() {
        return new SeckillDeductResult(CODE_SOLD_OUT, null);
    }
}
