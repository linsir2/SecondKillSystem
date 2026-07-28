package com.seckill.module.activity.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀商品表 seckill_goods 对应的实体。
 */
@Data
@TableName("seckill_goods")
public class SeckillGoods {
    @TableId(type = IdType.ASSIGN_ID)
    private Long seckillGoodsId;
    private Long activityId;
    private Long goodsId;
    private BigDecimal seckillPrice;
    private Integer stock;
    private Integer limitNum;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
