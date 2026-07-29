package com.seckill.module.gateway.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 秒杀执行请求。
 */
@Data
public class SeckillRequest {

    @NotNull
    private Long activityId;

    @NotNull
    private Long seckillGoodsId;

    @NotNull
    @Min(1)
    private Integer buyCount;
}
