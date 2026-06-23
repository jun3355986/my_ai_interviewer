package com.aiinterviewer.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 发送给Python后端的对话请求
 * 对应Python后端的UnifiedChatRequest
 */
@Data
@Builder
public class PythonChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     */
    @JsonProperty("session_id")
    private String sessionId;

    /**
     * Java侧每次请求ID，用于贯通Java日志与Python AI观测链路
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * Java面试会话ID
     */
    @JsonProperty("java_session_id")
    private String javaSessionId;

    /**
     * 当前用户ID
     */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * 当前用户名（若网关透传）
     */
    private String username;

    /**
     * 业务类型
     */
    @JsonProperty("business_type")
    private String businessType;

    /**
     * Java入口点
     */
    private String entrypoint;

    /**
     * 用户消息
     */
    private String message;

    /**
     * 简历内容（首次需要）
     */
    @JsonProperty("resume_content")
    private String resumeContent;

    /**
     * 职位要求（可选）
     */
    @JsonProperty("job_requirements")
    private String jobRequirements;

    /**
     * 候选人姓名（可选）
     */
    @JsonProperty("candidate_name")
    private String candidateName;
}
