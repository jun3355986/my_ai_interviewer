package com.aiinterviewer.admin.common.exception;

import lombok.Getter;

@Getter
public class AdminBusinessException extends RuntimeException {

    private final Integer code;

    public AdminBusinessException(String message) {
        this(500, message);
    }

    public AdminBusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public AdminBusinessException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
