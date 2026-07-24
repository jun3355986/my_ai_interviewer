package com.aiinterviewer.interview.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
public class LineageTreeDTO implements Serializable {

    private String lineageId;
    private String rootBranchId;
    private String focusedBranchId;
    private List<LineageTreeNodeDTO> nodes;
}
