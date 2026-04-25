package com.aiinterviewer.admin.questionbank.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionVectorSyncRecord {

    private Long id;
    private Long questionId;
    private String syncStatus;
    private String vectorStoreId;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
