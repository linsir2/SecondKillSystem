package com.seckill.module.user.model.dto;

import com.seckill.common.constant.UserRole;

/**
 * 登录/注册/刷新统一响应。
 *
 * @param accessToken  24h 有效 access token
 * @param refreshToken 7d 有效 refresh token
 * @param userId       用户 ID
 * @param userName     用户名
 * @param role         角色
 */
public record LoginVO(
        String accessToken,
        String refreshToken,
        Long userId,
        String userName,
        UserRole role
) {}
