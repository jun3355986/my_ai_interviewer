package com.aiinterviewer.interview.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ComposedAssessmentDTO implements Serializable {

    private Long id;
    private String owningBranchId;
    private Boolean inherited;
    private Integer displayOrder;
    private Integer questionIndex;
    private String turnId;
    private Long questionMessageId;
    private Long answerMessageId;
    private String questionType;
    private String question;
    private String answer;
    private Integer score;
    private String feedback;
    private Boolean isFollowup;
    private LocalDateTime createdAt;
}
