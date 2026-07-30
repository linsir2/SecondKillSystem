package com.seckill.module.user.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 *
 * @param email    邮箱
 * @param password 密码
 */
@Schema(description = "登录请求")
public record LoginRequest(
        @NotBlank @Email @Schema(description = "邮箱") String email,
        @NotBlank @Schema(description = "密码") String password
) {}
