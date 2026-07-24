package com.aiinterviewer.interview.model;

import java.util.Map;

public record TurnModelResult(
        String aiMessage,
        String nextStage,
        boolean interviewComplete,
        Integer score,
        String feedback,
        Map<String, Object> metadata,
        String pythonSessionId,
        AuthoritativeTurnState authoritativeState) {

    public TurnModelResult(
            String aiMessage,
            String nextStage,
            boolean interviewComplete,
            Integer score,
            String feedback,
            Map<String, Object> metadata,
            String pythonSessionId) {
        this(
                aiMessage,
                nextStage,
                interviewComplete,
                score,
                feedback,
                metadata,
                pythonSessionId,
                null);
    }
}
