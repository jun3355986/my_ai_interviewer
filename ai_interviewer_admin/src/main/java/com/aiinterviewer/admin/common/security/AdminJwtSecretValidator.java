package com.aiinterviewer.admin.common.security;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AdminJwtSecretValidator {

    static final String DEFAULT_SECRET = "ai-interviewer-admin-default-secret-key-change-me-in-production";
    static final String LOCAL_COMPOSE_SECRET = "ai-interviewer-admin-local-secret-change-me-please";
    static final String EXAMPLE_SECRET = "your-admin-256-bit-secret-key-for-jwt-signing-change-me";
    private static final Set<String> UNSAFE_SECRETS = Set.of(DEFAULT_SECRET, LOCAL_COMPOSE_SECRET, EXAMPLE_SECRET);

    private final String secret;
    private final Environment environment;

    public AdminJwtSecretValidator(@Value("${admin.jwt.secret}") String secret, Environment environment) {
        this.secret = secret;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (isProductionProfileActive() && UNSAFE_SECRETS.contains(secret)) {
            throw new IllegalStateException("admin.jwt.secret must be overridden in prod/production profiles");
        }
    }

    private boolean isProductionProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
    }
}
