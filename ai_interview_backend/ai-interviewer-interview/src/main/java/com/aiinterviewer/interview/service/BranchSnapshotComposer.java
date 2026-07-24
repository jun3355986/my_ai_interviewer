package com.aiinterviewer.interview.service;

import com.aiinterviewer.interview.dto.BranchMessageDTO;
import com.aiinterviewer.interview.dto.BranchTranscriptDTO;
import com.aiinterviewer.interview.dto.ComposedAssessmentDTO;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.entity.InterviewTurnAttempt;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import com.aiinterviewer.interview.mapper.ScoreRecordMapper;
import com.aiinterviewer.interview.model.BranchSnapshot;
import com.aiinterviewer.interview.model.BranchSnapshotAssessment;
import com.aiinterviewer.interview.model.BranchSnapshotMessage;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class BranchSnapshotComposer {

    public static final int SCHEMA_VERSION = 1;

    private final InterviewHistoryService historyService;
    private final InterviewSessionMapper sessionMapper;
    private final ScoreRecordMapper scoreRecordMapper;
    private final ComposedAssessmentService composedAssessmentService;
    private final TurnAttemptRepository attemptRepository;

    public BranchSnapshotComposer(
            InterviewHistoryService historyService,
            InterviewSessionMapper sessionMapper,
            ScoreRecordMapper scoreRecordMapper,
            TurnAttemptRepository attemptRepository) {
        this.historyService = historyService;
        this.sessionMapper = sessionMapper;
        this.scoreRecordMapper = scoreRecordMapper;
        this.attemptRepository = attemptRepository;
        this.composedAssessmentService = new ComposedAssessmentService(
                historyService,
                scoreRecordMapper);
    }

    public BranchSnapshot compose(
            InterviewTurnAttempt attempt,
            Long authenticatedUserId,
            String username) {
        InterviewSession branch = requireStableBranch(attempt, authenticatedUserId);
        BranchTranscriptDTO transcript = historyService.getBranchTranscript(
                attempt.getSessionId(), authenticatedUserId);
        requireStableTranscript(attempt, branch, transcript);

        List<BranchSnapshotMessage> messages = composeMessages(transcript.getMessages());
        Long actualTail = messages.isEmpty() ? null : messages.getLast().id();
        if (!Objects.equals(actualTail, attempt.getExpectedTailMessageId())) {
            throw new TurnCommitRejectedException("BRANCH_TAIL_CONFLICT");
        }
        branch = requireStableBranch(attempt, authenticatedUserId);

        return new BranchSnapshot(
                SCHEMA_VERSION,
                attempt.getId(),
                branch.getId(),
                branch.getLineageId(),
                branch.getBranchVersion(),
                attempt.getExpectedTailMessageId(),
                branch.getUserId(),
                username,
                branch.getCandidateName(),
                branch.getResumeContent(),
                branch.getJobRequirements(),
                branch.getStage(),
                branch.getStatus(),
                defaultValue(branch.getProjectQuestionsCount(), 0),
                defaultValue(branch.getTargetProjectQuestions(), 5),
                defaultValue(branch.getCurrentFollowupCount(), 0),
                safeList(branch.getProjectQuestionsPool()),
                safeList(branch.getTechnicalQuestionsPool()),
                messages,
                composeAssessments(branch.getId(), authenticatedUserId));
    }

    private InterviewSession requireStableBranch(
            InterviewTurnAttempt attempt,
            Long authenticatedUserId) {
        InterviewSession branch = sessionMapper.selectById(attempt.getSessionId());
        if (branch == null) {
            throw new TurnCommitRejectedException("BRANCH_NOT_FOUND");
        }
        if (!Objects.equals(branch.getUserId(), authenticatedUserId)) {
            throw new TurnCommitRejectedException("OWNERSHIP_CHANGED");
        }
        if (!Objects.equals(branch.getLineageId(), attempt.getLineageId())) {
            throw new TurnCommitRejectedException("LINEAGE_CHANGED");
        }
        if (!Objects.equals(branch.getStatus(), 1)) {
            throw new TurnCommitRejectedException("BRANCH_NOT_ACTIVE");
        }
        if (!Objects.equals(branch.getBranchVersion(), attempt.getExpectedBranchVersion())) {
            throw new TurnCommitRejectedException("BRANCH_VERSION_CONFLICT");
        }
        Long currentTail = attemptRepository.findTailMessageId(branch.getId());
        if (!Objects.equals(currentTail, attempt.getExpectedTailMessageId())) {
            throw new TurnCommitRejectedException("BRANCH_TAIL_CONFLICT");
        }
        return branch;
    }

    private void requireStableTranscript(
            InterviewTurnAttempt attempt,
            InterviewSession branch,
            BranchTranscriptDTO transcript) {
        if (!Objects.equals(transcript.getBranchId(), branch.getId())
                || !Objects.equals(transcript.getLineageId(), branch.getLineageId())) {
            throw new TurnCommitRejectedException("LINEAGE_CHANGED");
        }
        if (!Objects.equals(transcript.getStatus(), 1)) {
            throw new TurnCommitRejectedException("BRANCH_NOT_ACTIVE");
        }
        if (!Objects.equals(transcript.getBranchVersion(), attempt.getExpectedBranchVersion())) {
            throw new TurnCommitRejectedException("BRANCH_VERSION_CONFLICT");
        }
    }

    private List<BranchSnapshotMessage> composeMessages(List<BranchMessageDTO> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<BranchSnapshotMessage> messages = new ArrayList<>();
        for (BranchMessageDTO message : source) {
            if (!"completed".equals(message.getDeliveryStatus())) {
                continue;
            }
            messages.add(new BranchSnapshotMessage(
                    message.getId(),
                    message.getOwningBranchId(),
                    message.getRole(),
                    message.getContent(),
                    message.getStage(),
                    message.getMessageType(),
                    Boolean.TRUE.equals(message.getExpectsResponse()),
                    message.getMetadata() == null
                            ? Map.of()
                            : new LinkedHashMap<>(message.getMetadata()),
                    message.getSequence(),
                    messages.size() + 1));
        }
        return List.copyOf(messages);
    }

    private List<BranchSnapshotAssessment> composeAssessments(
            String branchId,
            Long authenticatedUserId) {
        List<BranchSnapshotAssessment> assessments = new ArrayList<>();
        for (ComposedAssessmentDTO score : composedAssessmentService.compose(
                branchId,
                authenticatedUserId)) {
            assessments.add(new BranchSnapshotAssessment(
                    score.getId(),
                    score.getOwningBranchId(),
                    score.getTurnId(),
                    score.getQuestionMessageId(),
                    score.getAnswerMessageId(),
                    score.getQuestionType(),
                    score.getQuestion(),
                    score.getAnswer(),
                    score.getScore(),
                    score.getFeedback(),
                    Boolean.TRUE.equals(score.getIsFollowup()),
                    score.getDisplayOrder()));
        }
        return List.copyOf(assessments);
    }

    private static int defaultValue(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static List<Object> safeList(List<?> value) {
        return value == null ? List.of() : new ArrayList<>(value);
    }
}
