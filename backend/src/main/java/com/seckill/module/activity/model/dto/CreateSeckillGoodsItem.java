package com.seckill.module.activity.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建活动时传入的秒杀商品条目。
 */
@Data
@AllArgsConstructor
@Schema(description = "创建活动 — 秒杀商品条目")
public class CreateSeckillGoodsItem {
    @Schema(description = "商品 ID")
    private Long goodsId;
    @Schema(description = "秒杀价")
    private BigDecimal seckillPrice;
    @Schema(description = "秒杀库存")
    private Integer stock;
    @Schema(description = "每人限购数量")
    private Integer limitNum;
}
