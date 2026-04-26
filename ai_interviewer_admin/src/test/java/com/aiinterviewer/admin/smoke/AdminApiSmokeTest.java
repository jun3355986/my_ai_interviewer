package com.aiinterviewer.admin.smoke;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminApiSmokeTest extends AdminPostgresIntegrationTest {

    private static final String ADMIN_USERNAME = "smoke_admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void seedAdminAndLogin() throws Exception {
        createAdminUser();
        MvcResult result = mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "smoke_admin",
                                  "password": "admin123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        adminToken = response.path("data").path("accessToken").asText();
    }

    @ParameterizedTest(name = "GET {0} returns success wrapper")
    @MethodSource("adminGetEndpoints")
    void adminGetEndpointsReturnSuccessWrapper(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    private static Stream<String> adminGetEndpoints() {
        return Stream.of(
                "/admin/auth/me",
                "/admin/dashboard/overview",
                "/admin/users",
                "/admin/jobs",
                "/admin/interviews",
                "/admin/questions",
                "/admin/audit/logs");
    }

    private Long createAdminUser() {
        Long userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO t_user (username, email, phone, password_hash, nickname, status)
                VALUES (?, ?, ?, ?, ?, 1)
                RETURNING id
                """,
                Long.class,
                ADMIN_USERNAME,
                "smoke.admin@example.com",
                "13900000000",
                passwordEncoder.encode("admin123"),
                "Smoke Admin");
        Long roleId = jdbcTemplate.queryForObject(
                """
                INSERT INTO t_role (role_code, role_name)
                VALUES ('ROLE_ADMIN', 'Admin')
                RETURNING id
                """,
                Long.class);
        jdbcTemplate.update("INSERT INTO t_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }
}
