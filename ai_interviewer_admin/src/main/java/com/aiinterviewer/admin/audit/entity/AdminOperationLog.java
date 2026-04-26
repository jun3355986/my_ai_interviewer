package com.aiinterviewer.admin.audit.entity;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class AdminOperationLog {

    private Long id;
    private Long adminUserId;
    private String module;
    private String operation;
    private String targetType;
    private String targetId;
    private String requestUri;
    private String requestMethod;
    private String requestParams;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String ipAddress;
    private String userAgent;
    private String result;
    private String errorMessage;
    private Long durationMs;
    private OffsetDateTime createdAt;
}
