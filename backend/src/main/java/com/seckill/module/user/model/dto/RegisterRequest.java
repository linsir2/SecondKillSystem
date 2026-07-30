package com.seckill.module.user.model.dto;

import com.seckill.common.constant.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "注册请求")
public record RegisterRequest(
        @NotBlank @Size(min = 1, max = 25) @Schema(description = "用户名（1-25 字符，唯一）") String userName,
        @NotBlank @Email @Schema(description = "邮箱（唯一）") String email,
        @NotBlank @Size(min = 8) @Schema(description = "密码（≥8 位）") String password,
        @Schema(description = "角色（选填，默认 user，不可选 admin）") UserRole role
) {
    public UserRole resolveRole() {
        // 注册只能选 user 或 merchant，禁止自选 admin（防止越权提权）
        if (role == UserRole.admin) return UserRole.user;
        return role != null ? role : UserRole.user;
    }
}
