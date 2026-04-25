package com.aiinterviewer.job.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建/更新职位请求
 */
@Data
public class JobRequest {

    private String title;
    private String company;
    private String department;
    private String location;
    private String jobType;
    private String experienceRequired;
    private String educationRequired;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String description;
    private String requirements;
    private List<String> skills;
    private LocalDateTime deadline;
}
