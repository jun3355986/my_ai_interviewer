package com.aiinterviewer.admin.observability.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class AiTraceQuery {

    private UUID traceId;
    private Long userId;
    private String username;
    private String sessionId;
    private String requestId;
    private String businessType;
    private String entrypoint;
    private String status;
    private String callType;
    private String provider;
    private String model;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime startedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime startedTo;

    private Long current = 1L;
    private Long size = 20L;

    public void normalizeFilters() {
        username = normalizeText(username);
        sessionId = normalizeText(sessionId);
        requestId = normalizeText(requestId);
        businessType = normalizeText(businessType);
        entrypoint = normalizeText(entrypoint);
        status = normalizeText(status);
        callType = normalizeText(callType);
        provider = normalizeText(provider);
        model = normalizeText(model);
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
