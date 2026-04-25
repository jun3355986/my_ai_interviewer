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
        createDashboardBusinessTables();
        truncateTables(List.of(
                "t_score_record",
                "t_evaluation",
                "t_interview_session",
                "t_resume_version",
                "t_resume",
                "t_job",
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

    private void createDashboardBusinessTables() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS t_job (
                    id BIGSERIAL PRIMARY KEY,
                    title VARCHAR(100) NOT NULL,
                    company VARCHAR(200),
                    department VARCHAR(100),
                    location VARCHAR(100),
                    job_type VARCHAR(50),
                    experience_required VARCHAR(50),
                    education_required VARCHAR(50),
                    salary_min DECIMAL(10,2),
                    salary_max DECIMAL(10,2),
                    description TEXT,
                    requirements TEXT,
                    skills JSONB,
                    status SMALLINT DEFAULT 1,
                    created_by BIGINT REFERENCES t_user(id),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS t_resume (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL REFERENCES t_user(id),
                    file_name VARCHAR(255) NOT NULL,
                    original_file_name VARCHAR(255),
                    file_path VARCHAR(500),
                    file_size BIGINT,
                    content_type VARCHAR(100),
                    parsed_content JSONB,
                    raw_text TEXT,
                    parse_status SMALLINT DEFAULT 0,
                    parse_error TEXT,
                    is_default BOOLEAN DEFAULT FALSE,
                    version_count INT DEFAULT 1,
                    parsed_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS t_resume_version (
                    id BIGSERIAL PRIMARY KEY,
                    resume_id BIGINT NOT NULL REFERENCES t_resume(id) ON DELETE CASCADE,
                    version INT NOT NULL,
                    file_path VARCHAR(500),
                    file_name VARCHAR(255),
                    file_size BIGINT,
                    parsed_content JSONB,
                    operation_type VARCHAR(20),
                    operator_id BIGINT,
                    remark TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS t_interview_session (
                    id VARCHAR(50) PRIMARY KEY,
                    user_id BIGINT NOT NULL REFERENCES t_user(id),
                    resume_id BIGINT REFERENCES t_resume(id),
                    job_id BIGINT REFERENCES t_job(id),
                    candidate_name VARCHAR(50),
                    stage VARCHAR(50) NOT NULL,
                    status SMALLINT DEFAULT 1,
                    resume_content TEXT,
                    job_requirements TEXT,
                    project_questions_count INT DEFAULT 0,
                    target_project_questions INT DEFAULT 5,
                    project_questions_pool JSONB,
                    technical_questions_pool JSONB,
                    current_followup_count INT DEFAULT 0,
                    python_session_id VARCHAR(100),
                    started_at TIMESTAMP,
                    finished_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS t_score_record (
                    id BIGSERIAL PRIMARY KEY,
                    session_id VARCHAR(50) NOT NULL REFERENCES t_interview_session(id),
                    question_index INT NOT NULL,
                    question_type VARCHAR(50),
                    question TEXT NOT NULL,
                    answer TEXT,
                    score INT,
                    feedback TEXT,
                    is_followup BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS t_evaluation (
                    id BIGSERIAL PRIMARY KEY,
                    session_id VARCHAR(50) NOT NULL UNIQUE REFERENCES t_interview_session(id),
                    user_id BIGINT NOT NULL REFERENCES t_user(id),
                    job_id BIGINT REFERENCES t_job(id),
                    overall_score INT,
                    technical_score INT,
                    communication_score INT,
                    logic_score INT,
                    experience_score INT,
                    summary TEXT,
                    strengths TEXT,
                    weaknesses TEXT,
                    recommendation VARCHAR(50),
                    detailed_feedback JSONB,
                    total_questions INT,
                    answered_questions INT,
                    average_score DECIMAL(5,2),
                    duration_minutes INT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void truncateTables(List<String> tableNames) {
        jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tableNames) + " RESTART IDENTITY CASCADE");
    }
}
