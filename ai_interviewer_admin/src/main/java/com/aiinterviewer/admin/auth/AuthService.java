package com.aiinterviewer.admin.auth;

import com.aiinterviewer.admin.auth.dto.AdminLoginRequest;
import com.aiinterviewer.admin.auth.dto.AdminLoginResponse;
import com.aiinterviewer.admin.auth.dto.AdminLoginResponse.AdminUserSummary;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.security.JwtService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int USER_STATUS_ENABLED = 1;
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminLoginResponse login(AdminLoginRequest request) {
        AdminUser user = findUser(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.passwordHash())) {
            throw new AdminBusinessException(401, "用户名或密码错误");
        }
        if (user.status() != USER_STATUS_ENABLED) {
            throw new AdminBusinessException(403, "账号已禁用");
        }

        List<String> roles = listRoleCodes(user.id());
        if (!roles.contains(ADMIN_ROLE)) {
            throw new AdminBusinessException(403, "缺少管理员权限");
        }

        jdbcTemplate.update("UPDATE t_user SET last_login_time = CURRENT_TIMESTAMP WHERE id = ?", user.id());
        String accessToken = jwtService.generateAccessToken(user.id(), roles);
        return new AdminLoginResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenExpiration() / 1000,
                user.toSummary(),
                roles);
    }

    public AdminUserSummary getAdminUser(Long userId) {
        AdminUser user = findUserById(userId);
        if (user == null || user.status() != USER_STATUS_ENABLED) {
            throw new AdminBusinessException(404, "管理员不存在或已禁用");
        }
        return user.toSummary();
    }

    private AdminUser findUser(String loginName) {
        List<AdminUser> users = jdbcTemplate.query(
                """
                SELECT id, username, email, phone, password_hash, nickname, avatar_url, status
                FROM t_user
                WHERE deleted_at IS NULL
                  AND (username = ? OR email = ? OR phone = ?)
                LIMIT 1
                """,
                this::mapUser,
                loginName,
                loginName,
                loginName);
        return users.isEmpty() ? null : users.getFirst();
    }

    private AdminUser findUserById(Long userId) {
        List<AdminUser> users = jdbcTemplate.query(
                """
                SELECT id, username, email, phone, password_hash, nickname, avatar_url, status
                FROM t_user
                WHERE id = ? AND deleted_at IS NULL
                """,
                this::mapUser,
                userId);
        return users.isEmpty() ? null : users.getFirst();
    }

    private List<String> listRoleCodes(Long userId) {
        return jdbcTemplate.queryForList(
                """
                SELECT r.role_code
                FROM t_role r
                INNER JOIN t_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ?
                ORDER BY r.role_code
                """,
                String.class,
                userId);
    }

    private AdminUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new AdminUser(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("password_hash"),
                rs.getString("nickname"),
                rs.getString("avatar_url"),
                rs.getInt("status"));
    }

    private record AdminUser(
            Long id,
            String username,
            String email,
            String phone,
            String passwordHash,
            String nickname,
            String avatarUrl,
            int status) {

        AdminUserSummary toSummary() {
            return new AdminUserSummary(id, username, nickname, email, phone, avatarUrl);
        }
    }
}
