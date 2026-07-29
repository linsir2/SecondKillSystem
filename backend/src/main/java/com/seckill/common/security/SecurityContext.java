package com.seckill.common.security;

/**
 * 线程级认证上下文 —— 请求进入时设值，退出时清理。
 * <p>典型用法：JwtAuthFilter 在 {@code doFilterInternal} 中 set，
 * 在 finally 块中 clear。Controller / Service 层只调用 {@link #get()}。</p>
 */
public class SecurityContext {

    private static final ThreadLocal<CurrentUser> holder = new ThreadLocal<>();

    public static CurrentUser get() {
        return holder.get();
    }

    public static void set(CurrentUser user) {
        holder.set(user);
    }

    public static void clear() {
        holder.remove();
    }

    private SecurityContext() {}
}
