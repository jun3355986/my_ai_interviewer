package com.aiinterviewer.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 模拟面试候选人回答请求
 *
 * 由 Flutter MockAutoDriver 在轮到候选人时调用；仅生成回答文本，
 * 不创建会话、不落库，真实流程仍由 durable-turn 管线驱动。
 */
@Data
@Schema(description = "模拟面试候选人回答请求")
public class MockCandidateAnswerRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "简历ID不能为空")
    @Schema(description = "简历ID，必须属于当前用户", required = true)
    private Long resumeId;

    @Schema(description = "职位ID，可选")
    private Long jobId;

    @NotBlank(message = "问题内容不能为空")
    @Schema(description = "面试官当前问题", required = true)
    private String question;

    @NotBlank(message = "问题类型不能为空")
    @Schema(description = "问题类型：self_introduction / project / technical", required = true)
    private String questionType;

    @Schema(description = "最近问答历史，用于保持回答一致性")
    private List<HistoryItem> recentHistory;

    @JsonProperty("java_session_id")
    @Schema(description = "Java 面试会话（分支）ID，用于观测关联")
    private String javaSessionId;

    @JsonProperty("request_id")
    @Schema(description = "上游请求 ID，用于观测关联")
    private String requestId;

    @Data
    @Schema(description = "历史问答项")
    public static class HistoryItem implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "已回答的问题")
        private String question;

        @Schema(description = "候选人当时的回答")
        private String answer;
    }
}
