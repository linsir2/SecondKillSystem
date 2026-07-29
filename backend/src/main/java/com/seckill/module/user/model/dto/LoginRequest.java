package com.seckill.module.user.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 *
 * @param email    邮箱
 * @param password 密码
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
