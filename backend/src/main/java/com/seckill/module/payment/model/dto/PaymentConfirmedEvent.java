package com.seckill.module.payment.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付已确认领域事件 —— {@link com.seckill.module.payment.service.PaymentService} 支付成功后发布。
 *
 * <p>预留用于：通知上下文（支付成功推送）、审计日志。v1 仅发布，无消费者。</p>
 *
 * @param orderNo 订单号
 * @param userId  买家 ID
 * @param amount  支付金额
 * @param payTime 支付时间
 */
public record PaymentConfirmedEvent(Long orderNo, Long userId, BigDecimal amount, LocalDateTime payTime) {
}
