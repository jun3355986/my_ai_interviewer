package com.aiinterviewer.admin.rbac.entity;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminMenu {

    private Long id;
    private Long parentId;
    private String menuCode;
    private String menuName;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private Boolean visible;
    private Boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
