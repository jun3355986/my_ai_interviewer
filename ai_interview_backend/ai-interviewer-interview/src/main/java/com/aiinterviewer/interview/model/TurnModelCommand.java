package com.aiinterviewer.interview.model;

public record TurnModelCommand(
        String turnId,
        String requestId,
        String agentRunId,
        String branchId,
        String lineageId,
        Long userId,
        String username,
        String pythonSessionId,
        String candidateAnswer,
        String candidateName,
        String resumeContent,
        String jobRequirements,
        String currentStage,
        BranchSnapshot branchSnapshot) {
}
