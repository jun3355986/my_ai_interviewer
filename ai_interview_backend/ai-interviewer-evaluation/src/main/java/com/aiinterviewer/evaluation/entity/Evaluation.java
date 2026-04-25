package com.aiinterviewer.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试评估报告实体
 */
@Data
@TableName(value = "t_evaluation")
public class Evaluation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 评估ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 面试会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 职位ID
     */
    private Long jobId;

    /**
     * 总体评分 0-100
     */
    private Integer overallScore;

    /**
     * 技术能力评分
     */
    private Integer technicalScore;

    /**
     * 沟通能力评分
     */
    private Integer communicationScore;

    /**
     * 逻辑思维评分
     */
    private Integer logicScore;

    /**
     * 经验匹配度评分
     */
    private Integer experienceScore;

    /**
     * 面试总结
     */
    private String summary;

    /**
     * 优势
     */
    private String strengths;

    /**
     * 待改进
     */
    private String weaknesses;

    /**
     * 推荐结果: EXCELLENT, RECOMMEND, CONSIDER, REJECT
     */
    private String recommendation;

    /**
     * 详细反馈 (JSON)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private EvaluationDetail detail;

    /**
     * 统计: 总问题数
     */
    private Integer totalQuestions;

    /**
     * 统计: 已回答问题数
     */
    private Integer answeredQuestions;

    /**
     * 统计: 平均分
     */
    private BigDecimal averageScore;

    /**
     * 面试时长(分钟)
     */
    private Integer durationMinutes;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 评估详情
     */
    @Data
    public static class EvaluationDetail implements Serializable {
        private static final long serialVersionUID = 1L;

        private String stageResults;       // 各阶段结果
        private String keyHighlights;      // 关键亮点
        private String concerns;           // 潜在问题
        private String candidateImpression; // 候选人印象
    }
}
