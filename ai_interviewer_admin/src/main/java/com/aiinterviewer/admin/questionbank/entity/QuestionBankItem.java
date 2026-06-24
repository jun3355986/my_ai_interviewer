package com.aiinterviewer.admin.questionbank.entity;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class QuestionBankItem {

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_PENDING_REVIEW = 2;
    public static final int STATUS_REJECTED = 3;
    public static final String VECTOR_SYNC_PENDING = "PENDING";
    public static final String VECTOR_SYNC_FAILED = "FAILED";
    public static final String VECTOR_SYNC_DELETE_PENDING = "DELETE_PENDING";
    public static final String VECTOR_SYNC_DELETED = "DELETED";

    private Long id;
    private String questionCode;
    private String questionText;
    private String answerReference;
    private String questionType;
    private String difficulty;
    private String skillArea;
    private Long jobId;
    private Integer status;
    private String vectorSyncStatus;
    private String vectorSyncError;
    private String sourceType;
    private Long sourceBatchId;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private List<String> tags = List.of();
    private List<QuestionMedia> media = List.of();

    public boolean isEligibleForVectorSync() {
        return STATUS_ENABLED == (status == null ? 0 : status)
                && deletedAt == null
                && (VECTOR_SYNC_PENDING.equals(vectorSyncStatus) || VECTOR_SYNC_FAILED.equals(vectorSyncStatus));
    }
}
