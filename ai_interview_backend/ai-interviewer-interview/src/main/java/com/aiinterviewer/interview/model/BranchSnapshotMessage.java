package com.aiinterviewer.interview.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record BranchSnapshotMessage(
        Long id,
        @JsonProperty("owning_branch_id") String owningBranchId,
        String role,
        String content,
        String stage,
        @JsonProperty("message_type") String messageType,
        @JsonProperty("expects_response") Boolean expectsResponse,
        Map<String, Object> metadata,
        Integer sequence,
        @JsonProperty("path_order") Integer pathOrder) {
}
