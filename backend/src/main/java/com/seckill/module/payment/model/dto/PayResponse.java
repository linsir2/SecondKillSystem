package com.seckill.module.payment.model.dto;

/**
 * 支付结果。
 *
 * @param success 是否成功
 * @param message 提示信息
 */
public record PayResponse(boolean success, String message) {
    public static PayResponse ok() {
        return new PayResponse(true, "支付成功");
    }

    public static PayResponse fail(String message) {
        return new PayResponse(false, message);
    }
}
