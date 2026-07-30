package com.seckill.module.user.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 刷新 token 请求。
 *
 * @param refreshToken 刷新令牌
 */
@Schema(description = "刷新 token 请求")
public record RefreshTokenRequest(
        @NotBlank @Schema(description = "刷新令牌") String refreshToken
) {}
