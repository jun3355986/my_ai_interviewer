package com.aiinterviewer.admin.questionbank.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionImportBatch {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PARTIAL_FAILED = "PARTIAL_FAILED";

    private Long id;
    private String batchNo;
    private String fileName;
    private String fileUrl;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private String errorMessage;
    private Long importedBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
