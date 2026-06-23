package com.aiinterviewer.admin.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AiObservabilitySchemaMigrationTest {

    private static final List<String> OBSERVABILITY_TABLES = List.of(
            "t_ai_trace",
            "t_ai_trace_step",
            "t_ai_llm_call",
            "t_ai_observability_access_log");

    private static final List<String> OBSERVABILITY_INDEXES = List.of(
            "idx_ai_trace_started_at",
            "idx_ai_trace_session_id",
            "idx_ai_trace_user_id",
            "idx_ai_trace_status",
            "idx_ai_trace_step_trace_id",
            "idx_ai_trace_step_started_at",
            "idx_ai_trace_step_status",
            "idx_ai_llm_call_trace_id",
            "idx_ai_llm_call_step_id",
            "idx_ai_llm_call_started_at",
            "idx_ai_llm_call_status",
            "idx_ai_llm_call_provider",
            "idx_ai_llm_call_model",
            "idx_ai_llm_call_call_type",
            "idx_ai_llm_call_cache_reported_by_provider",
            "idx_ai_observability_access_log_trace_id",
            "idx_ai_observability_access_log_llm_call_id",
            "idx_ai_observability_access_log_created_at");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ai_interviewer_admin")
            .withUsername("admin")
            .withPassword("admin");

    @Test
    void aiObservabilityTablesAreCreated() throws SQLException {
        migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (String tableName : OBSERVABILITY_TABLES) {
                assertTableExists(connection, tableName);
            }
        }
    }

    @Test
    void aiObservabilityIndexesAreCreated() throws SQLException {
        migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            for (String indexName : OBSERVABILITY_INDEXES) {
                assertThat(indexDefinition(connection, indexName))
                        .as("index %s should exist", indexName)
                        .isNotBlank();
            }
        }
    }

    @Test
    void aiObservabilityAdminMenuAndPermissionsAreSeeded() throws SQLException {
        migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Long menuId = findMenuId(connection, "ai_observability");
            assertThat(menuId)
                    .as("AI observability admin menu should be seeded")
                    .isNotNull();

            assertPermissionExists(connection, menuId, "AI_OBSERVABILITY_VIEW");
            assertPermissionExists(connection, menuId, "AI_OBSERVABILITY_RAW_READ");
            assertPermissionExists(connection, menuId, "AI_OBSERVABILITY_STATS");
        }
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void assertTableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet tables = metaData.getTables(null, "public", tableName, new String[] {"TABLE"})) {
            assertThat(tables.next())
                    .as("table %s should be created by Flyway", tableName)
                    .isTrue();
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

    private Long findMenuId(Connection connection, String menuCode) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT id FROM t_admin_menu WHERE menu_code = ? AND deleted_at IS NULL")) {
            statement.setString(1, menuCode);
            try (ResultSet menus = statement.executeQuery()) {
                return menus.next() ? menus.getLong("id") : null;
            }
        }
    }

    private void assertPermissionExists(Connection connection, Long menuId, String permissionCode) throws SQLException {
        try (var statement = connection.prepareStatement(
                """
                SELECT 1
                FROM t_admin_permission
                WHERE menu_id = ?
                  AND permission_code = ?
                  AND deleted_at IS NULL
                """)) {
            statement.setLong(1, menuId);
            statement.setString(2, permissionCode);
            try (ResultSet permissions = statement.executeQuery()) {
                assertThat(permissions.next())
                        .as("permission %s should be seeded for AI observability menu", permissionCode)
                        .isTrue();
            }
        }
    }
}
