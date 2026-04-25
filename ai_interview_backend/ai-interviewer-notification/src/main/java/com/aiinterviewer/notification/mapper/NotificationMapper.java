package com.aiinterviewer.notification.mapper;

import com.aiinterviewer.notification.entity.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 通知Mapper
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 查询用户的通知列表
     */
    @Select("SELECT * FROM t_notification WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Notification> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询用户未读通知数量
     */
    @Select("SELECT COUNT(*) FROM t_notification WHERE user_id = #{userId} AND status = 1 AND read_time IS NULL")
    Integer countUnread(@Param("userId") Long userId);

    /**
     * 标记通知为已读
     */
    @Update("UPDATE t_notification SET read_time = CURRENT_TIMESTAMP WHERE id = #{id} AND user_id = #{userId}")
    int markAsRead(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 标记所有通知为已读
     */
    @Update("UPDATE t_notification SET read_time = CURRENT_TIMESTAMP WHERE user_id = #{userId} AND read_time IS NULL")
    int markAllAsRead(@Param("userId") Long userId);
}
