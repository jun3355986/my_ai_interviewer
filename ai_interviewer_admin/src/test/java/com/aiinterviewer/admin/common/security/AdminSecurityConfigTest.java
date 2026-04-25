package com.aiinterviewer.admin.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiinterviewer.admin.common.model.Result;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = AdminSecurityConfigTest.TestAdminController.class)
@Import({
    AdminSecurityConfig.class,
    AdminSecurityConfigTest.JwtTestConfig.class,
    AdminSecurityConfigTest.TestAdminController.class
})
class AdminSecurityConfigTest {

    private static final String SECRET =
            "admin-test-secret-key-that-is-long-enough-for-hmac-sha256";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void loginWithoutTokenIsPermitAll() throws Exception {
        mockMvc.perform(post("/admin/auth/login")).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/protected")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithValidBearerTokenReturnsOk() throws Exception {
        String token = jwtService.generateAccessToken(1001L, List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/admin/protected").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithRoleUserTokenReturnsForbidden() throws Exception {
        String token = jwtService.generateAccessToken(1001L, List.of("ROLE_USER"));

        mockMvc.perform(get("/admin/protected").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointWithEmptyRolesTokenReturnsForbidden() throws Exception {
        String token = jwtService.generateAccessToken(1001L, List.of());

        mockMvc.perform(get("/admin/protected").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointWithInvalidTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/protected").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithExpiredTokenReturnsUnauthorized() throws Exception {
        JwtService expiredJwtService = new JwtService(SECRET, -1_000L);
        String token = expiredJwtService.generateAccessToken(1001L, List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/admin/protected").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonWhitelistedNonAdminEndpointIsDeniedByDefault() throws Exception {
        mockMvc.perform(get("/internal/accidental")).andExpect(status().isUnauthorized());
    }

    @RestController
    public static class TestAdminController {

        @PostMapping("/admin/auth/login")
        public Result<Void> login() {
            return Result.success();
        }

        @GetMapping("/admin/protected")
        public Result<String> protectedEndpoint() {
            return Result.success("ok");
        }

        @GetMapping("/internal/accidental")
        public Result<String> accidentalEndpoint() {
            return Result.success("should not be exposed");
        }
    }

    @TestConfiguration
    static class JwtTestConfig {

        @Bean
        JwtService jwtService() {
            return new JwtService(SECRET, 3_600_000L);
        }
    }
}
