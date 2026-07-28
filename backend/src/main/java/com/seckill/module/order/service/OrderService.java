package com.seckill.module.order.service;

import com.seckill.module.stock.model.dto.SeckillDeductedEvent;

/**
 * 订单上下文领域服务接口。
 *
 * <p>Order 是核心子域，消费 MQ 消息 {@link SeckillDeductedEvent} 创建订单，
 * 管理状态机（UNPAID → PAID / CANCELLED）。</p>
 */
public interface OrderService {

    /**
     * 消费 MQ 创建订单（含本地事务）。
     *
     * <p>幂等策略（选 B）：</p>
     * <ol>
     *   <li>参数校验</li>
     *   <li>查询 {@code seckill_goods.seckill_price} 计算总价</li>
     *   <li>try-insert {@code seckill_order}，{@code DuplicateKeyException} 捕获后静默返回</li>
     *   <li>insert {@code message_log}（bizType=order_timeout, status=INIT）</li>
     * </ol>
     *
     * @param event 库存预扣成功事件（不可 null，字段不可缺）
     * @throws IllegalArgumentException 参数不合法
     * @throws com.seckill.common.exception.BusinessException 秒杀商品不存在
     */
    void createOrder(SeckillDeductedEvent event);

    /**
     * 支付订单 UNPAID → PAID。
     *
     * <p>乐观锁：先 {@code selectById} 校验存在性和状态，再 UPDATE ... WHERE order_no=? AND status='UNPAID'。
     * 更新影响行数为 0 时重新查询并给出具体提示。</p>
     *
     * @param orderNo 订单号（不可 null）
     * @throws IllegalArgumentException orderNo 为 null
     * @throws com.seckill.common.exception.BusinessException 订单不存在 / 状态不合法 / 乐观锁冲突
     */
    void pay(Long orderNo);

    /**
     * 取消订单 UNPAID → CANCELLED。
     *
     * <p>乐观锁同 {@link #pay(Long)}。</p>
     *
     * @param orderNo 订单号（不可 null）
     * @throws IllegalArgumentException orderNo 为 null
     * @throws com.seckill.common.exception.BusinessException 订单不存在 / 状态不合法 / 乐观锁冲突
     */
    void cancel(Long orderNo);

    /**
     * 查询订单状态（前端轮询用）。
     *
     * @param orderToken 排队凭证
     * @return "UNPAID" / "PAID" / "CANCELLED"，不存在返回 {@code null}
     * @throws IllegalArgumentException orderToken 为 null 或 blank
     */
    String getOrderStatus(String orderToken);
}
