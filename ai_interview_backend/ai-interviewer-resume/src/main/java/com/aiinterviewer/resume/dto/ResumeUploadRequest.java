package com.aiinterviewer.resume.dto;

import lombok.Data;

import java.util.List;

/**
 * 简历上传请求
 */
@Data
public class ResumeUploadRequest {

    /**
     * 文件名 (可选, 用于重命名)
     */
    private String fileName;

    /**
     * 是否设为默认简历
     */
    private Boolean setAsDefault;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 备注
     */
    private String remark;

    public void setSetAsDefault(Boolean setAsDefault) {
        this.setAsDefault = setAsDefault;
    }

    public Boolean getSetAsDefault() {
        return setAsDefault;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getRemark() {
        return remark;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
