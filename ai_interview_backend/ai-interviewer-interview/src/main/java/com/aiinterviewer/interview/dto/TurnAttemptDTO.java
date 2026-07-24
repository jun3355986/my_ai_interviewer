package com.aiinterviewer.interview.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TurnAttemptDTO implements Serializable {

    private String turnId;
    private String lineageId;
    private String branchId;
    private Long expectedBranchVersion;
    private Long expectedTailMessageId;
    private String candidateAnswer;
    private String status;
    private String retryOfTurnId;
    private String errorCode;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime updatedAt;
}
