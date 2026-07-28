package com.seckill.common.exception;

import java.util.Collections;
import java.util.List;

/**
 * 业务异常 —— 违反领域规则时抛出。
 * <p>与 {@link IllegalArgumentException} 的分界：后者用于参数自身格式/范围问题，
 * 前者用于涉及外部状态或跨领域规则的问题。</p>
 */
public class BusinessException extends RuntimeException {

    private final List<String> errors;

    public BusinessException(String message) {
        super(message);
        this.errors = Collections.emptyList();
    }

    public BusinessException(String message, List<String> errors) {
        super(message);
        this.errors = errors != null ? errors : Collections.emptyList();
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errors = Collections.emptyList();
    }

    /**
     * 收集型校验的逐条错误明细，前端可据此做字段级展示。
     */
    public List<String> getErrors() {
        return errors;
    }
}
