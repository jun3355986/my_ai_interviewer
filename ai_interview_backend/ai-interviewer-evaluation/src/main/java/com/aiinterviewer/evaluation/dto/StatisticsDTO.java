package com.aiinterviewer.evaluation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 统计DTO
 */
@Data
public class StatisticsDTO {

    private Integer totalInterviews;       // 面试总数
    private Integer completedInterviews;   // 已完成面试数
    private Integer averageScore;          // 平均分
    private Double averageDuration;        // 平均时长(分钟)
    private List<ScoreDistribution> scoreDistribution;
    private List<StageStatistics> stageStatistics;

    @Data
    public static class ScoreDistribution {
        private String range;              // 分数范围
        private Integer count;             // 数量
        private Double percentage;         // 占比
    }

    @Data
    public static class StageStatistics {
        private String stage;
        private Double avgScore;
        private Integer count;
    }
}
