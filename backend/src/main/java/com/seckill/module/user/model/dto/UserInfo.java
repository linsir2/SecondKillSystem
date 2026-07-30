package com.seckill.module.user.model.dto;

import com.seckill.common.constant.BanStatus;
import com.seckill.common.constant.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户基本信息 —— Identity 上下文暴露给其他上下文的不可变快照。
 * <p>不含 {@code password} 字段。</p>
 */
@Data
@AllArgsConstructor
@Schema(description = "用户基本信息（内部上下文调用）")
public class UserInfo {
    @Schema(description = "用户 ID")
    private Long userId;
    @Schema(description = "用户名")
    private String userName;
    @Schema(description = "邮箱")
    private String email;
    @Schema(description = "角色")
    private UserRole role;
    @Schema(description = "封禁状态")
    private BanStatus banStatus;
}
