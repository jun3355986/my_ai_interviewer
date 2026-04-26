package com.aiinterviewer.admin.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.interview.dto.InterviewDiagnosisResponse;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminInterviewServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private AdminInterviewService adminInterviewService;

    @Test
    void interviewListSupportsUserJobStageStatusAndTimeRangeFilters() {
        seedInterviewFixtures();

        AdminInterviewService.AdminInterviewQuery userQuery = new AdminInterviewService.AdminInterviewQuery();
        userQuery.setUserId(1L);
        PageResult<AdminInterviewService.AdminInterviewListItem> userSessions =
                adminInterviewService.listInterviews(userQuery);

        AdminInterviewService.AdminInterviewQuery jobQuery = new AdminInterviewService.AdminInterviewQuery();
        jobQuery.setJobId(2L);
        PageResult<AdminInterviewService.AdminInterviewListItem> jobSessions =
                adminInterviewService.listInterviews(jobQuery);

        AdminInterviewService.AdminInterviewQuery stageQuery = new AdminInterviewService.AdminInterviewQuery();
        stageQuery.setStage("technical");
        PageResult<AdminInterviewService.AdminInterviewListItem> technicalSessions =
                adminInterviewService.listInterviews(stageQuery);

        AdminInterviewService.AdminInterviewQuery statusQuery = new AdminInterviewService.AdminInterviewQuery();
        statusQuery.setStatus(1);
        PageResult<AdminInterviewService.AdminInterviewListItem> inProgressSessions =
                adminInterviewService.listInterviews(statusQuery);

        AdminInterviewService.AdminInterviewQuery timeQuery = new AdminInterviewService.AdminInterviewQuery();
        timeQuery.setStartedFrom(LocalDateTime.of(2026, 4, 25, 10, 0));
        timeQuery.setStartedTo(LocalDateTime.of(2026, 4, 25, 12, 0));
        PageResult<AdminInterviewService.AdminInterviewListItem> morningSessions =
                adminInterviewService.listInterviews(timeQuery);

        assertThat(userSessions.getRecords())
                .extracting(AdminInterviewService.AdminInterviewListItem::getId)
                .containsExactly("session-003", "session-001");
        assertThat(jobSessions.getRecords())
                .extracting(AdminInterviewService.AdminInterviewListItem::getId)
                .containsExactly("session-002");
        assertThat(technicalSessions.getRecords())
                .extracting(AdminInterviewService.AdminInterviewListItem::getId)
                .containsExactly("session-002", "session-001");
        assertThat(inProgressSessions.getRecords())
                .extracting(AdminInterviewService.AdminInterviewListItem::getId)
                .containsExactly("session-003");
        assertThat(morningSessions.getRecords())
                .extracting(AdminInterviewService.AdminInterviewListItem::getId)
                .containsExactly("session-002", "session-001");
    }

    @Test
    void blankStageFilterBehavesAsNoFilter() {
        seedInterviewFixtures();

        AdminInterviewService.AdminInterviewQuery blankStageQuery = new AdminInterviewService.AdminInterviewQuery();
        blankStageQuery.setStage("  ");
        PageResult<AdminInterviewService.AdminInterviewListItem> sessions =
                adminInterviewService.listInterviews(blankStageQuery);

        assertThat(sessions.getRecords())
                .extracting(AdminInterviewService.AdminInterviewListItem::getId)
                .containsExactly("session-003", "session-002", "session-001");
    }

    @Test
    void interviewDetailIncludesSessionMessagesScoresAndEvaluationSummary() {
        seedInterviewFixtures();

        AdminInterviewService.AdminInterviewDetail detail = adminInterviewService.getInterviewDetail("session-001");

        assertThat(detail.getId()).isEqualTo("session-001");
        assertThat(detail.getUsername()).isEqualTo("alice");
        assertThat(detail.getJobTitle()).isEqualTo("Java Engineer");
        assertThat(detail.getMessages())
                .extracting(AdminInterviewService.AdminInterviewMessageItem::getContent)
                .containsExactly("请介绍一下 JVM 内存模型", "JVM 包含堆、栈和方法区");
        assertThat(detail.getScoreRecords())
                .extracting(AdminInterviewService.AdminScoreRecordItem::getQuestionType)
                .containsExactly("technical", "project");
        assertThat(detail.getEvaluation()).isNotNull();
        assertThat(detail.getEvaluation().getRecommendation()).isEqualTo("recommend");
        assertThat(detail.getEvaluation().getOverallScore()).isEqualTo(86);
    }

    @Test
    void diagnoseReportsMissingTechnicalQuestionsEmptyPoolMissingScoresAndEarlyConclusion() {
        seedInterviewFixtures();

        InterviewDiagnosisResponse diagnosis = adminInterviewService.diagnoseInterview("session-002");

        assertThat(diagnosis.getSessionId()).isEqualTo("session-002");
        assertThat(diagnosis.isMissingTechnicalQuestions()).isTrue();
        assertThat(diagnosis.isEmptyTechnicalPool()).isTrue();
        assertThat(diagnosis.isMissingScores()).isTrue();
        assertThat(diagnosis.isEarlyConcludedStage()).isTrue();
        assertThat(diagnosis.getFindings())
                .contains(
                        "MISSING_TECHNICAL_QUESTIONS",
                        "EMPTY_TECHNICAL_POOL",
                        "MISSING_SCORES",
                        "EARLY_CONCLUDED_STAGE");

        Integer unchangedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM t_interview_session WHERE id = 'session-002'",
                Integer.class);
        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_admin_operation_log WHERE module = 'INTERVIEW'",
                Long.class);
        assertThat(unchangedStatus).isEqualTo(2);
        assertThat(auditCount).isZero();
    }

    @Test
    void inProgressProjectStageWithoutTechnicalDataDoesNotReportTechnicalFindings() {
        seedInterviewFixtures();

        InterviewDiagnosisResponse diagnosis = adminInterviewService.diagnoseInterview("session-003");

        assertThat(diagnosis.isMissingTechnicalQuestions()).isFalse();
        assertThat(diagnosis.isEmptyTechnicalPool()).isFalse();
        assertThat(diagnosis.getFindings())
                .doesNotContain("MISSING_TECHNICAL_QUESTIONS", "EMPTY_TECHNICAL_POOL");
    }

    @Test
    void nonArrayTechnicalPoolOnCompletedTechnicalSessionReportsEmptyInvalidPool() {
        seedInterviewFixtures();
        jdbcTemplate.update(
                """
                INSERT INTO t_interview_session
                    (id, user_id, job_id, candidate_name, stage, status, technical_questions_pool,
                     started_at, finished_at, created_at, updated_at)
                VALUES
                    ('session-004', 1, 1, 'Alice', 'technical', 2, CAST('{"question":"JVM?"}' AS jsonb),
                     TIMESTAMP '2026-04-25 14:00:00', TIMESTAMP '2026-04-25 14:10:00',
                     TIMESTAMP '2026-04-25 14:00:00', TIMESTAMP '2026-04-25 14:10:00')
                """);

        InterviewDiagnosisResponse diagnosis = adminInterviewService.diagnoseInterview("session-004");

        assertThat(diagnosis.isEmptyTechnicalPool()).isTrue();
        assertThat(diagnosis.getFindings()).contains("EMPTY_TECHNICAL_POOL");
    }

    @Test
    void inProgressTechnicalStageDoesNotReportTechnicalFindings() {
        seedInterviewFixtures();
        jdbcTemplate.update(
                """
                INSERT INTO t_interview_session
                    (id, user_id, job_id, candidate_name, stage, status, technical_questions_pool,
                     started_at, finished_at, created_at, updated_at)
                VALUES
                    ('session-005', 1, 1, 'Alice', 'technical', 1, CAST('[]' AS jsonb),
                     TIMESTAMP '2026-04-25 15:00:00', NULL,
                     TIMESTAMP '2026-04-25 15:00:00', TIMESTAMP '2026-04-25 15:00:00')
                """);

        InterviewDiagnosisResponse diagnosis = adminInterviewService.diagnoseInterview("session-005");

        assertThat(diagnosis.isMissingTechnicalQuestions()).isFalse();
        assertThat(diagnosis.isEmptyTechnicalPool()).isFalse();
        assertThat(diagnosis.getFindings())
                .doesNotContain("MISSING_TECHNICAL_QUESTIONS", "EMPTY_TECHNICAL_POOL");
    }

    @Test
    void canceledTechnicalStageDoesNotReportTechnicalFindings() {
        seedInterviewFixtures();
        jdbcTemplate.update(
                """
                INSERT INTO t_interview_session
                    (id, user_id, job_id, candidate_name, stage, status, technical_questions_pool,
                     started_at, finished_at, created_at, updated_at)
                VALUES
                    ('session-006', 1, 1, 'Alice', 'technical', 3, CAST('[]' AS jsonb),
                     TIMESTAMP '2026-04-25 16:00:00', TIMESTAMP '2026-04-25 16:01:00',
                     TIMESTAMP '2026-04-25 16:00:00', TIMESTAMP '2026-04-25 16:01:00')
                """);

        InterviewDiagnosisResponse diagnosis = adminInterviewService.diagnoseInterview("session-006");

        assertThat(diagnosis.isMissingTechnicalQuestions()).isFalse();
        assertThat(diagnosis.isEmptyTechnicalPool()).isFalse();
        assertThat(diagnosis.getFindings())
                .doesNotContain("MISSING_TECHNICAL_QUESTIONS", "EMPTY_TECHNICAL_POOL");
    }

    @Test
    void completedSessionCancelRejectedAndStatusRemainsCompleted() {
        seedInterviewFixtures();

        assertThatThrownBy(() -> adminInterviewService.cancelInterview("session-001"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessage("已完成面试不能取消")
                .satisfies(ex -> assertThat(((AdminBusinessException) ex).getCode()).isEqualTo(409));

        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM t_interview_session WHERE id = 'session-001'",
                Integer.class);
        assertThat(status).isEqualTo(2);
    }

    @Test
    void alreadyCanceledSessionCancelRejected() {
        seedInterviewFixtures();
        jdbcTemplate.update(
                """
                INSERT INTO t_interview_session
                    (id, user_id, job_id, candidate_name, stage, status, started_at, finished_at, created_at, updated_at)
                VALUES
                    ('session-004', 1, 1, 'Alice', 'canceled', 3,
                     TIMESTAMP '2026-04-25 14:00:00', TIMESTAMP '2026-04-25 14:05:00',
                     TIMESTAMP '2026-04-25 14:00:00', TIMESTAMP '2026-04-25 14:05:00')
                """);

        assertThatThrownBy(() -> adminInterviewService.cancelInterview("session-004"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessage("面试会话已取消")
                .satisfies(ex -> assertThat(((AdminBusinessException) ex).getCode()).isEqualTo(409));
    }

    @Test
    void cancelSessionChangesStatusToCanceledSetsFinishedAtAndWritesAuditLog() {
        seedInterviewFixtures();

        adminInterviewService.cancelInterview("session-003");

        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM t_interview_session WHERE id = 'session-003'",
                Integer.class);
        LocalDateTime finishedAt = jdbcTemplate.queryForObject(
                "SELECT finished_at FROM t_interview_session WHERE id = 'session-003'",
                LocalDateTime.class);
        String auditTargetId = jdbcTemplate.queryForObject(
                """
                SELECT target_id
                FROM t_admin_operation_log
                WHERE module = 'INTERVIEW'
                  AND operation = 'CANCEL'
                  AND target_type = 'INTERVIEW_SESSION'
                ORDER BY id DESC
                LIMIT 1
                """,
                String.class);
        assertThat(status).isEqualTo(3);
        assertThat(finishedAt).isNotNull();
        assertThat(auditTargetId).isEqualTo("session-003");
    }

    private void seedInterviewFixtures() {
        jdbcTemplate.update(
                """
                INSERT INTO t_user (username, email, password_hash, nickname, status)
                VALUES
                    ('alice', 'alice@example.com', 'hash', 'Alice', 1),
                    ('bob', 'bob@example.com', 'hash', 'Bob', 1)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO t_job (title, company, skills, status, created_by, created_at, updated_at)
                VALUES
                    ('Java Engineer', 'Acme', CAST('["Java"]' AS jsonb), 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('AI Product Manager', 'Beta', CAST('["Product"]' AS jsonb), 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO t_interview_session
                    (id, user_id, job_id, candidate_name, stage, status, technical_questions_pool,
                     started_at, finished_at, created_at, updated_at)
                VALUES
                    ('session-001', 1, 1, 'Alice', 'technical', 2, CAST('[{"question":"JVM?"}]' AS jsonb),
                     TIMESTAMP '2026-04-25 10:00:00', TIMESTAMP '2026-04-25 10:25:00',
                     TIMESTAMP '2026-04-25 10:00:00', TIMESTAMP '2026-04-25 10:25:00'),
                    ('session-002', 2, 2, 'Bob', 'technical', 2, CAST('[]' AS jsonb),
                     TIMESTAMP '2026-04-25 11:00:00', TIMESTAMP '2026-04-25 11:01:00',
                     TIMESTAMP '2026-04-25 11:00:00', TIMESTAMP '2026-04-25 11:01:00'),
                    ('session-003', 1, 1, 'Alice', 'project', 1, NULL,
                     TIMESTAMP '2026-04-25 13:00:00', NULL,
                     TIMESTAMP '2026-04-25 13:00:00', TIMESTAMP '2026-04-25 13:00:00')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO t_interview_message (session_id, role, content, stage, sequence, created_at)
                VALUES
                    ('session-001', 'ai', '请介绍一下 JVM 内存模型', 'technical', 1, TIMESTAMP '2026-04-25 10:01:00'),
                    ('session-001', 'human', 'JVM 包含堆、栈和方法区', 'technical', 2, TIMESTAMP '2026-04-25 10:02:00')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO t_score_record
                    (session_id, question_index, question_type, question, answer, score, feedback, is_followup, created_at)
                VALUES
                    ('session-001', 1, 'technical', 'JVM?', '堆栈方法区', 88, '技术回答扎实', false,
                     TIMESTAMP '2026-04-25 10:03:00'),
                    ('session-001', 2, 'project', '项目难点?', '性能优化', 82, '项目经验较好', false,
                     TIMESTAMP '2026-04-25 10:04:00'),
                    ('session-002', 1, 'project', '项目经验?', '做过管理后台', NULL, '缺少评分', false,
                     TIMESTAMP '2026-04-25 11:01:00')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO t_evaluation
                    (session_id, user_id, job_id, overall_score, technical_score, communication_score,
                     logic_score, experience_score, summary, strengths, weaknesses, recommendation,
                     detailed_feedback, total_questions, answered_questions, average_score, duration_minutes,
                     created_at, updated_at)
                VALUES
                    ('session-001', 1, 1, 86, 88, 84, 85, 87, '表现优秀', '技术扎实', '可加强架构表达',
                     'recommend', CAST('{"note":"good"}' AS jsonb), 2, 2, 85.00, 25,
                     TIMESTAMP '2026-04-25 10:30:00', TIMESTAMP '2026-04-25 10:30:00'),
                    ('session-002', 2, 2, 62, 50, 70, 60, 65, '需要观察', '表达清晰', '技术深度不足',
                     'consider', CAST('{"note":"watch"}' AS jsonb), 1, 1, 62.00, 1,
                     TIMESTAMP '2026-04-25 11:02:00', TIMESTAMP '2026-04-25 11:02:00')
                """);
    }
}
