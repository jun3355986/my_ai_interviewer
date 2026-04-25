package com.aiinterviewer.admin.common.security;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AdminJwtSecretValidator {

    static final String DEFAULT_SECRET = "ai-interviewer-admin-default-secret-key-change-me-in-production";

    private final String secret;
    private final Environment environment;

    public AdminJwtSecretValidator(@Value("${admin.jwt.secret}") String secret, Environment environment) {
        this.secret = secret;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (isProductionProfileActive() && DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException("admin.jwt.secret must be overridden in prod/production profiles");
        }
    }

    private boolean isProductionProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
    }
}
