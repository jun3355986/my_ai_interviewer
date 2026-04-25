package com.aiinterviewer.resume.dto;

import com.aiinterviewer.resume.entity.ResumeContent;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历数据传输对象
 */
@Data
public class ResumeDTO {

    /**
     * 简历ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 原始文件名
     */
    private String originalFileName;

    /**
     * 文件大小 (格式化)
     */
    private String fileSize;

    /**
     * 文件类型
     */
    private String contentType;

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
    private ResumeContent parsedContent;

    /**
     * 是否默认简历
     */
    private Boolean isDefault;

    /**
     * 简历版本数
     */
    private Integer versionCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 解析完成时间
     */
    private LocalDateTime parsedAt;

    /**
     * 姓名 (从解析内容提取)
     */
    private String name;

    /**
     * 最高学历
     */
    private String education;

    /**
     * 毕业院校
     */
    private String university;

    /**
     * 工作年限
     */
    private String workYears;

    /**
     * 求职意向
     */
    private String jobIntent;
}
