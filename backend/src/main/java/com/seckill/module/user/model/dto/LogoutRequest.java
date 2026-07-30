package com.seckill.module.user.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 登出请求。
 *
 * @param refreshToken 需撤销的 refresh token
 */
@Schema(description = "登出请求")
public record LogoutRequest(
        @NotBlank @Schema(description = "需撤销的 refresh token") String refreshToken
) {}
