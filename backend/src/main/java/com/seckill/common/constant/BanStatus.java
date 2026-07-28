package com.seckill.common.constant;

/**
 * 用户封禁状态 —— 与 {@code sys_user.ban_status} 字段值一致。
 * <p>网关层据此拦截黑名单用户。</p>
 */
public enum BanStatus {
    normal, banned
}
