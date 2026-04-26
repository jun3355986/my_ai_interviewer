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
    private List<String> tags;
    private boolean answerReferenceSet;
    private boolean tagsSet;

    public void setAnswerReference(String answerReference) {
        this.answerReference = answerReference;
        this.answerReferenceSet = true;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
        this.tagsSet = true;
    }
}
