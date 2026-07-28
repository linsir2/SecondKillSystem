package com.seckill.module.user.model.dto;

import com.seckill.common.constant.BanStatus;
import com.seckill.common.constant.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户基本信息 —— Identity 上下文暴露给其他上下文的不可变快照。
 * <p>不含 {@code password} 字段。</p>
 */
@Data
@AllArgsConstructor
public class UserInfo {
    private Long userId;
    private String userName;
    private String email;
    private UserRole role;
    private BanStatus banStatus;
}
