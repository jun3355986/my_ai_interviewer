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

    @Select("SELECT * FROM t_interview_session WHERE id = #{sessionId} FOR UPDATE")
    InterviewSession selectByIdForUpdate(@Param("sessionId") String sessionId);

    /**
     * 查询用户的未完成会话
     */
    @Select("""
            SELECT session.*
            FROM t_interview_session session
            JOIN t_interview_lineage lineage ON lineage.id = session.lineage_id
            WHERE session.user_id = #{userId}
              AND lineage.user_id = #{userId}
              AND session.status = 1
            ORDER BY session.updated_at DESC
            """)
    List<InterviewSession> selectIncompleteByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的所有会话（分页）
     */
    @Select("""
            SELECT session.*
            FROM t_interview_session session
            JOIN t_interview_lineage lineage ON lineage.id = session.lineage_id
            WHERE session.user_id = #{userId}
              AND lineage.user_id = #{userId}
            ORDER BY session.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<InterviewSession> selectByUserIdWithPage(@Param("userId") Long userId,
                                                   @Param("limit") Long limit,
                                                   @Param("offset") Long offset);

    /**
     * 统计用户的会话数
     */
    @Select("""
            SELECT COUNT(*)
            FROM t_interview_session session
            JOIN t_interview_lineage lineage ON lineage.id = session.lineage_id
            WHERE session.user_id = #{userId}
              AND lineage.user_id = #{userId}
            """)
    Long countByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM t_interview_session WHERE python_session_id = #{pythonSessionId} LIMIT 1")
    InterviewSession selectByPythonSessionId(@Param("pythonSessionId") String pythonSessionId);

    /**
     * 统计用户仍有进行中分支的谱系数量
     */
    @Select("""
            SELECT COUNT(DISTINCT session.lineage_id)
            FROM t_interview_session session
            JOIN t_interview_lineage lineage ON lineage.id = session.lineage_id
            WHERE session.user_id = #{userId}
              AND lineage.user_id = #{userId}
              AND session.status = 1
            """)
    Long countActiveLineages(@Param("userId") Long userId);
}
