package com.aiinterviewer.interview.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AuthoritativeTurnState(
        @JsonProperty("current_stage") String currentStage,
        @JsonProperty("branch_status") Integer branchStatus,
        @JsonProperty("project_questions_count") Integer projectQuestionsCount,
        @JsonProperty("target_project_questions") Integer targetProjectQuestions,
        @JsonProperty("current_followup_count") Integer currentFollowupCount,
        @JsonProperty("project_questions_pool") List<Object> projectQuestionsPool,
        @JsonProperty("technical_questions_pool") List<Object> technicalQuestionsPool) {
}
