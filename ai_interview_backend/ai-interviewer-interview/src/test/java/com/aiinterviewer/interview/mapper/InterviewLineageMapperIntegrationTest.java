package com.aiinterviewer.interview.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.interview.projection.LineageSummaryRow;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class InterviewLineageMapperIntegrationTest {

    private static final String ROOT_SESSION_ID = "ef3d58eb84c74358a4b55dd09ff635b2";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private DataSource dataSource;

    @BeforeEach
    void migrateLegacySchemaAndCreateBranch() throws SQLException {
        dataSource = dataSource();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/legacy/V0__legacy_interview_schema.sql"));
        }

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table("flyway_interview_schema_history")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .load()
                .migrate();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO t_interview_session(
                        id,
                        user_id,
                        job_id,
                        candidate_name,
                        stage,
                        status,
                        project_questions_count,
                        target_project_questions,
                        created_at,
                        updated_at,
                        lineage_id,
                        parent_session_id,
                        fork_point_message_id,
                        branch_label,
                        branch_version,
                        last_business_activity_at,
                        legacy_migrated
                    ) VALUES (
                        'completed-branch',
                        1,
                        10,
                        'Legacy Candidate',
                        'concluded',
                        2,
                        5,
                        5,
                        TIMESTAMP '2026-07-23 19:00:00',
                        TIMESTAMP '2026-07-23 20:00:00',
                        '%s',
                        '%s',
                        (SELECT id FROM t_interview_message
                         WHERE session_id = '%s' AND sequence = 5),
                        '分支 1',
                        1,
                        TIMESTAMP '2026-07-23 20:00:00',
                        FALSE
                    )
                    """.formatted(ROOT_SESSION_ID, ROOT_SESSION_ID, ROOT_SESSION_ID));
            statement.execute("""
                    INSERT INTO t_evaluation(session_id, user_id, overall_score)
                    VALUES ('completed-branch', 1, 88)
                    """);
            statement.execute("""
                    UPDATE t_interview_lineage
                    SET last_business_activity_at = TIMESTAMP '2026-07-23 20:00:00'
                    WHERE id = '%s'
                    """.formatted(ROOT_SESSION_ID));
        }
    }

    @Test
    void returnsOneSummaryPerLineageWithBranchCountsAndBestCompletedScore() throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/InterviewLineageMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();

        try (SqlSession session = factory.openSession()) {
            InterviewLineageMapper mapper = session.getMapper(InterviewLineageMapper.class);

            List<LineageSummaryRow> rows = mapper.selectSummaryPage(
                    1L,
                    "Java",
                    "score",
                    "all",
                    10L,
                    0L);

            assertThat(mapper.countSummaries(1L, "Java", "all")).isEqualTo(1L);
            assertThat(mapper.countSummaries(2L, null, "all")).isZero();
            assertThat(mapper.selectSummaryPage(2L, null, "time", "all", 10L, 0L)).isEmpty();
            assertThat(rows).hasSize(1);
            LineageSummaryRow row = rows.get(0);
            assertThat(row.getLineageId()).isEqualTo(ROOT_SESSION_ID);
            assertThat(row.getRootSessionId()).isEqualTo(ROOT_SESSION_ID);
            assertThat(row.getJobTitle()).isEqualTo("Java 后端工程师");
            assertThat(row.getBranchCount()).isEqualTo(2L);
            assertThat(row.getActiveBranchCount()).isEqualTo(1L);
            assertThat(row.getCompletedBranchCount()).isEqualTo(1L);
            assertThat(row.getBestCompletedScore()).isEqualTo(88);
            assertThat(row.getFocusedBranchId()).isEqualTo(ROOT_SESSION_ID);
            assertThat(row.getFocusedBranchStage()).isEqualTo("project_qna");
        }
    }

    @Test
    void statusFilterUsesServerFocusedBranchBeforePagination() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("SET CONSTRAINTS ALL DEFERRED");
            statement.execute("""
                    INSERT INTO t_interview_lineage(
                        id, user_id, root_session_id, last_business_activity_at,
                        archived, created_at, updated_at
                    ) VALUES
                    ('completed-lineage', 1, 'completed-root', TIMESTAMP '2026-07-24 21:00:00',
                     FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('ended-lineage', 1, 'ended-root', TIMESTAMP '2026-07-24 22:00:00',
                     FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO t_interview_session(
                        id, user_id, stage, status, project_questions_count,
                        target_project_questions, lineage_id, branch_label,
                        branch_version, last_business_activity_at, legacy_migrated,
                        created_at, updated_at
                    ) VALUES
                    ('completed-root', 1, 'concluded', 2, 5, 5,
                     'completed-lineage', '原始分支', 1, TIMESTAMP '2026-07-24 21:00:00',
                     FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('ended-root', 1, 'technical_qna', 3, 1, 5,
                     'ended-lineage', '原始分支', 1, TIMESTAMP '2026-07-24 22:00:00',
                     FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            connection.commit();
        }

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new ClassPathResource("mapper/InterviewLineageMapper.xml"));
        try (SqlSession session = factoryBean.getObject().openSession()) {
            InterviewLineageMapper mapper = session.getMapper(InterviewLineageMapper.class);

            assertThat(mapper.selectSummaryPage(1L, null, "time", "active", 1L, 0L))
                    .extracting(LineageSummaryRow::getLineageId)
                    .containsExactly(ROOT_SESSION_ID);
            assertThat(mapper.selectSummaryPage(1L, null, "time", "completed", 1L, 0L))
                    .extracting(LineageSummaryRow::getLineageId)
                    .containsExactly("completed-lineage");
            assertThat(mapper.selectSummaryPage(1L, null, "time", "ended", 1L, 0L))
                    .extracting(LineageSummaryRow::getLineageId)
                    .containsExactly("ended-lineage");
            assertThat(mapper.countSummaries(1L, null, "all")).isEqualTo(3L);
            assertThat(mapper.countSummaries(1L, null, "active")).isEqualTo(1L);
            assertThat(mapper.countSummaries(1L, null, "completed")).isEqualTo(1L);
            assertThat(mapper.countSummaries(1L, null, "ended")).isEqualTo(1L);
        }
    }

    @Test
    void bestScoreFallbackUsesComposedPathAndSortsAcrossLineages() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM t_evaluation WHERE session_id = 'completed-branch'");
            statement.execute("""
                    INSERT INTO t_interview_message(
                        session_id, role, content, stage, sequence, message_type,
                        expects_response, delivery_status, metadata, created_at
                    ) VALUES
                    ('completed-branch', 'ai', 'child-q', 'technical_qna', 1,
                     'ai_question', TRUE, 'completed', '{}'::jsonb, CURRENT_TIMESTAMP),
                    ('completed-branch', 'human', 'child-a', 'technical_qna', 2,
                     'candidate_answer', FALSE, 'completed', '{}'::jsonb, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO t_score_record(
                        session_id, question_index, question_type, question, answer,
                        score, feedback, is_followup, question_message_id,
                        answer_message_id, created_at
                    ) VALUES (
                        'completed-branch', 2, 'technical_qna', 'child-q', 'child-a',
                        100, 'child', FALSE,
                        (SELECT id FROM t_interview_message
                         WHERE session_id = 'completed-branch' AND sequence = 1),
                        (SELECT id FROM t_interview_message
                         WHERE session_id = 'completed-branch' AND sequence = 2),
                        CURRENT_TIMESTAMP
                    )
                    """);

            connection.setAutoCommit(false);
            statement.execute("SET CONSTRAINTS ALL DEFERRED");
            statement.execute("""
                    INSERT INTO t_interview_lineage(
                        id, user_id, root_session_id, last_business_activity_at,
                        archived, created_at, updated_at
                    ) VALUES (
                        'second-lineage', 1, 'second-root',
                        TIMESTAMP '2026-07-24 12:00:00', FALSE,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    INSERT INTO t_interview_session(
                        id, user_id, job_id, candidate_name, stage, status,
                        project_questions_count, target_project_questions,
                        lineage_id, branch_label, branch_version,
                        last_business_activity_at, legacy_migrated,
                        created_at, updated_at
                    ) VALUES (
                        'second-root', 1, 10, 'Second Candidate', 'concluded', 2,
                        5, 5, 'second-lineage', '原始分支', 1,
                        TIMESTAMP '2026-07-24 12:00:00', FALSE,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
            connection.commit();
            connection.setAutoCommit(true);
            statement.execute("""
                    INSERT INTO t_interview_message(
                        session_id, role, content, stage, sequence, message_type,
                        expects_response, delivery_status, metadata, created_at
                    ) VALUES
                    ('second-root', 'ai', 'second-q', 'technical_qna', 1,
                     'ai_question', TRUE, 'completed', '{}'::jsonb, CURRENT_TIMESTAMP),
                    ('second-root', 'human', 'second-a', 'technical_qna', 2,
                     'candidate_answer', FALSE, 'completed', '{}'::jsonb, CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    INSERT INTO t_score_record(
                        session_id, question_index, question_type, question, answer,
                        score, feedback, is_followup, question_message_id,
                        answer_message_id, created_at
                    ) VALUES (
                        'second-root', 1, 'technical_qna', 'second-q', 'second-a',
                        95, 'second', FALSE,
                        (SELECT id FROM t_interview_message
                         WHERE session_id = 'second-root' AND sequence = 1),
                        (SELECT id FROM t_interview_message
                         WHERE session_id = 'second-root' AND sequence = 2),
                        CURRENT_TIMESTAMP
                    )
                    """);
        }

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/InterviewLineageMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();

        try (SqlSession session = factory.openSession()) {
            List<LineageSummaryRow> rows = session.getMapper(InterviewLineageMapper.class)
                    .selectSummaryPage(1L, null, "score", "all", 10L, 0L);

            assertThat(rows).extracting(LineageSummaryRow::getLineageId)
                    .containsExactly("second-lineage", ROOT_SESSION_ID);
            assertThat(rows).extracting(LineageSummaryRow::getBestCompletedScore)
                    .containsExactly(95, 90);
        }
    }

    @Test
    void partialOwnershipReassignmentCannotExposeOldRootMetadataOrInflateCount()
            throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO t_user(id) VALUES (2)");
            statement.execute("""
                    UPDATE t_interview_lineage
                    SET user_id = 2
                    WHERE id = '%s'
                    """.formatted(ROOT_SESSION_ID));
        }

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/InterviewLineageMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();

        try (SqlSession session = factory.openSession()) {
            InterviewLineageMapper mapper = session.getMapper(InterviewLineageMapper.class);

            assertThat(mapper.selectSummaryPage(2L, null, "time", "all", 10L, 0L)).isEmpty();
            assertThat(mapper.countSummaries(2L, null, "all")).isZero();
        }
    }

    @Test
    void fullReassignmentIgnoresPreviousOwnersEvaluationInListFallback() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO t_user(id) VALUES (2)");
            statement.execute("""
                    UPDATE t_interview_lineage
                    SET user_id = 2
                    WHERE id = '%s'
                    """.formatted(ROOT_SESSION_ID));
            statement.execute("""
                    UPDATE t_interview_session
                    SET user_id = 2
                    WHERE lineage_id = '%s'
                    """.formatted(ROOT_SESSION_ID));
        }

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new ClassPathResource("mapper/InterviewLineageMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();

        try (SqlSession session = factory.openSession()) {
            List<LineageSummaryRow> rows = session.getMapper(InterviewLineageMapper.class)
                    .selectSummaryPage(2L, null, "score", "all", 10L, 0L);

            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().getBestCompletedScore()).isEqualTo(80);
        }
    }

    private static DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }
}
