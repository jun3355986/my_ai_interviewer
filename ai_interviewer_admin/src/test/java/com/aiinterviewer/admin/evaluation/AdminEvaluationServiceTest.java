package com.aiinterviewer.admin.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminEvaluationServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private AdminEvaluationService adminEvaluationService;

    @Test
    void evaluationListSupportsRecommendationAndScoreRangeFilters() {
        seedEvaluationFixtures();

        AdminEvaluationService.AdminEvaluationQuery recommendationQuery =
                new AdminEvaluationService.AdminEvaluationQuery();
        recommendationQuery.setRecommendation("recommend");
        PageResult<AdminEvaluationService.AdminEvaluationListItem> recommended =
                adminEvaluationService.listEvaluations(recommendationQuery);

        AdminEvaluationService.AdminEvaluationQuery scoreRangeQuery =
                new AdminEvaluationService.AdminEvaluationQuery();
        scoreRangeQuery.setMinOverallScore(60);
        scoreRangeQuery.setMaxOverallScore(80);
        PageResult<AdminEvaluationService.AdminEvaluationListItem> middleScores =
                adminEvaluationService.listEvaluations(scoreRangeQuery);

        assertThat(recommended.getRecords())
                .extracting(AdminEvaluationService.AdminEvaluationListItem::getSessionId)
                .containsExactly("eval-session-001");
        assertThat(middleScores.getRecords())
                .extracting(AdminEvaluationService.AdminEvaluationListItem::getSessionId)
                .containsExactly("eval-session-002");
    }

    private void seedEvaluationFixtures() {
        jdbcTemplate.update(
                """
                INSERT INTO t_user (username, email, password_hash, nickname, status)
                VALUES
                    ('alice', 'alice@example.com', 'hash', 'Alice', 1),
                    ('bob', 'bob@example.com', 'hash', 'Bob', 1),
                    ('carol', 'carol@example.com', 'hash', 'Carol', 1)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO t_job (title, company, skills, status, created_by, created_at, updated_at)
                VALUES
                    ('Java Engineer', 'Acme', CAST('["Java"]' AS jsonb), 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('Product Manager', 'Beta', CAST('["Product"]' AS jsonb), 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO t_interview_session
                    (id, user_id, job_id, candidate_name, stage, status, started_at, finished_at, created_at, updated_at)
                VALUES
                    ('eval-session-001', 1, 1, 'Alice', 'completed', 2,
                     TIMESTAMP '2026-04-25 09:00:00', TIMESTAMP '2026-04-25 09:30:00',
                     TIMESTAMP '2026-04-25 09:00:00', TIMESTAMP '2026-04-25 09:30:00'),
                    ('eval-session-002', 2, 2, 'Bob', 'completed', 2,
                     TIMESTAMP '2026-04-25 10:00:00', TIMESTAMP '2026-04-25 10:20:00',
                     TIMESTAMP '2026-04-25 10:00:00', TIMESTAMP '2026-04-25 10:20:00'),
                    ('eval-session-003', 3, 1, 'Carol', 'completed', 2,
                     TIMESTAMP '2026-04-25 11:00:00', TIMESTAMP '2026-04-25 11:15:00',
                     TIMESTAMP '2026-04-25 11:00:00', TIMESTAMP '2026-04-25 11:15:00')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO t_evaluation
                    (session_id, user_id, job_id, overall_score, technical_score, communication_score,
                     logic_score, experience_score, summary, strengths, weaknesses, recommendation,
                     detailed_feedback, total_questions, answered_questions, average_score, duration_minutes,
                     created_at, updated_at)
                VALUES
                    ('eval-session-001', 1, 1, 91, 92, 90, 90, 91, '强烈推荐', '技术强', '无',
                     'recommend', CAST('{"level":"high"}' AS jsonb), 5, 5, 91.00, 30,
                     TIMESTAMP '2026-04-25 09:35:00', TIMESTAMP '2026-04-25 09:35:00'),
                    ('eval-session-002', 2, 2, 72, 68, 80, 70, 72, '可考虑', '沟通好', '技术需提升',
                     'consider', CAST('{"level":"middle"}' AS jsonb), 5, 5, 72.00, 20,
                     TIMESTAMP '2026-04-25 10:25:00', TIMESTAMP '2026-04-25 10:25:00'),
                    ('eval-session-003', 3, 1, 45, 40, 60, 45, 42, '不推荐', '态度好', '基础不足',
                     'reject', CAST('{"level":"low"}' AS jsonb), 5, 4, 45.00, 15,
                     TIMESTAMP '2026-04-25 11:20:00', TIMESTAMP '2026-04-25 11:20:00')
                """);
    }
}
