package com.seckill.module.gateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 秒杀执行响应。
 */
@Data
@AllArgsConstructor
@Schema(description = "秒杀执行响应")
public class SeckillResponse {

    @Schema(description = "排队凭证（用于轮询订单状态）")
    private String orderToken;
}
