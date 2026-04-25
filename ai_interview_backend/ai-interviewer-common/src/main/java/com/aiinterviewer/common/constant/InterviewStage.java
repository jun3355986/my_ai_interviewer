package com.aiinterviewer.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面试阶段枚举
 */
@Getter
@AllArgsConstructor
public enum InterviewStage {

    /**
     * 开场阶段
     */
    OPENING("opening", "开场"),

    /**
     * 自我介绍阶段
     */
    SELF_INTRODUCTION("self_introduction", "自我介绍"),

    /**
     * 项目提问阶段
     */
    PROJECT_QNA("project_qna", "项目提问"),

    /**
     * 技术面试阶段
     */
    TECHNICAL_QNA("technical_qna", "技术面试"),

    /**
     * 总结阶段
     */
    CONCLUSION("conclusion", "总结"),

    /**
     * 已完成
     */
    COMPLETED("completed", "已完成");

    private final String code;
    private final String description;

    public static InterviewStage fromCode(String code) {
        for (InterviewStage stage : values()) {
            if (stage.getCode().equals(code)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Unknown interview stage: " + code);
    }
}
