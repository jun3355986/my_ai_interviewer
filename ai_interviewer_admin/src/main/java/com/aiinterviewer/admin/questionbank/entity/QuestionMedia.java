package com.aiinterviewer.admin.questionbank.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionMedia {

    private Long id;
    private Long questionId;
    private String mediaType;
    private String mediaUrl;
    private String caption;
    private String altText;
    private Integer sortOrder;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
