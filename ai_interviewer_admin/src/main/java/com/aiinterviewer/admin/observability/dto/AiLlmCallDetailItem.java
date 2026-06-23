package com.aiinterviewer.admin.observability.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class AiLlmCallDetailItem {

    private UUID id;
    private UUID traceId;
    private UUID stepId;
    private String callType;
    private String provider;
    private String model;
    private Boolean fallbackUsed;
    private String fallbackFromModel;
    private String status;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private String tokenSource;
    private Long promptCacheHitTokens;
    private Long promptCacheMissTokens;
    private BigDecimal promptCacheHitRate;
    private Boolean cacheReportedByProvider;
    private Long latencyMs;
    private String rawUsageJson;
    private String metadataJson;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private OffsetDateTime createdAt;
}
