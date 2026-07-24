package com.aiinterviewer.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_interview_lineage")
public class InterviewLineage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private String id;
    private Long userId;
    private String rootSessionId;
    private LocalDateTime lastBusinessActivityAt;
    private Boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
