package com.aiinterviewer.job.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 职位DTO
 */
@Data
public class JobDTO {

    private Long id;
    private String title;
    private String company;
    private String department;
    private String location;
    private String jobType;
    private String experienceRequired;
    private String educationRequired;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String salaryDisplay;
    private String description;
    private String requirements;
    private List<String> skills;
    private Integer status;
    private String statusText;
    private LocalDateTime publishedAt;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
}
