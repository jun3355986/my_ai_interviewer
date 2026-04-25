package com.aiinterviewer.admin.rbac.entity;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPermission {

    private Long id;
    private Long menuId;
    private String permissionCode;
    private String permissionName;
    private String resourceType;
    private String resourcePath;
    private String httpMethod;
    private Boolean enabled;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
