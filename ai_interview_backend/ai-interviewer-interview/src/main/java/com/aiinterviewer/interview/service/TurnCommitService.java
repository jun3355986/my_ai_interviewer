package com.aiinterviewer.interview.service;

import com.aiinterviewer.interview.entity.InterviewTurnAttempt;
import com.aiinterviewer.interview.model.AuthoritativeTurnState;
import com.aiinterviewer.interview.model.ForkStateSnapshot;
import com.aiinterviewer.interview.model.TurnModelResult;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import com.aiinterviewer.interview.repository.TurnAttemptRepository.BranchState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TurnCommitService {

    private final TurnAttemptRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ComposedAssessmentService composedAssessmentService;

    @Transactional
    public void commit(String turnId, TurnModelResult result) {
        InterviewTurnAttempt hint = repository.findById(turnId)
                .orElseThrow(() -> new TurnCommitRejectedException("ATTEMPT_NOT_FOUND"));
        if (!repository.lockOwnedLineage(hint.getLineageId(), hint.getOwnerUserId())) {
            throw new TurnCommitRejectedException("OWNERSHIP_CHANGED");
        }
        InterviewTurnAttempt attempt = repository.lockById(turnId)
                .orElseThrow(() -> new TurnCommitRejectedException("ATTEMPT_NOT_FOUND"));
        if (!Objects.equals(attempt.getLineageId(), hint.getLineageId())
                || !Objects.equals(attempt.getOwnerUserId(), hint.getOwnerUserId())) {
            throw new TurnCommitRejectedException("OWNERSHIP_CHANGED");
        }
        if (!"PROCESSING".equals(attempt.getStatus())) {
            throw new TurnCommitRejectedException("ATTEMPT_NOT_PROCESSING");
        }

        BranchState branch = repository.lockBranch(attempt.getSessionId())
                .orElseThrow(() -> new TurnCommitRejectedException("BRANCH_NOT_FOUND"));
        if (!Integer.valueOf(1).equals(branch.status())) {
            throw new TurnCommitRejectedException("BRANCH_NOT_ACTIVE");
        }
        if (!attempt.getLineageId().equals(branch.lineageId())) {
            throw new TurnCommitRejectedException("OWNERSHIP_CHANGED");
        }
        if (!Objects.equals(attempt.getOwnerUserId(), branch.userId())) {
            throw new TurnCommitRejectedException("OWNERSHIP_CHANGED");
        }
        if (!Objects.equals(attempt.getExpectedBranchVersion(), branch.branchVersion())) {
            throw new TurnCommitRejectedException("BRANCH_VERSION_CONFLICT");
        }
        Long currentTail = repository.findTailMessageId(branch.id());
        if (!Objects.equals(attempt.getExpectedTailMessageId(), currentTail)) {
            throw new TurnCommitRejectedException("BRANCH_TAIL_CONFLICT");
        }
        if (!StringUtils.hasText(result.aiMessage())) {
            throw new IllegalArgumentException("Model result has no complete AI message");
        }

        LocalDateTime committedAt = LocalDateTime.now();
        int sequence = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sequence), 0) FROM t_interview_message WHERE session_id = ?",
                Integer.class,
                branch.id());
        boolean openingTrigger = "opening".equals(branch.stage())
                && attempt.getExpectedTailMessageId() == null
                && StartAttemptService.OPENING_TRIGGER.equals(attempt.getCandidateAnswer());
        long answerMessageId = insertMessage(
                branch.id(),
                "human",
                attempt.getCandidateAnswer(),
                branch.stage(),
                sequence + 1,
                turnId,
                openingTrigger ? "system_trigger" : "candidate_answer",
                false,
                committedAt,
                null);
        Map<String, Object> aiMetadata = result.metadata();
        if (result.authoritativeState() != null) {
            aiMetadata = new java.util.LinkedHashMap<>(
                    result.metadata() == null ? Map.of() : result.metadata());
            aiMetadata.put(
                    ForkStateSnapshot.METADATA_KEY,
                    ForkStateSnapshot.from(result.authoritativeState()).toMetadataValue());
        }
        insertMessage(
                branch.id(),
                "ai",
                result.aiMessage(),
                StringUtils.hasText(result.nextStage()) ? result.nextStage() : branch.stage(),
                sequence + 2,
                turnId,
                result.interviewComplete() ? "final_summary" : "ai_question",
                !result.interviewComplete(),
                committedAt,
                serializeMetadata(aiMetadata));

        if (result.score() != null) {
            int questionIndex = composedAssessmentService.compose(
                            branch.id(),
                            attempt.getOwnerUserId())
                    .size() + 1;
            String question = repository.findMessageContent(attempt.getExpectedTailMessageId());
            if (!StringUtils.hasText(question)) {
                question = "未记录问题";
            }
            jdbcTemplate.update("""
                    INSERT INTO t_score_record(
                        session_id, question_index, question_type, question, answer,
                        score, feedback, is_followup, turn_id, question_message_id,
                        answer_message_id, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, false, ?, ?, ?, ?)
                    """,
                    branch.id(),
                    questionIndex,
                    branch.stage(),
                    question,
                    attempt.getCandidateAnswer(),
                    result.score(),
                    result.feedback(),
                    turnId,
                    attempt.getExpectedTailMessageId(),
                    answerMessageId,
                    committedAt);
        }

        String nextStage = StringUtils.hasText(result.nextStage())
                ? result.nextStage()
                : branch.stage();
        AuthoritativeTurnState state = result.authoritativeState();
        if (state != null) {
            commitAuthoritativeState(branch, result, state, committedAt);
        } else {
            commitLegacyState(branch, result, nextStage, committedAt);
        }
        jdbcTemplate.update("""
                UPDATE t_interview_lineage
                SET last_business_activity_at = ?, updated_at = ?
                WHERE id = ? AND user_id = ?
                """, committedAt, committedAt, branch.lineageId(), branch.userId());
        repository.markCompleted(turnId, committedAt);
    }

    private void commitAuthoritativeState(
            BranchState branch,
            TurnModelResult result,
            AuthoritativeTurnState state,
            LocalDateTime committedAt) {
        if (!StringUtils.hasText(state.currentStage())
                || !Objects.equals(result.nextStage(), state.currentStage())
                || state.branchStatus() == null
                || !java.util.List.of(1, 2).contains(state.branchStatus())
                || state.projectQuestionsCount() == null
                || state.projectQuestionsCount() < 0
                || state.targetProjectQuestions() == null
                || state.targetProjectQuestions() < 0
                || state.currentFollowupCount() == null
                || state.currentFollowupCount() < 0
                || state.projectQuestionsPool() == null
                || state.technicalQuestionsPool() == null
                || result.interviewComplete() != (state.branchStatus() == 2)
                || result.interviewComplete() != "concluded".equals(state.currentStage())) {
            throw new IllegalArgumentException("Model authoritative post-turn state is invalid");
        }
        jdbcTemplate.update("""
                UPDATE t_interview_session
                SET stage = ?,
                    status = ?,
                    finished_at = CASE WHEN ? THEN ? ELSE finished_at END,
                    project_questions_count = ?,
                    target_project_questions = ?,
                    current_followup_count = ?,
                    project_questions_pool = CAST(? AS jsonb),
                    technical_questions_pool = CAST(? AS jsonb),
                    python_session_id = COALESCE(?, python_session_id),
                    branch_version = branch_version + 1,
                    last_business_activity_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                state.currentStage(),
                state.branchStatus(),
                result.interviewComplete(),
                committedAt,
                state.projectQuestionsCount(),
                state.targetProjectQuestions(),
                state.currentFollowupCount(),
                serializeJson(state.projectQuestionsPool()),
                serializeJson(state.technicalQuestionsPool()),
                result.pythonSessionId(),
                committedAt,
                committedAt,
                branch.id());
    }

    private void commitLegacyState(
            BranchState branch,
            TurnModelResult result,
            String nextStage,
            LocalDateTime committedAt) {
        int projectIncrement = result.score() != null && "project_qna".equals(branch.stage()) ? 1 : 0;
        jdbcTemplate.update("""
                UPDATE t_interview_session
                SET stage = ?,
                    status = ?,
                    finished_at = CASE WHEN ? THEN ? ELSE finished_at END,
                    project_questions_count = COALESCE(project_questions_count, 0) + ?,
                    python_session_id = COALESCE(?, python_session_id),
                    branch_version = branch_version + 1,
                    last_business_activity_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                nextStage,
                result.interviewComplete() ? 2 : branch.status(),
                result.interviewComplete(),
                committedAt,
                projectIncrement,
                result.pythonSessionId(),
                committedAt,
                committedAt,
                branch.id());
    }

    private long insertMessage(
            String branchId,
            String role,
            String content,
            String stage,
            int sequence,
            String turnId,
            String messageType,
            boolean expectsResponse,
            LocalDateTime createdAt,
            String metadataJson) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO t_interview_message(
                        session_id, role, content, stage, sequence, turn_id,
                        message_type, expects_response, delivery_status, created_at, metadata
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'completed', ?, CAST(? AS jsonb))
                    """, new String[] {"id"});
            statement.setString(1, branchId);
            statement.setString(2, role);
            statement.setString(3, content);
            statement.setString(4, stage);
            statement.setInt(5, sequence);
            statement.setString(6, turnId);
            statement.setString(7, messageType);
            statement.setBoolean(8, expectsResponse);
            statement.setObject(9, createdAt);
            statement.setString(10, metadataJson);
            return statement;
        }, keyHolder);
        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("Message insert returned no generated id");
        }
        return keyHolder.getKey().longValue();
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Model metadata is not serializable", exception);
        }
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Model state is not serializable", exception);
        }
    }
}
