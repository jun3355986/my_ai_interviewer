package com.aiinterviewer.interview.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForkAttemptDTO implements Serializable {

    private String branchId;
    private TurnAttemptDTO attempt;
}
