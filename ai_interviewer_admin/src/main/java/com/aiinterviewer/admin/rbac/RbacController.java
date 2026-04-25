package com.aiinterviewer.admin.rbac;

import com.aiinterviewer.admin.common.model.Result;
import com.aiinterviewer.admin.rbac.entity.AdminMenu;
import com.aiinterviewer.admin.rbac.entity.AdminPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/rbac")
@RequiredArgsConstructor
public class RbacController {

    private final RbacService rbacService;

    @GetMapping("/menus")
    public Result<List<AdminMenu>> listMenus() {
        return Result.success(rbacService.listMenus());
    }

    @GetMapping("/permissions")
    public Result<List<AdminPermission>> listPermissions() {
        return Result.success(rbacService.listPermissions());
    }

    @PostMapping("/roles/{roleCode}/permissions")
    public Result<Integer> bindRolePermissions(
            @PathVariable String roleCode,
            @Valid @RequestBody BindRolePermissionsRequest request) {
        return Result.success(rbacService.bindRolePermissions(roleCode, request.getPermissionIds()));
    }

    @Data
    public static class BindRolePermissionsRequest {

        @NotNull(message = "权限ID列表不能为空")
        private List<Long> permissionIds;
    }
}
