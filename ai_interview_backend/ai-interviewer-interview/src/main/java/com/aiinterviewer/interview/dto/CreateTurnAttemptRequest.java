package com.aiinterviewer.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.Data;

@Data
public class CreateTurnAttemptRequest implements Serializable {

    @NotBlank
    @Size(max = 50)
    private String turnId;

    @NotBlank
    private String candidateAnswer;

    @NotNull
    private Long expectedBranchVersion;

    private Long expectedTailMessageId;
}
