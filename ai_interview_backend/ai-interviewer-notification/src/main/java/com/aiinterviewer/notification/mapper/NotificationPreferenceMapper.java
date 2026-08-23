package com.aiinterviewer.notification.mapper;

import com.aiinterviewer.notification.entity.NotificationPreference;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 通知偏好Mapper
 */
@Mapper
public interface NotificationPreferenceMapper extends BaseMapper<NotificationPreference> {

    /**
     * 插入或更新用户通知偏好
     */
    @Update("""
            INSERT INTO t_notification_preference (user_id, progress_notify, evaluation_notify, updated_at)
            VALUES (#{userId}, #{progressNotify}, #{evaluationNotify}, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id)
            DO UPDATE SET progress_notify = EXCLUDED.progress_notify,
                          evaluation_notify = EXCLUDED.evaluation_notify,
                          updated_at = CURRENT_TIMESTAMP
            """)
    int upsert(NotificationPreference preference);
}
