package com.aiinterviewer.admin.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.admin.observability.dto.AiObservabilityStatsResponse;
import com.aiinterviewer.admin.observability.dto.AiTraceQuery;
import com.aiinterviewer.admin.observability.dto.LlmCallRawPayload;
import com.aiinterviewer.admin.observability.dto.ObservabilityAccessLog;
import com.aiinterviewer.admin.observability.mapper.AiObservabilityMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiObservabilityServiceTest {

    @Mock
    private AiObservabilityMapper mapper;

    private AiObservabilityService service;

    @BeforeEach
    void setUp() {
        service = new AiObservabilityService(mapper);
    }

    @Test
    void statsExcludeUnreportedProviderCacheCallsFromCacheDenominator() {
        AiTraceQuery query = queryForToday();
        when(mapper.selectStats(query)).thenReturn(statsRow(
                10L,
                2L,
                1_000L,
                600L,
                400L,
                3L,
                6L,
                2L));

        AiObservabilityStatsResponse stats = service.getStats(query);

        assertThat(stats.getProviderPromptCacheTokenHitRate()).isEqualByComparingTo("0.600000");
        assertThat(stats.getProviderPromptCacheCallHitRate()).isEqualByComparingTo("0.500000");
        assertThat(stats.getProviderCacheUnreportedCalls()).isEqualTo(2L);
    }

    @Test
    void rawPayloadAccessWritesAuditLog() {
        UUID callId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        Long adminUserId = 9001L;
        when(mapper.selectLlmCallRawPayload(callId)).thenReturn(rawPayload(callId));

        service.getLlmCallRawPayload(callId, adminUserId, "PROMPT");

        verify(mapper).insertAccessLog(argThat(log ->
                "PROMPT".equals(log.getAccessType())
                        && adminUserId.equals(log.getAdminUserId())
                        && callId.equals(log.getLlmCallId())));
    }

    private AiTraceQuery queryForToday() {
        AiTraceQuery query = new AiTraceQuery();
        query.setStartedFrom(OffsetDateTime.parse("2026-06-23T00:00:00+08:00"));
        query.setStartedTo(OffsetDateTime.parse("2026-06-24T00:00:00+08:00"));
        return query;
    }

    private AiObservabilityStatsResponse statsRow(
            Long totalCalls,
            Long failedCalls,
            Long promptTokens,
            Long providerCacheHitTokens,
            Long providerCacheMissTokens,
            Long providerCacheHitCalls,
            Long providerCacheReportedCalls,
            Long providerCacheUnreportedCalls) {
        AiObservabilityStatsResponse stats = new AiObservabilityStatsResponse();
        stats.setTotalLlmCalls(totalCalls);
        stats.setFailedLlmCalls(failedCalls);
        stats.setTotalPromptTokens(promptTokens);
        stats.setProviderPromptCacheHitTokens(providerCacheHitTokens);
        stats.setProviderPromptCacheMissTokens(providerCacheMissTokens);
        stats.setProviderPromptCacheHitCalls(providerCacheHitCalls);
        stats.setProviderCacheReportedCalls(providerCacheReportedCalls);
        stats.setProviderCacheUnreportedCalls(providerCacheUnreportedCalls);
        stats.setProviderPromptCacheTokenHitRate(BigDecimal.ZERO);
        stats.setProviderPromptCacheCallHitRate(BigDecimal.ZERO);
        return stats;
    }

    private LlmCallRawPayload rawPayload(UUID callId) {
        LlmCallRawPayload payload = new LlmCallRawPayload();
        payload.setCallId(callId);
        payload.setTraceId(UUID.fromString("00000000-0000-0000-0000-000000000201"));
        payload.setPromptText("full prompt");
        payload.setResponseText("full response");
        return payload;
    }
}
