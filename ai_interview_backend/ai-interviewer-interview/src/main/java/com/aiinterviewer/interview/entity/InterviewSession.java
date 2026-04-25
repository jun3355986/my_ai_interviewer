package com.aiinterviewer.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试会话实体
 */
@Data
@TableName(value = "t_interview_session", autoResultMap = true)
public class InterviewSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID (UUID)
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 简历ID
     */
    private Long resumeId;

    /**
     * 职位ID
     */
    private Long jobId;

    /**
     * 候选人姓名
     */
    private String candidateName;

    /**
     * 当前阶段
     */
    private String stage;

    /**
     * 会话状态: 1-进行中, 2-已完成, 3-已取消
     */
    private Integer status;

    /**
     * 简历内容(冗余存储)
     */
    private String resumeContent;

    /**
     * 职位要求(冗余存储)
     */
    private String jobRequirements;

    /**
     * 已完成的项目问题数
     */
    private Integer projectQuestionsCount;

    /**
     * 目标项目问题数
     */
    private Integer targetProjectQuestions;

    /**
     * 已完成的技术问题数
     */
    @TableField(exist = false)
    private Integer technicalQuestionsCount;

    /**
     * 项目问题池(JSON)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> projectQuestionsPool;

    /**
     * 技术问题池(JSON)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> technicalQuestionsPool;

    /**
     * 当前追问计数
     */
    private Integer currentFollowupCount;

    /**
     * Python后端会话ID
     */
    private String pythonSessionId;

    /**
     * 最后一个问题
     */
    @TableField(exist = false)
    private String lastQuestion;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime finishedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
