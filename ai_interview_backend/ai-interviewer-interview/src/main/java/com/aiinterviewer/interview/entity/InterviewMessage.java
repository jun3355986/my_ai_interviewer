package com.aiinterviewer.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面试消息历史实体
 */
@Data
@TableName("t_interview_message")
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
     * 创建时间
     */
    private LocalDateTime createdAt;
}
