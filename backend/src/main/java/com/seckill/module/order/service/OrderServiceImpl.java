package com.seckill.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seckill.common.exception.BusinessException;
import com.seckill.module.activity.mapper.SeckillGoodsMapper;
import com.seckill.module.activity.model.entity.SeckillGoods;
import com.seckill.module.order.mapper.MessageLogMapper;
import com.seckill.module.order.mapper.SeckillOrderMapper;
import com.seckill.module.order.model.dto.OrderCancelledEvent;
import com.seckill.module.order.model.dto.OrderPaidEvent;
import com.seckill.module.order.model.dto.OrderTimedOutEvent;
import com.seckill.module.order.model.entity.MessageLog;
import com.seckill.module.order.model.entity.SeckillOrder;
import com.seckill.module.order.model.enums.OrderStatus;
import com.seckill.module.order.model.enums.SendStatus;
import com.seckill.module.stock.model.dto.SeckillDeductedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 订单领域服务实现。
 *
 * <p>消费 MQ 创建订单（幂等 insert + message_log outbox），
 * 状态机管理（UNPAID → PAID / CANCELLED，乐观锁控制并发），
 * 前端轮询订单状态。状态转换委托 {@link SeckillOrder} 实体方法，
 * 成功时通过 {@link ApplicationEventPublisher} 发布领域事件。</p>
 */
@Service
public class OrderServiceImpl implements OrderService {

    private final SeckillOrderMapper orderMapper;
    private final MessageLogMapper messageLogMapper;
    private final SeckillGoodsMapper seckillGoodsMapper;
    private final ApplicationEventPublisher eventPublisher;

    public OrderServiceImpl(SeckillOrderMapper orderMapper,
                            MessageLogMapper messageLogMapper,
                            SeckillGoodsMapper seckillGoodsMapper,
                            ApplicationEventPublisher eventPublisher) {
        this.orderMapper = orderMapper;
        this.messageLogMapper = messageLogMapper;
        this.seckillGoodsMapper = seckillGoodsMapper;
        this.eventPublisher = eventPublisher;
    }

    // ========================================================================
    // 订单创建
    // ========================================================================

    @Override
    @Transactional
    public void createOrder(SeckillDeductedEvent event) {
        // ---- 1. 参数校验 ----
        if (event == null) throw new IllegalArgumentException("event must not be null");
        if (event.orderToken() == null || event.orderToken().isBlank())
            throw new IllegalArgumentException("orderToken must not be blank");
        if (event.userId() == null) throw new IllegalArgumentException("userId must not be null");
        if (event.activityId() == null) throw new IllegalArgumentException("activityId must not be null");
        if (event.seckillGoodsId() == null) throw new IllegalArgumentException("seckillGoodsId must not be null");
        if (event.buyCount() <= 0) throw new IllegalArgumentException("buyCount must be > 0, got: " + event.buyCount());

        // ---- 2. 查询秒杀商品（取秒杀价计算总金额） ----
        SeckillGoods sg = seckillGoodsMapper.selectById(event.seckillGoodsId());
        if (sg == null) throw new BusinessException("秒杀商品不存在");

        // ---- 3. 构建订单，幂等 insert ----
        SeckillOrder order = new SeckillOrder();
        order.setOrderToken(event.orderToken());
        order.setUserId(event.userId());
        order.setActivityId(event.activityId());
        order.setSeckillGoodsId(event.seckillGoodsId());
        order.setBuyCount(event.buyCount());
        order.setTotalAmount(sg.getSeckillPrice().multiply(BigDecimal.valueOf(event.buyCount())));
        order.setStatus(OrderStatus.UNPAID);

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // uk_order_token 或 uk_user_activity_goods 冲突 → 已处理过
            return;
        }

        // ---- 4. 写入消息表（延时关单 outbox） ----
        MessageLog log = new MessageLog();
        log.setBizType("order_timeout");
        log.setBizId(event.orderToken());
        log.setTopic("seckill_order");
        log.setTag("order_timeout");
        log.setBody("{\"orderToken\":\"" + event.orderToken()
                + "\",\"orderNo\":" + order.getOrderNo() + "}");
        log.setStatus(SendStatus.INIT);
        log.setRetryCount(0);

        messageLogMapper.insert(log);
    }

    // ========================================================================
    // 支付
    // ========================================================================

    @Override
    public void pay(Long orderNo) {
        if (orderNo == null) throw new IllegalArgumentException("orderNo must not be null");

        // ---- 1. 查询 ----
        SeckillOrder order = orderMapper.selectById(orderNo);
        if (order == null) throw new BusinessException("订单不存在");

        // ---- 2. 实体领域行为（校验 + 设状态） ----
        try {
            order.pay();
        } catch (IllegalStateException e) {
            throw new BusinessException(e.getMessage());
        }

        // ---- 3. 乐观锁写库 ----
        LambdaUpdateWrapper<SeckillOrder> wrapper = new LambdaUpdateWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, orderNo)
                .eq(SeckillOrder::getStatus, OrderStatus.UNPAID);
        int affected = orderMapper.update(order, wrapper);

        // ---- 4. 冲突处理 ----
        if (affected == 0) {
            SeckillOrder current = orderMapper.selectById(orderNo);
            if (current == null) throw new BusinessException("订单不存在");
            if (current.getStatus() == OrderStatus.PAID) throw new BusinessException("订单已支付");
            if (current.getStatus() == OrderStatus.CANCELLED) throw new BusinessException("订单已取消");
            throw new BusinessException("订单状态异常");
        }

        // ---- 5. 领域事件 ----
        eventPublisher.publishEvent(new OrderPaidEvent(orderNo, order.getOrderToken()));
    }

    // ========================================================================
    // 取消
    // ========================================================================

    @Override
    public void cancel(Long orderNo) {
        if (orderNo == null) throw new IllegalArgumentException("orderNo must not be null");

        // ---- 1. 查询 ----
        SeckillOrder order = orderMapper.selectById(orderNo);
        if (order == null) throw new BusinessException("订单不存在");

        // ---- 2. 实体领域行为（校验 + 设状态） ----
        try {
            order.cancel();
        } catch (IllegalStateException e) {
            throw new BusinessException(e.getMessage());
        }

        // ---- 3. 乐观锁写库 ----
        LambdaUpdateWrapper<SeckillOrder> wrapper = new LambdaUpdateWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, orderNo)
                .eq(SeckillOrder::getStatus, OrderStatus.UNPAID);
        int affected = orderMapper.update(order, wrapper);

        // ---- 4. 冲突处理 ----
        if (affected == 0) {
            SeckillOrder current = orderMapper.selectById(orderNo);
            if (current == null) throw new BusinessException("订单不存在");
            if (current.getStatus() == OrderStatus.PAID) throw new BusinessException("订单已支付");
            if (current.getStatus() == OrderStatus.CANCELLED) throw new BusinessException("订单已取消");
            throw new BusinessException("订单状态异常");
        }

        // ---- 5. 领域事件 ----
        eventPublisher.publishEvent(new OrderCancelledEvent(orderNo, order.getOrderToken()));
    }

    // ========================================================================
    // 超时取消
    // ========================================================================

    @Override
    public void cancelByTimeout(String orderToken) {
        if (orderToken == null || orderToken.isBlank())
            throw new IllegalArgumentException("orderToken must not be blank");

        // ---- 1. 按 orderToken 查询 ----
        SeckillOrder order = orderMapper.selectOne(
                new QueryWrapper<SeckillOrder>().eq("order_token", orderToken));
        if (order == null) throw new BusinessException("订单不存在");

        // ---- 2. 实体领域行为 ----
        try {
            order.cancel();
        } catch (IllegalStateException e) {
            throw new BusinessException(e.getMessage());
        }

        // ---- 3. 乐观锁写库 ----
        LambdaUpdateWrapper<SeckillOrder> wrapper = new LambdaUpdateWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, order.getOrderNo())
                .eq(SeckillOrder::getStatus, OrderStatus.UNPAID);
        int affected = orderMapper.update(order, wrapper);

        // ---- 4. 冲突处理 ----
        if (affected == 0) {
            SeckillOrder current = orderMapper.selectOne(
                    new QueryWrapper<SeckillOrder>().eq("order_token", orderToken));
            if (current == null) throw new BusinessException("订单不存在");
            if (current.getStatus() == OrderStatus.PAID) throw new BusinessException("订单已支付");
            if (current.getStatus() == OrderStatus.CANCELLED) throw new BusinessException("订单已取消");
            throw new BusinessException("订单状态异常");
        }

        // ---- 5. 领域事件（含 Redis 补偿所需全字段） ----
        eventPublisher.publishEvent(new OrderTimedOutEvent(
                order.getOrderNo(), order.getOrderToken(),
                order.getActivityId(), order.getSeckillGoodsId(),
                order.getUserId(), order.getBuyCount()));
    }

    // ========================================================================
    // 状态查询
    // ========================================================================

    @Override
    public String getOrderStatus(String orderToken) {
        if (orderToken == null || orderToken.isBlank())
            throw new IllegalArgumentException("orderToken must not be blank");

        // 使用字符串 QueryWrapper 而非 LambdaQueryWrapper（lambda cache 在纯 Mockito 测试中不可用）
        SeckillOrder order = orderMapper.selectOne(
                new QueryWrapper<SeckillOrder>()
                        .eq("order_token", orderToken)
                        .select("status"));
        return order != null ? order.getStatus().name() : null;
    }
}
