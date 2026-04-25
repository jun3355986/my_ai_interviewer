package com.aiinterviewer.admin.notification;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.notification.entity.NotificationTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private static final int STATUS_SENT = 1;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([^}]*)\\s*}}");
    private static final Pattern VARIABLE_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    @AdminAudit(module = "NOTIFICATION", operation = "CREATE_TEMPLATE", targetType = "NOTIFICATION_TEMPLATE",
            targetIdFromResult = true)
    public String createTemplate(TemplateRequest request) {
        validateTemplateRequest(request, true);
        String templateCode = request.getTemplateCode().trim();
        if (activeTemplateExists(templateCode)) {
            throw new AdminBusinessException(409, "通知模板编码已存在");
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO t_notification_template
                        (template_code, template_name, channel, subject, content, variables,
                         enabled, created_by, updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    templateCode,
                    request.getTemplateName().trim(),
                    request.getChannel().trim(),
                    trimToNull(request.getSubject()),
                    request.getContent().trim(),
                    toVariablesJson(request.getVariables()),
                    request.getEnabled() == null || Boolean.TRUE.equals(request.getEnabled()),
                    request.getCreatedBy(),
                    request.getUpdatedBy() == null ? request.getCreatedBy() : request.getUpdatedBy());
        } catch (DataIntegrityViolationException ex) {
            throw new AdminBusinessException(409, "通知模板编码已存在", ex);
        }
        return templateCode;
    }

    @Transactional
    @AdminAudit(module = "NOTIFICATION", operation = "UPDATE_TEMPLATE", targetType = "NOTIFICATION_TEMPLATE",
            targetIdParam = "templateCode")
    public void updateTemplate(String templateCode, TemplateRequest request) {
        String normalizedCode = normalizeTemplateCode(templateCode);
        validateTemplateRequest(request, false);
        int updated = jdbcTemplate.update(
                """
                UPDATE t_notification_template
                SET template_name = ?,
                    channel = ?,
                    subject = ?,
                    content = ?,
                    variables = CAST(? AS jsonb),
                    enabled = ?,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE template_code = ?
                  AND deleted_at IS NULL
                """,
                request.getTemplateName().trim(),
                request.getChannel().trim(),
                trimToNull(request.getSubject()),
                request.getContent().trim(),
                toVariablesJson(request.getVariables()),
                request.getEnabled() == null || Boolean.TRUE.equals(request.getEnabled()),
                request.getUpdatedBy(),
                normalizedCode);
        if (updated == 0) {
            throw new AdminBusinessException(404, "通知模板不存在");
        }
    }

    public NotificationTemplate getTemplate(String templateCode) {
        String normalizedCode = normalizeTemplateCode(templateCode);
        List<NotificationTemplate> templates = jdbcTemplate.query(
                """
                SELECT id, template_code, template_name, channel, subject, content, variables::text,
                       enabled, created_by, updated_by, created_at, updated_at
                FROM t_notification_template
                WHERE template_code = ?
                  AND deleted_at IS NULL
                ORDER BY id DESC
                LIMIT 1
                """,
                this::mapTemplate,
                normalizedCode);
        if (templates.isEmpty()) {
            throw new AdminBusinessException(404, "通知模板不存在");
        }
        return templates.getFirst();
    }

    public List<NotificationTemplate> listTemplates() {
        return jdbcTemplate.query(
                """
                SELECT id, template_code, template_name, channel, subject, content, variables::text,
                       enabled, created_by, updated_by, created_at, updated_at
                FROM t_notification_template
                WHERE deleted_at IS NULL
                ORDER BY channel ASC, template_code ASC
                """,
                this::mapTemplate);
    }

    @Transactional
    @AdminAudit(module = "NOTIFICATION", operation = "SEND", targetType = "NOTIFICATION_TEMPLATE",
            targetIdParam = "arg0")
    public SendNotificationResponse sendNotification(SendNotificationRequest request) {
        validateSendRequest(request);
        NotificationTemplate template = getEnabledTemplate(request.getTemplateCode());
        validateSendVariables(template, request.getVariables());
        String title = render(template.getSubject(), request.getVariables());
        String content = render(template.getContent(), request.getVariables());
        assertNoUnresolvedPlaceholders(title);
        assertNoUnresolvedPlaceholders(content);
        Long notificationId = jdbcTemplate.queryForObject(
                """
                INSERT INTO t_notification
                    (user_id, type, template_code, title, content, related_type, related_id, status, send_time, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                request.getUserId(),
                template.getChannel(),
                template.getTemplateCode(),
                title,
                content,
                trimToNull(request.getRelatedType()),
                trimToNull(request.getRelatedId()),
                STATUS_SENT);
        SendNotificationResponse response = new SendNotificationResponse();
        response.setNotificationId(notificationId);
        response.setTemplateCode(template.getTemplateCode());
        response.setUserId(request.getUserId());
        response.setChannel(template.getChannel());
        response.setStatus(STATUS_SENT);
        return response;
    }

    private NotificationTemplate getEnabledTemplate(String templateCode) {
        NotificationTemplate template = getTemplate(templateCode);
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw new AdminBusinessException(400, "通知模板未启用");
        }
        return template;
    }

    private void validateTemplateRequest(TemplateRequest request, boolean creating) {
        if (request == null) {
            throw new AdminBusinessException(400, "通知模板参数不能为空");
        }
        if (creating && !StringUtils.hasText(request.getTemplateCode())) {
            throw new AdminBusinessException(400, "模板编码不能为空");
        }
        if (!StringUtils.hasText(request.getTemplateName())) {
            throw new AdminBusinessException(400, "模板名称不能为空");
        }
        if (!StringUtils.hasText(request.getChannel())) {
            throw new AdminBusinessException(400, "通知渠道不能为空");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new AdminBusinessException(400, "模板内容不能为空");
        }
        request.setVariables(validateAndNormalizeVariables(
                request.getVariables(),
                request.getSubject(),
                request.getContent()));
    }

    private void validateSendRequest(SendNotificationRequest request) {
        if (request == null) {
            throw new AdminBusinessException(400, "通知发送参数不能为空");
        }
        normalizeTemplateCode(request.getTemplateCode());
        if (request.getUserId() == null) {
            throw new AdminBusinessException(400, "接收用户不能为空");
        }
    }

    private List<String> validateAndNormalizeVariables(List<String> declaredVariables, String subject, String content) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (declaredVariables != null) {
            for (String variable : declaredVariables) {
                if (!StringUtils.hasText(variable)) {
                    throw new AdminBusinessException(400, "模板变量不能为空");
                }
                String trimmed = variable.trim();
                if (!VARIABLE_NAME_PATTERN.matcher(trimmed).matches()) {
                    throw new AdminBusinessException(400, "模板变量名格式不合法");
                }
                if (!normalized.add(trimmed)) {
                    throw new AdminBusinessException(400, "模板变量不能重复");
                }
            }
        }

        Set<String> placeholders = extractPlaceholders(subject, content);
        if (!normalized.equals(placeholders)) {
            throw new AdminBusinessException(400, "模板变量必须与占位符一致");
        }
        return List.copyOf(normalized);
    }

    private Set<String> extractPlaceholders(String... templates) {
        LinkedHashSet<String> placeholders = new LinkedHashSet<>();
        for (String template : templates) {
            if (!StringUtils.hasText(template)) {
                continue;
            }
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
            while (matcher.find()) {
                String variableName = matcher.group(1).trim();
                if (!StringUtils.hasText(variableName)) {
                    throw new AdminBusinessException(400, "模板占位符不能为空");
                }
                if (!VARIABLE_NAME_PATTERN.matcher(variableName).matches()) {
                    throw new AdminBusinessException(400, "模板占位符格式不合法");
                }
                placeholders.add(variableName);
            }
        }
        return placeholders;
    }

    private void validateSendVariables(NotificationTemplate template, Map<String, Object> providedVariables) {
        Set<String> requiredVariables = new LinkedHashSet<>(template.getVariables());
        Set<String> providedKeys = new LinkedHashSet<>();
        if (providedVariables != null) {
            for (String key : providedVariables.keySet()) {
                if (!StringUtils.hasText(key)) {
                    throw new AdminBusinessException(400, "通知变量名不能为空");
                }
                String normalizedKey = key.trim();
                if (!VARIABLE_NAME_PATTERN.matcher(normalizedKey).matches()) {
                    throw new AdminBusinessException(400, "通知变量名格式不合法");
                }
                providedKeys.add(normalizedKey);
            }
        }
        if (!providedKeys.containsAll(requiredVariables)) {
            throw new AdminBusinessException(400, "通知变量缺失");
        }
        if (!requiredVariables.containsAll(providedKeys)) {
            throw new AdminBusinessException(400, "通知变量包含未知项");
        }
    }

    private NotificationTemplate mapTemplate(ResultSet rs, int rowNum) throws SQLException {
        NotificationTemplate template = new NotificationTemplate();
        template.setId(rs.getLong("id"));
        template.setTemplateCode(rs.getString("template_code"));
        template.setTemplateName(rs.getString("template_name"));
        template.setChannel(rs.getString("channel"));
        template.setSubject(rs.getString("subject"));
        template.setContent(rs.getString("content"));
        template.setVariables(readVariables(rs.getString("variables")));
        template.setEnabled(rs.getBoolean("enabled"));
        template.setCreatedBy(readNullableLong(rs, "created_by"));
        template.setUpdatedBy(readNullableLong(rs, "updated_by"));
        template.setCreatedAt(readNullableDateTime(rs, "created_at"));
        template.setUpdatedAt(readNullableDateTime(rs, "updated_at"));
        return template;
    }

    private String render(String template, Map<String, Object> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            if (!StringUtils.hasText(entry.getKey())) {
                continue;
            }
            String placeholder = "\\{\\{\\s*" + Pattern.quote(entry.getKey().trim()) + "\\s*}}";
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            rendered = rendered.replaceAll(placeholder, java.util.regex.Matcher.quoteReplacement(value));
        }
        return rendered;
    }

    private void assertNoUnresolvedPlaceholders(String rendered) {
        if (StringUtils.hasText(rendered) && PLACEHOLDER_PATTERN.matcher(rendered).find()) {
            throw new AdminBusinessException(400, "通知内容存在未解析占位符");
        }
    }

    private boolean activeTemplateExists(String templateCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM t_notification_template
                WHERE template_code = ?
                  AND deleted_at IS NULL
                """,
                Integer.class,
                templateCode);
        return count != null && count > 0;
    }

    private String normalizeTemplateCode(String templateCode) {
        if (!StringUtils.hasText(templateCode)) {
            throw new AdminBusinessException(400, "模板编码不能为空");
        }
        return templateCode.trim();
    }

    private String toVariablesJson(List<String> variables) {
        try {
            return objectMapper.writeValueAsString(variables == null ? List.of() : variables);
        } catch (JsonProcessingException ex) {
            throw new AdminBusinessException(400, "模板变量格式不合法", ex);
        }
    }

    private List<String> readVariables(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> variables = objectMapper.readValue(json, new TypeReference<>() {
            });
            return variables == null ? List.of() : variables;
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Long readNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime readNullableDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Data
    public static class TemplateRequest {
        private String templateCode;
        private String templateName;
        private String channel;
        private String subject;
        private String content;
        private List<String> variables;
        private Boolean enabled;
        private Long createdBy;
        private Long updatedBy;
    }

    @Data
    public static class SendNotificationRequest {
        private String templateCode;
        private Long userId;
        private Map<String, Object> variables;
        private String relatedType;
        private String relatedId;

        @Override
        public String toString() {
            return templateCode;
        }
    }

    @Data
    public static class SendNotificationResponse {
        private Long notificationId;
        private String templateCode;
        private Long userId;
        private String channel;
        private Integer status;
    }
}
