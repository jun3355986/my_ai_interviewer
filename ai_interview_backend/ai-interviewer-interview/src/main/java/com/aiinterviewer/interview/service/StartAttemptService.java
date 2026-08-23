package com.aiinterviewer.interview.service;

import cn.hutool.core.util.IdUtil;
import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.interview.dto.CreateStartAttemptRequest;
import com.aiinterviewer.interview.dto.StartAttemptDTO;
import com.aiinterviewer.interview.dto.TurnAttemptDTO;
import com.aiinterviewer.interview.entity.InterviewTurnAttempt;
import com.aiinterviewer.interview.repository.StartAttemptRepository;
import com.aiinterviewer.interview.repository.StartAttemptRepository.JobContext;
import com.aiinterviewer.interview.repository.StartAttemptRepository.ResumeContext;
import com.aiinterviewer.interview.repository.StartAttemptRepository.RootContext;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StartAttemptService {

    static final String OPENING_TRIGGER = "我准备好了";
    static final int DEFAULT_TARGET_PROJECT_QUESTIONS = 5;

    private final StartAttemptRepository startRepository;
    private final TurnAttemptRepository attemptRepository;
    private final TurnAttemptWorker worker;
    private final TurnAttemptEventPublisher eventPublisher;

    @Transactional
    public StartAttemptDTO create(
            Long userId,
            String username,
            CreateStartAttemptRequest request) {
        validate(userId, request);
        String rootId = rootId(request.getTurnId());
        InterviewTurnAttempt existing = attemptRepository.findById(request.getTurnId())
                .orElse(null);
        if (existing != null) {
            return replay(existing, rootId, userId, request);
        }

        ResumeContext resume = resolveResume(request.getResumeId(), userId);
        JobContext job = resolveJob(request.getJobId());
        LocalDateTime now = LocalDateTime.now();
        if (!startRepository.insertLineage(rootId, userId, now)) {
            InterviewTurnAttempt concurrent = attemptRepository.findById(request.getTurnId())
                    .orElseThrow(() -> new TurnAttemptConflictException("IDEMPOTENCY_CONFLICT"));
            return replay(concurrent, rootId, userId, request);
        }
        if (!startRepository.insertRootBranch(
                rootId,
                userId,
                request.getResumeId(),
                request.getJobId(),
                resume == null ? null : normalize(resume.candidateName()),
                resume == null ? null : resume.rawText(),
                job == null ? null : job.requirements(),
                resolveTargetProjectQuestions(),
                now)) {
            throw new TurnAttemptConflictException("IDEMPOTENCY_CONFLICT");
        }

        InterviewTurnAttempt attempt = new InterviewTurnAttempt();
        attempt.setId(request.getTurnId());
        attempt.setLineageId(rootId);
        attempt.setSessionId(rootId);
        attempt.setOwnerUserId(userId);
        attempt.setExpectedBranchVersion(1L);
        attempt.setExpectedTailMessageId(null);
        attempt.setCandidateAnswer(OPENING_TRIGGER);
        attempt.setStatus("PROCESSING");
        attempt.setAgentRunId(IdUtil.fastSimpleUUID());
        attempt.setRequestId(IdUtil.fastSimpleUUID());
        attempt.setUsername(normalize(username));
        attempt.setCreatedAt(now);
        attempt.setProcessingStartedAt(now);
        attempt.setUpdatedAt(now);
        if (!attemptRepository.insert(attempt)) {
            throw new TurnAttemptConflictException("IDEMPOTENCY_CONFLICT");
        }

        scheduleAfterCommit(attempt.getId());
        eventPublisher.publish(attempt.getId(), "created", "PROCESSING");
        return response(rootId, attempt);
    }

    private StartAttemptDTO replay(
            InterviewTurnAttempt attempt,
            String rootId,
            Long userId,
            CreateStartAttemptRequest request) {
        RootContext root = startRepository.findRoot(rootId)
                .orElseThrow(() -> new TurnAttemptConflictException("IDEMPOTENCY_PAYLOAD_MISMATCH"));
        if (!Objects.equals(attempt.getOwnerUserId(), userId)
                || !Objects.equals(root.userId(), userId)
                || !attemptRepository.lineageOwnedBy(rootId, userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该面试启动请求");
        }
        if (!Objects.equals(attempt.getLineageId(), rootId)
                || !Objects.equals(attempt.getSessionId(), rootId)
                || !Objects.equals(root.lineageId(), rootId)
                || !Objects.equals(root.resumeId(), request.getResumeId())
                || !Objects.equals(root.jobId(), request.getJobId())
                || !Objects.equals(attempt.getExpectedBranchVersion(), 1L)
                || attempt.getExpectedTailMessageId() != null
                || !Objects.equals(attempt.getCandidateAnswer(), OPENING_TRIGGER)
                || attempt.getRetryOfId() != null) {
            throw new TurnAttemptConflictException("IDEMPOTENCY_PAYLOAD_MISMATCH");
        }
        return response(rootId, attempt);
    }

    /**
     * 独立项目题目标数量：默认 5，可由 admin 侧 t_system_config 的
     * interview.project-questions.target 覆盖（共享库直读，配置缺失或非法时回退默认）。
     */
    private int resolveTargetProjectQuestions() {
        return startRepository.findIntegerSystemConfig("interview.project-questions.target")
                .filter(value -> value > 0 && value <= 20)
                .orElse(DEFAULT_TARGET_PROJECT_QUESTIONS);
    }

    private ResumeContext resolveResume(Long resumeId, Long userId) {
        if (resumeId == null) {
            return null;
        }
        return startRepository.findOwnedResume(resumeId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESUME_NOT_FOUND,
                        "简历不存在或无权访问"));
    }

    private JobContext resolveJob(Long jobId) {
        if (jobId == null) {
            return null;
        }
        return startRepository.findActiveJob(jobId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.JOB_NOT_FOUND,
                        "职位不存在或已关闭"));
    }

    private void validate(Long userId, CreateStartAttemptRequest request) {
        if (userId == null
                || request == null
                || !StringUtils.hasText(request.getTurnId())
                || request.getTurnId().length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "面试启动请求参数无效");
        }
    }

    private String rootId(String turnId) {
        UUID stable = UUID.nameUUIDFromBytes(
                ("interview-start:" + turnId).getBytes(StandardCharsets.UTF_8));
        return "start-" + stable.toString().replace("-", "");
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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

    private StartAttemptDTO response(String rootId, InterviewTurnAttempt attempt) {
        StartAttemptDTO response = new StartAttemptDTO();
        response.setLineageId(rootId);
        response.setBranchId(rootId);
        response.setAttempt(toDto(attempt));
        return response;
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
}
