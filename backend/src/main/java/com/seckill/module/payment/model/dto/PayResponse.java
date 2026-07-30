package com.seckill.module.payment.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 支付结果。
 *
 * @param success 是否成功
 * @param message 提示信息
 */
@Schema(description = "支付结果")
public record PayResponse(
        @Schema(description = "是否成功") boolean success,
        @Schema(description = "提示信息") String message
) {
    public static PayResponse ok() {
        return new PayResponse(true, "支付成功");
    }

    public static PayResponse fail(String message) {
        return new PayResponse(false, message);
    }
}
