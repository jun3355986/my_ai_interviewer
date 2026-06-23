package com.aiinterviewer.admin.observability.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class AiTraceStepItem {

    private UUID id;
    private UUID traceId;
    private Integer stepOrder;
    private String stepType;
    private String stepName;
    private String status;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private Long durationMs;
    private String metadataJson;
    private String errorMessage;
    private OffsetDateTime createdAt;
}
