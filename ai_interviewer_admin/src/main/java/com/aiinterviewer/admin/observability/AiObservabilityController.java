package com.aiinterviewer.admin.observability;

import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.common.model.Result;
import com.aiinterviewer.admin.observability.dto.AiLlmCallDetailItem;
import com.aiinterviewer.admin.observability.dto.AiObservabilityStatsResponse;
import com.aiinterviewer.admin.observability.dto.AiTraceDetailResponse;
import com.aiinterviewer.admin.observability.dto.AiTraceListItem;
import com.aiinterviewer.admin.observability.dto.AiTraceQuery;
import com.aiinterviewer.admin.observability.dto.LlmCallRawPayload;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ai-observability")
@RequiredArgsConstructor
public class AiObservabilityController {

    private final AiObservabilityService service;

    @GetMapping("/traces")
    public Result<PageResult<AiTraceListItem>> listTraces(@ModelAttribute AiTraceQuery query) {
        return Result.success(service.listTraces(query));
    }

    @GetMapping("/traces/{traceId}")
    public Result<AiTraceDetailResponse> getTraceDetail(@PathVariable UUID traceId) {
        return Result.success(service.getTraceDetail(traceId));
    }

    @GetMapping("/llm-calls/{callId}")
    public Result<AiLlmCallDetailItem> getLlmCallDetail(@PathVariable UUID callId) {
        return Result.success(service.getLlmCallDetail(callId));
    }

    @GetMapping("/llm-calls/{callId}/raw")
    public Result<LlmCallRawPayload> getLlmCallRawPayload(
            @PathVariable UUID callId,
            @RequestParam("type") String type,
            Authentication authentication) {
        return Result.success(service.getLlmCallRawPayload(callId, currentAdminUserId(authentication), type));
    }

    @GetMapping("/stats")
    public Result<AiObservabilityStatsResponse> getStats(@ModelAttribute AiTraceQuery query) {
        return Result.success(service.getStats(query));
    }

    private Long currentAdminUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long adminUserId) {
            return adminUserId;
        }
        if (principal instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
