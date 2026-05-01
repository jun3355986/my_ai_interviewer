package com.aiinterviewer.interview.sse;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SSE事件类型
 * 与Python后端保持一致
 */
@Getter
@AllArgsConstructor
public enum SSEEventType {

    /**
     * 会话状态（首先发送）
     */
    STATUS("status"),

    /**
     * LLM流式输出片段
     */
    CHUNK("chunk"),

    /**
     * 结构化题目事件
     */
    QUESTION("question"),

    /**
     * 评分结果
     */
    SCORE("score"),

    /**
     * 完整处理结果
     */
    RESULT("result"),

    /**
     * 错误信息
     */
    ERROR("error"),

    /**
     * 流结束标记
     */
    DONE("done"),

    /**
     * 历史摘要（恢复时使用）
     */
    HISTORY_SUMMARY("history_summary"),

    /**
     * 当前问题（恢复时使用）
     */
    CURRENT_QUESTION("current_question"),

    /**
     * 恢复完成
     */
    RESUMED("resumed");

    private final String value;

    public static SSEEventType fromValue(String value) {
        for (SSEEventType type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
