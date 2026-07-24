package com.aiinterviewer.admin.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AdminSchemaMigrationTest {

    private static final List<String> ADMIN_TABLES = List.of(
            "t_admin_menu",
            "t_admin_permission",
            "t_admin_role_permission",
            "t_admin_user_role",
            "t_question_bank",
            "t_question_tag",
            "t_question_tag_relation",
            "t_question_import_batch",
            "t_question_vector_sync_record",
            "t_notification_template",
            "t_system_config",
            "t_interview_strategy_config",
            "t_admin_operation_log");

    private static final Map<String, List<String>> REQUIRED_COLUMNS = Map.of(
            "t_question_bank",
            List.of(
                    "question_text",
                    "answer_reference",
                    "skill_area",
                    "job_id",
                    "vector_sync_status",
                    "source_batch_id"),
            "t_admin_operation_log",
            List.of(
                    "target_id",
                    "request_uri",
                    "before_snapshot",
                    "after_snapshot",
                    "result"),
            "t_question_vector_sync_record",
            List.of(
                    "total_count",
                    "success_count",
                    "failed_count"));

    private static final Map<String, String> SOFT_DELETE_UNIQUE_INDEXES = Map.of(
            "uk_admin_menu_code", "menu_code",
            "uk_admin_permission_code", "permission_code",
            "uk_question_bank_code", "question_code",
            "uk_question_tag_code", "tag_code",
            "uk_question_import_batch_no", "batch_no",
            "uk_notification_template_code", "template_code",
            "uk_system_config_key", "config_key",
            "uk_interview_strategy_config_code", "strategy_code");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ai_interviewer_admin")
            .withUsername("admin")
            .withPassword("admin");

    @Test
    void flywayCreatesAllAdminOwnedTables() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (String tableName : ADMIN_TABLES) {
                assertThat(tableExists(connection, tableName))
                        .as("table %s should be created by Flyway", tableName)
                        .isTrue();
            }

            REQUIRED_COLUMNS.forEach((tableName, columnNames) -> {
                for (String columnName : columnNames) {
                    assertThat(columnExists(connection, tableName, columnName))
                            .as("column %s.%s should be created by Flyway", tableName, columnName)
                            .isTrue();
                }
            });

            SOFT_DELETE_UNIQUE_INDEXES.forEach((indexName, indexedColumn) -> {
                String indexDefinition = indexDefinition(connection, indexName);
                assertThat(indexDefinition)
                        .as("index %s should exist", indexName)
                        .isNotBlank();
                assertThat(indexDefinition)
                        .as("index %s should be unique on %s", indexName, indexedColumn)
                        .contains("CREATE UNIQUE INDEX")
                        .contains("(" + indexedColumn + ")");
                assertThat(indexDefinition)
                        .as("index %s should only constrain active rows", indexName)
                        .contains("WHERE (deleted_at IS NULL)");
            });

            String normalizedTagIndex = indexDefinition(connection, "uk_question_tag_name_lower");
            assertThat(normalizedTagIndex)
                    .as("normalized active tag names should be unique")
                    .contains("CREATE UNIQUE INDEX")
                    .contains("lower((tag_name)::text)")
                    .contains("WHERE (deleted_at IS NULL)");
        }
    }

    @Test
    void flywayBaselinesExistingBusinessSchemaAndAddsCompatibleColumns() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
            statement.execute(
                    """
                    CREATE TABLE t_notification (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        type VARCHAR(50) NOT NULL,
                        title VARCHAR(200),
                        content TEXT,
                        related_type VARCHAR(50),
                        related_id VARCHAR(100),
                        status SMALLINT DEFAULT 0,
                        send_time TIMESTAMP,
                        read_time TIMESTAMP,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE t_job (
                        id BIGSERIAL PRIMARY KEY,
                        title VARCHAR(100) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(columnExists(connection, "t_notification", "template_code"))
                    .as("admin migration should add template_code to existing business notification table")
                    .isTrue();
            assertThat(tableExists(connection, "t_admin_menu"))
                    .as("admin-owned V1 tables should still be created after baseline version 0")
                    .isTrue();
            assertThat(columnExists(connection, "t_job", "published_at"))
                    .as("admin migration should add published_at to the existing job table")
                    .isTrue();
            assertThat(columnExists(connection, "t_job", "deadline"))
                    .as("admin migration should add deadline to the existing job table")
                    .isTrue();
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet tables = metaData.getTables(null, "public", tableName, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, "public", tableName, columnName)) {
                return columns.next();
            }
        } catch (SQLException ex) {
            throw new AssertionError("Failed to inspect column " + tableName + "." + columnName, ex);
        }
    }

    private String indexDefinition(Connection connection, String indexName) {
        String sql = "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = '" + indexName + "'";
        try (Statement statement = connection.createStatement();
                ResultSet indexes = statement.executeQuery(sql)) {
            if (!indexes.next()) {
                return "";
            }
            return indexes.getString("indexdef");
        } catch (SQLException ex) {
            throw new AssertionError("Failed to inspect index " + indexName, ex);
        }
    }
}
