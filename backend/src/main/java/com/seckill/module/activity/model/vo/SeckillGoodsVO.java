package com.seckill.module.activity.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 活动详情中秒杀商品的返回数据结构。
 */
@Data
@AllArgsConstructor
@Schema(description = "秒杀商品视图对象")
public class SeckillGoodsVO {
    @Schema(description = "秒杀商品 ID")
    private Long seckillGoodsId;
    @Schema(description = "商品 ID")
    private Long goodsId;
    @Schema(description = "商品名称")
    private String goodsName;
    @Schema(description = "秒杀价")
    private BigDecimal seckillPrice;
    @Schema(description = "剩余库存")
    private Integer stock;
    @Schema(description = "每人限购数量")
    private Integer limitNum;
}
