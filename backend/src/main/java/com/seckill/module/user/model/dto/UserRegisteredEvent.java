package com.seckill.module.user.model.dto;

import com.seckill.common.constant.UserRole;

/**
 * 用户注册成功领域事件。
 *
 * @param userId 用户 ID
 * @param email  注册邮箱
 * @param role   注册角色
 */
public record UserRegisteredEvent(
        Long userId,
        String email,
        UserRole role
) {}
