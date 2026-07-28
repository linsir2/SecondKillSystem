package com.seckill.module.goods.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("goods")
public class Goods {
    @TableId(type = IdType.ASSIGN_ID)
    private Long goodsId;
    private String goodsName;
    private Long merchantId;
    private BigDecimal price;
    private Integer status;
    private Integer stock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
