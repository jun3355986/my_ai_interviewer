package com.aiinterviewer.interview.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BranchSnapshot(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("turn_id") String turnId,
        @JsonProperty("branch_id") String branchId,
        @JsonProperty("lineage_id") String lineageId,
        @JsonProperty("branch_version") Long branchVersion,
        @JsonProperty("expected_tail_message_id") Long expectedTailMessageId,
        @JsonProperty("owner_user_id") Long ownerUserId,
        String username,
        @JsonProperty("candidate_name") String candidateName,
        @JsonProperty("resume_content") String resumeContent,
        @JsonProperty("job_requirements") String jobRequirements,
        @JsonProperty("current_stage") String currentStage,
        @JsonProperty("branch_status") Integer branchStatus,
        @JsonProperty("project_questions_count") Integer projectQuestionsCount,
        @JsonProperty("target_project_questions") Integer targetProjectQuestions,
        @JsonProperty("current_followup_count") Integer currentFollowupCount,
        @JsonProperty("project_questions_pool") List<Object> projectQuestionsPool,
        @JsonProperty("technical_questions_pool") List<Object> technicalQuestionsPool,
        List<BranchSnapshotMessage> messages,
        List<BranchSnapshotAssessment> assessments) {
}
