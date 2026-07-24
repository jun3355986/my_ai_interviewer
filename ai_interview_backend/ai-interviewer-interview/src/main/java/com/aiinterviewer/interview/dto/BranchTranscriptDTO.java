package com.aiinterviewer.interview.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class BranchTranscriptDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String lineageId;
    private String branchId;
    private String branchLabel;
    private String parentBranchId;
    private Long forkPointMessageId;
    private String stage;
    private Integer status;
    private Long branchVersion;
    private List<BranchMessageDTO> messages;
}
