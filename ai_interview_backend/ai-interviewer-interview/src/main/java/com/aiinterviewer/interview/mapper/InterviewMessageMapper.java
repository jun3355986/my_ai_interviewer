package com.aiinterviewer.interview.mapper;

import com.aiinterviewer.interview.entity.InterviewMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 面试消息Mapper
 */
@Mapper
public interface InterviewMessageMapper extends BaseMapper<InterviewMessage> {

    /**
     * 查询会话的消息历史
     */
    default List<InterviewMessage> selectBySessionId(String sessionId) {
        return selectList(Wrappers.<InterviewMessage>lambdaQuery()
                .eq(InterviewMessage::getSessionId, sessionId)
                .orderByAsc(InterviewMessage::getSequence));
    }

    /**
     * 获取会话的最大消息序号
     */
    @Select("SELECT COALESCE(MAX(sequence), 0) FROM t_interview_message WHERE session_id = #{sessionId}")
    Integer getMaxSequence(@Param("sessionId") String sessionId);

    /**
     * 获取会话最近一条AI消息内容（通常是当前问题）
     */
    @Select("SELECT content FROM t_interview_message WHERE session_id = #{sessionId} AND role = 'ai' ORDER BY sequence DESC LIMIT 1")
    String selectLatestAIMessageContent(@Param("sessionId") String sessionId);
}
