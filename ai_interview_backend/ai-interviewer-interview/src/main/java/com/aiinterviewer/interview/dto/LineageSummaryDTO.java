package com.aiinterviewer.interview.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class LineageSummaryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String lineageId;
    private String rootSessionId;
    private String candidateName;
    private Long resumeId;
    private Long jobId;
    private String jobTitle;
    private Long branchCount;
    private Long activeBranchCount;
    private Long completedBranchCount;
    private Integer bestCompletedScore;
    private LocalDateTime latestActivityAt;
    private String focusedBranchId;
    private String focusedBranchStage;
    private String focusedBranchStageDisplay;
    private Integer focusedBranchStatus;
    private Integer focusedBranchProgress;
}
