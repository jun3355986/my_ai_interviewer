package com.aiinterviewer.interview.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StartAttemptRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<ResumeContext> findOwnedResume(Long resumeId, Long userId) {
        if (resumeId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                        SELECT id, raw_text, parsed_content ->> 'name' AS candidate_name
                        FROM t_resume
                        WHERE id = ? AND user_id = ?
                        """,
                        (rs, rowNumber) -> new ResumeContext(
                                rs.getLong("id"),
                                rs.getString("raw_text"),
                                rs.getString("candidate_name")),
                        resumeId,
                        userId)
                .stream()
                .findFirst();
    }

    public Optional<JobContext> findActiveJob(Long jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                        SELECT id, requirements
                        FROM t_job
                        WHERE id = ? AND status = 1
                        """,
                        (rs, rowNumber) -> new JobContext(
                                rs.getLong("id"),
                                rs.getString("requirements")),
                        jobId)
                .stream()
                .findFirst();
    }

    public boolean insertLineage(String rootId, Long userId, LocalDateTime now) {
        return jdbcTemplate.update("""
                INSERT INTO t_interview_lineage(
                    id, user_id, root_session_id, last_business_activity_at,
                    archived, created_at, updated_at
                ) VALUES (?, ?, ?, ?, FALSE, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, rootId, userId, rootId, now, now, now) == 1;
    }

    public boolean insertRootBranch(
            String rootId,
            Long userId,
            Long resumeId,
            Long jobId,
            String candidateName,
            String resumeContent,
            String jobRequirements,
            LocalDateTime now) {
        return jdbcTemplate.update("""
                INSERT INTO t_interview_session(
                    id, user_id, resume_id, job_id, candidate_name, stage, status,
                    resume_content, job_requirements, project_questions_count,
                    target_project_questions, project_questions_pool,
                    technical_questions_pool, current_followup_count, lineage_id,
                    branch_label, branch_version, last_business_activity_at,
                    legacy_migrated, started_at, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, 'opening', 1,
                    ?, ?, 0, 5, '[]'::jsonb, '[]'::jsonb, 0, ?,
                    '原始分支', 1, ?, FALSE, ?, ?, ?
                )
                ON CONFLICT (id) DO NOTHING
                """,
                rootId,
                userId,
                resumeId,
                jobId,
                candidateName,
                resumeContent,
                jobRequirements,
                rootId,
                now,
                now,
                now,
                now) == 1;
    }

    public Optional<RootContext> findRoot(String rootId) {
        return jdbcTemplate.query("""
                        SELECT id, lineage_id, user_id, resume_id, job_id
                        FROM t_interview_session
                        WHERE id = ?
                        """,
                        (rs, rowNumber) -> new RootContext(
                                rs.getString("id"),
                                rs.getString("lineage_id"),
                                rs.getLong("user_id"),
                                nullableLong(rs, "resume_id"),
                                nullableLong(rs, "job_id")),
                        rootId)
                .stream()
                .findFirst();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record ResumeContext(Long id, String rawText, String candidateName) {
    }

    public record JobContext(Long id, String requirements) {
    }

    public record RootContext(
            String id,
            String lineageId,
            Long userId,
            Long resumeId,
            Long jobId) {
    }
}
