package com.aiinterviewer.admin.interview.dto;

import java.util.List;
import lombok.Data;

@Data
public class InterviewDiagnosisResponse {

    private String sessionId;
    private boolean missingTechnicalQuestions;
    private boolean emptyTechnicalPool;
    private boolean missingScores;
    private boolean earlyConcludedStage;
    private List<String> findings = List.of();
}
