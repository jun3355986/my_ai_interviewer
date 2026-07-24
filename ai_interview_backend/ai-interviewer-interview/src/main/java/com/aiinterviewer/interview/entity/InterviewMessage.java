package com.aiinterviewer.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 面试消息历史实体
 */
@Data
@TableName(value = "t_interview_message", autoResultMap = true)
public class InterviewMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 角色: human, ai, system
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息所属阶段
     */
    private String stage;

    /**
     * 消息序号
     */
    private Integer sequence;

    /**
     * 所属原子轮次ID
     */
    private String turnId;

    /**
     * 业务消息类型
     */
    private String messageType;

    /**
     * 是否等待候选人回答
     */
    private Boolean expectsResponse;

    /**
     * completed, interrupted, failed
     */
    private String deliveryStatus;

    /**
     * 结构化题目和媒体信息
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
