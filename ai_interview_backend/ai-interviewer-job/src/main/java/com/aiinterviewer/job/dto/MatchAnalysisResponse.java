package com.aiinterviewer.job.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 匹配度分析响应
 */
@Data
public class MatchAnalysisResponse {

    /**
     * 匹配度分数 (0-100)
     */
    private BigDecimal matchScore;

    /**
     * 匹配等级: EXCELLENT, GOOD, MATCHED, PARTIAL, MISMATCHED
     */
    private String matchLevel;

    /**
     * 匹配详情
     */
    private List<MatchItem> matchDetails;

    /**
     * 建议
     */
    private List<String> suggestions;

    /**
     * 匹配项
     */
    @Data
    public static class MatchItem {
        private String category;           // 技能/经验/学历/证书
        private String name;               // 要求名称
        private Boolean matched;           // 是否匹配
        private String resumeValue;        // 简历中的值
        private Integer score;             // 单项得分
    }
}
