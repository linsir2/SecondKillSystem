package com.seckill.module.order.model.vo;

/**
 * 订单状态查询结果 —— 前端轮询 {@code GET /api/v1/order/status} 的返回值。
 *
 * @param status  订单状态 {@code "UNPAID"} / {@code "PAID"} / {@code "CANCELLED"}，未查到为 {@code null}
 * @param orderNo 订单号（雪花算法），未查到为 {@code null}
 */
public record OrderStatusVO(String status, Long orderNo) {
}
