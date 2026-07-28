package com.seckill.module.order.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seckill.module.order.model.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单表 seckill_order 对应的实体（聚合根）。
 *
 * <p>封装状态机不变量：</p>
 * <ul>
 *   <li>{@link #pay()} — UNPAID → PAID，校验前置状态，设置支付时间</li>
 *   <li>{@link #cancel()} — UNPAID → CANCELLED，校验前置状态，设置取消时间</li>
 * </ul>
 */
@Data
@TableName("seckill_order")
public class SeckillOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long orderNo;
    private String orderToken;
    private Long userId;
    private Long activityId;
    private Long seckillGoodsId;
    private Integer buyCount;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 支付订单 UNPAID → PAID。
     *
     * @throws IllegalStateException 当前状态不是 UNPAID
     */
    public void pay() {
        if (this.status != OrderStatus.UNPAID) {
            throw new IllegalStateException("只能支付待支付订单");
        }
        this.status = OrderStatus.PAID;
        this.payTime = LocalDateTime.now();
    }

    /**
     * 取消订单 UNPAID → CANCELLED。
     *
     * @throws IllegalStateException 当前状态不是 UNPAID
     */
    public void cancel() {
        if (this.status != OrderStatus.UNPAID) {
            throw new IllegalStateException("只能取消待支付订单");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelTime = LocalDateTime.now();
    }
}
