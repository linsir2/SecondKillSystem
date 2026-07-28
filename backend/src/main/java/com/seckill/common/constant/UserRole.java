package com.seckill.common.constant;

/**
 * 系统角色枚举 —— 与 {@code sys_user.role} 字段值一致。
 * <p>跨模块使用，放在 common 层避免循环依赖。</p>
 */
public enum UserRole {
    admin, merchant, user
}
