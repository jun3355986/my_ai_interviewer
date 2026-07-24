package com.aiinterviewer.interview.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InterviewTurnAttempt {

    private String id;
    private String lineageId;
    private String sessionId;
    private Long ownerUserId;
    private Long expectedBranchVersion;
    private Long expectedTailMessageId;
    private String candidateAnswer;
    private String status;
    private String retryOfId;
    private String agentRunId;
    private String requestId;
    private String username;
    private String forkSourceSessionId;
    private Long forkTriggerMessageId;
    private Long forkPointMessageId;
    private Long forkExpectedSourceVersion;
    private Long forkExpectedSourceTailMessageId;
    private String errorCode;
    private String diagnosticRef;
    private LocalDateTime createdAt;
    private LocalDateTime processingStartedAt;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime updatedAt;
}
