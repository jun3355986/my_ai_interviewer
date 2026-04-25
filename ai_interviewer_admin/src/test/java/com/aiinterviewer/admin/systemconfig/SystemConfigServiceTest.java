package com.aiinterviewer.admin.systemconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SystemConfigServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private SystemConfigService systemConfigService;

    @Test
    void systemConfigReturnsMaskedValueForSecretLikeKeys() {
        jdbcTemplate.update(
                """
                INSERT INTO t_system_config
                    (config_key, config_value, config_type, config_group, description, encrypted, editable)
                VALUES
                    ('deepseek.api.key', 'sk-real-value', 'STRING', 'AI', 'DeepSeek API key', FALSE, TRUE),
                    ('smtp.password', 'plain-password', 'STRING', 'NOTIFICATION', 'SMTP password', FALSE, TRUE),
                    ('public.site_name', 'AI Interviewer', 'STRING', 'GENERAL', 'Site name', FALSE, TRUE),
                    ('internal.endpoint', 'https://example.test', 'STRING', 'GENERAL', 'Encrypted value', TRUE, TRUE)
                """);

        List<SystemConfigService.SystemConfigResponse> configs = systemConfigService.listConfigs(null);

        assertThat(valueOf(configs, "deepseek.api.key")).isEqualTo("******");
        assertThat(valueOf(configs, "smtp.password")).isEqualTo("******");
        assertThat(valueOf(configs, "internal.endpoint")).isEqualTo("******");
        assertThat(valueOf(configs, "public.site_name")).isEqualTo("AI Interviewer");

        SystemConfigService.SystemConfigResponse detail = systemConfigService.getConfig("deepseek.api.key");
        assertThat(detail.getConfigValue()).isEqualTo("******");
    }

    @Test
    void systemConfigUpdateWritesAuditLog() {
        SystemConfigService.SystemConfigUpdateRequest request = new SystemConfigService.SystemConfigUpdateRequest();
        request.setConfigValue("enabled");
        request.setConfigType("STRING");
        request.setConfigGroup("FEATURE");
        request.setDescription("Feature switch");
        request.setEncrypted(false);
        request.setEditable(true);
        request.setUpdatedBy(42L);

        systemConfigService.updateConfig("feature.interview.mock", request);

        String value = jdbcTemplate.queryForObject(
                "SELECT config_value FROM t_system_config WHERE config_key = 'feature.interview.mock'",
                String.class);
        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM t_admin_operation_log
                WHERE module = 'SYSTEM_CONFIG'
                  AND operation = 'UPDATE_CONFIG'
                  AND target_type = 'SYSTEM_CONFIG'
                  AND target_id = 'feature.interview.mock'
                  AND result = 'SUCCESS'
                """,
                Integer.class);

        assertThat(value).isEqualTo("enabled");
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void maskedSensitiveConfigUpdateKeepsExistingValue() {
        jdbcTemplate.update(
                """
                INSERT INTO t_system_config
                    (config_key, config_value, config_type, config_group, description, encrypted, editable)
                VALUES ('deepseek.api.key', 'sk-real', 'STRING', 'AI', 'DeepSeek API key', FALSE, TRUE)
                """);
        SystemConfigService.SystemConfigUpdateRequest request = new SystemConfigService.SystemConfigUpdateRequest();
        request.setConfigValue("******");
        request.setConfigType("STRING");
        request.setConfigGroup("AI");
        request.setDescription("DeepSeek API key");
        request.setEncrypted(false);
        request.setEditable(true);
        request.setUpdatedBy(42L);

        systemConfigService.updateConfig("deepseek.api.key", request);

        String value = jdbcTemplate.queryForObject(
                "SELECT config_value FROM t_system_config WHERE config_key = 'deepseek.api.key'",
                String.class);
        assertThat(value).isEqualTo("sk-real");
    }

    private String valueOf(List<SystemConfigService.SystemConfigResponse> configs, String configKey) {
        return configs.stream()
                .filter(config -> configKey.equals(config.getConfigKey()))
                .findFirst()
                .orElseThrow()
                .getConfigValue();
    }
}
