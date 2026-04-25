package com.aiinterviewer.resume.dto;

import com.aiinterviewer.resume.entity.ResumeContent;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历解析响应
 */
@Data
public class ParseResumeResponse {

    /**
     * 简历ID
     */
    private Long resumeId;

    /**
     * 解析状态: 0-未解析, 1-解析中, 2-解析成功, 3-解析失败
     */
    private Integer parseStatus;

    /**
     * 解析状态文本
     */
    private String parseStatusText;

    /**
     * 解析后的内容
     */
    private ResumeContent content;

    /**
     * 解析耗时 (毫秒)
     */
    private Long duration;

    /**
     * 解析失败原因
     */
    private String errorMessage;

    /**
     * 解析完成时间
     */
    private LocalDateTime parsedAt;
}
