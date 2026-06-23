package com.aiinterviewer.admin.observability.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class AiObservabilityStatsResponse {

    private Long traceCount;
    private Long totalLlmCalls;
    private Long failedLlmCalls;
    private BigDecimal llmFailureRate;
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private Long totalTokens;
    private BigDecimal averageLatencyMs;
    private Long providerPromptCacheHitTokens;
    private Long providerPromptCacheMissTokens;
    private Long providerPromptCacheHitCalls;
    private Long providerCacheReportedCalls;
    private Long providerCacheUnreportedCalls;
    private BigDecimal providerPromptCacheTokenHitRate;
    private BigDecimal providerPromptCacheCallHitRate;
}
