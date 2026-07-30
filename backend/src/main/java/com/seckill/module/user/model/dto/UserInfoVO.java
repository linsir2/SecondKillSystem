package com.seckill.module.user.model.dto;

import com.seckill.common.constant.BanStatus;
import com.seckill.common.constant.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 当前用户信息（含封禁状态）。
 *
 * @param userId    用户 ID
 * @param userName  用户名
 * @param email     邮箱
 * @param role      角色
 * @param banStatus 封禁状态
 */
@Schema(description = "当前用户信息")
public record UserInfoVO(
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户名") String userName,
        @Schema(description = "邮箱") String email,
        @Schema(description = "角色") UserRole role,
        @Schema(description = "封禁状态") BanStatus banStatus
) {}
