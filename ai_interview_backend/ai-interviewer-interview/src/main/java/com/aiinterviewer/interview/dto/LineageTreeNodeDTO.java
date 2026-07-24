package com.aiinterviewer.interview.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LineageTreeNodeDTO implements Serializable {

    private String branchId;
    private String parentBranchId;
    private String branchLabel;
    private Long forkPointMessageId;
    private Long forkTriggerMessageId;
    private String stage;
    private Integer status;
    private Long branchVersion;
    private LocalDateTime latestBusinessActivityAt;
    private Integer progress;
    private Integer ownedAssessmentCount;
    private Integer inheritedAssessmentCount;
    private Integer totalAssessmentCount;
    private Integer completedScore;
    private String evaluationSummary;
    private String recoverableTurnId;
    private String recoverableTurnStatus;
    private String recoverableTurnErrorCode;
}
