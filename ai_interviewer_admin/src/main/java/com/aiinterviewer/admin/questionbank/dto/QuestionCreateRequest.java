package com.aiinterviewer.admin.questionbank.dto;

import java.util.List;
import lombok.Data;

@Data
public class QuestionCreateRequest {

    private String questionText;
    private String answerReference;
    private String questionType;
    private String difficulty;
    private String skillArea;
    private Long jobId;
    private Integer status;
    private Long createdBy;
    private List<String> tags = List.of();
}
