package com.aiinterviewer.interview.repository;

import com.aiinterviewer.interview.entity.InterviewSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ForkBranchRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public boolean lockOwnedLineage(String lineageId, Long userId) {
        List<String> ids = jdbcTemplate.query(
                "SELECT id FROM t_interview_lineage WHERE id = ? AND user_id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getString(1),
                lineageId,
                userId);
        return !ids.isEmpty();
    }

    public int nextBranchNumber(String lineageId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM t_interview_session
                WHERE lineage_id = ?
                  AND parent_session_id IS NOT NULL
                """,
                Integer.class,
                lineageId);
        return (count == null ? 0 : count) + 1;
    }

    public Optional<MessageContext> findMessageContext(Long messageId) {
        return jdbcTemplate.query("""
                        SELECT message.id, message.session_id, session.lineage_id,
                               session.user_id, message.delivery_status
                        FROM t_interview_message message
                        JOIN t_interview_session session ON session.id = message.session_id
                        WHERE message.id = ?
                        """,
                        (rs, rowNum) -> new MessageContext(
                                rs.getLong("id"),
                                rs.getString("session_id"),
                                rs.getString("lineage_id"),
                                rs.getLong("user_id"),
                                rs.getString("delivery_status")),
                        messageId)
                .stream()
                .findFirst();
    }

    public boolean insertChild(InterviewSession child) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO t_interview_session(
                    id, user_id, resume_id, job_id, candidate_name, stage, status,
                    resume_content, job_requirements, project_questions_count,
                    target_project_questions, project_questions_pool,
                    technical_questions_pool, current_followup_count, python_session_id,
                    lineage_id, parent_session_id, fork_point_message_id,
                    fork_trigger_message_id, branch_label, branch_version,
                    last_business_activity_at, legacy_migrated, started_at,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb),
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                ON CONFLICT (id) DO NOTHING
                """,
                child.getId(),
                child.getUserId(),
                child.getResumeId(),
                child.getJobId(),
                child.getCandidateName(),
                child.getStage(),
                child.getStatus(),
                child.getResumeContent(),
                child.getJobRequirements(),
                child.getProjectQuestionsCount(),
                child.getTargetProjectQuestions(),
                json(child.getProjectQuestionsPool()),
                json(child.getTechnicalQuestionsPool()),
                child.getCurrentFollowupCount(),
                child.getPythonSessionId(),
                child.getLineageId(),
                child.getParentSessionId(),
                child.getForkPointMessageId(),
                child.getForkTriggerMessageId(),
                child.getBranchLabel(),
                child.getBranchVersion(),
                child.getLastBusinessActivityAt(),
                child.getLegacyMigrated(),
                child.getStartedAt(),
                child.getCreatedAt(),
                child.getUpdatedAt());
        return inserted == 1;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Fork state is not serializable", exception);
        }
    }

    public record MessageContext(
            Long messageId,
            String branchId,
            String lineageId,
            Long userId,
            String deliveryStatus) {
    }
}
