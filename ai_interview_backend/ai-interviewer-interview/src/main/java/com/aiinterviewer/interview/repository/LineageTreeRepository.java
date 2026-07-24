package com.aiinterviewer.interview.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LineageTreeRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<String> findOwnedRootSessionId(String lineageId, Long userId) {
        return jdbcTemplate.query("""
                        SELECT lineage.root_session_id
                        FROM t_interview_lineage lineage
                        JOIN t_interview_session root
                          ON root.id = lineage.root_session_id
                         AND root.user_id = ?
                        WHERE lineage.id = ?
                          AND lineage.user_id = ?
                          AND lineage.archived = FALSE
                        """,
                        (rs, rowNum) -> rs.getString(1),
                        userId,
                        lineageId,
                        userId)
                .stream()
                .findFirst();
    }

    public List<BranchNodeRow> findBranches(String lineageId, Long userId) {
        return jdbcTemplate.query("""
                SELECT id, parent_session_id, branch_label, fork_point_message_id,
                       fork_trigger_message_id, stage, status, branch_version,
                       last_business_activity_at, project_questions_count,
                       target_project_questions
                FROM t_interview_session
                WHERE lineage_id = ?
                  AND user_id = ?
                ORDER BY created_at, id
                """, (rs, rowNum) -> new BranchNodeRow(
                rs.getString("id"),
                rs.getString("parent_session_id"),
                rs.getString("branch_label"),
                nullableLong(rs, "fork_point_message_id"),
                nullableLong(rs, "fork_trigger_message_id"),
                rs.getString("stage"),
                rs.getInt("status"),
                rs.getLong("branch_version"),
                rs.getTimestamp("last_business_activity_at") == null
                        ? null
                        : rs.getTimestamp("last_business_activity_at").toLocalDateTime(),
                nullableInteger(rs, "project_questions_count"),
                nullableInteger(rs, "target_project_questions")), lineageId, userId);
    }

    public Optional<EvaluationSummaryRow> findEvaluation(String branchId, Long userId) {
        return jdbcTemplate.query("""
                        SELECT evaluation.overall_score, evaluation.summary
                        FROM t_evaluation evaluation
                        JOIN t_interview_session session
                          ON session.id = evaluation.session_id
                        WHERE evaluation.session_id = ?
                          AND evaluation.user_id = ?
                          AND session.user_id = ?
                        """,
                        (rs, rowNum) -> new EvaluationSummaryRow(
                                nullableInteger(rs, "overall_score"),
                                rs.getString("summary")),
                        branchId,
                        userId,
                        userId)
                .stream()
                .findFirst();
    }

    public Optional<TurnStateRow> findLatestRecoverableTurn(String branchId, Long userId) {
        return jdbcTemplate.query("""
                        SELECT id, status, error_code
                        FROM t_interview_turn_attempt
                        WHERE session_id = ?
                          AND owner_user_id = ?
                          AND status IN (
                              'PROCESSING', 'CANCEL_REQUESTED', 'FAILED',
                              'INTERRUPTED', 'CANCELLED'
                          )
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                        (rs, rowNum) -> new TurnStateRow(
                                rs.getString("id"),
                                rs.getString("status"),
                                rs.getString("error_code")),
                        branchId,
                        userId)
                .stream()
                .findFirst();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public record BranchNodeRow(
            String branchId,
            String parentBranchId,
            String branchLabel,
            Long forkPointMessageId,
            Long forkTriggerMessageId,
            String stage,
            Integer status,
            Long branchVersion,
            LocalDateTime latestBusinessActivityAt,
            Integer projectQuestionsCount,
            Integer targetProjectQuestions) {
    }

    public record EvaluationSummaryRow(Integer overallScore, String summary) {
    }

    public record TurnStateRow(String turnId, String status, String errorCode) {
    }
}
