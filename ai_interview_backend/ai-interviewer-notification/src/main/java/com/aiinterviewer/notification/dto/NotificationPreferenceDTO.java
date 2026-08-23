package com.aiinterviewer.notification.dto;

import lombok.Data;

/**
 * 通知偏好 DTO
 */
@Data
public class NotificationPreferenceDTO {

    private Boolean progressNotify;
    private Boolean evaluationNotify;
}
