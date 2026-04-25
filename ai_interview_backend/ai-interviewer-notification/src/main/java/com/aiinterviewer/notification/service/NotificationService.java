package com.aiinterviewer.notification.service;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.notification.dto.NotificationDTO;
import com.aiinterviewer.notification.dto.SendNotificationRequest;
import com.aiinterviewer.notification.entity.Notification;
import com.aiinterviewer.notification.mapper.NotificationMapper;
import com.aiinterviewer.user.entity.User;
import com.aiinterviewer.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final EmailService emailService;

    /**
     * 发送通知
     */
    @Transactional
    public void sendNotification(SendNotificationRequest request) {
        // 保存通知记录
        Notification notification = new Notification();
        notification.setType(request.getType());
        notification.setUserId(request.getUserId());
        notification.setTitle(request.getTitle());
        notification.setContent(request.getContent());
        notification.setRelatedType(request.getRelatedType());
        notification.setRelatedId(request.getRelatedId());
        notification.setStatus(0); // 待发送
        notification.setCreatedAt(LocalDateTime.now());

        notificationMapper.insert(notification);

        // 根据类型发送
        switch (request.getType()) {
            case "EMAIL" -> sendEmailNotification(request, notification);
            case "IN_APP" -> {
                notification.setStatus(1);
                notification.setSendTime(LocalDateTime.now());
                notificationMapper.updateById(notification);
            }
            default -> log.warn("未知通知类型: {}", request.getType());
        }
    }

    private void sendEmailNotification(SendNotificationRequest request, Notification notification) {
        try {
            String email = request.getEmail();
            if (email == null && request.getUserId() != null) {
                User user = userMapper.selectById(request.getUserId());
                if (user != null) {
                    email = user.getEmail();
                }
            }

            if (email != null && !email.isEmpty()) {
                emailService.sendSimpleEmail(email, request.getTitle(), request.getContent());
                notification.setStatus(1); // 已发送
                notification.setSendTime(LocalDateTime.now());
            } else {
                notification.setStatus(2); // 发送失败
                notification.setStatus(2);
            }
        } catch (Exception e) {
            log.error("邮件发送失败", e);
            notification.setStatus(2); // 发送失败
        }
        notificationMapper.updateById(notification);
    }

    /**
     * 获取用户通知列表
     */
    public List<NotificationDTO> listNotifications(Long userId) {
        List<Notification> notifications = notificationMapper.selectByUserId(userId);
        return notifications.stream().map(this::toDTO).toList();
    }

    /**
     * 获取未读通知数量
     */
    public Integer getUnreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    /**
     * 标记通知为已读
     */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限操作");
        }

        notification.setReadTime(LocalDateTime.now());
        notificationMapper.updateById(notification);
    }

    /**
     * 标记所有通知为已读
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsRead(userId);
    }

    /**
     * 删除通知
     */
    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限操作");
        }

        notificationMapper.deleteById(notificationId);
    }

    /**
     * 面试完成通知
     */
    public void notifyInterviewCompleted(Long userId, String sessionId, String jobTitle) {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setType("IN_APP");
        request.setUserId(userId);
        request.setTitle("面试已完成");
        request.setContent("您的面试已完成，职位: " + jobTitle);
        request.setRelatedType("INTERVIEW");
        request.setRelatedId(sessionId);
        sendNotification(request);
    }

    /**
     * 报告生成通知
     */
    public void notifyReportReady(Long userId, String sessionId, int score) {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setType("IN_APP");
        request.setUserId(userId);
        request.setTitle("评估报告已生成");
        request.setContent("您的面试评估报告已生成，综合评分: " + score + "分");
        request.setRelatedType("EVALUATION");
        request.setRelatedId(sessionId);
        sendNotification(request);
    }

    private NotificationDTO toDTO(Notification notification) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(notification.getId());
        dto.setUserId(notification.getUserId());
        dto.setType(notification.getType());
        dto.setTypeText(getTypeText(notification.getType()));
        dto.setTitle(notification.getTitle());
        dto.setContent(notification.getContent());
        dto.setRelatedType(notification.getRelatedType());
        dto.setRelatedId(notification.getRelatedId());
        dto.setStatus(notification.getStatus());
        dto.setStatusText(getStatusText(notification.getStatus()));
        dto.setIsRead(notification.getReadTime() != null);
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }

    private String getTypeText(String type) {
        return switch (type) {
            case "EMAIL" -> "邮件";
            case "SMS" -> "短信";
            case "IN_APP" -> "站内信";
            default -> "未知";
        };
    }

    private String getStatusText(Integer status) {
        return switch (status) {
            case 0 -> "待发送";
            case 1 -> "已发送";
            case 2 -> "发送失败";
            default -> "未知";
        };
    }
}
