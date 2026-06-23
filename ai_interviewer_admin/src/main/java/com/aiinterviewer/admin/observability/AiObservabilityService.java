package com.aiinterviewer.admin.observability;

import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.observability.dto.AiLlmCallDetailItem;
import com.aiinterviewer.admin.observability.dto.AiObservabilityStatsResponse;
import com.aiinterviewer.admin.observability.dto.AiTraceDetailResponse;
import com.aiinterviewer.admin.observability.dto.AiTraceListItem;
import com.aiinterviewer.admin.observability.dto.AiTraceQuery;
import com.aiinterviewer.admin.observability.dto.LlmCallRawPayload;
import com.aiinterviewer.admin.observability.dto.ObservabilityAccessLog;
import com.aiinterviewer.admin.observability.mapper.AiObservabilityMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AiObservabilityService {

    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 20L;
    private static final long MAX_SIZE = 100L;
    private static final long MAX_CURRENT = 1_000_000L;
    private static final int RATE_SCALE = 6;

    private final AiObservabilityMapper mapper;

    public PageResult<AiTraceListItem> listTraces(AiTraceQuery query) {
        AiTraceQuery safeQuery = normalize(query);
        long current = normalizedCurrent(safeQuery);
        long size = normalizedSize(safeQuery);
        Long total = mapper.countTraces(safeQuery);
        List<AiTraceListItem> records = mapper.selectTraces(safeQuery, size, safeOffset(current, size));
        return PageResult.of(current, size, total == null ? 0L : total, records);
    }

    public AiTraceDetailResponse getTraceDetail(UUID traceId) {
        ensureUuid(traceId, "traceId");
        AiTraceDetailResponse detail = mapper.selectTraceById(traceId);
        if (detail == null) {
            throw new AdminBusinessException(404, "AI 调用链路不存在");
        }
        detail.setSteps(mapper.selectTraceSteps(traceId));
        detail.setLlmCalls(mapper.selectLlmCalls(traceId));
        return detail;
    }

    @Transactional
    public LlmCallRawPayload getLlmCallRawPayload(UUID callId, Long adminUserId, String type) {
        ensureUuid(callId, "callId");
        String accessType = normalizeAccessType(type);
        LlmCallRawPayload payload = mapper.selectLlmCallRawPayload(callId);
        if (payload == null) {
            throw new AdminBusinessException(404, "LLM 调用记录不存在");
        }

        payload.setAccessType(accessType);
        payload.setRawText("PROMPT".equals(accessType) ? payload.getPromptText() : payload.getResponseText());
        mapper.insertAccessLog(buildAccessLog(payload, adminUserId, accessType));
        return payload;
    }

    public AiObservabilityStatsResponse getStats(AiTraceQuery query) {
        AiTraceQuery safeQuery = normalize(query);
        AiObservabilityStatsResponse stats = mapper.selectStats(safeQuery);
        if (stats == null) {
            stats = new AiObservabilityStatsResponse();
        }
        fillStatDefaults(stats);
        stats.setLlmFailureRate(rate(stats.getFailedLlmCalls(), stats.getTotalLlmCalls()));
        stats.setProviderPromptCacheTokenHitRate(rate(
                stats.getProviderPromptCacheHitTokens(),
                stats.getProviderPromptCacheHitTokens() + stats.getProviderPromptCacheMissTokens()));
        stats.setProviderPromptCacheCallHitRate(rate(
                stats.getProviderPromptCacheHitCalls(),
                stats.getProviderCacheReportedCalls()));
        return stats;
    }

    private ObservabilityAccessLog buildAccessLog(
            LlmCallRawPayload payload,
            Long adminUserId,
            String accessType) {
        HttpServletRequest request = currentRequest();
        ObservabilityAccessLog log = new ObservabilityAccessLog();
        log.setAdminUserId(adminUserId);
        log.setTraceId(payload.getTraceId());
        log.setLlmCallId(payload.getCallId());
        log.setAccessType(accessType);
        log.setRequestUri(request == null ? null : request.getRequestURI());
        log.setIpAddress(resolveIpAddress(request));
        log.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        return log;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String resolveIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            for (String candidate : forwardedFor.split(",")) {
                if (!candidate.isBlank()) {
                    return candidate.trim();
                }
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp;
    }

    private AiTraceQuery normalize(AiTraceQuery query) {
        AiTraceQuery safeQuery = query == null ? new AiTraceQuery() : query;
        safeQuery.normalizeFilters();
        return safeQuery;
    }

    private long normalizedCurrent(AiTraceQuery query) {
        Long current = query.getCurrent();
        if (current == null || current < 1) {
            return DEFAULT_CURRENT;
        }
        return Math.min(current, MAX_CURRENT);
    }

    private long normalizedSize(AiTraceQuery query) {
        Long size = query.getSize();
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private long safeOffset(long current, long size) {
        try {
            return Math.multiplyExact(current - 1, size);
        } catch (ArithmeticException ex) {
            return (MAX_CURRENT - 1) * MAX_SIZE;
        }
    }

    private String normalizeAccessType(String type) {
        if (type == null || type.isBlank()) {
            throw new AdminBusinessException(400, "原文读取类型不能为空");
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!"PROMPT".equals(normalized) && !"RESPONSE".equals(normalized)) {
            throw new AdminBusinessException(400, "原文读取类型必须为 PROMPT 或 RESPONSE");
        }
        return normalized;
    }

    private void ensureUuid(UUID value, String fieldName) {
        if (value == null) {
            throw new AdminBusinessException(400, fieldName + " 不能为空");
        }
    }

    private void fillStatDefaults(AiObservabilityStatsResponse stats) {
        stats.setTraceCount(defaultLong(stats.getTraceCount()));
        stats.setTotalLlmCalls(defaultLong(stats.getTotalLlmCalls()));
        stats.setFailedLlmCalls(defaultLong(stats.getFailedLlmCalls()));
        stats.setTotalPromptTokens(defaultLong(stats.getTotalPromptTokens()));
        stats.setTotalCompletionTokens(defaultLong(stats.getTotalCompletionTokens()));
        stats.setTotalTokens(defaultLong(stats.getTotalTokens()));
        stats.setProviderPromptCacheHitTokens(defaultLong(stats.getProviderPromptCacheHitTokens()));
        stats.setProviderPromptCacheMissTokens(defaultLong(stats.getProviderPromptCacheMissTokens()));
        stats.setProviderPromptCacheHitCalls(defaultLong(stats.getProviderPromptCacheHitCalls()));
        stats.setProviderCacheReportedCalls(defaultLong(stats.getProviderCacheReportedCalls()));
        stats.setProviderCacheUnreportedCalls(defaultLong(stats.getProviderCacheUnreportedCalls()));
        stats.setAverageLatencyMs(defaultDecimal(stats.getAverageLatencyMs()));
    }

    private Long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP) : value;
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0L) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), RATE_SCALE, RoundingMode.HALF_UP);
    }
}
