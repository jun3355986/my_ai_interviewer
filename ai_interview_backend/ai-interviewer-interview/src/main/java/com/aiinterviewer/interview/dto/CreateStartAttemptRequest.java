package com.aiinterviewer.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateStartAttemptRequest {

    @NotBlank
    @Size(max = 50)
    private String turnId;

    private Long resumeId;

    private Long jobId;
}
