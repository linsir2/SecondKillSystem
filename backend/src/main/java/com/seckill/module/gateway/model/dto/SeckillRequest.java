package com.seckill.module.gateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 秒杀执行请求。
 */
@Data
@Schema(description = "秒杀执行请求")
public class SeckillRequest {

    @NotNull
    @Schema(description = "活动 ID")
    private Long activityId;

    @NotNull
    @Schema(description = "秒杀商品 ID")
    private Long seckillGoodsId;

    @NotNull
    @Min(1)
    @Schema(description = "购买数量（≥1）")
    private Integer buyCount;
}
