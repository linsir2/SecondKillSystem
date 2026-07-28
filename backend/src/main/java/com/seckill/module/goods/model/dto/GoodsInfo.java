package com.seckill.module.goods.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品基本信息 —— Product 上下文暴露给其他上下文的不可变快照。
 * <p>Activity 上下文通过 {@code GoodsService.getGoodsInfo()} 获取，
 * 用于校验秒杀绑定时的商品归属和库存上限。</p>
 */
@Data
@AllArgsConstructor
public class GoodsInfo {
    private Long goodsId;
    private String goodsName;
    private BigDecimal price;
    private Integer stock;
}
