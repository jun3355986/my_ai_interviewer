package com.aiinterviewer.interview.service;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.interview.dto.BranchMessageDTO;
import com.aiinterviewer.interview.dto.BranchTranscriptDTO;
import com.aiinterviewer.interview.dto.CreateForkAttemptRequest;
import com.aiinterviewer.interview.dto.CreateTurnAttemptRequest;
import com.aiinterviewer.interview.dto.ForkAttemptDTO;
import com.aiinterviewer.interview.dto.TurnAttemptDTO;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.entity.InterviewTurnAttempt;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import com.aiinterviewer.interview.model.ForkStateSnapshot;
import com.aiinterviewer.interview.repository.ForkBranchRepository;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import com.aiinterviewer.interview.repository.TurnAttemptRepository.BranchState;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DataAccessException;

@Service
@RequiredArgsConstructor
public class ForkAttemptService {

    private final InterviewHistoryService historyService;
    private final InterviewSessionMapper sessionMapper;
    private final TurnAttemptRepository attemptRepository;
    private final ForkBranchRepository branchRepository;
    private final TurnAttemptService turnAttemptService;

    @Transactional
    public ForkAttemptDTO create(
            String focusedBranchId,
            Long userId,
            String username,
            CreateForkAttemptRequest request) {
        validate(request);
        InterviewTurnAttempt existing = attemptRepository.findById(request.getTurnId()).orElse(null);
        if (existing != null) {
            return replayExisting(existing, focusedBranchId, userId, request);
        }

        BranchState focusedHint = requireOwnedBranch(focusedBranchId, userId);
        if (!branchRepository.lockOwnedLineage(focusedHint.lineageId(), userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该面试谱系");
        }
        BranchState focused = requireOwnedLockedBranch(focusedBranchId, userId);
        if (!Objects.equals(focused.lineageId(), focusedHint.lineageId())) {
            throw new TurnAttemptConflictException("LINEAGE_CHANGED");
        }
        if (!Objects.equals(focused.branchVersion(), request.getExpectedFocusedBranchVersion())) {
            throw new TurnAttemptConflictException("BRANCH_VERSION_CONFLICT");
        }
        Long actualTail = attemptRepository.findTailMessageId(focusedBranchId);
        if (!Objects.equals(actualTail, request.getExpectedFocusedTailMessageId())) {
            throw new TurnAttemptConflictException("BRANCH_TAIL_CONFLICT");
        }

        BranchTranscriptDTO transcript = historyService.getBranchTranscript(focusedBranchId, userId);
        if (!Objects.equals(transcript.getLineageId(), focused.lineageId())
                || !Objects.equals(transcript.getBranchVersion(), focused.branchVersion())) {
            throw new TurnAttemptConflictException("BRANCH_VERSION_CONFLICT");
        }
        BranchMessageDTO trigger = transcript.getMessages().stream()
                .filter(message -> Objects.equals(message.getId(), request.getTriggerMessageId()))
                .findFirst()
                .orElseGet(() -> resolveMissingTrigger(
                        request.getTriggerMessageId(),
                        focused,
                        userId));
        if (!Boolean.TRUE.equals(trigger.getForkable()) || trigger.getForkPointMessageId() == null) {
            throw new TurnAttemptConflictException("FORK_TRIGGER_NOT_FORKABLE");
        }
        BranchMessageDTO forkPoint = transcript.getMessages().stream()
                .filter(message -> Objects.equals(message.getId(), trigger.getForkPointMessageId()))
                .findFirst()
                .orElseThrow(() -> new TurnAttemptConflictException("FORK_STATE_UNAVAILABLE"));
        ForkStateSnapshot state = ForkStateSnapshot.fromMetadata(forkPoint.getMetadata())
                .orElseThrow(() -> new TurnAttemptConflictException("FORK_STATE_UNAVAILABLE"));

        InterviewTurnAttempt concurrentReplay = attemptRepository.findById(request.getTurnId())
                .orElse(null);
        if (concurrentReplay != null) {
            return replayExisting(concurrentReplay, focusedBranchId, userId, request);
        }
        attemptRepository.findProcessingByLineage(focused.lineageId()).ifPresent(active -> {
            throw new TurnAttemptConflictException("LINEAGE_PROCESSING_CONFLICT:" + active.getId());
        });

        String parentBranchId = trigger.getOwningBranchId();
        BranchState parent = requireOwnedLockedBranch(parentBranchId, userId);
        if (!Objects.equals(parent.lineageId(), focused.lineageId())) {
            throw new TurnAttemptConflictException("FORK_TRIGGER_NOT_ON_FOCUSED_PATH");
        }
        InterviewSession source = sessionMapper.selectById(parentBranchId);
        if (source == null || !Objects.equals(source.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问分叉来源");
        }

        String childId = deterministicChildId(request.getTurnId());
        InterviewSession child = childSession(
                childId,
                source,
                state,
                trigger,
                forkPoint,
                branchRepository.nextBranchNumber(focused.lineageId()));
        try {
            boolean inserted = branchRepository.insertChild(child);
            if (!inserted) {
                InterviewTurnAttempt concurrent = attemptRepository.findById(request.getTurnId())
                        .orElseThrow(() -> new TurnAttemptConflictException("IDEMPOTENCY_CONFLICT"));
                return replayExisting(concurrent, focusedBranchId, userId, request);
            }

            CreateTurnAttemptRequest attemptRequest = new CreateTurnAttemptRequest();
            attemptRequest.setTurnId(request.getTurnId());
            attemptRequest.setCandidateAnswer(request.getCandidateAnswer());
            attemptRequest.setExpectedBranchVersion(1L);
            attemptRequest.setExpectedTailMessageId(forkPoint.getId());
            TurnAttemptDTO attempt = turnAttemptService.create(
                    childId,
                    userId,
                    username,
                    attemptRequest);
            attemptRepository.attachForkContext(
                    request.getTurnId(),
                    focusedBranchId,
                    trigger.getId(),
                    forkPoint.getId(),
                    request.getExpectedFocusedBranchVersion(),
                    request.getExpectedFocusedTailMessageId());
            return new ForkAttemptDTO(childId, attempt);
        } catch (DataAccessException storageFailure) {
            throw new TurnAttemptConflictException("FORK_CREATION_FAILED");
        }
    }

    private ForkAttemptDTO replayExisting(
            InterviewTurnAttempt existing,
            String focusedBranchId,
            Long userId,
            CreateForkAttemptRequest request) {
        if (!Objects.equals(existing.getOwnerUserId(), userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该Turn Attempt");
        }
        if (!Objects.equals(existing.getForkSourceSessionId(), focusedBranchId)
                || !Objects.equals(existing.getForkTriggerMessageId(), request.getTriggerMessageId())
                || !Objects.equals(existing.getForkExpectedSourceVersion(), request.getExpectedFocusedBranchVersion())
                || !Objects.equals(existing.getForkExpectedSourceTailMessageId(), request.getExpectedFocusedTailMessageId())
                || !Objects.equals(existing.getCandidateAnswer(), request.getCandidateAnswer())) {
            throw new TurnAttemptConflictException("IDEMPOTENCY_PAYLOAD_MISMATCH");
        }
        return new ForkAttemptDTO(
                existing.getSessionId(),
                turnAttemptService.get(existing.getId(), userId));
    }

    private BranchState requireOwnedLockedBranch(String branchId, Long userId) {
        BranchState branch = attemptRepository.lockBranch(branchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!Objects.equals(branch.userId(), userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该面试分支");
        }
        return branch;
    }

    private BranchState requireOwnedBranch(String branchId, Long userId) {
        BranchState branch = attemptRepository.findBranch(branchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!Objects.equals(branch.userId(), userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该面试分支");
        }
        return branch;
    }

    private BranchMessageDTO resolveMissingTrigger(
            Long triggerMessageId,
            BranchState focused,
            Long userId) {
        ForkBranchRepository.MessageContext context = branchRepository
                .findMessageContext(triggerMessageId)
                .orElseThrow(() -> new TurnAttemptConflictException(
                        "FORK_TRIGGER_NOT_ON_FOCUSED_PATH"));
        if (!Objects.equals(context.userId(), userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问分叉消息");
        }
        if (Objects.equals(context.lineageId(), focused.lineageId())
                && !"completed".equals(context.deliveryStatus())) {
            throw new TurnAttemptConflictException("FORK_TRIGGER_NOT_FORKABLE");
        }
        throw new TurnAttemptConflictException("FORK_TRIGGER_NOT_ON_FOCUSED_PATH");
    }

    private InterviewSession childSession(
            String childId,
            InterviewSession source,
            ForkStateSnapshot state,
            BranchMessageDTO trigger,
            BranchMessageDTO forkPoint,
            int branchNumber) {
        LocalDateTime now = LocalDateTime.now();
        InterviewSession child = new InterviewSession();
        child.setId(childId);
        child.setUserId(source.getUserId());
        child.setResumeId(source.getResumeId());
        child.setJobId(source.getJobId());
        child.setCandidateName(source.getCandidateName());
        child.setStage(state.currentStage());
        child.setStatus(1);
        child.setResumeContent(source.getResumeContent());
        child.setJobRequirements(source.getJobRequirements());
        child.setProjectQuestionsCount(state.projectQuestionsCount());
        child.setTargetProjectQuestions(state.targetProjectQuestions());
        child.setProjectQuestionsPool(new ArrayList<>(state.projectQuestionsPool()));
        child.setTechnicalQuestionsPool(new ArrayList<>(state.technicalQuestionsPool()));
        child.setCurrentFollowupCount(state.currentFollowupCount());
        child.setPythonSessionId(null);
        child.setLineageId(source.getLineageId());
        child.setParentSessionId(trigger.getOwningBranchId());
        child.setForkPointMessageId(forkPoint.getId());
        child.setForkTriggerMessageId(trigger.getId());
        child.setBranchLabel("分支 " + branchNumber);
        child.setBranchVersion(1L);
        child.setLastBusinessActivityAt(forkPoint.getCreatedAt());
        child.setLegacyMigrated(false);
        child.setStartedAt(now);
        child.setCreatedAt(now);
        child.setUpdatedAt(now);
        return child;
    }

    private static String deterministicChildId(String turnId) {
        return UUID.nameUUIDFromBytes(("interview-fork:" + turnId)
                        .getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    private void validate(CreateForkAttemptRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getTurnId())
                || request.getTurnId().length() > 50
                || request.getTriggerMessageId() == null
                || !StringUtils.hasText(request.getCandidateAnswer())
                || request.getExpectedFocusedBranchVersion() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分叉请求参数无效");
        }
    }
}
