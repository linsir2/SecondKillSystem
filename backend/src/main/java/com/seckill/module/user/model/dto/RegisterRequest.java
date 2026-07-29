package com.seckill.module.user.model.dto;

import com.seckill.common.constant.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 *
 * @param userName 用户名（1~25 字符，唯一）
 * @param email    邮箱（唯一，必填）
 * @param password 密码（≥8 位）
 * @param role     角色（选填，默认 user）
 */
public record RegisterRequest(
        @NotBlank @Size(min = 1, max = 25) String userName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        UserRole role
) {
    public UserRole resolveRole() {
        // 注册只能选 user 或 merchant，禁止自选 admin（防止越权提权）
        if (role == UserRole.admin) return UserRole.user;
        return role != null ? role : UserRole.user;
    }
}
