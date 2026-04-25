package com.aiinterviewer.job.dto;

import java.util.List;

/**
 * 职位-简历匹配度分析请求
 */
public class MatchAnalysisRequest {

    /**
     * 简历ID
     */
    private Long resumeId;

    /**
     * 职位ID
     */
    private Long jobId;

    /**
     * 简历解析内容
     */
    private String resumeContent;

    public Long getResumeId() {
        return resumeId;
    }

    public void setResumeId(Long resumeId) {
        this.resumeId = resumeId;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getResumeContent() {
        return resumeContent;
    }

    public void setResumeContent(String resumeContent) {
        this.resumeContent = resumeContent;
    }
}
