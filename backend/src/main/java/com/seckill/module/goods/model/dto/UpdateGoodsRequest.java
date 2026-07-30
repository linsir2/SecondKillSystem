package com.seckill.module.goods.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "更新商品请求")
public class UpdateGoodsRequest {
    @NotBlank
    @Schema(description = "商品名称")
    private String goodsName;

    @NotNull
    @Min(0)
    @Schema(description = "商品价格")
    private BigDecimal price;

    @NotNull
    @Min(0)
    @Schema(description = "库存数量")
    private Integer stock;
}
