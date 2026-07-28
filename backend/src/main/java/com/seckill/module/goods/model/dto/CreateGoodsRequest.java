package com.seckill.module.goods.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateGoodsRequest {
    private String goodsName;
    private BigDecimal price;
    private Integer stock;
}
