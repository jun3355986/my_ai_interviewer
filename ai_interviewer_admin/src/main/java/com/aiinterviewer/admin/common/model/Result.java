package com.aiinterviewer.admin.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import lombok.Data;

/**
 * Unified API response aligned with the existing backend response shape.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int SUCCESS_CODE = 200;
    public static final int DEFAULT_FAIL_CODE = 500;

    private Integer code;
    private String message;
    private T data;
    private Long timestamp;
    private String traceId;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success() {
        return new Result<>(SUCCESS_CODE, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "success", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(SUCCESS_CODE, message, data);
    }

    public static <T> Result<T> fail(String message) {
        return fail(DEFAULT_FAIL_CODE, message);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    public boolean isSuccess() {
        return code != null && code == SUCCESS_CODE;
    }

    public Result<T> traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
