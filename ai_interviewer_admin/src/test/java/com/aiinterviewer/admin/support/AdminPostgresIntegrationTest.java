package com.aiinterviewer.admin.support;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AdminPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ai_interviewer_admin")
            .withUsername("admin")
            .withPassword("admin");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("admin.jwt.secret", () -> "admin-test-secret-key-that-is-long-enough-for-hmac-sha256");
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("spring.cloud.nacos.config.enabled", () -> "false");
    }

    @BeforeEach
    void resetDatabase() {
        createBusinessIdentityTables();
        truncateTables(List.of(
                "t_admin_operation_log",
                "t_admin_role_permission",
                "t_admin_permission",
                "t_admin_menu",
                "t_user_role",
                "t_role",
                "t_user"));
    }

    private void createBusinessIdentityTables() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS t_user (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(50) NOT NULL UNIQUE,
                    email VARCHAR(100) UNIQUE,
                    phone VARCHAR(20) UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    nickname VARCHAR(50),
                    avatar_url VARCHAR(500),
                    status SMALLINT DEFAULT 1,
                    last_login_time TIMESTAMP,
                    last_login_ip VARCHAR(50),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS t_role (
                    id BIGSERIAL PRIMARY KEY,
                    role_code VARCHAR(50) NOT NULL UNIQUE,
                    role_name VARCHAR(100) NOT NULL,
                    description TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS t_user_role (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL REFERENCES t_user(id),
                    role_id BIGINT NOT NULL REFERENCES t_role(id),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(user_id, role_id)
                )
                """);
    }

    private void truncateTables(List<String> tableNames) {
        jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tableNames) + " RESTART IDENTITY CASCADE");
    }
}
