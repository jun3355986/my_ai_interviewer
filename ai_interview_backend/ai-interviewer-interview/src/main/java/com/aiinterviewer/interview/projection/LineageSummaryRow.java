package com.aiinterviewer.interview.projection;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LineageSummaryRow {

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
    private Integer focusedBranchStatus;
    private Integer projectQuestionsCount;
    private Integer targetProjectQuestions;
}
