package com.seckill.module.payment.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.module.order.mapper.SeckillOrderMapper;
import com.seckill.module.order.model.entity.SeckillOrder;
import com.seckill.module.order.service.OrderService;
import com.seckill.module.payment.mapper.PaymentMapper;
import com.seckill.module.payment.model.dto.PayResponse;
import com.seckill.module.payment.model.dto.PaymentConfirmedEvent;
import com.seckill.module.payment.model.entity.Payment;
import com.seckill.module.payment.model.enums.PaymentStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 支付领域服务实现。
 *
 * <p>接收支付请求 → 委托 {@link OrderService#pay(Long, Long)} 校验并更新订单状态 →
 * 写入支付流水 → 发布 {@link PaymentConfirmedEvent}。</p>
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private final OrderService orderService;
    private final PaymentMapper paymentMapper;
    private final SeckillOrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentServiceImpl(OrderService orderService,
                              PaymentMapper paymentMapper,
                              SeckillOrderMapper orderMapper,
                              ApplicationEventPublisher eventPublisher) {
        this.orderService = orderService;
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public PayResponse pay(Long orderNo, Long userId) {
        // ---- 1. 参数校验 ----
        if (orderNo == null) throw new IllegalArgumentException("orderNo must not be null");
        if (userId == null) throw new IllegalArgumentException("userId must not be null");

        // ---- 2. 调用订单服务支付（含归属校验、状态校验、乐观锁） ----
        orderService.pay(orderNo, userId);

        // ---- 3. 查询订单获取金额 ----
        SeckillOrder order = orderMapper.selectById(orderNo);
        if (order == null) throw new BusinessException("订单不存在");

        // ---- 4. 写入支付流水（幂等） ----
        Payment payment = new Payment();
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(order.getUserId());
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPayTime(LocalDateTime.now());

        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException e) {
            // uk_payment_order_no 冲突 → 重复请求，幂等处理
        }

        // ---- 5. 发布领域事件 ----
        eventPublisher.publishEvent(new PaymentConfirmedEvent(
                orderNo, userId, order.getTotalAmount(), payment.getPayTime()));

        return PayResponse.ok();
    }
}
