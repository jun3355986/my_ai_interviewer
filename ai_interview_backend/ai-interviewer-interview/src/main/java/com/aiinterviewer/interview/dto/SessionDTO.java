package com.aiinterviewer.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话信息DTO
 */
@Data
@Schema(description = "会话信息")
public class SessionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "候选人姓名")
    private String candidateName;

    @Schema(description = "当前阶段")
    private String stage;

    @Schema(description = "阶段显示名")
    private String stageDisplay;

    @Schema(description = "进度百分比 0-100")
    private Integer progress;

    @Schema(description = "会话状态: 1-进行中, 2-已完成, 3-已取消")
    private Integer status;

    @Schema(description = "最后一个问题")
    private String lastQuestion;

    @Schema(description = "已完成的项目问题数")
    private Integer projectQuestionsCount;

    @Schema(description = "目标项目问题数")
    private Integer targetProjectQuestions;

    @Schema(description = "已完成的技术问题数")
    private Integer technicalQuestionsCount;

    @Schema(description = "简历ID")
    private Long resumeId;

    @Schema(description = "职位ID")
    private Long jobId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
