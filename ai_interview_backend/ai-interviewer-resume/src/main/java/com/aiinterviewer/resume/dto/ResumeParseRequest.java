package com.aiinterviewer.resume.dto;

import lombok.Data;

/**
 * 简历解析请求
 */
@Data
public class ResumeParseRequest {

    /**
     * 是否强制重新解析 (即使已解析过)
     */
    private Boolean forceReparse;

    /**
     * 自定义备注
     */
    private String remark;
}
