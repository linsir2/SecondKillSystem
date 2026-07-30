package com.seckill.module.goods.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "商品基本信息（内部上下文调用）")
public class GoodsInfo {
    @Schema(description = "商品 ID")
    private Long goodsId;
    @Schema(description = "商品名称")
    private String goodsName;
    @Schema(description = "价格")
    private BigDecimal price;
    @Schema(description = "库存")
    private Integer stock;
}
