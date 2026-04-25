package com.aiinterviewer.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一对话请求DTO
 * 对应Python后端的UnifiedChatRequest
 */
@Data
@Schema(description = "统一对话请求")
public class ChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID，首次调用时为空
     */
    @Schema(description = "会话ID，首次调用时为空")
    private String sessionId;

    /**
     * 用户消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "用户消息内容", required = true)
    private String message;

    /**
     * 简历ID（首次需要）
     */
    @Schema(description = "简历ID，首次调用时需要")
    private Long resumeId;

    /**
     * 简历内容（首次需要，如果没有resumeId）
     */
    @Schema(description = "简历内容，首次调用时需要（如果没有resumeId）")
    private String resumeContent;

    /**
     * 职位ID（可选）
     */
    @Schema(description = "职位ID，可选")
    private Long jobId;

    /**
     * 职位要求（可选）
     */
    @Schema(description = "职位要求，可选")
    private String jobRequirements;

    /**
     * 候选人姓名（可选）
     */
    @Schema(description = "候选人姓名，可选")
    private String candidateName;
}
