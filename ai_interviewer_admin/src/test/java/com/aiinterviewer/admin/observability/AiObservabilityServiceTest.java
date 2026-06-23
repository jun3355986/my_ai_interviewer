package com.aiinterviewer.admin.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.observability.dto.AiLlmCallDetailItem;
import com.aiinterviewer.admin.observability.dto.AiObservabilityStatsResponse;
import com.aiinterviewer.admin.observability.dto.AiTraceListItem;
import com.aiinterviewer.admin.observability.dto.AiTraceQuery;
import com.aiinterviewer.admin.observability.dto.LlmCallRawPayload;
import com.aiinterviewer.admin.observability.dto.ObservabilityAccessLog;
import com.aiinterviewer.admin.observability.mapper.AiObservabilityMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.CallableStatement;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.PostgreSQLContainer;

@ExtendWith(MockitoExtension.class)
class AiObservabilityServiceTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ai_interviewer_admin_mapper")
            .withUsername("admin")
            .withPassword("admin");

    @Mock
    private AiObservabilityMapper mapper;

    private AiObservabilityService service;

    @BeforeAll
    static void migrateMapperDatabase() {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

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
        allowRawRead(adminUserId);
        when(mapper.selectLlmCallRawPayload(callId)).thenReturn(rawPayload(callId));

        service.getLlmCallRawPayload(callId, adminUserId, "PROMPT");

        verify(mapper).insertAccessLog(argThat(log ->
                "PROMPT".equals(log.getAccessType())
                        && adminUserId.equals(log.getAdminUserId())
                        && callId.equals(log.getLlmCallId())));
    }

    @Test
    void rawPayloadAccessRequiresRawReadPermissionBeforeSelectingPayload() {
        UUID callId = UUID.fromString("00000000-0000-0000-0000-000000000105");
        Long adminUserId = 9005L;

        assertThatThrownBy(() -> service.getLlmCallRawPayload(callId, adminUserId, "PROMPT"))
                .isInstanceOfSatisfying(AdminBusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(403);
                    assertThat(exception.getMessage()).contains("AI_OBSERVABILITY_RAW_READ");
                });

        verify(mapper, never()).selectLlmCallRawPayload(any());
        verify(mapper, never()).insertAccessLog(any(ObservabilityAccessLog.class));
    }

    @Test
    void promptRawPayloadDoesNotExposeResponseText() {
        UUID callId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        Long adminUserId = 9002L;
        allowRawRead(adminUserId);
        when(mapper.selectLlmCallRawPayload(callId)).thenReturn(rawPayload(callId));

        LlmCallRawPayload payload = service.getLlmCallRawPayload(callId, adminUserId, "PROMPT");

        assertThat(payload.getAccessType()).isEqualTo("PROMPT");
        assertThat(payload.getRawText()).isEqualTo("full prompt");
        assertThat(payload.getPromptText()).isEqualTo("full prompt");
        assertThat(payload.getResponseText()).isNull();
        verify(mapper).insertAccessLog(argThat(log -> "PROMPT".equals(log.getAccessType())));
    }

    @Test
    void responseRawPayloadDoesNotExposePromptText() {
        UUID callId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        Long adminUserId = 9003L;
        allowRawRead(adminUserId);
        when(mapper.selectLlmCallRawPayload(callId)).thenReturn(rawPayload(callId));

        LlmCallRawPayload payload = service.getLlmCallRawPayload(callId, adminUserId, " response ");

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
        assertThat(mapperXml).contains("FROM t_ai_llm_call fc");
        assertThat(mapperXml).contains("<property name=\"alias\" value=\"fc\"/>");
        assertThat(mapperXml).contains("AND ${alias}.call_type = #{query.callType}");
        assertThat(mapperXml).contains("AND ${alias}.provider = #{query.provider}");
        assertThat(mapperXml).doesNotContain("FROM t_ai_llm_call cc");
        assertThat(mapperXml).doesNotContain("FROM t_ai_llm_call pc");
        assertThat(mapperXml).doesNotContain("FROM t_ai_llm_call mc");
    }

    @Test
    void statsSqlConstrainsCallTypeAggregatesOnEligibleTrace() throws Exception {
        String selectStats = mapperXmlSelect("selectStats");

        assertThat(selectStats).contains("LEFT JOIN t_ai_llm_call c ON c.trace_id = t.id");
        assertThat(selectStats).contains("<include refid=\"LlmCallFilterPredicates\">");
        assertThat(selectStats).contains("<property name=\"alias\" value=\"c\"/>");
        assertThat(selectStats).contains("<include refid=\"TraceWhere\"/>");
    }

    @Test
    void statsSqlConstrainsProviderAndModelAggregatesForProviderCacheDenominators() throws Exception {
        String selectStats = mapperXmlSelect("selectStats");

        assertThat(selectStats).contains("<include refid=\"LlmCallFilterPredicates\">");
        assertThat(selectStats).contains("<property name=\"alias\" value=\"c\"/>");
        assertThat(selectStats.indexOf("<include refid=\"LlmCallFilterPredicates\">"))
                .isLessThan(selectStats.indexOf("<include refid=\"TraceWhere\"/>"));
    }

    @Test
    void combinedLlmFiltersRequireSameCallRowAndListAggregatesUseFilteredCalls() throws Exception {
        SqlSessionFactory sessionFactory = mapperSessionFactory();
        seedMixedLlmCalls();

        AiTraceQuery query = new AiTraceQuery();
        query.setCallType("resume");
        query.setProvider("openai");
        query.normalizeFilters();

        try (SqlSession session = sessionFactory.openSession()) {
            AiObservabilityMapper realMapper = session.getMapper(AiObservabilityMapper.class);

            Long total = realMapper.countTraces(query);
            List<AiTraceListItem> traces = realMapper.selectTraces(query, 20, 0);
            AiObservabilityStatsResponse stats = realMapper.selectStats(query);

            assertThat(total).isEqualTo(1L);
            assertThat(traces).singleElement().satisfies(trace -> {
                assertThat(trace.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000502"));
                assertThat(trace.getLlmCallCount()).isEqualTo(1L);
                assertThat(trace.getTotalTokens()).isEqualTo(333L);
            });
            assertThat(stats.getTraceCount()).isEqualTo(1L);
            assertThat(stats.getTotalLlmCalls()).isEqualTo(1L);
            assertThat(stats.getTotalTokens()).isEqualTo(333L);
        }
    }

    @Test
    void traceListIncludesProviderModelAndCacheRatesFromFilteredLlmRows() throws Exception {
        SqlSessionFactory sessionFactory = mapperSessionFactory();
        seedTraceListContractRows();

        AiTraceQuery query = new AiTraceQuery();
        query.setProvider("deepseek");
        query.setModel("deepseek-chat");
        query.normalizeFilters();

        try (SqlSession session = sessionFactory.openSession()) {
            AiObservabilityMapper realMapper = session.getMapper(AiObservabilityMapper.class);

            List<AiTraceListItem> traces = realMapper.selectTraces(query, 20, 0);

            assertThat(traces).singleElement().satisfies(trace -> {
                assertThat(trace.getId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000701"));
                assertThat(trace.getLlmCallCount()).isEqualTo(3L);
                assertThat(trace.getTotalTokens()).isEqualTo(175L);
                assertThat(invoke(trace, "getProvider")).isEqualTo("deepseek");
                assertThat(invoke(trace, "getModel")).isEqualTo("deepseek-chat");
                assertThat((BigDecimal) invoke(trace, "getProviderPromptCacheTokenHitRate"))
                        .isEqualByComparingTo("0.666667");
                assertThat((BigDecimal) invoke(trace, "getProviderPromptCacheCallHitRate"))
                        .isEqualByComparingTo("0.500000");
            });
        }
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

    private void allowRawRead(Long adminUserId) {
        when(mapper.adminHasPermission(adminUserId, "AI_OBSERVABILITY_RAW_READ")).thenReturn(true);
    }

    private SqlSessionFactory mapperSessionFactory() throws Exception {
        DataSource dataSource = new UnpooledDataSource(
                "org.postgresql.Driver",
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/AiObservabilityMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/AiObservabilityMapper.xml",
                    configuration.getSqlFragments())
                    .parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private void seedMixedLlmCalls() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement truncate = connection.prepareStatement(
                    "TRUNCATE TABLE t_ai_observability_access_log, t_ai_llm_call, "
                            + "t_ai_trace_step, t_ai_trace RESTART IDENTITY CASCADE")) {
                truncate.executeUpdate();
            }
            insertTrace(connection, "00000000-0000-0000-0000-000000000501", "request-501");
            insertTrace(connection, "00000000-0000-0000-0000-000000000502", "request-502");
            insertLlmCall(
                    connection,
                    "00000000-0000-0000-0000-000000000601",
                    "00000000-0000-0000-0000-000000000501",
                    "resume",
                    "deepseek",
                    111L);
            insertLlmCall(
                    connection,
                    "00000000-0000-0000-0000-000000000602",
                    "00000000-0000-0000-0000-000000000501",
                    "chat",
                    "openai",
                    222L);
            insertLlmCall(
                    connection,
                    "00000000-0000-0000-0000-000000000603",
                    "00000000-0000-0000-0000-000000000502",
                    "resume",
                    "openai",
                    333L);
            insertLlmCall(
                    connection,
                    "00000000-0000-0000-0000-000000000604",
                    "00000000-0000-0000-0000-000000000502",
                    "chat",
                    "openai",
                    444L);
        }
    }

    private void seedTraceListContractRows() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement truncate = connection.prepareStatement(
                    "TRUNCATE TABLE t_ai_observability_access_log, t_ai_llm_call, "
                            + "t_ai_trace_step, t_ai_trace RESTART IDENTITY CASCADE")) {
                truncate.executeUpdate();
            }
            insertTrace(connection, "00000000-0000-0000-0000-000000000701", "request-701");
            insertTrace(connection, "00000000-0000-0000-0000-000000000702", "request-702");
            insertLlmCall(
                    connection,
                    "00000000-0000-0000-0000-000000000801",
                    "00000000-0000-0000-0000-000000000701",
                    "CHAT",
                    "deepseek",
                    "deepseek-chat",
                    100L,
                    true,
                    40L,
                    10L);
            insertLlmCall(
                    connection,
                    "00000000-0000-0000-0000-000000000802",
                    "00000000-0000-0000-0000-000000000701",
                    "CHAT",
                    "deepseek",
                    "deepseek-chat",
                    50L,
                    true,
                    0L,
                    10L);
            insertLlmCall(
                    connection,
                    "00000000-0000-0000-0000-000000000803",
                    "00000000-0000-0000-0000-000000000701",
                    "CHAT",
                    "deepseek",
                    "deepseek-chat",
                    25L,
                    false,
                    null,
                    null);
            insertLlmCall(
                    connection,
                    "00000000-0000-0000-0000-000000000804",
                    "00000000-0000-0000-0000-000000000701",
                    "CHAT",
                    "openai",
                    "gpt-4o-mini",
                    1_000L,
                    true,
                    900L,
                    100L);
            insertLlmCall(
                    connection,
                    "00000000-0000-0000-0000-000000000805",
                    "00000000-0000-0000-0000-000000000702",
                    "CHAT",
                    "deepseek",
                    "deepseek-reasoner",
                    200L,
                    true,
                    100L,
                    100L);
        }
    }

    private void insertTrace(Connection connection, String traceId, String requestId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO t_ai_trace
                    (id, request_id, user_id, username, business_type, entrypoint, status, started_at)
                VALUES (?, ?, 42, 'filter-user', 'interview', 'router', 'SUCCESS', ?)
                """)) {
            statement.setObject(1, UUID.fromString(traceId));
            statement.setString(2, requestId);
            statement.setTimestamp(3, Timestamp.from(Instant.parse("2026-06-23T01:00:00Z")));
            statement.executeUpdate();
        }
    }

    private void insertLlmCall(
            Connection connection,
            String callId,
            String traceId,
            String callType,
            String provider,
            String model,
            Long totalTokens,
            boolean cacheReportedByProvider,
            Long promptCacheHitTokens,
            Long promptCacheMissTokens) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO t_ai_llm_call
                    (id, trace_id, call_type, provider, model, status, prompt_tokens,
                     completion_tokens, total_tokens, token_source, latency_ms, started_at,
                     cache_reported_by_provider, prompt_cache_hit_tokens, prompt_cache_miss_tokens)
                VALUES (?, ?, ?, ?, ?, 'SUCCESS', ?, ?, ?, 'PROVIDER', 100, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, UUID.fromString(callId));
            statement.setObject(2, UUID.fromString(traceId));
            statement.setString(3, callType);
            statement.setString(4, provider);
            statement.setString(5, model);
            statement.setLong(6, totalTokens / 3);
            statement.setLong(7, totalTokens - (totalTokens / 3));
            statement.setLong(8, totalTokens);
            statement.setTimestamp(9, Timestamp.from(Instant.parse("2026-06-23T01:01:00Z")));
            statement.setBoolean(10, cacheReportedByProvider);
            if (promptCacheHitTokens == null) {
                statement.setObject(11, null);
            } else {
                statement.setLong(11, promptCacheHitTokens);
            }
            if (promptCacheMissTokens == null) {
                statement.setObject(12, null);
            } else {
                statement.setLong(12, promptCacheMissTokens);
            }
            statement.executeUpdate();
        }
    }

    private void insertLlmCall(
            Connection connection,
            String callId,
            String traceId,
            String callType,
            String provider,
            Long totalTokens) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO t_ai_llm_call
                    (id, trace_id, call_type, provider, model, status, prompt_tokens,
                     completion_tokens, total_tokens, token_source, latency_ms, started_at)
                VALUES (?, ?, ?, ?, 'gpt-4o-mini', 'SUCCESS', ?, ?, ?, 'PROVIDER', 100, ?)
                """)) {
            statement.setObject(1, UUID.fromString(callId));
            statement.setObject(2, UUID.fromString(traceId));
            statement.setString(3, callType);
            statement.setString(4, provider);
            statement.setLong(5, totalTokens / 3);
            statement.setLong(6, totalTokens - (totalTokens / 3));
            statement.setLong(7, totalTokens);
            statement.setTimestamp(8, Timestamp.from(Instant.parse("2026-06-23T01:01:00Z")));
            statement.executeUpdate();
        }
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

    public static class UuidTypeHandler extends BaseTypeHandler<UUID> {

        @Override
        public void setNonNullParameter(
                PreparedStatement ps,
                int index,
                UUID parameter,
                JdbcType jdbcType) throws SQLException {
            ps.setObject(index, parameter);
        }

        @Override
        public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
            return toUuid(rs.getObject(columnName));
        }

        @Override
        public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            return toUuid(rs.getObject(columnIndex));
        }

        @Override
        public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            return toUuid(cs.getObject(columnIndex));
        }

        private UUID toUuid(Object value) {
            if (value == null) {
                return null;
            }
            return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
        }
    }
}
