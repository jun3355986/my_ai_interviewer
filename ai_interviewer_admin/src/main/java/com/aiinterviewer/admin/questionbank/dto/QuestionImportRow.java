package com.aiinterviewer.admin.questionbank.dto;

import java.util.List;
import lombok.Data;

@Data
public class QuestionImportRow {

    private int rowNumber;
    private String questionText;
    private String answerReference;
    private String questionType;
    private String difficulty;
    private List<String> tags = List.of();
    private String skillArea;
    private Long jobId;
    private Integer status;
}
