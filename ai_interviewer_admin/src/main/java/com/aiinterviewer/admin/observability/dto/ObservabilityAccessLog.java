package com.aiinterviewer.admin.observability.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class ObservabilityAccessLog {

    private Long id;
    private Long adminUserId;
    private UUID traceId;
    private UUID llmCallId;
    private String accessType;
    private String requestUri;
    private String ipAddress;
    private String userAgent;
    private OffsetDateTime createdAt;
}
