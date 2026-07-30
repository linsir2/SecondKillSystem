package com.seckill.module.goods.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "更新商品请求")
public class UpdateGoodsRequest {
    @Schema(description = "商品名称")
    private String goodsName;
    @Schema(description = "商品价格")
    private BigDecimal price;
    @Schema(description = "库存数量")
    private Integer stock;
}
