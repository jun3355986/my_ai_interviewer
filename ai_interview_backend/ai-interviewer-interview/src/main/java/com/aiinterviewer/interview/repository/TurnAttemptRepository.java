package com.aiinterviewer.interview.repository;

import com.aiinterviewer.interview.entity.InterviewTurnAttempt;
import com.aiinterviewer.interview.service.TurnAttemptConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TurnAttemptRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<BranchState> findBranch(String branchId) {
        return jdbcTemplate.query("""
                        SELECT id, lineage_id, user_id, stage, status, branch_version,
                               python_session_id, candidate_name, resume_content, job_requirements,
                               last_business_activity_at
                        FROM t_interview_session
                        WHERE id = ?
                        """, BRANCH_ROW_MAPPER, branchId)
                .stream()
                .findFirst();
    }

    public Optional<BranchState> lockBranch(String branchId) {
        return jdbcTemplate.query("""
                        SELECT id, lineage_id, user_id, stage, status, branch_version,
                               python_session_id, candidate_name, resume_content, job_requirements,
                               last_business_activity_at
                        FROM t_interview_session
                        WHERE id = ?
                        FOR UPDATE
                        """, BRANCH_ROW_MAPPER, branchId)
                .stream()
                .findFirst();
    }

    public Long findTailMessageId(String branchId) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT COALESCE(
                    (
                        SELECT message.id
                        FROM t_interview_message message
                        WHERE message.session_id = session.id
                          AND message.delivery_status = 'completed'
                        ORDER BY message.sequence DESC, message.id DESC
                        LIMIT 1
                    ),
                    session.fork_point_message_id
                ) AS tail_message_id
                FROM t_interview_session session
                WHERE session.id = ?
                """, (rs, rowNum) -> {
            long id = rs.getLong(1);
            return rs.wasNull() ? null : id;
        }, branchId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public String findMessageContent(Long messageId) {
        if (messageId == null) {
            return null;
        }
        List<String> contents = jdbcTemplate.query(
                "SELECT content FROM t_interview_message WHERE id = ?",
                (rs, rowNum) -> rs.getString(1),
                messageId);
        return contents.isEmpty() ? null : contents.getFirst();
    }

    public Optional<InterviewTurnAttempt> findById(String turnId) {
        return jdbcTemplate.query("""
                        SELECT * FROM t_interview_turn_attempt WHERE id = ?
                        """, ATTEMPT_ROW_MAPPER, turnId)
                .stream()
                .findFirst();
    }

    public Optional<InterviewTurnAttempt> lockById(String turnId) {
        return jdbcTemplate.query("""
                        SELECT * FROM t_interview_turn_attempt WHERE id = ? FOR UPDATE
                        """, ATTEMPT_ROW_MAPPER, turnId)
                .stream()
                .findFirst();
    }

    public Optional<InterviewTurnAttempt> findProcessingByLineage(String lineageId) {
        return jdbcTemplate.query("""
                        SELECT * FROM t_interview_turn_attempt
                        WHERE lineage_id = ? AND status IN ('PROCESSING', 'CANCEL_REQUESTED')
                        ORDER BY created_at
                        LIMIT 1
                        """, ATTEMPT_ROW_MAPPER, lineageId)
                .stream()
                .findFirst();
    }

    public boolean lineageOwnedBy(String lineageId, Long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM t_interview_lineage
                WHERE id = ? AND user_id = ?
                """, Integer.class, lineageId, userId);
        return count != null && count == 1;
    }

    public boolean lockOwnedLineage(String lineageId, Long userId) {
        return !jdbcTemplate.query("""
                        SELECT id
                        FROM t_interview_lineage
                        WHERE id = ? AND user_id = ?
                        FOR UPDATE
                        """,
                        (rs, rowNum) -> rs.getString(1),
                        lineageId,
                        userId)
                .isEmpty();
    }

    public boolean insert(InterviewTurnAttempt attempt) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO t_interview_turn_attempt(
                    id, lineage_id, session_id, owner_user_id, expected_branch_version,
                    expected_tail_message_id, candidate_answer, status, retry_of_id,
                    agent_run_id, request_id, username, created_at, processing_started_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                attempt.getId(),
                attempt.getLineageId(),
                attempt.getSessionId(),
                attempt.getOwnerUserId(),
                attempt.getExpectedBranchVersion(),
                attempt.getExpectedTailMessageId(),
                attempt.getCandidateAnswer(),
                attempt.getStatus(),
                attempt.getRetryOfId(),
                attempt.getAgentRunId(),
                attempt.getRequestId(),
                attempt.getUsername(),
                attempt.getCreatedAt(),
                attempt.getProcessingStartedAt(),
                attempt.getUpdatedAt());
        return inserted == 1;
    }

    public void attachForkContext(
            String turnId,
            String sourceSessionId,
            Long triggerMessageId,
            Long forkPointMessageId,
            Long expectedSourceVersion,
            Long expectedSourceTailMessageId) {
        int updated = jdbcTemplate.update("""
                UPDATE t_interview_turn_attempt
                SET fork_source_session_id = ?,
                    fork_trigger_message_id = ?,
                    fork_point_message_id = ?,
                    fork_expected_source_version = ?,
                    fork_expected_source_tail_message_id = ?
                WHERE id = ?
                  AND fork_source_session_id IS NULL
                """,
                sourceSessionId,
                triggerMessageId,
                forkPointMessageId,
                expectedSourceVersion,
                expectedSourceTailMessageId,
                turnId);
        if (updated != 1) {
            throw new TurnAttemptConflictException("IDEMPOTENCY_PAYLOAD_MISMATCH");
        }
    }

    public int requestCancellation(String turnId) {
        return jdbcTemplate.update("""
                UPDATE t_interview_turn_attempt
                SET status = 'CANCEL_REQUESTED', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PROCESSING'
                """, turnId);
    }

    public int markCancelled(String turnId) {
        return jdbcTemplate.update("""
                UPDATE t_interview_turn_attempt
                SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'CANCEL_REQUESTED'
                """, turnId);
    }

    public int markFailed(String turnId, String errorCode, String diagnosticRef) {
        return jdbcTemplate.update("""
                UPDATE t_interview_turn_attempt
                SET status = 'FAILED', error_code = ?, diagnostic_ref = ?,
                    failed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PROCESSING'
                """, errorCode, diagnosticRef, turnId);
    }

    public int markInterrupted(String turnId, String errorCode) {
        return jdbcTemplate.update("""
                UPDATE t_interview_turn_attempt
                SET status = 'INTERRUPTED', error_code = ?, failed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PROCESSING'
                """, errorCode, turnId);
    }

    public int discard(String turnId) {
        return jdbcTemplate.update("""
                UPDATE t_interview_turn_attempt
                SET status = 'DISCARDED', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('FAILED', 'INTERRUPTED', 'CANCELLED')
                """, turnId);
    }

    public List<InterviewTurnAttempt> findRecoverableByBranch(String branchId) {
        return jdbcTemplate.query("""
                SELECT * FROM t_interview_turn_attempt
                WHERE session_id = ?
                  AND status IN ('PROCESSING', 'FAILED', 'INTERRUPTED', 'CANCEL_REQUESTED', 'CANCELLED')
                ORDER BY created_at DESC
                """, ATTEMPT_ROW_MAPPER, branchId);
    }

    public List<String> recoverStaleProcessing(LocalDateTime cutoff) {
        return jdbcTemplate.query("""
                UPDATE t_interview_turn_attempt
                SET status = 'INTERRUPTED', error_code = 'SERVICE_RESTARTED',
                    failed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE status IN ('PROCESSING', 'CANCEL_REQUESTED') AND updated_at < ?
                RETURNING id
                """, (rs, rowNumber) -> rs.getString("id"), cutoff);
    }

    public void markCompleted(String turnId, LocalDateTime completedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE t_interview_turn_attempt
                SET status = 'COMPLETED', completed_at = ?, updated_at = ?,
                    error_code = NULL, diagnostic_ref = NULL
                WHERE id = ? AND status = 'PROCESSING'
                """, completedAt, completedAt, turnId);
        if (updated != 1) {
            throw new IllegalStateException("Attempt was no longer PROCESSING");
        }
    }

    private static final RowMapper<BranchState> BRANCH_ROW_MAPPER = (rs, rowNum) ->
            new BranchState(
                    rs.getString("id"),
                    rs.getString("lineage_id"),
                    rs.getLong("user_id"),
                    rs.getString("stage"),
                    rs.getInt("status"),
                    rs.getLong("branch_version"),
                    rs.getString("python_session_id"),
                    rs.getString("candidate_name"),
                    rs.getString("resume_content"),
                    rs.getString("job_requirements"),
                    rs.getTimestamp("last_business_activity_at") == null
                            ? null
                            : rs.getTimestamp("last_business_activity_at").toLocalDateTime());

    private static final RowMapper<InterviewTurnAttempt> ATTEMPT_ROW_MAPPER =
            (rs, rowNum) -> mapAttempt(rs);

    private static InterviewTurnAttempt mapAttempt(ResultSet rs) throws SQLException {
        InterviewTurnAttempt attempt = new InterviewTurnAttempt();
        attempt.setId(rs.getString("id"));
        attempt.setLineageId(rs.getString("lineage_id"));
        attempt.setSessionId(rs.getString("session_id"));
        attempt.setOwnerUserId(rs.getLong("owner_user_id"));
        attempt.setExpectedBranchVersion(rs.getLong("expected_branch_version"));
        long tail = rs.getLong("expected_tail_message_id");
        attempt.setExpectedTailMessageId(rs.wasNull() ? null : tail);
        attempt.setCandidateAnswer(rs.getString("candidate_answer"));
        attempt.setStatus(rs.getString("status"));
        attempt.setRetryOfId(rs.getString("retry_of_id"));
        attempt.setAgentRunId(rs.getString("agent_run_id"));
        attempt.setRequestId(rs.getString("request_id"));
        attempt.setUsername(rs.getString("username"));
        attempt.setForkSourceSessionId(rs.getString("fork_source_session_id"));
        attempt.setForkTriggerMessageId(nullableLong(rs, "fork_trigger_message_id"));
        attempt.setForkPointMessageId(nullableLong(rs, "fork_point_message_id"));
        attempt.setForkExpectedSourceVersion(nullableLong(rs, "fork_expected_source_version"));
        attempt.setForkExpectedSourceTailMessageId(nullableLong(
                rs, "fork_expected_source_tail_message_id"));
        attempt.setErrorCode(rs.getString("error_code"));
        attempt.setDiagnosticRef(rs.getString("diagnostic_ref"));
        attempt.setCreatedAt(toLocalDateTime(rs, "created_at"));
        attempt.setProcessingStartedAt(toLocalDateTime(rs, "processing_started_at"));
        attempt.setCompletedAt(toLocalDateTime(rs, "completed_at"));
        attempt.setFailedAt(toLocalDateTime(rs, "failed_at"));
        attempt.setCancelledAt(toLocalDateTime(rs, "cancelled_at"));
        attempt.setUpdatedAt(toLocalDateTime(rs, "updated_at"));
        return attempt;
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record BranchState(
            String id,
            String lineageId,
            Long userId,
            String stage,
            Integer status,
            Long branchVersion,
            String pythonSessionId,
            String candidateName,
            String resumeContent,
            String jobRequirements,
            LocalDateTime lastBusinessActivityAt) {
    }
}
