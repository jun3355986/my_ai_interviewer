package com.aiinterviewer.interview.service;

import cn.hutool.core.util.IdUtil;
import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.interview.dto.CreateTurnAttemptRequest;
import com.aiinterviewer.interview.dto.RetryTurnAttemptRequest;
import com.aiinterviewer.interview.dto.TurnAttemptDTO;
import com.aiinterviewer.interview.dto.TurnAttemptEventDTO;
import com.aiinterviewer.interview.entity.InterviewTurnAttempt;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import com.aiinterviewer.interview.repository.TurnAttemptRepository.BranchState;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class TurnAttemptService {

    private final TurnAttemptRepository repository;
    private final TurnAttemptWorker worker;
    private final TurnAttemptEventPublisher eventPublisher;

    @Transactional
    public TurnAttemptDTO create(
            String branchId,
            Long userId,
            CreateTurnAttemptRequest request) {
        return create(branchId, userId, null, request);
    }

    @Transactional
    public TurnAttemptDTO create(
            String branchId,
            Long userId,
            String username,
            CreateTurnAttemptRequest request) {
        return createInternal(branchId, userId, username, request, null);
    }

    public TurnAttemptDTO get(String turnId, Long userId) {
        return toDto(requireOwnedAttempt(turnId, userId));
    }

    public Flux<TurnAttemptEventDTO> events(String turnId, Long userId) {
        requireOwnedAttempt(turnId, userId);
        return Flux.defer(() -> {
            InterviewTurnAttempt initial = requireOwnedAttempt(turnId, userId);
            if (isTerminal(initial.getStatus())) {
                return Flux.just(snapshot(initial));
            }

            TurnAttemptEventPublisher.EventSubscription live = eventPublisher.subscribe(turnId);
            InterviewTurnAttempt rechecked = requireOwnedAttempt(turnId, userId);
            if (isTerminal(rechecked.getStatus())) {
                live.close();
                return Flux.just(snapshot(rechecked));
            }
            Flux<TurnAttemptEventDTO> authorizedLive = live.events().handle((event, sink) -> {
                try {
                    requireOwnedAttempt(turnId, userId);
                    sink.next(event);
                } catch (BusinessException ownershipChanged) {
                    live.close();
                    sink.error(ownershipChanged);
                }
            });
            return Flux.concat(Flux.just(snapshot(rechecked)), authorizedLive)
                    .doFinally(signal -> live.close());
        });
    }

    @Transactional
    public TurnAttemptDTO retry(
            String originalTurnId,
            Long userId,
            RetryTurnAttemptRequest request) {
        return retry(originalTurnId, userId, null, request);
    }

    @Transactional
    public TurnAttemptDTO retry(
            String originalTurnId,
            Long userId,
            String username,
            RetryTurnAttemptRequest request) {
        InterviewTurnAttempt original = requireOwnedAttempt(originalTurnId, userId);
        if (!List.of("FAILED", "INTERRUPTED", "CANCELLED").contains(original.getStatus())) {
            throw new TurnAttemptConflictException("ATTEMPT_NOT_RETRYABLE");
        }
        CreateTurnAttemptRequest create = new CreateTurnAttemptRequest();
        create.setTurnId(request.getTurnId());
        create.setCandidateAnswer(request.getCandidateAnswer());
        create.setExpectedBranchVersion(request.getExpectedBranchVersion());
        create.setExpectedTailMessageId(request.getExpectedTailMessageId());
        String effectiveUsername = StringUtils.hasText(username)
                ? username.trim()
                : original.getUsername();
        return createInternal(
                original.getSessionId(),
                userId,
                effectiveUsername,
                create,
                originalTurnId);
    }

    @Transactional
    public TurnAttemptDTO cancel(String turnId, Long userId) {
        InterviewTurnAttempt attempt = requireOwnedAttempt(turnId, userId);
        if ("PROCESSING".equals(attempt.getStatus())) {
            if (repository.requestCancellation(turnId) == 1) {
                cancelWorkerAfterCommit(turnId);
            }
        } else if (!List.of("CANCEL_REQUESTED", "CANCELLED").contains(attempt.getStatus())) {
            throw new TurnAttemptConflictException("ATTEMPT_NOT_CANCELLABLE");
        }
        return toDto(repository.findById(turnId).orElseThrow());
    }

    @Transactional
    public TurnAttemptDTO discard(String turnId, Long userId) {
        InterviewTurnAttempt attempt = requireOwnedAttempt(turnId, userId);
        if (repository.discard(turnId) != 1 && !"DISCARDED".equals(attempt.getStatus())) {
            throw new TurnAttemptConflictException("ATTEMPT_NOT_DISCARDABLE");
        }
        eventPublisher.publishTerminal(turnId, "discarded", "DISCARDED");
        return toDto(repository.findById(turnId).orElseThrow());
    }

    public List<TurnAttemptDTO> listRecoverable(String branchId, Long userId) {
        requireOwnedBranch(branchId, userId);
        return repository.findRecoverableByBranch(branchId).stream().map(this::toDto).toList();
    }

    @Transactional
    public int recoverStaleProcessing(Duration staleAfter) {
        if (staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalArgumentException("staleAfter must be positive");
        }
        List<String> recovered = repository.recoverStaleProcessing(
                LocalDateTime.now().minus(staleAfter));
        Runnable publishTerminal = () -> recovered.forEach(turnId ->
                eventPublisher.publishTerminal(turnId, "interrupted", "INTERRUPTED"));
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishTerminal.run();
                }
            });
        } else {
            publishTerminal.run();
        }
        return recovered.size();
    }

    private TurnAttemptDTO createInternal(
            String branchId,
            Long userId,
            String username,
            CreateTurnAttemptRequest request,
            String retryOfTurnId) {
        validateRequest(request);
        InterviewTurnAttempt existing = repository.findById(request.getTurnId()).orElse(null);
        if (existing != null) {
            requireOwnedAttempt(existing, userId);
            if (samePayload(existing, branchId, request, retryOfTurnId)) {
                return toDto(existing);
            }
            throw new TurnAttemptConflictException("IDEMPOTENCY_PAYLOAD_MISMATCH");
        }

        BranchState branch = requireOwnedBranch(branchId, userId);
        if (!Integer.valueOf(1).equals(branch.status())) {
            throw new TurnAttemptConflictException("BRANCH_NOT_ACTIVE");
        }
        if (!Objects.equals(branch.branchVersion(), request.getExpectedBranchVersion())) {
            throw new TurnAttemptConflictException("BRANCH_VERSION_CONFLICT");
        }
        Long actualTail = repository.findTailMessageId(branchId);
        if (!Objects.equals(actualTail, request.getExpectedTailMessageId())) {
            throw new TurnAttemptConflictException("BRANCH_TAIL_CONFLICT");
        }
        repository.findProcessingByLineage(branch.lineageId()).ifPresent(active -> {
            throw new TurnAttemptConflictException("LINEAGE_PROCESSING_CONFLICT:" + active.getId());
        });

        LocalDateTime now = LocalDateTime.now();
        InterviewTurnAttempt attempt = new InterviewTurnAttempt();
        attempt.setId(request.getTurnId());
        attempt.setLineageId(branch.lineageId());
        attempt.setSessionId(branchId);
        attempt.setOwnerUserId(userId);
        attempt.setExpectedBranchVersion(request.getExpectedBranchVersion());
        attempt.setExpectedTailMessageId(request.getExpectedTailMessageId());
        attempt.setCandidateAnswer(request.getCandidateAnswer());
        attempt.setStatus("PROCESSING");
        attempt.setRetryOfId(retryOfTurnId);
        attempt.setAgentRunId(IdUtil.fastSimpleUUID());
        attempt.setRequestId(IdUtil.fastSimpleUUID());
        attempt.setUsername(StringUtils.hasText(username) ? username.trim() : null);
        attempt.setCreatedAt(now);
        attempt.setProcessingStartedAt(now);
        attempt.setUpdatedAt(now);

        try {
            if (!repository.insert(attempt)) {
                InterviewTurnAttempt concurrent = repository.findById(request.getTurnId())
                        .orElseThrow(() -> new TurnAttemptConflictException("IDEMPOTENCY_CONFLICT"));
                requireOwnedAttempt(concurrent, userId);
                if (samePayload(concurrent, branchId, request, retryOfTurnId)) {
                    return toDto(concurrent);
                }
                throw new TurnAttemptConflictException("IDEMPOTENCY_PAYLOAD_MISMATCH");
            }
        } catch (DataIntegrityViolationException conflict) {
            throw new TurnAttemptConflictException("LINEAGE_PROCESSING_CONFLICT");
        }

        scheduleAfterCommit(attempt.getId());
        eventPublisher.publish(attempt.getId(), "created", "PROCESSING");
        return toDto(attempt);
    }

    private void scheduleAfterCommit(String turnId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    worker.schedule(turnId);
                }
            });
            return;
        }
        worker.schedule(turnId);
    }

    private void cancelWorkerAfterCommit(String turnId) {
        Runnable cancelWorker = () -> {
            eventPublisher.publish(turnId, "cancel_requested", "CANCEL_REQUESTED");
            worker.cancel(turnId);
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cancelWorker.run();
                }
            });
            return;
        }
        cancelWorker.run();
    }

    private BranchState requireOwnedBranch(String branchId, Long userId) {
        BranchState branch = repository.findBranch(branchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!Objects.equals(branch.userId(), userId)
                || !repository.lineageOwnedBy(branch.lineageId(), userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该面试分支");
        }
        return branch;
    }

    private InterviewTurnAttempt requireOwnedAttempt(String turnId, Long userId) {
        InterviewTurnAttempt attempt = repository.findById(turnId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Turn Attempt不存在"));
        requireOwnedAttempt(attempt, userId);
        return attempt;
    }

    private void requireOwnedAttempt(InterviewTurnAttempt attempt, Long userId) {
        BranchState branch = repository.findBranch(attempt.getSessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!Objects.equals(attempt.getOwnerUserId(), userId)
                || !Objects.equals(branch.userId(), userId)
                || !repository.lineageOwnedBy(attempt.getLineageId(), userId)
                || !Objects.equals(branch.lineageId(), attempt.getLineageId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该Turn Attempt");
        }
    }

    private boolean samePayload(
            InterviewTurnAttempt attempt,
            String branchId,
            CreateTurnAttemptRequest request,
            String retryOfTurnId) {
        return Objects.equals(attempt.getSessionId(), branchId)
                && Objects.equals(attempt.getExpectedBranchVersion(), request.getExpectedBranchVersion())
                && Objects.equals(attempt.getExpectedTailMessageId(), request.getExpectedTailMessageId())
                && Objects.equals(attempt.getCandidateAnswer(), request.getCandidateAnswer())
                && Objects.equals(attempt.getRetryOfId(), retryOfTurnId);
    }

    private void validateRequest(CreateTurnAttemptRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getTurnId())
                || request.getTurnId().length() > 50
                || !StringUtils.hasText(request.getCandidateAnswer())
                || request.getExpectedBranchVersion() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Turn Attempt请求参数无效");
        }
    }

    private TurnAttemptDTO toDto(InterviewTurnAttempt attempt) {
        TurnAttemptDTO dto = new TurnAttemptDTO();
        dto.setTurnId(attempt.getId());
        dto.setLineageId(attempt.getLineageId());
        dto.setBranchId(attempt.getSessionId());
        dto.setExpectedBranchVersion(attempt.getExpectedBranchVersion());
        dto.setExpectedTailMessageId(attempt.getExpectedTailMessageId());
        dto.setCandidateAnswer(attempt.getCandidateAnswer());
        dto.setStatus(attempt.getStatus());
        dto.setRetryOfTurnId(attempt.getRetryOfId());
        dto.setErrorCode(attempt.getErrorCode());
        dto.setCreatedAt(attempt.getCreatedAt());
        dto.setCompletedAt(attempt.getCompletedAt());
        dto.setFailedAt(attempt.getFailedAt());
        dto.setCancelledAt(attempt.getCancelledAt());
        dto.setUpdatedAt(attempt.getUpdatedAt());
        return dto;
    }

    private TurnAttemptEventDTO snapshot(InterviewTurnAttempt attempt) {
        return new TurnAttemptEventDTO(
                attempt.getId(),
                0L,
                "snapshot",
                attempt.getStatus(),
                LocalDateTime.now());
    }

    private boolean isTerminal(String status) {
        return List.of("COMPLETED", "FAILED", "INTERRUPTED", "CANCELLED", "DISCARDED")
                .contains(status);
    }
}
