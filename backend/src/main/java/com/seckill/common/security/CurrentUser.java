package com.seckill.common.security;

import com.seckill.common.constant.UserRole;
import lombok.Value;

/**
 * 当前认证用户的不可变快照。
 * <p>由 {@code JwtAuthFilter} 从 JWT（或 dev 回退 X-User-Id Header）解析 userId 后组装，
 * 存入 {@link SecurityContext}。
 * 不含 {@code banStatus} —— 封禁校验由 seckill/网关层自行处理。</p>
 */
@Value
public class CurrentUser {
    Long userId;
    String userName;
    UserRole role;
}
