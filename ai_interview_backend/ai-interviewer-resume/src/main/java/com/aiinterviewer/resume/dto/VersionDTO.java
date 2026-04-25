package com.aiinterviewer.resume.dto;

import com.aiinterviewer.resume.entity.ResumeContent;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历版本历史DTO
 */
@Data
public class VersionDTO {

    /**
     * 版本ID
     */
    private Long id;

    /**
     * 简历ID
     */
    private Long resumeId;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小 (格式化)
     */
    private String fileSize;

    /**
     * 操作类型: UPLOAD, UPDATE, REPARSE
     */
    private String operationType;

    /**
     * 操作备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 解析内容 (仅展示摘要)
     */
    private ResumeContent content;
}
