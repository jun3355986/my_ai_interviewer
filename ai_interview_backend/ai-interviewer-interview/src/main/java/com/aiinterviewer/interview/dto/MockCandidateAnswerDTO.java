package com.aiinterviewer.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 模拟面试候选人回答响应
 */
@Data
@AllArgsConstructor
@Schema(description = "模拟面试候选人回答")
public class MockCandidateAnswerDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "候选人回答文本")
    private String answer;
}
