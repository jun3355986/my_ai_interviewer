package com.aiinterviewer.notification.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知DTO
 */
@Data
public class NotificationDTO {

    private Long id;
    private Long userId;
    private String type;
    private String typeText;
    private String title;
    private String content;
    private String relatedType;
    private String relatedId;
    private Integer status;
    private String statusText;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
