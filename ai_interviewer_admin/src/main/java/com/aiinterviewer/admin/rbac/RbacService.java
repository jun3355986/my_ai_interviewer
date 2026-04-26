package com.aiinterviewer.admin.rbac;

import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.rbac.entity.AdminMenu;
import com.aiinterviewer.admin.rbac.entity.AdminPermission;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RbacService {

    private final JdbcTemplate jdbcTemplate;

    public List<AdminMenu> listMenus() {
        return jdbcTemplate.query(
                """
                SELECT id, parent_id, menu_code, menu_name, path, component, icon,
                       sort_order, visible, enabled, created_at, updated_at
                FROM t_admin_menu
                WHERE deleted_at IS NULL AND enabled = TRUE
                ORDER BY sort_order ASC, id ASC
                """,
                this::mapMenu);
    }

    public List<AdminPermission> listPermissions() {
        return jdbcTemplate.query(
                """
                SELECT id, menu_id, permission_code, permission_name, resource_type,
                       resource_path, http_method, enabled, description, created_at, updated_at
                FROM t_admin_permission
                WHERE deleted_at IS NULL AND enabled = TRUE
                ORDER BY id ASC
                """,
                this::mapPermission);
    }

    @Transactional
    public int bindRolePermissions(String roleCode, List<Long> permissionIds) {
        if (!StringUtils.hasText(roleCode)) {
            throw new AdminBusinessException(400, "角色编码不能为空");
        }
        if (!roleExists(roleCode)) {
            throw new AdminBusinessException(404, "角色不存在");
        }

        List<Long> distinctPermissionIds = permissionIds == null
                ? List.of()
                : permissionIds.stream().distinct().toList();
        ensurePermissionsExist(distinctPermissionIds);

        jdbcTemplate.update("DELETE FROM t_admin_role_permission WHERE role_code = ?", roleCode);
        for (Long permissionId : distinctPermissionIds) {
            jdbcTemplate.update(
                    "INSERT INTO t_admin_role_permission (role_code, permission_id) VALUES (?, ?)",
                    roleCode,
                    permissionId);
        }
        return distinctPermissionIds.size();
    }

    private boolean roleExists(String roleCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_role WHERE role_code = ?",
                Integer.class,
                roleCode);
        return count != null && count > 0;
    }

    private void ensurePermissionsExist(List<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", permissionIds.stream().map(id -> "?").toList());
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_admin_permission WHERE deleted_at IS NULL AND enabled = TRUE AND id IN ("
                        + placeholders + ")",
                Integer.class,
                permissionIds.toArray());
        if (count == null || count != permissionIds.size()) {
            throw new AdminBusinessException(404, "权限不存在或已禁用");
        }
    }

    private AdminMenu mapMenu(ResultSet rs, int rowNum) throws SQLException {
        Long parentId = rs.getObject("parent_id", Long.class);
        return new AdminMenu(
                rs.getLong("id"),
                parentId,
                rs.getString("menu_code"),
                rs.getString("menu_name"),
                rs.getString("path"),
                rs.getString("component"),
                rs.getString("icon"),
                rs.getInt("sort_order"),
                rs.getBoolean("visible"),
                rs.getBoolean("enabled"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private AdminPermission mapPermission(ResultSet rs, int rowNum) throws SQLException {
        Long menuId = rs.getObject("menu_id", Long.class);
        return new AdminPermission(
                rs.getLong("id"),
                menuId,
                rs.getString("permission_code"),
                rs.getString("permission_name"),
                rs.getString("resource_type"),
                rs.getString("resource_path"),
                rs.getString("http_method"),
                rs.getBoolean("enabled"),
                rs.getString("description"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
