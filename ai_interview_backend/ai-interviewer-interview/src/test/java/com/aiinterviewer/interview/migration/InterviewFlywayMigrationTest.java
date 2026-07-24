package com.aiinterviewer.interview.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class InterviewFlywayMigrationTest {

    private static final String LEGACY_SESSION_ID = "ef3d58eb84c74358a4b55dd09ff635b2";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void restoreLegacySchema() throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/legacy/V0__legacy_interview_schema.sql"));
        }
    }

    @Test
    void migratesLegacySessionIntoRootLineageWithoutChangingBusinessData() throws SQLException {
        migrate();
        migrate();

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(queryLong(connection, "SELECT count(*) FROM t_interview_session")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT count(*) FROM t_interview_message")).isEqualTo(6);
            assertThat(queryLong(connection, "SELECT count(*) FROM t_score_record")).isEqualTo(1);
            assertThat(queryLong(connection, "SELECT count(*) FROM t_evaluation")).isEqualTo(1);

            assertThat(queryString(connection, """
                    SELECT root_session_id
                    FROM t_interview_lineage
                    WHERE id = '%s'
                    """.formatted(LEGACY_SESSION_ID))).isEqualTo(LEGACY_SESSION_ID);

            assertThat(queryString(connection, """
                    SELECT lineage_id
                    FROM t_interview_session
                    WHERE id = '%s'
                    """.formatted(LEGACY_SESSION_ID))).isEqualTo(LEGACY_SESSION_ID);
            assertThat(queryLong(connection, """
                    SELECT branch_version
                    FROM t_interview_session
                    WHERE id = '%s'
                    """.formatted(LEGACY_SESSION_ID))).isEqualTo(1);
            assertThat(queryString(connection, """
                    SELECT branch_label
                    FROM t_interview_session
                    WHERE id = '%s'
                    """.formatted(LEGACY_SESSION_ID))).isEqualTo("原始分支");

            assertThat(queryString(connection, """
                    SELECT message_type || '|' || expects_response || '|' || delivery_status
                    FROM t_interview_message
                    WHERE session_id = '%s' AND sequence = 1
                    """.formatted(LEGACY_SESSION_ID))).isEqualTo("system_trigger|false|completed");
            assertThat(queryString(connection, """
                    SELECT message_type || '|' || expects_response || '|' || delivery_status
                    FROM t_interview_message
                    WHERE session_id = '%s' AND sequence = 2
                    """.formatted(LEGACY_SESSION_ID))).isEqualTo("ai_question|true|completed");
            assertThat(queryString(connection, """
                    SELECT metadata ->> 'legacyForkEligible'
                    FROM t_interview_message
                    WHERE session_id = '%s' AND sequence = 3
                    """.formatted(LEGACY_SESSION_ID))).isEqualTo("false");
            assertThat(queryString(connection, """
                    SELECT message_type || '|' || expects_response || '|' || delivery_status
                    FROM t_interview_message
                    WHERE session_id = '%s' AND sequence = 3
                    """.formatted(LEGACY_SESSION_ID))).isEqualTo("candidate_answer|false|completed");
            assertThat(queryString(connection, """
                    SELECT message_type || '|' || expects_response || '|' || delivery_status
                    FROM t_interview_message
                    WHERE session_id = '%s' AND sequence = 6
                    """.formatted(LEGACY_SESSION_ID))).isEqualTo("ai_question|true|completed");

            assertThat(queryLong(connection, "SELECT score FROM t_score_record WHERE id = 1"))
                    .isEqualTo(80);
            assertThat(queryString(connection, "SELECT summary FROM t_evaluation WHERE id = 1"))
                    .isEqualTo("Legacy evaluation summary.");
            assertThat(queryString(connection, "SELECT marker FROM flyway_schema_history"))
                    .isEqualTo("admin-history");
            assertThat(queryLong(connection, """
                    SELECT count(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = 'flyway_interview_schema_history'
                    """)).isEqualTo(1);
        }
    }

    @Test
    void freshDockerBootstrapRemainsCompatibleWithAllInterviewMigrations() throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(resolveInitSql()));

            assertThat(queryLong(connection, """
                    SELECT count(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = 't_interview_lineage'
                    """)).isZero();
        }

        migrate();

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(queryLong(connection, """
                    SELECT max(version::integer)
                    FROM flyway_interview_schema_history
                    WHERE success = TRUE AND type = 'SQL'
                    """)).isEqualTo(6L);
            assertThat(queryLong(connection, """
                    SELECT count(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 't_interview_turn_attempt'
                      AND column_name IN ('owner_user_id', 'username', 'fork_source_session_id')
                    """)).isEqualTo(3L);
            assertThat(queryLong(connection, """
                    SELECT count(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 't_interview_message'
                      AND column_name IN ('message_type', 'metadata', 'turn_id')
                    """)).isEqualTo(3L);
            assertThat(queryLong(connection, "SELECT count(*) FROM t_interview_lineage"))
                    .isZero();
        }
    }

    @Test
    void allowsAtomicCreationOfANewRootLineageAndItsRootSession() throws SQLException {
        migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("""
                    INSERT INTO t_interview_lineage(
                        id, user_id, root_session_id, last_business_activity_at,
                        archived, created_at, updated_at
                    ) VALUES (
                        'new-root', 1, 'new-root', CURRENT_TIMESTAMP,
                        FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    INSERT INTO t_interview_session(
                        id, user_id, stage, status, lineage_id, branch_label,
                        branch_version, last_business_activity_at, legacy_migrated,
                        created_at, updated_at
                    ) VALUES (
                        'new-root', 1, 'opening', 1, 'new-root', '原始分支',
                        1, CURRENT_TIMESTAMP, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
            connection.commit();

            assertThat(queryLong(connection, """
                    SELECT count(*)
                    FROM t_interview_lineage lineage
                    JOIN t_interview_session session
                      ON session.id = lineage.root_session_id
                     AND session.lineage_id = lineage.id
                    WHERE lineage.id = 'new-root'
                    """)).isEqualTo(1);
        }
    }

    @Test
    void cancellationRequestedContinuesToOccupyTheLineageProcessingSlot() throws SQLException {
        migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO t_interview_turn_attempt(
                        id, lineage_id, session_id, owner_user_id, expected_branch_version,
                        expected_tail_message_id, candidate_answer, status
                    ) VALUES (
                        'cancel-requested', '%1$s', '%1$s',
                        (SELECT user_id FROM t_interview_session WHERE id = '%1$s'), 1,
                        (SELECT id FROM t_interview_message WHERE session_id = '%1$s' ORDER BY sequence DESC LIMIT 1),
                        'answer', 'CANCEL_REQUESTED'
                    )
                    """.formatted(LEGACY_SESSION_ID));

            assertThatThrownBy(() -> statement.execute("""
                            INSERT INTO t_interview_turn_attempt(
                                id, lineage_id, session_id, owner_user_id, expected_branch_version,
                                expected_tail_message_id, candidate_answer, status
                            ) VALUES (
                                'second-processing', '%1$s', '%1$s',
                                (SELECT user_id FROM t_interview_session WHERE id = '%1$s'), 1,
                                (SELECT id FROM t_interview_message WHERE session_id = '%1$s' ORDER BY sequence DESC LIMIT 1),
                                'second answer', 'PROCESSING'
                            )
                            """.formatted(LEGACY_SESSION_ID)))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void backfillsImmutableAttemptOwnerWhenUpgradingFromVersionThree() throws SQLException {
        migrateTo("3");
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO t_interview_turn_attempt(
                        id, lineage_id, session_id, expected_branch_version,
                        expected_tail_message_id, candidate_answer, status
                    ) VALUES (
                        'pre-v4-attempt', '%1$s', '%1$s', 1,
                        (SELECT id FROM t_interview_message WHERE session_id = '%1$s' ORDER BY sequence DESC LIMIT 1),
                        'answer', 'FAILED'
                    )
                    """.formatted(LEGACY_SESSION_ID));
        }

        migrate();

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(queryLong(connection, """
                    SELECT owner_user_id
                    FROM t_interview_turn_attempt
                    WHERE id = 'pre-v4-attempt'
                    """)).isEqualTo(1L);
            assertThat(queryLong(connection, """
                    SELECT count(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 't_interview_turn_attempt'
                      AND column_name = 'owner_user_id'
                      AND is_nullable = 'NO'
                    """)).isEqualTo(1L);
        }
    }

    @Test
    void addsNullableHistoricalForkContextWhenUpgradingFromVersionFour() throws SQLException {
        migrateTo("4");
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO t_interview_turn_attempt(
                        id, lineage_id, session_id, owner_user_id, expected_branch_version,
                        expected_tail_message_id, candidate_answer, status
                    ) VALUES (
                        'pre-v5-attempt', '%1$s', '%1$s', 1, 1,
                        (SELECT id FROM t_interview_message WHERE session_id = '%1$s' ORDER BY sequence DESC LIMIT 1),
                        'answer', 'FAILED'
                    )
                    """.formatted(LEGACY_SESSION_ID));
        }

        migrate();

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(queryLong(connection, """
                    SELECT count(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 't_interview_turn_attempt'
                      AND column_name IN (
                          'fork_source_session_id',
                          'fork_trigger_message_id',
                          'fork_point_message_id',
                          'fork_expected_source_version',
                          'fork_expected_source_tail_message_id'
                      )
                      AND is_nullable = 'YES'
                    """)).isEqualTo(5L);
            assertThat(queryLong(connection, """
                    SELECT count(*)
                    FROM t_interview_turn_attempt
                    WHERE id = 'pre-v5-attempt'
                      AND fork_source_session_id IS NULL
                      AND fork_trigger_message_id IS NULL
                      AND fork_point_message_id IS NULL
                    """)).isEqualTo(1L);
        }
    }

    @Test
    void versionSixBackfillsOnlyDeterministicScoreMessageLinks() throws SQLException {
        migrateTo("5");
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO t_score_record(
                        session_id, question_index, question_type, question, answer,
                        score, feedback, is_followup, created_at
                    ) VALUES (
                        '%1$s', 2, 'self_introduction',
                        '欢迎参加本次面试，请先做自我介绍。',
                        '我有五年 Java 开发经验。',
                        90, 'unique', FALSE, CURRENT_TIMESTAMP
                    )
                    """.formatted(LEGACY_SESSION_ID));
            statement.execute("""
                    INSERT INTO t_interview_message(
                        session_id, role, content, stage, sequence, message_type,
                        expects_response, delivery_status, metadata, created_at
                    ) VALUES
                    ('%1$s', 'ai', '请介绍一个有挑战性的项目。', 'project_qna', 7,
                     'ai_question', TRUE, 'completed', '{}'::jsonb, CURRENT_TIMESTAMP),
                    ('%1$s', 'human', '我负责过高并发订单系统。', 'project_qna', 8,
                     'candidate_answer', FALSE, 'completed', '{}'::jsonb, CURRENT_TIMESTAMP)
                    """.formatted(LEGACY_SESSION_ID));
        }

        migrate();

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(queryString(connection, """
                    SELECT question_message_id || '|' || answer_message_id
                    FROM t_score_record
                    WHERE question_index = 2
                    """)).isEqualTo("2|3");
            assertThat(queryLong(connection, """
                    SELECT count(*)
                    FROM t_score_record
                    WHERE id = 1
                      AND question_message_id IS NULL
                      AND answer_message_id IS NULL
                    """)).isEqualTo(1L);
        }
    }

    private static void migrate() {
        migrateTo(null);
    }

    private static void migrateTo(String version) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .table("flyway_interview_schema_history")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"));
        if (version != null) {
            configuration.target(MigrationVersion.fromVersion(version));
        }
        configuration.load().migrate();
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static Path resolveInitSql() {
        String reactorRoot = System.getProperty("maven.multiModuleProjectDirectory", "");
        return Stream.of(
                        reactorRoot.isBlank() ? null : Path.of(reactorRoot, "sql", "init.sql"),
                        Path.of("sql", "init.sql"),
                        Path.of("..", "sql", "init.sql"))
                .filter(path -> path != null && Files.isRegularFile(path))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot locate ai_interview_backend/sql/init.sql"));
    }
}
