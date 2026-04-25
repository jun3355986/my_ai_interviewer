package com.aiinterviewer.admin.user;

import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.common.model.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Result<PageResult<AdminUserService.AdminUserListItem>> listUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size) {
        AdminUserService.AdminUserQuery query = new AdminUserService.AdminUserQuery();
        query.setUsername(username);
        query.setEmail(email);
        query.setPhone(phone);
        query.setStatus(status);
        query.setCurrent(current);
        query.setSize(size);
        return Result.success(adminUserService.listUsers(query));
    }

    @PatchMapping("/{userId}/disable")
    public Result<Void> disableUser(@PathVariable Long userId) {
        adminUserService.disableUser(userId);
        return Result.success();
    }

    @PostMapping("/{userId}/reset-password")
    public Result<Void> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ResetPasswordRequest request) {
        adminUserService.resetPassword(userId, request.newPassword());
        return Result.success();
    }

    public record ResetPasswordRequest(@NotBlank(message = "新密码不能为空") String newPassword) {
    }
}
