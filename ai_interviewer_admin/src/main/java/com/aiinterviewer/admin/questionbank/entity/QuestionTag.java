package com.aiinterviewer.admin.questionbank.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionTag {

    private Long id;
    private String tagCode;
    private String tagName;
    private String tagType;
    private String color;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
