package com.seckill.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "统一 API 响应格式")
public class Result<T> {
    @Schema(description = "状态码（200=成功）")
    private int code;
    @Schema(description = "提示信息")
    private String message;
    @Schema(description = "数据负载")
    private T data;
    @Schema(description = "错误详情列表")
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
