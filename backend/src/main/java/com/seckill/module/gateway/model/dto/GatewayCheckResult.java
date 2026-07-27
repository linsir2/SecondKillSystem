package com.seckill.module.gateway.model.dto;

/**
 * 网关检查结果值对象。
 *
 * @param code 结果码: 0 通过 / -1 黑名单 / -2 限流拒绝
 */
public record GatewayCheckResult(int code) {

    public static final int CODE_PASS = 0;
    public static final int CODE_BLACKLISTED = -1;
    public static final int CODE_RATE_LIMITED = -2;

    public static GatewayCheckResult pass() {
        return new GatewayCheckResult(CODE_PASS);
    }

    public static GatewayCheckResult blacklisted() {
        return new GatewayCheckResult(CODE_BLACKLISTED);
    }

    public static GatewayCheckResult rateLimited() {
        return new GatewayCheckResult(CODE_RATE_LIMITED);
    }
}
