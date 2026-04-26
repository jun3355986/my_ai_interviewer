package com.aiinterviewer.admin.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.admin.rbac.entity.AdminMenu;
import com.aiinterviewer.admin.rbac.entity.AdminPermission;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RbacServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private RbacService rbacService;

    @Test
    void adminCanListMenusAndPermissions() {
        Long menuId = jdbcTemplate.queryForObject(
                """
                INSERT INTO t_admin_menu (menu_code, menu_name, path, sort_order)
                VALUES ('dashboard', 'Dashboard', '/dashboard', 10)
                RETURNING id
                """,
                Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO t_admin_permission (menu_id, permission_code, permission_name, resource_path, http_method)
                VALUES (?, 'dashboard:view', 'View Dashboard', '/admin/dashboard', 'GET')
                """,
                menuId);

        List<AdminMenu> menus = rbacService.listMenus();
        List<AdminPermission> permissions = rbacService.listPermissions();

        assertThat(menus).extracting(AdminMenu::getMenuCode).containsExactly("dashboard");
        assertThat(permissions).extracting(AdminPermission::getPermissionCode).containsExactly("dashboard:view");
    }

    @Test
    void adminCanBindRolePermissions() {
        jdbcTemplate.update("INSERT INTO t_role (role_code, role_name) VALUES ('ROLE_ADMIN', 'Admin')");
        Long firstPermissionId = createPermission("user:list", "List Users");
        Long secondPermissionId = createPermission("user:disable", "Disable Users");

        int boundCount = rbacService.bindRolePermissions("ROLE_ADMIN", List.of(firstPermissionId, secondPermissionId));

        Integer relationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_admin_role_permission WHERE role_code = 'ROLE_ADMIN'",
                Integer.class);
        assertThat(boundCount).isEqualTo(2);
        assertThat(relationCount).isEqualTo(2);
    }

    private Long createPermission(String code, String name) {
        Long menuId = jdbcTemplate.queryForObject(
                """
                INSERT INTO t_admin_menu (menu_code, menu_name)
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                code.replace(':', '_'),
                name);
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO t_admin_permission (menu_id, permission_code, permission_name)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                menuId,
                code,
                name);
    }
}
