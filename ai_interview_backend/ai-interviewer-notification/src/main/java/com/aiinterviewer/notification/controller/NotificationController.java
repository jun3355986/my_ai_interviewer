package com.aiinterviewer.notification.controller;

import com.aiinterviewer.common.model.Result;
import com.aiinterviewer.notification.dto.NotificationDTO;
import com.aiinterviewer.notification.dto.NotificationPreferenceDTO;
import com.aiinterviewer.notification.dto.SendNotificationRequest;
import com.aiinterviewer.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知控制器
 */
@Tag(name = "通知管理", description = "通知相关接口")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    private Long getCurrentUserId(HttpServletRequest request) {
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new IllegalStateException("用户未登录");
        }
        return Long.parseLong(userIdStr);
    }

    /**
     * 获取通知列表
     */
    @Operation(summary = "获取通知列表")
    @GetMapping
    public Result<List<NotificationDTO>> listNotifications(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<NotificationDTO> notifications = notificationService.listNotifications(userId);
        return Result.success(notifications);
    }

    /**
     * 获取未读通知数量
     */
    @Operation(summary = "获取未读数量")
    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        Integer count = notificationService.getUnreadCount(userId);
        return Result.success(count);
    }

    /**
     * 标记通知为已读
     */
    @Operation(summary = "标记已读")
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        notificationService.markAsRead(id, userId);
        return Result.success(null);
    }

    /**
     * 标记所有通知为已读
     */
    @Operation(summary = "全部已读")
    @PutMapping("/read-all")
    public Result<Void> markAllAsRead(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        notificationService.markAllAsRead(userId);
        return Result.success(null);
    }

    /**
     * 删除通知
     */
    @Operation(summary = "删除通知")
    @DeleteMapping("/{id}")
    public Result<Void> deleteNotification(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        notificationService.deleteNotification(id, userId);
        return Result.success(null);
    }

    /**
     * 发送通知 (管理员用)
     */
    @Operation(summary = "发送通知")
    @PostMapping("/send")
    public Result<Void> sendNotification(@RequestBody SendNotificationRequest request) {
        notificationService.sendNotification(request);
        return Result.success(null);
    }

    /**
     * 获取当前用户通知偏好
     */
    @Operation(summary = "获取通知偏好")
    @GetMapping("/preferences")
    public Result<NotificationPreferenceDTO> getPreferences(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        return Result.success(notificationService.getPreferences(userId));
    }

    /**
     * 更新当前用户通知偏好
     */
    @Operation(summary = "更新通知偏好")
    @PutMapping("/preferences")
    public Result<NotificationPreferenceDTO> updatePreferences(
            @RequestBody NotificationPreferenceDTO preferenceRequest,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        return Result.success(notificationService.updatePreferences(userId, preferenceRequest));
    }
}
