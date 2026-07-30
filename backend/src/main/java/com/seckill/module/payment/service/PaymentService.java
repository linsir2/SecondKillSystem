package com.seckill.module.payment.service;

import com.seckill.module.payment.model.dto.PayResponse;

/**
 * 支付上下文领域服务接口。
 *
 * <p>接收支付请求，委托 {@link com.seckill.module.order.service.OrderService#pay(Long, Long)} 执行订单支付，
 * 写入支付流水并发布 {@link com.seckill.module.payment.model.dto.PaymentConfirmedEvent}。</p>
 */
public interface PaymentService {

    /**
     * 处理支付请求。
     *
     * @param orderNo 订单号
     * @param userId  买家用户 ID（从 JWT 获取）
     * @return {@link PayResponse#ok()} 成功 / {@link PayResponse#fail(String)} 业务失败
     * @throws IllegalArgumentException orderNo / userId 为 null
     */
    PayResponse pay(Long orderNo, Long userId);
}
