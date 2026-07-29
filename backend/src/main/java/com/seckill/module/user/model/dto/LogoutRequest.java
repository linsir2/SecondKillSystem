package com.seckill.module.user.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登出请求。
 *
 * @param refreshToken 需撤销的 refresh token
 */
public record LogoutRequest(
        @NotBlank String refreshToken
) {}
