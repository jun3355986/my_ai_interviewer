package com.aiinterviewer.admin.auth.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private AdminUserSummary admin;
    private List<String> roles;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminUserSummary {

        private Long id;
        private String username;
        private String nickname;
        private String email;
        private String phone;
        private String avatarUrl;
    }
}
