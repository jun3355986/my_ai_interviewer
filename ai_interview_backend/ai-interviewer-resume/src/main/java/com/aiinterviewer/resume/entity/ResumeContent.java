package com.aiinterviewer.resume.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 简历解析内容 (JSON结构)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeContent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 姓名
     */
    private String name;

    /**
     * 性别
     */
    private String gender;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 居住城市
     */
    private String location;

    /**
     * 求职意向
     */
    private String jobIntent;

    /**
     * 期望薪资
     */
    private String expectedSalary;

    /**
     * 工作年限
     */
    private String workYears;

    /**
     * 最高学历
     */
    private String education;

    /**
     * 毕业院校
     */
    private String university;

    /**
     * 专业
     */
    private String major;

    /**
     * 技能列表
     */
    private List<String> skills;

    /**
     * 工作经历
     */
    private List<WorkExperience> workExperience;

    /**
     * 项目经历
     */
    private List<ProjectExperience> projectExperience;

    /**
     * 证书
     */
    private List<String> certificates;

    /**
     * 自我评价
     */
    private String selfEvaluation;

    /**
     * 其他信息
     */
    private String otherInfo;

    /**
     * 工作经历
     */
    @Data
    public static class WorkExperience implements Serializable {
        private static final long serialVersionUID = 1L;

        private String company;           // 公司名称
        private String position;          // 职位
        private String duration;          // 工作时长
        private String department;        // 部门
        private String description;       // 工作描述
        private List<String> achievements; // 主要成就
    }

    /**
     * 项目经历
     */
    @Data
    public static class ProjectExperience implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;              // 项目名称
        private String role;              // 角色
        private String duration;          // 项目时长
        private String description;       // 项目描述
        private List<String> technologies; // 使用技术
        private List<String> responsibilities; // 职责
    }
}
