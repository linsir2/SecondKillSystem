package com.seckill.common.security;

import com.seckill.common.constant.UserRole;
import lombok.Value;

/**
 * 当前认证用户的不可变快照。
 * <p>由 {@code AuthFilter} 从 Header 提取 userId 后查 UserService 组装，
 * 存入 {@link SecurityContext}。
 * 不含 {@code banStatus} —— 封禁校验由 seckill/网关层自行处理。</p>
 */
@Value
public class CurrentUser {
    Long userId;
    String userName;
    UserRole role;
}
