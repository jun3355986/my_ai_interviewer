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
 * 简历版本历史实体
 */
@Data
@TableName(value = "t_resume_version", autoResultMap = true)
public class ResumeVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 版本ID
     */
    @TableId(type = IdType.AUTO)
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
     * 文件路径
     */
    private String filePath;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 解析内容快照 (JSON)
     */
    @TableField(typeHandler = ResumeContentJsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private ResumeContent parsedContent;

    /**
     * 操作类型: UPLOAD, UPDATE, REPARSE
     */
    private String operationType;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
