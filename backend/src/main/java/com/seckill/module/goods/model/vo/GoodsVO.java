package com.seckill.module.goods.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Schema(description = "商品视图对象")
public class GoodsVO {
    @Schema(description = "商品 ID")
    private Long goodsId;
    @Schema(description = "商品名称")
    private String goodsName;
    @Schema(description = "价格")
    private BigDecimal price;
    @Schema(description = "状态（0-下架，1-上架）")
    private Integer status;
    @Schema(description = "库存")
    private Integer stock;
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
