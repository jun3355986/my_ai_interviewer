package com.aiinterviewer.evaluation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评估报告DTO
 */
@Data
public class EvaluationDTO {

    private Long id;
    private String sessionId;
    private Long userId;
    private Long jobId;

    // 评分维度
    private Integer overallScore;
    private Integer technicalScore;
    private Integer communicationScore;
    private Integer logicScore;
    private Integer experienceScore;

    // 评估内容
    private String summary;
    private String strengths;
    private String weaknesses;
    private String recommendation;
    private String recommendationText;

    // 统计数据
    private Integer totalQuestions;
    private Integer answeredQuestions;
    private BigDecimal averageScore;
    private Integer durationMinutes;

    // 时间
    private LocalDateTime createdAt;

    // 候选人信息
    private String candidateName;
    private String jobTitle;
}
