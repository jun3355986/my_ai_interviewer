package com.aiinterviewer.notification.dto;

import lombok.Data;

/**
 * 发送通知请求
 */
@Data
public class SendNotificationRequest {

    /**
     * 通知类型: EMAIL, SMS, IN_APP
     */
    private String type;

    /**
     * 接收用户ID (可选, 用于站内信)
     */
    private Long userId;

    /**
     * 接收邮箱 (用于邮件)
     */
    private String email;

    /**
     * 接收手机号 (用于短信)
     */
    private String phone;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 关联类型
     */
    private String relatedType;

    /**
     * 关联ID
     */
    private String relatedId;
}
