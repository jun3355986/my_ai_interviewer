package com.aiinterviewer.admin.observability.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class AiTraceListItem {

    private UUID id;
    private String requestId;
    private Long userId;
    private String username;
    private String sessionId;
    private String pythonSessionId;
    private String businessType;
    private String entrypoint;
    private String status;
    private String errorCode;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private Long durationMs;
    private OffsetDateTime createdAt;
    private Long stepCount;
    private Long llmCallCount;
    private Long totalTokens;
    private Long failedLlmCalls;
    private Boolean fallbackUsed;
    private String provider;
    private String model;
    private BigDecimal providerPromptCacheTokenHitRate;
    private BigDecimal providerPromptCacheCallHitRate;
}
