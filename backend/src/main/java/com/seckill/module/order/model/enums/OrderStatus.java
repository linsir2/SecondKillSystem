package com.seckill.module.order.model.enums;

/**
 * 订单状态机。
 *
 * <p>合法转换：UNPAID → PAID / CANCELLED</p>
 */
public enum OrderStatus {
    UNPAID,
    PAID,
    CANCELLED
}
