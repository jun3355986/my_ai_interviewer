package com.aiinterviewer.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiinterviewer.admin.audit.AuditLogService;
import com.aiinterviewer.admin.audit.entity.AdminOperationLog;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AdminUserServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearRequestAndSecurityContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void userListSupportsUsernameEmailPhoneStatusFiltersAndExcludesDeletedUsers() {
        seedUsers();

        AdminUserService.AdminUserQuery usernameQuery = new AdminUserService.AdminUserQuery();
        usernameQuery.setUsername("ali");
        assertThat(adminUserService.listUsers(usernameQuery).getRecords())
                .extracting(AdminUserService.AdminUserListItem::getUsername)
                .containsExactly("alice");

        AdminUserService.AdminUserQuery emailQuery = new AdminUserService.AdminUserQuery();
        emailQuery.setEmail("bob@example.com");
        assertThat(adminUserService.listUsers(emailQuery).getRecords())
                .extracting(AdminUserService.AdminUserListItem::getUsername)
                .containsExactly("bob");

        AdminUserService.AdminUserQuery phoneQuery = new AdminUserService.AdminUserQuery();
        phoneQuery.setPhone("0003");
        assertThat(adminUserService.listUsers(phoneQuery).getRecords())
                .extracting(AdminUserService.AdminUserListItem::getUsername)
                .containsExactly("carol");

        AdminUserService.AdminUserQuery statusQuery = new AdminUserService.AdminUserQuery();
        statusQuery.setStatus(0);
        PageResult<AdminUserService.AdminUserListItem> disabledUsers = adminUserService.listUsers(statusQuery);

        assertThat(disabledUsers.getTotal()).isEqualTo(1);
        assertThat(disabledUsers.getRecords())
                .extracting(AdminUserService.AdminUserListItem::getUsername)
                .containsExactly("bob");
    }

    @Test
    void disableUserChangesStatusAndWritesAuditLog() {
        seedUsers();
        bindRequestAndAdmin("/admin/users/1/disable", "PATCH", 9001L);

        adminUserService.disableUser(1L);

        Integer status = jdbcTemplate.queryForObject("SELECT status FROM t_user WHERE id = 1", Integer.class);
        List<AdminOperationLog> logs = auditLogService.listLogs(new AuditLogService.AuditLogQuery()).getRecords();
        assertThat(status).isZero();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getAdminUserId()).isEqualTo(9001L);
        assertThat(logs.getFirst().getModule()).isEqualTo("USER");
        assertThat(logs.getFirst().getOperation()).isEqualTo("DISABLE");
        assertThat(logs.getFirst().getTargetType()).isEqualTo("USER");
        assertThat(logs.getFirst().getTargetId()).isEqualTo("1");
        assertThat(logs.getFirst().getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void disableUserRejectsCurrentAdmin() {
        seedUsers();
        bindRequestAndAdmin("/admin/users/1/disable", "PATCH", 1);

        assertThatThrownBy(() -> adminUserService.disableUser(1L))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessage("不能禁用当前登录管理员")
                .extracting("code")
                .isEqualTo(409);
    }

    @Test
    void disableUserRejectsLastEnabledRoleAdmin() {
        seedUsers();
        Long roleId = createRole("ROLE_ADMIN");
        bindUserRole(1L, roleId);
        bindUserRole(2L, roleId);
        bindRequestAndAdmin("/admin/users/1/disable", "PATCH", 9001L);

        assertThatThrownBy(() -> adminUserService.disableUser(1L))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessage("至少保留一个启用管理员")
                .extracting("code")
                .isEqualTo(409);
    }

    @Test
    void resetPasswordUpdatesPasswordHashAndWritesAuditLog() {
        seedUsers();
        bindRequestAndAdmin("/admin/users/1/reset-password", "POST", 9002L);

        adminUserService.resetPassword(1L, "New-Password-123");

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM t_user WHERE id = 1",
                String.class);
        List<AdminOperationLog> logs = auditLogService.listLogs(new AuditLogService.AuditLogQuery()).getRecords();
        assertThat(passwordHash).isNotEqualTo("hash-a");
        assertThat(passwordEncoder.matches("New-Password-123", passwordHash)).isTrue();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getAdminUserId()).isEqualTo(9002L);
        assertThat(logs.getFirst().getModule()).isEqualTo("USER");
        assertThat(logs.getFirst().getOperation()).isEqualTo("RESET_PASSWORD");
        assertThat(logs.getFirst().getTargetType()).isEqualTo("USER");
        assertThat(logs.getFirst().getTargetId()).isEqualTo("1");
        assertThat(logs.getFirst().getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void resetPasswordRejectsWeakPassword() {
        seedUsers();
        bindRequestAndAdmin("/admin/users/1/reset-password", "POST", 9002L);

        assertThatThrownBy(() -> adminUserService.resetPassword(1L, "password"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessage("新密码至少包含字母和数字")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void resetPasswordRejectsOverlongPassword() {
        seedUsers();
        bindRequestAndAdmin("/admin/users/1/reset-password", "POST", 9002L);

        assertThatThrownBy(() -> adminUserService.resetPassword(1L, "A1" + "x".repeat(71)))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessage("新密码长度不能超过72位")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void guardedWritesRejectDeletedOrMissingUsers() {
        seedUsers();
        bindRequestAndAdmin("/admin/users/4/disable", "PATCH", 9003L);

        assertThatThrownBy(() -> adminUserService.disableUser(4L))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessage("用户不存在或已删除");
        assertThatThrownBy(() -> adminUserService.resetPassword(404L, "New-Password-123"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessage("用户不存在或已删除");
    }

    private void seedUsers() {
        jdbcTemplate.update(
                """
                INSERT INTO t_user (username, email, phone, password_hash, nickname, status, created_at, updated_at, deleted_at)
                VALUES
                    ('alice', 'alice@example.com', '18800000001', 'hash-a', 'Alice', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL),
                    ('bob', 'bob@example.com', '18800000002', 'hash-b', 'Bob', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL),
                    ('carol', 'carol@example.com', '18800000003', 'hash-c', 'Carol', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL),
                    ('deleted-user', 'deleted@example.com', '18800000004', 'hash-d', 'Deleted', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
    }

    private Long createRole(String roleCode) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO t_role (role_code, role_name)
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                roleCode,
                roleCode);
    }

    private void bindUserRole(Long userId, Long roleId) {
        jdbcTemplate.update("INSERT INTO t_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private void bindRequestAndAdmin(String uri, String method, Object adminUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "AdminUserServiceTest/1.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                adminUserId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
