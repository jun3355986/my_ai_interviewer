package com.aiinterviewer.admin.notification.entity;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class NotificationTemplate {

    private Long id;
    private String templateCode;
    private String templateName;
    private String channel;
    private String subject;
    private String content;
    private List<String> variables;
    private Boolean enabled;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
