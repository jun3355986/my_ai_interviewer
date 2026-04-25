package com.aiinterviewer.admin.systemconfig;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private static final String MASKED_VALUE = "******";
    private static final String DEFAULT_CONFIG_TYPE = "STRING";
    private static final String DEFAULT_CONFIG_GROUP = "DEFAULT";
    private static final List<String> SECRET_KEYWORDS =
            List.of("password", "secret", "token", "key", "credential");

    private final JdbcTemplate jdbcTemplate;

    public List<SystemConfigResponse> listConfigs(String configGroup) {
        List<Object> args = new java.util.ArrayList<>();
        StringBuilder sql = new StringBuilder(
                """
                SELECT id, config_key, config_value, config_type, config_group, description,
                       encrypted, editable, created_by, updated_by, created_at, updated_at
                FROM t_system_config
                WHERE deleted_at IS NULL
                """);
        if (StringUtils.hasText(configGroup)) {
            sql.append(" AND config_group = ?");
            args.add(configGroup.trim());
        }
        sql.append(" ORDER BY config_group ASC, config_key ASC");
        return jdbcTemplate.query(sql.toString(), this::mapConfig, args.toArray());
    }

    public SystemConfigResponse getConfig(String configKey) {
        String normalizedKey = normalizeConfigKey(configKey);
        List<SystemConfigResponse> configs = jdbcTemplate.query(
                """
                SELECT id, config_key, config_value, config_type, config_group, description,
                       encrypted, editable, created_by, updated_by, created_at, updated_at
                FROM t_system_config
                WHERE config_key = ?
                  AND deleted_at IS NULL
                ORDER BY id DESC
                LIMIT 1
                """,
                this::mapConfig,
                normalizedKey);
        if (configs.isEmpty()) {
            throw new AdminBusinessException(404, "系统配置不存在");
        }
        return configs.getFirst();
    }

    @Transactional
    @AdminAudit(module = "SYSTEM_CONFIG", operation = "UPDATE_CONFIG", targetType = "SYSTEM_CONFIG",
            targetIdParam = "configKey")
    public void updateConfig(String configKey, SystemConfigUpdateRequest request) {
        String normalizedKey = normalizeConfigKey(configKey);
        validateUpdateRequest(request);
        Long existingId = findConfigId(normalizedKey);
        if (existingId == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO t_system_config
                        (config_key, config_value, config_type, config_group, description,
                         encrypted, editable, created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    normalizedKey,
                    request.getConfigValue(),
                    defaultIfBlank(request.getConfigType(), DEFAULT_CONFIG_TYPE),
                    defaultIfBlank(request.getConfigGroup(), DEFAULT_CONFIG_GROUP),
                    trimToNull(request.getDescription()),
                    Boolean.TRUE.equals(request.getEncrypted()),
                    request.getEditable() == null || Boolean.TRUE.equals(request.getEditable()),
                    request.getUpdatedBy(),
                    request.getUpdatedBy());
            return;
        }

        Boolean editable = jdbcTemplate.queryForObject(
                "SELECT editable FROM t_system_config WHERE id = ?",
                Boolean.class,
                existingId);
        if (Boolean.FALSE.equals(editable)) {
            throw new AdminBusinessException(400, "系统配置不允许编辑");
        }
        String configValue = resolveUpdateValue(normalizedKey, request, existingId);
        jdbcTemplate.update(
                """
                UPDATE t_system_config
                SET config_value = ?,
                    config_type = ?,
                    config_group = ?,
                    description = ?,
                    encrypted = ?,
                    editable = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND deleted_at IS NULL
                """,
                configValue,
                defaultIfBlank(request.getConfigType(), DEFAULT_CONFIG_TYPE),
                defaultIfBlank(request.getConfigGroup(), DEFAULT_CONFIG_GROUP),
                trimToNull(request.getDescription()),
                Boolean.TRUE.equals(request.getEncrypted()),
                request.getEditable() == null || Boolean.TRUE.equals(request.getEditable()),
                request.getUpdatedBy(),
                existingId);
    }

    private String resolveUpdateValue(String configKey, SystemConfigUpdateRequest request, Long existingId) {
        if (!MASKED_VALUE.equals(request.getConfigValue())) {
            return request.getConfigValue();
        }
        Boolean encrypted = jdbcTemplate.queryForObject(
                "SELECT encrypted FROM t_system_config WHERE id = ?",
                Boolean.class,
                existingId);
        if (!Boolean.TRUE.equals(encrypted) && !isSecretLikeKey(configKey)) {
            return request.getConfigValue();
        }
        return jdbcTemplate.queryForObject(
                "SELECT config_value FROM t_system_config WHERE id = ?",
                String.class,
                existingId);
    }

    private Long findConfigId(String configKey) {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT id
                FROM t_system_config
                WHERE config_key = ?
                  AND deleted_at IS NULL
                ORDER BY id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getLong("id"),
                configKey);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private void validateUpdateRequest(SystemConfigUpdateRequest request) {
        if (request == null) {
            throw new AdminBusinessException(400, "系统配置参数不能为空");
        }
        if (request.getConfigValue() == null) {
            throw new AdminBusinessException(400, "配置值不能为空");
        }
    }

    private SystemConfigResponse mapConfig(ResultSet rs, int rowNum) throws SQLException {
        SystemConfigResponse response = new SystemConfigResponse();
        response.setId(rs.getLong("id"));
        response.setConfigKey(rs.getString("config_key"));
        response.setConfigType(rs.getString("config_type"));
        response.setConfigGroup(rs.getString("config_group"));
        response.setDescription(rs.getString("description"));
        response.setEncrypted(rs.getBoolean("encrypted"));
        response.setEditable(rs.getBoolean("editable"));
        response.setConfigValue(maskIfNeeded(response.getConfigKey(), rs.getString("config_value"), response.getEncrypted()));
        response.setCreatedBy(readNullableLong(rs, "created_by"));
        response.setUpdatedBy(readNullableLong(rs, "updated_by"));
        response.setCreatedAt(readNullableDateTime(rs, "created_at"));
        response.setUpdatedAt(readNullableDateTime(rs, "updated_at"));
        return response;
    }

    private String maskIfNeeded(String configKey, String configValue, Boolean encrypted) {
        if (configValue == null) {
            return null;
        }
        if (Boolean.TRUE.equals(encrypted) || isSecretLikeKey(configKey)) {
            return MASKED_VALUE;
        }
        return configValue;
    }

    private boolean isSecretLikeKey(String configKey) {
        if (configKey == null) {
            return false;
        }
        String lowerKey = configKey.toLowerCase(Locale.ROOT);
        return SECRET_KEYWORDS.stream().anyMatch(lowerKey::contains);
    }

    private String normalizeConfigKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            throw new AdminBusinessException(400, "配置键不能为空");
        }
        return configKey.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long readNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime readNullableDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    @Data
    public static class SystemConfigUpdateRequest {
        private String configValue;
        private String configType;
        private String configGroup;
        private String description;
        private Boolean encrypted;
        private Boolean editable;
        private Long updatedBy;
    }

    @Data
    public static class SystemConfigResponse {
        private Long id;
        private String configKey;
        private String configValue;
        private String configType;
        private String configGroup;
        private String description;
        private Boolean encrypted;
        private Boolean editable;
        private Long createdBy;
        private Long updatedBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
