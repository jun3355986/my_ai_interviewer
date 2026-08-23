package com.aiinterviewer.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户通知偏好实体
 */
@Data
@TableName("t_notification_preference")
public class NotificationPreference implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID（主键）
     */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /**
     * 面试进度通知开关
     */
    private Boolean progressNotify;

    /**
     * 评估完成通知开关
     */
    private Boolean evaluationNotify;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
