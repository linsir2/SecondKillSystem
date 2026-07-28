package com.seckill.module.goods.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class GoodsVO {
    private Long goodsId;
    private String goodsName;
    private BigDecimal price;
    private Integer status;
    private Integer stock;
    private LocalDateTime createdAt;
}
