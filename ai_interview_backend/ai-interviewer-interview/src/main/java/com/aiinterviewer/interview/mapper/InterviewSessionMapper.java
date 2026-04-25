package com.aiinterviewer.interview.mapper;

import com.aiinterviewer.interview.entity.InterviewSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 面试会话Mapper
 */
@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {

    /**
     * 查询用户的未完成会话
     */
    @Select("SELECT * FROM t_interview_session WHERE user_id = #{userId} AND status = 1 ORDER BY updated_at DESC")
    List<InterviewSession> selectIncompleteByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的所有会话（分页）
     */
    @Select("SELECT * FROM t_interview_session WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<InterviewSession> selectByUserIdWithPage(@Param("userId") Long userId,
                                                   @Param("limit") Long limit,
                                                   @Param("offset") Long offset);

    /**
     * 统计用户的会话数
     */
    @Select("SELECT COUNT(*) FROM t_interview_session WHERE user_id = #{userId}")
    Long countByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM t_interview_session WHERE python_session_id = #{pythonSessionId} LIMIT 1")
    InterviewSession selectByPythonSessionId(@Param("pythonSessionId") String pythonSessionId);
}
