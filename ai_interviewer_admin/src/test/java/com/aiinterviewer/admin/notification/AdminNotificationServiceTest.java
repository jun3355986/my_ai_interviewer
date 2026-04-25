package com.aiinterviewer.admin.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.admin.notification.entity.NotificationTemplate;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminNotificationServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private AdminNotificationService adminNotificationService;

    @Test
    void notificationTemplateCreateAndUpdateWorks() {
        AdminNotificationService.TemplateRequest createRequest = templateRequest();

        adminNotificationService.createTemplate(createRequest);

        NotificationTemplate created = adminNotificationService.getTemplate("INTERVIEW_REMINDER");
        assertThat(created.getTemplateName()).isEqualTo("Interview reminder");
        assertThat(created.getChannel()).isEqualTo("IN_APP");
        assertThat(created.getContent()).isEqualTo("Hi {{name}}, your interview starts at {{time}}.");
        assertThat(created.getVariables()).containsExactly("name", "time");

        AdminNotificationService.TemplateRequest updateRequest = templateRequest();
        updateRequest.setTemplateName("Interview reminder updated");
        updateRequest.setSubject("Updated subject");
        updateRequest.setContent("Hello {{name}}, join {{room}}.");
        updateRequest.setVariables(List.of("name", "room"));
        updateRequest.setEnabled(false);
        updateRequest.setUpdatedBy(8L);

        adminNotificationService.updateTemplate("INTERVIEW_REMINDER", updateRequest);

        NotificationTemplate updated = adminNotificationService.getTemplate("INTERVIEW_REMINDER");
        assertThat(updated.getTemplateName()).isEqualTo("Interview reminder updated");
        assertThat(updated.getSubject()).isEqualTo("Updated subject");
        assertThat(updated.getContent()).isEqualTo("Hello {{name}}, join {{room}}.");
        assertThat(updated.getVariables()).containsExactly("name", "room");
        assertThat(updated.getEnabled()).isFalse();
    }

    @Test
    void sendNotificationWritesNotificationAndReferencesSelectedTemplate() {
        Long userId = insertUser();
        adminNotificationService.createTemplate(templateRequest());

        AdminNotificationService.SendNotificationRequest request =
                new AdminNotificationService.SendNotificationRequest();
        request.setTemplateCode("INTERVIEW_REMINDER");
        request.setUserId(userId);
        request.setVariables(Map.of("name", "Ada", "time", "10:00"));
        request.setRelatedType("INTERVIEW");
        request.setRelatedId("session-1");

        AdminNotificationService.SendNotificationResponse response =
                adminNotificationService.sendNotification(request);

        Map<String, Object> notification = jdbcTemplate.queryForMap(
                "SELECT type, title, content, related_type, related_id, status FROM t_notification WHERE id = ?",
                response.getNotificationId());
        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM t_admin_operation_log
                WHERE module = 'NOTIFICATION'
                  AND operation = 'SEND'
                  AND target_type = 'NOTIFICATION_TEMPLATE'
                  AND target_id = 'INTERVIEW_REMINDER'
                  AND result = 'SUCCESS'
                """,
                Integer.class);

        assertThat(response.getTemplateCode()).isEqualTo("INTERVIEW_REMINDER");
        assertThat(notification.get("type")).isEqualTo("IN_APP");
        assertThat(notification.get("title")).isEqualTo("Interview soon");
        assertThat(notification.get("content")).isEqualTo("Hi Ada, your interview starts at 10:00.");
        assertThat(notification.get("related_type")).isEqualTo("INTERVIEW");
        assertThat(notification.get("related_id")).isEqualTo("session-1");
        assertThat(((Number) notification.get("status")).intValue()).isEqualTo(1);
        assertThat(auditCount).isEqualTo(1);
    }

    private AdminNotificationService.TemplateRequest templateRequest() {
        AdminNotificationService.TemplateRequest request = new AdminNotificationService.TemplateRequest();
        request.setTemplateCode("INTERVIEW_REMINDER");
        request.setTemplateName("Interview reminder");
        request.setChannel("IN_APP");
        request.setSubject("Interview soon");
        request.setContent("Hi {{name}}, your interview starts at {{time}}.");
        request.setVariables(List.of("name", "time"));
        request.setEnabled(true);
        request.setCreatedBy(7L);
        request.setUpdatedBy(7L);
        return request;
    }

    private Long insertUser() {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO t_user (username, email, password_hash, nickname, status)
                VALUES ('candidate', 'candidate@example.test', 'hash', 'Candidate', 1)
                RETURNING id
                """,
                Long.class);
    }
}
