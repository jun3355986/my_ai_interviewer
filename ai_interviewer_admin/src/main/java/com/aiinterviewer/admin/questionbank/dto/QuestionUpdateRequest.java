package com.aiinterviewer.admin.questionbank.dto;

import java.util.List;
import lombok.Data;

@Data
public class QuestionUpdateRequest {

    private String questionText;
    private String answerReference;
    private String questionType;
    private String difficulty;
    private String skillArea;
    private Long jobId;
    private Integer status;
    private Long updatedBy;
    private List<String> tags = List.of();
}
