package com.aiinterviewer.admin.observability.mapper;

import com.aiinterviewer.admin.observability.dto.AiLlmCallDetailItem;
import com.aiinterviewer.admin.observability.dto.AiObservabilityStatsResponse;
import com.aiinterviewer.admin.observability.dto.AiTraceDetailResponse;
import com.aiinterviewer.admin.observability.dto.AiTraceListItem;
import com.aiinterviewer.admin.observability.dto.AiTraceQuery;
import com.aiinterviewer.admin.observability.dto.AiTraceStepItem;
import com.aiinterviewer.admin.observability.dto.HighConsumptionCallTypeStats;
import com.aiinterviewer.admin.observability.dto.LlmCallRawPayload;
import com.aiinterviewer.admin.observability.dto.ObservabilityAccessLog;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface AiObservabilityMapper {

    Long countTraces(@Param("query") AiTraceQuery query);

    List<AiTraceListItem> selectTraces(
            @Param("query") AiTraceQuery query,
            @Param("limit") long limit,
            @Param("offset") long offset);

    AiTraceDetailResponse selectTraceById(@Param("traceId") UUID traceId);

    List<AiTraceStepItem> selectTraceSteps(@Param("traceId") UUID traceId);

    List<AiLlmCallDetailItem> selectLlmCalls(@Param("traceId") UUID traceId);

    AiLlmCallDetailItem selectLlmCallById(@Param("callId") UUID callId);

    LlmCallRawPayload selectLlmCallRawPayload(@Param("callId") UUID callId);

    AiObservabilityStatsResponse selectStats(@Param("query") AiTraceQuery query);

    List<HighConsumptionCallTypeStats> selectHighConsumptionCallTypes(@Param("query") AiTraceQuery query);

    int insertAccessLog(@Param("log") ObservabilityAccessLog log);
}
