package com.aiinterviewer.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 职位要求实体 (用于存储JD结构化数据)
 */
@Data
@TableName(value = "t_job_requirement", autoResultMap = true)
public class JobRequirement implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 要求ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 职位ID
     */
    private Long jobId;

    /**
     * 要求类型: SKILL, EXPERIENCE, EDUCATION, CERTIFICATE
     */
    private String requirementType;

    /**
     * 要求名称
     */
    private String name;

    /**
     * 要求描述
     */
    private String description;

    /**
     * 是否必需
     */
    private Boolean isRequired;

    /**
     * 权重 (0-100)
     */
    private Integer weight;

    /**
     * 详细条件 (JSON)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> conditions;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
