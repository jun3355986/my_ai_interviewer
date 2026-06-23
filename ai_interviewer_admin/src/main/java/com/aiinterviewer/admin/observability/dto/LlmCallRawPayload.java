package com.aiinterviewer.admin.observability.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class LlmCallRawPayload {

    private UUID callId;
    private UUID traceId;
    private String accessType;
    private String promptText;
    private String responseText;
    private String rawText;
}
