package com.seckill.module.user.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新 token 请求。
 *
 * @param refreshToken 刷新令牌
 */
public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {}
