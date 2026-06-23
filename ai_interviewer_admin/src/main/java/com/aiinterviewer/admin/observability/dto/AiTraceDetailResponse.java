package com.aiinterviewer.admin.observability.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class AiTraceDetailResponse {

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
    private String metadataJson;
    private OffsetDateTime createdAt;
    private List<AiTraceStepItem> steps = List.of();
    private List<AiLlmCallDetailItem> llmCalls = List.of();
}
