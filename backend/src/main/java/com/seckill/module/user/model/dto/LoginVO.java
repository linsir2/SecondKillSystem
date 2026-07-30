package com.seckill.module.user.model.dto;

import com.seckill.common.constant.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录/注册/刷新统一响应。
 *
 * @param accessToken  24h 有效 access token
 * @param refreshToken 7d 有效 refresh token
 * @param userId       用户 ID
 * @param userName     用户名
 * @param role         角色
 */
@Schema(description = "登录/注册/刷新统一响应")
public record LoginVO(
        @Schema(description = "24h 有效的 access token") String accessToken,
        @Schema(description = "7d 有效的 refresh token") String refreshToken,
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户名") String userName,
        @Schema(description = "角色（user/merchant/admin）") UserRole role
) {}
