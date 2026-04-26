package com.aiinterviewer.admin.audit;

import com.aiinterviewer.admin.audit.entity.AdminOperationLog;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.common.model.Result;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/audit/logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public Result<PageResult<AdminOperationLog>> listLogs(
            @RequestParam(required = false) Long adminUserId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime endTime,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size) {
        AuditLogService.AuditLogQuery query = new AuditLogService.AuditLogQuery();
        query.setAdminUserId(adminUserId);
        query.setModule(module);
        query.setOperation(operation);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setCurrent(current);
        query.setSize(size);
        return Result.success(auditLogService.listLogs(query));
    }
}
