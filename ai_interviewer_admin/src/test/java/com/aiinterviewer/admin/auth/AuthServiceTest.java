package com.aiinterviewer.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiinterviewer.admin.auth.dto.AdminLoginRequest;
import com.aiinterviewer.admin.auth.dto.AdminLoginResponse;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void adminUserWithRoleAdminCanLogin() {
        createUserWithRole("admin", "Admin User", 1, "ROLE_ADMIN");

        AdminLoginResponse response = authService.login(new AdminLoginRequest("admin", "pass123456"));

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isPositive();
        assertThat(response.getAdmin().getUsername()).isEqualTo("admin");
        assertThat(response.getRoles()).containsExactly("ROLE_ADMIN");
    }

    @Test
    void normalUserWithoutRoleAdminCannotLogin() {
        createUserWithRole("candidate", "Candidate", 1, "ROLE_USER");

        assertThatThrownBy(() -> authService.login(new AdminLoginRequest("candidate", "pass123456")))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void disabledUserCannotLogin() {
        createUserWithRole("disabled_admin", "Disabled Admin", 0, "ROLE_ADMIN");

        assertThatThrownBy(() -> authService.login(new AdminLoginRequest("disabled_admin", "pass123456")))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void getAdminUserRejectsUserAfterAdminRoleRemoved() {
        Long userId = createUserWithRole("role_removed", "Role Removed", 1, "ROLE_ADMIN");
        jdbcTemplate.update("DELETE FROM t_user_role WHERE user_id = ?", userId);

        assertThatThrownBy(() -> authService.getAdminUser(userId))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("管理员权限");
    }

    private Long createUserWithRole(String username, String nickname, int status, String roleCode) {
        Long userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO t_user (username, password_hash, nickname, status)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                username,
                passwordEncoder.encode("pass123456"),
                nickname,
                status);
        Long roleId = jdbcTemplate.queryForObject(
                """
                INSERT INTO t_role (role_code, role_name)
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                roleCode,
                roleCode);
        jdbcTemplate.update("INSERT INTO t_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }
}
