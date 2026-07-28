package com.seckill.module.activity.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 活动详情中秒杀商品的返回数据结构。
 */
@Data
@AllArgsConstructor
public class SeckillGoodsVO {
    private Long seckillGoodsId;
    private Long goodsId;
    private String goodsName;
    private BigDecimal seckillPrice;
    private Integer stock;
    private Integer limitNum;
}
