package com.aiinterviewer.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aiinterviewer.resume.handler.ResumeContentJsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 简历实体
 */
@Data
@TableName(value = "t_resume", autoResultMap = true)
public class Resume implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 简历ID
     */
    @TableId(type = IdType.AUTO)
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
     * 文件路径 (MinIO存储路径)
     */
    private String filePath;

    /**
     * 文件大小 (字节)
     */
    private Long fileSize;

    /**
     * 文件类型 (MIME类型)
     */
    private String contentType;

    /**
     * 解析后的内容 (JSON格式)
     */
    @TableField(typeHandler = ResumeContentJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private ResumeContent parsedContent;

    /**
     * 原始文本内容 (用于搜索)
     */
    private String rawText;

    /**
     * 解析状态: 0-未解析, 1-解析中, 2-解析成功, 3-解析失败
     */
    private Integer parseStatus;

    /**
     * 解析失败原因
     */
    private String parseError;

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
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 解析完成时间
     */
    private LocalDateTime parsedAt;
}
