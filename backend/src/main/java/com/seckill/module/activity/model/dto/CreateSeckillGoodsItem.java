package com.seckill.module.activity.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建活动时传入的秒杀商品条目。
 */
@Data
@AllArgsConstructor
public class CreateSeckillGoodsItem {
    private Long goodsId;
    private BigDecimal seckillPrice;
    private Integer stock;
    private Integer limitNum;
}
