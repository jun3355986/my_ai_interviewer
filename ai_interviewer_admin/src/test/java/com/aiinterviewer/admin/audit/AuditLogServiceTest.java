package com.aiinterviewer.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.audit.entity.AdminOperationLog;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditLogServiceTest extends AdminPostgresIntegrationTest {

    @SpyBean
    private AuditLogService auditLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditedOperationFixture auditedOperationFixture;

    @AfterEach
    void clearRequestAndSecurityContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        reset(auditLogService);
    }

    @Test
    void successfulAuditedOperationWritesAdminOperationLog() {
        MockHttpServletRequest request = bindRequestAndAdmin("/admin/users/42/disable", "PATCH", 1001L);
        request.setParameter("password", "plain-text-password");
        request.setParameter("apiToken", "token-value");
        request.setParameter("comment", "x".repeat(300));
        request.setParameter("roles", "admin", "auditor", "operator", "viewer", "support", "extra");

        String result = auditedOperationFixture.disableUser(42L);

        List<AdminOperationLog> logs = auditLogService.listLogs(new AuditLogService.AuditLogQuery())
                .getRecords();
        assertThat(result).isEqualTo("disabled-42");
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getAdminUserId()).isEqualTo(1001L);
        assertThat(logs.getFirst().getModule()).isEqualTo("USER");
        assertThat(logs.getFirst().getOperation()).isEqualTo("DISABLE");
        assertThat(logs.getFirst().getTargetType()).isEqualTo("USER");
        assertThat(logs.getFirst().getTargetId()).isEqualTo("42");
        assertThat(logs.getFirst().getRequestUri()).isEqualTo("/admin/users/42/disable");
        assertThat(logs.getFirst().getRequestMethod()).isEqualTo("PATCH");
        assertThat(logs.getFirst().getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(logs.getFirst().getUserAgent()).isEqualTo("AuditTest/1.0");
        assertThat(logs.getFirst().getResult()).isEqualTo("SUCCESS");
        assertThat(logs.getFirst().getErrorMessage()).isNull();
        assertThat(logs.getFirst().getDurationMs()).isNotNegative();

        JsonNode params = readJson(logs.getFirst().getRequestParams());
        assertThat(params.get("password").asText()).isEqualTo("******");
        assertThat(params.get("apiToken").asText()).isEqualTo("******");
        assertThat(params.get("comment").asText()).hasSize(256);
        assertThat(params.get("roles")).hasSize(6);
        assertThat(params.get("roles").get(5).asText()).isEqualTo("...");
    }

    @Test
    void failedAuditedOperationWritesFailedResultAndRethrowsOriginalException() {
        bindRequestAndAdmin("/admin/users/42/disable", "PATCH", 1002L);

        assertThatThrownBy(() -> auditedOperationFixture.failDisableUser(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cannot disable user 42");

        List<AdminOperationLog> logs = auditLogService.listLogs(new AuditLogService.AuditLogQuery())
                .getRecords();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getAdminUserId()).isEqualTo(1002L);
        assertThat(logs.getFirst().getModule()).isEqualTo("USER");
        assertThat(logs.getFirst().getOperation()).isEqualTo("DISABLE");
        assertThat(logs.getFirst().getTargetId()).isEqualTo("42");
        assertThat(logs.getFirst().getResult()).isEqualTo("FAILED");
        assertThat(logs.getFirst().getErrorMessage()).contains("cannot disable user 42");
    }

    @Test
    void failedTransactionalOperationKeepsFailedAuditLogAfterBusinessRollback() {
        bindRequestAndAdmin("/admin/users/create-and-fail", "POST", 1003L);

        assertThatThrownBy(() -> auditedOperationFixture.createUserThenFail("rollback-user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("business rollback");

        Integer businessRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE username = ?",
                Integer.class,
                "rollback-user");
        List<AdminOperationLog> logs = auditLogService.listLogs(new AuditLogService.AuditLogQuery())
                .getRecords();
        assertThat(businessRows).isZero();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getOperation()).isEqualTo("CREATE_FAIL");
        assertThat(logs.getFirst().getTargetId()).isEqualTo("rollback-user");
        assertThat(logs.getFirst().getResult()).isEqualTo("FAILED");
        assertThat(logs.getFirst().getErrorMessage()).contains("business rollback");
    }

    @Test
    void successfulOperationStillReturnsWhenAuditWriteFails() {
        bindRequestAndAdmin("/admin/users/43/disable", "PATCH", 1004L);
        doThrow(new DataAccessResourceFailureException("audit storage down"))
                .when(auditLogService)
                .write(any(AdminOperationLog.class));

        String result = auditedOperationFixture.disableUser(43L);

        assertThat(result).isEqualTo("disabled-43");
    }

    @Test
    void invalidJsonSnapshotsAreConvertedBeforeInsertAndStringsAreTruncated() {
        AdminOperationLog log = new AdminOperationLog();
        log.setModule("M".repeat(150));
        log.setOperation("O".repeat(150));
        log.setTargetType("T".repeat(150));
        log.setTargetId("I".repeat(150));
        log.setRequestUri("/" + "u".repeat(600));
        log.setRequestMethod("METHOD".repeat(10));
        log.setRequestParams("{invalid-json");
        log.setBeforeSnapshot("plain-before");
        log.setAfterSnapshot("{\"valid\":true}");
        log.setIpAddress("1".repeat(150));
        log.setUserAgent("A".repeat(600));
        log.setResult("R".repeat(60));
        log.setErrorMessage("E".repeat(3000));
        log.setDurationMs(1L);

        auditLogService.write(log);

        AdminOperationLog saved = auditLogService.listLogs(new AuditLogService.AuditLogQuery())
                .getRecords()
                .getFirst();
        assertThat(saved.getModule()).hasSize(100);
        assertThat(saved.getOperation()).hasSize(100);
        assertThat(saved.getTargetType()).hasSize(100);
        assertThat(saved.getTargetId()).hasSize(100);
        assertThat(saved.getRequestUri()).hasSize(500);
        assertThat(saved.getRequestMethod()).hasSize(20);
        assertThat(saved.getIpAddress()).hasSize(100);
        assertThat(saved.getUserAgent()).hasSize(500);
        assertThat(saved.getResult()).hasSize(30);
        assertThat(saved.getErrorMessage()).hasSize(2000);
        assertThat(readJson(saved.getRequestParams()).asText()).isEqualTo("{invalid-json");
        assertThat(readJson(saved.getBeforeSnapshot()).asText()).isEqualTo("plain-before");
        assertThat(readJson(saved.getAfterSnapshot()).get("valid").asBoolean()).isTrue();
    }

    @Test
    void unresolvedTargetIdParamDoesNotStringifySingleDtoArgument() {
        bindRequestAndAdmin("/admin/users/unresolved", "POST", 1005L);

        String result = auditedOperationFixture.unresolvedTarget(new SensitiveDto("secret-payload"));

        List<AdminOperationLog> logs = auditLogService.listLogs(new AuditLogService.AuditLogQuery())
                .getRecords();
        assertThat(result).isEqualTo("ok");
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getTargetId()).isNull();
    }

    @Test
    void auditListClampsHugePaginationValues() {
        AuditLogService.AuditLogQuery query = new AuditLogService.AuditLogQuery();
        query.setCurrent(Long.MAX_VALUE);
        query.setSize(Long.MAX_VALUE);

        PageResult<AdminOperationLog> page = auditLogService.listLogs(query);

        assertThat(page.getCurrent()).isEqualTo(1_000_000L);
        assertThat(page.getSize()).isEqualTo(100L);
        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    void auditListCanFilterByAdminUserModuleOperationAndTimeRange() {
        OffsetDateTime now = OffsetDateTime.now();
        insertLog(2001L, "USER", "DISABLE", now.minusHours(2));
        insertLog(2001L, "USER", "RESET_PASSWORD", now.minusHours(1));
        insertLog(2002L, "JOB", "CLOSE", now.minusMinutes(30));

        AuditLogService.AuditLogQuery query = new AuditLogService.AuditLogQuery();
        query.setAdminUserId(2001L);
        query.setModule("USER");
        query.setOperation("DISABLE");
        query.setStartTime(now.minusHours(3));
        query.setEndTime(now.minusMinutes(90));

        PageResult<AdminOperationLog> page = auditLogService.listLogs(query);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(AdminOperationLog::getOperation).containsExactly("DISABLE");
    }

    private MockHttpServletRequest bindRequestAndAdmin(String uri, String method, Long adminUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 127.0.0.1");
        request.addHeader("User-Agent", "AuditTest/1.0");
        request.setParameter("reason", "manual");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                adminUserId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        return request;
    }

    private void insertLog(Long adminUserId, String module, String operation, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO t_admin_operation_log
                    (admin_user_id, module, operation, result, duration_ms, created_at)
                VALUES (?, ?, ?, 'SUCCESS', 1, ?)
                """,
                adminUserId,
                module,
                operation,
                createdAt);
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new AssertionError("Expected valid JSON: " + json, ex);
        }
    }

    record SensitiveDto(String secret) {

        @Override
        public String toString() {
            return "SensitiveDto{secret='" + secret + "'}";
        }
    }

    @TestConfiguration
    static class AuditLogTestConfiguration {

        @Bean
        AuditedOperationFixture auditedOperationFixture(JdbcTemplate jdbcTemplate) {
            return new AuditedOperationFixture(jdbcTemplate);
        }
    }

    static class AuditedOperationFixture {

        private final JdbcTemplate jdbcTemplate;

        AuditedOperationFixture(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @AdminAudit(module = "USER", operation = "DISABLE", targetType = "USER", targetIdParam = "userId")
        public String disableUser(Long userId) {
            return "disabled-" + userId;
        }

        @AdminAudit(module = "USER", operation = "DISABLE", targetType = "USER", targetIdParam = "userId")
        public void failDisableUser(Long userId) {
            throw new IllegalStateException("cannot disable user " + userId);
        }

        @Transactional
        @AdminAudit(module = "USER", operation = "CREATE_FAIL", targetType = "USER", targetIdParam = "username")
        public void createUserThenFail(String username) {
            jdbcTemplate.update(
                    "INSERT INTO t_user (username, password_hash) VALUES (?, ?)",
                    username,
                    "hash");
            throw new IllegalStateException("business rollback");
        }

        @AdminAudit(module = "USER", operation = "UNRESOLVED", targetType = "USER", targetIdParam = "missingId")
        public String unresolvedTarget(SensitiveDto dto) {
            return "ok";
        }
    }
}
