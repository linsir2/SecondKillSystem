package com.seckill.module.payment.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seckill.module.payment.model.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水表 payment 对应的实体。
 */
@Data
@TableName("payment")
public class Payment {
    @TableId(type = IdType.ASSIGN_ID)
    private Long paymentId;
    private Long orderNo;
    private Long userId;
    private BigDecimal amount;
    private PaymentStatus status;
    private LocalDateTime payTime;
    private LocalDateTime createdAt;
}
