package com.seckill.module.order.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 订单状态查询结果 —— 前端轮询 {@code GET /api/v1/order/status} 的返回值。
 *
 * @param status  订单状态 {@code "UNPAID"} / {@code "PAID"} / {@code "CANCELLED"}，未查到为 {@code null}
 * @param orderNo 订单号（雪花算法），未查到为 {@code null}
 */
@Schema(description = "订单状态查询结果")
public record OrderStatusVO(
        @Schema(description = "订单状态（UNPAID/PAID/CANCELLED，未查到为 null）") String status,
        @Schema(description = "订单号（未查到为 null）") Long orderNo
) {}
