package com.aiinterviewer.admin.questionbank;

import lombok.Data;

@Data
public class QuestionQuery {

    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 20L;
    private static final long MAX_CURRENT = 1_000_000L;
    private static final long MAX_SIZE = 100L;

    private String questionType;
    private String difficulty;
    private String tag;
    private Integer status;
    private Long jobId;
    private String keyword;
    private Long current = DEFAULT_CURRENT;
    private Long size = DEFAULT_SIZE;

    long normalizedCurrent() {
        if (current == null || current < 1) {
            return DEFAULT_CURRENT;
        }
        return Math.min(current, MAX_CURRENT);
    }

    long normalizedSize() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
