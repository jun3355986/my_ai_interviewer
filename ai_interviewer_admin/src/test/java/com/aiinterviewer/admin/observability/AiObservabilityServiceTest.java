package com.aiinterviewer.admin.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.admin.observability.dto.AiLlmCallDetailItem;
import com.aiinterviewer.admin.observability.dto.AiObservabilityStatsResponse;
import com.aiinterviewer.admin.observability.dto.AiTraceQuery;
import com.aiinterviewer.admin.observability.dto.LlmCallRawPayload;
import com.aiinterviewer.admin.observability.dto.ObservabilityAccessLog;
import com.aiinterviewer.admin.observability.mapper.AiObservabilityMapper;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
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

    @Test
    void promptRawPayloadDoesNotExposeResponseText() {
        UUID callId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        when(mapper.selectLlmCallRawPayload(callId)).thenReturn(rawPayload(callId));

        LlmCallRawPayload payload = service.getLlmCallRawPayload(callId, 9002L, "PROMPT");

        assertThat(payload.getAccessType()).isEqualTo("PROMPT");
        assertThat(payload.getRawText()).isEqualTo("full prompt");
        assertThat(payload.getPromptText()).isEqualTo("full prompt");
        assertThat(payload.getResponseText()).isNull();
        verify(mapper).insertAccessLog(argThat(log -> "PROMPT".equals(log.getAccessType())));
    }

    @Test
    void responseRawPayloadDoesNotExposePromptText() {
        UUID callId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        when(mapper.selectLlmCallRawPayload(callId)).thenReturn(rawPayload(callId));

        LlmCallRawPayload payload = service.getLlmCallRawPayload(callId, 9003L, " response ");

        assertThat(payload.getAccessType()).isEqualTo("RESPONSE");
        assertThat(payload.getRawText()).isEqualTo("full response");
        assertThat(payload.getPromptText()).isNull();
        assertThat(payload.getResponseText()).isEqualTo("full response");
        verify(mapper).insertAccessLog(argThat(log -> "RESPONSE".equals(log.getAccessType())));
    }

    @Test
    void standaloneLlmCallDetailEndpointAndServiceUseNonRawDetailContract() throws Exception {
        UUID callId = UUID.fromString("00000000-0000-0000-0000-000000000104");
        assertThat(AiObservabilityController.class.getMethod("getLlmCallDetail", UUID.class)
                .getAnnotation(GetMapping.class).value())
                .containsExactly("/llm-calls/{callId}");
        assertThat(AiObservabilityMapper.class.getMethod("selectLlmCallById", UUID.class))
                .isNotNull();

        AiLlmCallDetailItem detail = new AiLlmCallDetailItem();
        detail.setId(callId);
        when(mapper.selectLlmCallById(callId)).thenReturn(detail);

        AiLlmCallDetailItem result = service.getLlmCallDetail(callId);

        assertThat(result).isSameAs(detail);
        assertThat(result.getClass().getMethods())
                .extracting(Method::getName)
                .doesNotContain("getPromptText", "getResponseText", "getRawText");
        verify(mapper, never()).insertAccessLog(any(ObservabilityAccessLog.class));
    }

    @Test
    void traceQueryNormalizesTraceIdAndCallTypeFilters() throws Exception {
        UUID traceId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        AiTraceQuery query = new AiTraceQuery();
        query.getClass().getMethod("setTraceId", UUID.class).invoke(query, traceId);
        query.getClass().getMethod("setCallType", String.class).invoke(query, "  summary  ");

        query.normalizeFilters();

        assertThat(query.getClass().getMethod("getTraceId").invoke(query)).isEqualTo(traceId);
        assertThat(query.getClass().getMethod("getCallType").invoke(query)).isEqualTo("summary");
    }

    @Test
    void mapperXmlFiltersTraceListByTraceIdAndCallTypeWithoutDuplicateRows() throws Exception {
        String mapperXml = new ClassPathResource("mapper/AiObservabilityMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(mapperXml).contains("t.id = #{query.traceId}");
        assertThat(mapperXml).contains("cc.call_type = #{query.callType}");
        assertThat(mapperXml).contains("SELECT 1\n                FROM t_ai_llm_call cc");
    }

    @Test
    void statsSqlConstrainsCallTypeAggregatesOnEligibleTrace() throws Exception {
        String selectStats = mapperXmlSelect("selectStats");

        assertThat(selectStats).contains("LEFT JOIN t_ai_llm_call c ON c.trace_id = t.id");
        assertThat(selectStats).contains("AND c.call_type = #{query.callType}");
        assertThat(selectStats).contains("<include refid=\"TraceWhere\"/>");
    }

    @Test
    void statsSqlConstrainsProviderAndModelAggregatesForProviderCacheDenominators() throws Exception {
        String selectStats = mapperXmlSelect("selectStats");

        assertThat(selectStats).contains("AND c.provider = #{query.provider}");
        assertThat(selectStats).contains("AND c.model = #{query.model}");
        assertThat(selectStats.indexOf("AND c.provider = #{query.provider}"))
                .isLessThan(selectStats.indexOf("<include refid=\"TraceWhere\"/>"));
        assertThat(selectStats.indexOf("AND c.model = #{query.model}"))
                .isLessThan(selectStats.indexOf("<include refid=\"TraceWhere\"/>"));
    }

    @Test
    void statsIncludeHighConsumptionCallTypesForTheSameQuery() throws Exception {
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

        Class<?> itemClass = Class.forName(
                "com.aiinterviewer.admin.observability.dto.HighConsumptionCallTypeStats");
        Object item = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setCallType", String.class).invoke(item, "answer_evaluation");
        itemClass.getMethod("setTotalTokens", Long.class).invoke(item, 9_000L);
        itemClass.getMethod("setCallCount", Long.class).invoke(item, 12L);

        Method mapperMethod = AiObservabilityMapper.class.getMethod(
                "selectHighConsumptionCallTypes",
                AiTraceQuery.class);
        when(mapperMethod.invoke(mapper, query)).thenReturn(List.of(item));

        AiObservabilityStatsResponse stats = service.getStats(query);

        Object breakdown = stats.getClass().getMethod("getHighConsumptionCallTypes").invoke(stats);
        assertThat((List<?>) breakdown).singleElement().satisfies(row -> {
            assertThat(invoke(row, "getCallType")).isEqualTo("answer_evaluation");
            assertThat(invoke(row, "getTotalTokens")).isEqualTo(9_000L);
            assertThat(invoke(row, "getCallCount")).isEqualTo(12L);
        });
    }

    private String mapperXmlSelect(String selectId) throws Exception {
        String mapperXml = new ClassPathResource("mapper/AiObservabilityMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);
        String startTag = "<select id=\"" + selectId + "\"";
        int start = mapperXml.indexOf(startTag);
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = mapperXml.indexOf("</select>", start);
        assertThat(end).isGreaterThan(start);
        return mapperXml.substring(start, end);
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

    private Object invoke(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to invoke " + methodName, ex);
        }
    }
}
