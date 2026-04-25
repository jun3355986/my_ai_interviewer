package com.aiinterviewer.admin.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AdminJwtSecretValidatorTest {

    @Test
    void allowsDefaultSecretOutsideProductionProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        AdminJwtSecretValidator validator =
                new AdminJwtSecretValidator(AdminJwtSecretValidator.DEFAULT_SECRET, environment);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsDefaultSecretInProdProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AdminJwtSecretValidator validator =
                new AdminJwtSecretValidator(AdminJwtSecretValidator.DEFAULT_SECRET, environment);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin.jwt.secret must be overridden");
    }

    @Test
    void rejectsComposeLocalSecretInProdProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AdminJwtSecretValidator validator =
                new AdminJwtSecretValidator(AdminJwtSecretValidator.LOCAL_COMPOSE_SECRET, environment);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin.jwt.secret must be overridden");
    }

    @Test
    void rejectsExampleSecretInProdProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        AdminJwtSecretValidator validator =
                new AdminJwtSecretValidator(AdminJwtSecretValidator.EXAMPLE_SECRET, environment);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin.jwt.secret must be overridden");
    }

    @Test
    void allowsCustomSecretInProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        AdminJwtSecretValidator validator =
                new AdminJwtSecretValidator("custom-admin-secret-key-that-is-safe-enough", environment);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
