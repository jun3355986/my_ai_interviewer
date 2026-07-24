package com.aiinterviewer.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.Data;

@Data
public class CreateForkAttemptRequest implements Serializable {

    @NotBlank
    @Size(max = 50)
    private String turnId;

    @NotNull
    private Long triggerMessageId;

    @NotBlank
    private String candidateAnswer;

    @NotNull
    private Long expectedFocusedBranchVersion;

    private Long expectedFocusedTailMessageId;
}
