package com.aiinterviewer.admin.notification;

import com.aiinterviewer.admin.common.model.Result;
import com.aiinterviewer.admin.notification.entity.NotificationTemplate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    @GetMapping("/templates")
    public Result<List<NotificationTemplate>> listTemplates() {
        return Result.success(adminNotificationService.listTemplates());
    }

    @GetMapping("/templates/{templateCode}")
    public Result<NotificationTemplate> getTemplate(@PathVariable String templateCode) {
        return Result.success(adminNotificationService.getTemplate(templateCode));
    }

    @PostMapping("/templates")
    public Result<String> createTemplate(@RequestBody AdminNotificationService.TemplateRequest request) {
        return Result.success(adminNotificationService.createTemplate(request));
    }

    @PutMapping("/templates/{templateCode}")
    public Result<Void> updateTemplate(
            @PathVariable String templateCode,
            @RequestBody AdminNotificationService.TemplateRequest request) {
        adminNotificationService.updateTemplate(templateCode, request);
        return Result.success();
    }

    @PostMapping("/send")
    public Result<AdminNotificationService.SendNotificationResponse> sendNotification(
            @RequestBody AdminNotificationService.SendNotificationRequest request) {
        return Result.success(adminNotificationService.sendNotification(request));
    }
}
