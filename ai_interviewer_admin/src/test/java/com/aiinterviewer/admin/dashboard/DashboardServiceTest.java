package com.aiinterviewer.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.admin.dashboard.dto.DashboardOverviewResponse;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DashboardServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

    @Test
    void overviewReturnsCountsForCoreBusinessTables() {
        seedDashboardFixture();

        DashboardOverviewResponse overview = dashboardService.getOverview();

        assertThat(overview.getUserCount()).isEqualTo(3);
        assertThat(overview.getJobCount()).isEqualTo(2);
        assertThat(overview.getResumeCount()).isEqualTo(2);
        assertThat(overview.getInterviewCount()).isEqualTo(5);
        assertThat(overview.getEvaluationCount()).isEqualTo(5);
    }

    @Test
    void scoreDistributionGroupsEvaluationsByScoreRanges() {
        seedDashboardFixture();

        DashboardOverviewResponse overview = dashboardService.getOverview();

        Map<String, Long> distribution = overview.getScoreDistribution().stream()
                .collect(Collectors.toMap(
                        DashboardOverviewResponse.ScoreRangeCount::getRange,
                        DashboardOverviewResponse.ScoreRangeCount::getCount));
        assertThat(distribution)
                .containsEntry("0-59", 1L)
                .containsEntry("60-69", 1L)
                .containsEntry("70-79", 1L)
                .containsEntry("80-89", 1L)
                .containsEntry("90-100", 1L);
    }

    @Test
    void interviewTrendGroupsSessionsByDayForLastThirtyDays() {
        seedDashboardFixture();

        DashboardOverviewResponse overview = dashboardService.getOverview();

        assertThat(overview.getInterviewTrend()).hasSize(30);
        Map<LocalDate, Long> trend = overview.getInterviewTrend().stream()
                .collect(Collectors.toMap(
                        DashboardOverviewResponse.DailyInterviewCount::getDate,
                        DashboardOverviewResponse.DailyInterviewCount::getCount));
        assertThat(trend).containsEntry(LocalDate.now(), 2L);
        assertThat(trend).containsEntry(LocalDate.now().minusDays(3), 1L);
        assertThat(trend).containsEntry(LocalDate.now().minusDays(10), 1L);
        assertThat(trend).doesNotContainKey(LocalDate.now().minusDays(31));
    }

    @Test
    void recentErrorsIncludeTooShortCompletedSessionsAndSessionsWithoutScoreRecords() {
        seedDashboardFixture();

        DashboardOverviewResponse overview = dashboardService.getOverview();

        assertThat(overview.getRecentErrors())
                .extracting(DashboardOverviewResponse.RecentErrorSession::getSessionId)
                .contains("short-session", "missing-score-session")
                .doesNotContain("healthy-session", "in-progress-with-score");
        Map<String, String> reasons = overview.getRecentErrors().stream()
                .collect(Collectors.toMap(
                        DashboardOverviewResponse.RecentErrorSession::getSessionId,
                        DashboardOverviewResponse.RecentErrorSession::getReason));
        assertThat(reasons)
                .containsEntry("short-session", "TOO_SHORT")
                .containsEntry("missing-score-session", "MISSING_SCORE_RECORD");
    }

    private void seedDashboardFixture() {
        jdbcTemplate.update(
                """
                INSERT INTO t_user (username, email, password_hash, created_at)
                VALUES
                    ('alice', 'alice@example.com', 'hash', CURRENT_TIMESTAMP),
                    ('bob', 'bob@example.com', 'hash', CURRENT_TIMESTAMP),
                    ('carol', 'carol@example.com', 'hash', CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO t_job (title, company, created_by, created_at)
                VALUES
                    ('Java Engineer', 'Acme', 1, CURRENT_TIMESTAMP),
                    ('AI Engineer', 'Acme', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO t_resume (user_id, file_name, original_file_name, created_at)
                VALUES
                    (1, 'resume-1.pdf', 'Alice.pdf', CURRENT_TIMESTAMP),
                    (2, 'resume-2.pdf', 'Bob.pdf', CURRENT_TIMESTAMP)
                """);

        insertSession("healthy-session", 1, 1, 1, 2, LocalDateTime.now().minusMinutes(45),
                LocalDateTime.now().minusMinutes(15), LocalDateTime.now());
        insertSession("short-session", 1, 1, 1, 2, LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().minusMinutes(4), LocalDateTime.now());
        insertSession("missing-score-session", 2, 2, 2, 2, LocalDateTime.now().minusDays(3).minusMinutes(30),
                LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(3));
        insertSession("in-progress-with-score", 3, null, 2, 1, LocalDateTime.now().minusDays(10),
                null, LocalDateTime.now().minusDays(10));
        insertSession("old-session", 3, null, 2, 2, LocalDateTime.now().minusDays(31).minusMinutes(20),
                LocalDateTime.now().minusDays(31), LocalDateTime.now().minusDays(31));

        insertScore("healthy-session", 88);
        insertScore("short-session", 70);
        insertScore("in-progress-with-score", 92);
        insertScore("old-session", 65);

        insertEvaluation("healthy-session", 1, 1, 95);
        insertEvaluation("short-session", 1, 1, 85);
        insertEvaluation("missing-score-session", 2, 2, 75);
        insertEvaluation("in-progress-with-score", 3, 2, 65);
        insertEvaluation("old-session", 3, 2, 55);
    }

    private void insertSession(
            String id,
            long userId,
            Integer resumeId,
            long jobId,
            int status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO t_interview_session
                    (id, user_id, resume_id, job_id, candidate_name, stage, status,
                     started_at, finished_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'technical', ?, ?, ?, ?, ?)
                """,
                id,
                userId,
                resumeId,
                jobId,
                "Candidate " + userId,
                status,
                startedAt,
                finishedAt,
                createdAt,
                createdAt);
    }

    private void insertScore(String sessionId, int score) {
        jdbcTemplate.update(
                """
                INSERT INTO t_score_record (session_id, question_index, question_type, question, answer, score)
                VALUES (?, 1, 'technical', 'question', 'answer', ?)
                """,
                sessionId,
                score);
    }

    private void insertEvaluation(String sessionId, long userId, long jobId, int overallScore) {
        jdbcTemplate.update(
                """
                INSERT INTO t_evaluation
                    (session_id, user_id, job_id, overall_score, technical_score, communication_score,
                     logic_score, experience_score)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sessionId,
                userId,
                jobId,
                overallScore,
                overallScore,
                overallScore,
                overallScore,
                overallScore);
    }
}
