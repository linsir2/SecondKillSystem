package com.seckill.module.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.module.payment.model.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付流水 Mapper。
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
