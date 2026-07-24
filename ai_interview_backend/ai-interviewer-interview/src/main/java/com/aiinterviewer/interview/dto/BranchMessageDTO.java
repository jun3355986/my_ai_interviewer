package com.aiinterviewer.interview.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class BranchMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String owningBranchId;
    private String role;
    private String messageType;
    private String content;
    private String stage;
    private Integer sequence;
    private Boolean expectsResponse;
    private String deliveryStatus;
    private Boolean inherited;
    private Boolean forkable;
    private Long forkPointMessageId;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
