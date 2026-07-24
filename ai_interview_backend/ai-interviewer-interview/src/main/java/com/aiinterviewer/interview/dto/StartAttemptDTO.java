package com.aiinterviewer.interview.dto;

import java.io.Serializable;
import lombok.Data;

@Data
public class StartAttemptDTO implements Serializable {

    private String lineageId;
    private String branchId;
    private TurnAttemptDTO attempt;
}
