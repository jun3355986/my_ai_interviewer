package com.aiinterviewer.admin.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET =
            "admin-test-secret-key-that-is-long-enough-for-hmac-sha256";

    @Test
    void generateAccessTokenIncludesAdminUserIdAndRoles() {
        JwtService jwtService = new JwtService(SECRET, 3_600_000L);

        String token = jwtService.generateAccessToken(1001L, List.of("ROLE_ADMIN", "ROLE_AUDITOR"));

        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.getUserId(token)).isEqualTo(1001L);
        assertThat(jwtService.getRoles(token)).containsExactly("ROLE_ADMIN", "ROLE_AUDITOR");
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwtService = new JwtService(SECRET, -1_000L);

        String token = jwtService.generateAccessToken(1001L, List.of("ROLE_ADMIN"));

        assertThat(jwtService.validateToken(token)).isFalse();
        assertThatThrownBy(() -> jwtService.getUserId(token))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("Token已过期");
    }
}
