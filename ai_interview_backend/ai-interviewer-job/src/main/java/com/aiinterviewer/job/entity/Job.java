package com.aiinterviewer.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aiinterviewer.job.handler.JobSkillsJsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 职位实体
 */
@Data
@TableName(value = "t_job", autoResultMap = true)
public class Job implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 职位ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 职位名称
     */
    private String title;

    /**
     * 公司名称
     */
    private String company;

    /**
     * 部门
     */
    private String department;

    /**
     * 工作地点
     */
    private String location;

    /**
     * 职位类型: FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP
     */
    private String jobType;

    /**
     * 经验要求
     */
    private String experienceRequired;

    /**
     * 学历要求
     */
    private String educationRequired;

    /**
     * 薪资范围(最低)
     */
    private java.math.BigDecimal salaryMin;

    /**
     * 薪资范围(最高)
     */
    private java.math.BigDecimal salaryMax;

    /**
     * 职位描述
     */
    private String description;

    /**
     * 岗位要求
     */
    private String requirements;

    /**
     * 技能标签 (JSON数组)
     */
    @TableField(typeHandler = JobSkillsJsonbTypeHandler.class)
    private List<String> skills;

    /**
     * 状态: 1-招聘中 0-已关闭
     */
    private Integer status;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 开始发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 截止时间
     */
    private LocalDateTime deadline;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
