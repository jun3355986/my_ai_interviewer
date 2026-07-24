package com.aiinterviewer.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评分记录实体
 */
@Data
@TableName("t_score_record")
public class ScoreRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 问题序号
     */
    private Integer questionIndex;

    /**
     * 问题类型: project, technical
     */
    private String questionType;

    /**
     * 问题内容
     */
    private String question;

    /**
     * 回答内容
     */
    private String answer;

    /**
     * 分数 0-100
     */
    private Integer score;

    /**
     * AI反馈
     */
    private String feedback;

    /**
     * 是否是追问
     */
    private Boolean isFollowup;

    /**
     * Durable turn attempt linkage.
     */
    private String turnId;

    /**
     * Canonical AI prompt answered by this score.
     */
    private Long questionMessageId;

    /**
     * Canonical candidate answer created by this turn.
     */
    private Long answerMessageId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
