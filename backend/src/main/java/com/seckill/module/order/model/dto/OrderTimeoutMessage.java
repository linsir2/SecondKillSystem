package com.seckill.module.order.model.dto;

/**
 * 延时关单 MQ 消息体 —— 由 {@code MessageLogScanner} 从 message_log.body 解析后投递。
 *
 * @param orderToken 排队凭证 / 订单幂等键
 * @param orderNo    订单号
 */
public record OrderTimeoutMessage(
        String orderToken,
        Long orderNo
) {}
