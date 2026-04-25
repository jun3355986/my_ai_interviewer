package com.aiinterviewer.admin.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

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
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet tables = metaData.getTables(null, "public", tableName, new String[] {"TABLE"})) {
            return tables.next();
        }
    }
}
