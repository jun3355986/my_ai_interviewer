package com.aiinterviewer.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面试会话状态
 */
@Getter
@AllArgsConstructor
public enum SessionStatus {

    /**
     * 进行中
     */
    IN_PROGRESS(1, "进行中"),

    /**
     * 已完成
     */
    COMPLETED(2, "已完成"),

    /**
     * 已取消
     */
    CANCELLED(3, "已取消");

    private final int code;
    private final String description;

    public static SessionStatus fromCode(int code) {
        for (SessionStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown session status: " + code);
    }
}
