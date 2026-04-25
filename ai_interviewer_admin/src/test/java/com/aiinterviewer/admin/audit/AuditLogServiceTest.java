package com.aiinterviewer.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.audit.entity.AdminOperationLog;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditLogServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditedOperationFixture auditedOperationFixture;

    @AfterEach
    void clearRequestAndSecurityContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulAuditedOperationWritesAdminOperationLog() {
        bindRequestAndAdmin("/admin/users/42/disable", "PATCH", 1001L);

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

    private void bindRequestAndAdmin(String uri, String method, Long adminUserId) {
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

    @TestConfiguration
    static class AuditLogTestConfiguration {

        @Bean
        AuditedOperationFixture auditedOperationFixture() {
            return new AuditedOperationFixture();
        }
    }

    static class AuditedOperationFixture {

        @AdminAudit(module = "USER", operation = "DISABLE", targetType = "USER", targetIdParam = "userId")
        String disableUser(Long userId) {
            return "disabled-" + userId;
        }

        @AdminAudit(module = "USER", operation = "DISABLE", targetType = "USER", targetIdParam = "userId")
        void failDisableUser(Long userId) {
            throw new IllegalStateException("cannot disable user " + userId);
        }
    }
}
