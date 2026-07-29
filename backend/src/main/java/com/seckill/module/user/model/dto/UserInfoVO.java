package com.seckill.module.user.model.dto;

import com.seckill.common.constant.BanStatus;
import com.seckill.common.constant.UserRole;

/**
 * 当前用户信息（含封禁状态）。
 *
 * @param userId    用户 ID
 * @param userName  用户名
 * @param email     邮箱
 * @param role      角色
 * @param banStatus 封禁状态
 */
public record UserInfoVO(
        Long userId,
        String userName,
        String email,
        UserRole role,
        BanStatus banStatus
) {}
