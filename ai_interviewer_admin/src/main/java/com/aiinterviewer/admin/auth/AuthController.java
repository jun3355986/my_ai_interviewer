package com.aiinterviewer.admin.auth;

import com.aiinterviewer.admin.auth.dto.AdminLoginRequest;
import com.aiinterviewer.admin.auth.dto.AdminLoginResponse;
import com.aiinterviewer.admin.auth.dto.AdminLoginResponse.AdminUserSummary;
import com.aiinterviewer.admin.common.model.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    public Result<AdminUserSummary> me(Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        return Result.success(authService.getAdminUser(adminUserId));
    }
}
