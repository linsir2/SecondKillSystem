package com.seckill.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一 API 响应格式。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;
    private List<String> errors;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data, null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null, null);
    }

    public static <T> Result<T> error(int code, String message, List<String> errors) {
        return new Result<>(code, message, null, errors);
    }
}
