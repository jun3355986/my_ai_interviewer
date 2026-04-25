package com.aiinterviewer.interview.mapper;

import com.aiinterviewer.interview.entity.ScoreRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评分记录Mapper
 */
@Mapper
public interface ScoreRecordMapper extends BaseMapper<ScoreRecord> {

    /**
     * 查询会话的评分记录
     */
    @Select("SELECT * FROM t_score_record WHERE session_id = #{sessionId} ORDER BY question_index ASC")
    List<ScoreRecord> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 计算会话的平均分
     */
    @Select("SELECT AVG(score) FROM t_score_record WHERE session_id = #{sessionId}")
    Double calculateAverageScore(@Param("sessionId") String sessionId);

    /**
     * 获取会话的最大问题序号
     */
    @Select("SELECT COALESCE(MAX(question_index), 0) FROM t_score_record WHERE session_id = #{sessionId}")
    Integer getMaxQuestionIndex(@Param("sessionId") String sessionId);
}
