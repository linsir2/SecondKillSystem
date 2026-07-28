package com.seckill.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.module.order.model.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀订单 Mapper。
 */
@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {
}
