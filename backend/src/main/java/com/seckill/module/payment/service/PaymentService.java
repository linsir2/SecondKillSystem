package com.seckill.module.payment.service;

import com.seckill.module.payment.model.dto.PayRequest;
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
     * @param request 支付请求（含 orderNo, userId，不可 null）
     * @return {@link PayResponse#ok()} 成功 / {@link PayResponse#fail(String)} 业务失败
     * @throws IllegalArgumentException request / orderNo / userId 为 null
     */
    PayResponse pay(PayRequest request);
}
