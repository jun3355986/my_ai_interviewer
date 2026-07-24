package com.aiinterviewer.interview.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BranchSnapshotAssessment(
        Long id,
        @JsonProperty("owning_branch_id") String owningBranchId,
        @JsonProperty("turn_id") String turnId,
        @JsonProperty("question_message_id") Long questionMessageId,
        @JsonProperty("answer_message_id") Long answerMessageId,
        @JsonProperty("question_type") String questionType,
        String question,
        String answer,
        Integer score,
        String feedback,
        @JsonProperty("is_followup") Boolean isFollowup,
        @JsonProperty("path_order") Integer pathOrder) {
}
