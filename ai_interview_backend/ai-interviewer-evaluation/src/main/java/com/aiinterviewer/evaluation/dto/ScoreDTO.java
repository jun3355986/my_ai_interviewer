package com.aiinterviewer.evaluation.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评分记录DTO
 */
@Data
public class ScoreDTO {

    private Long id;
    private String sessionId;
    private Integer questionIndex;
    private String questionType;
    private String question;
    private String answer;
    private Integer score;
    private String feedback;
    private Boolean isFollowup;
    private LocalDateTime createdAt;
}
