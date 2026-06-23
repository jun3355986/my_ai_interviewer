package com.aiinterviewer.admin.observability.dto;

import lombok.Data;

@Data
public class HighConsumptionCallTypeStats {

    private String callType;
    private Long totalTokens;
    private Long callCount;
}
