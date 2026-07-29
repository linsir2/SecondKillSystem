package com.seckill.module.gateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 秒杀执行响应。
 */
@Data
@AllArgsConstructor
public class SeckillResponse {

    private String orderToken;
}
