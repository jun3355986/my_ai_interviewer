package com.aiinterviewer.evaluation.mapper;

import com.aiinterviewer.evaluation.entity.Evaluation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评估Mapper
 */
@Mapper
public interface EvaluationMapper extends BaseMapper<Evaluation> {

    /**
     * 根据会话ID查询评估
     */
    @Select("SELECT * FROM t_evaluation WHERE session_id = #{sessionId}")
    Evaluation selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询用户的评估报告
     */
    @Select("SELECT * FROM t_evaluation WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Evaluation> selectByUserId(@Param("userId") Long userId);

    /**
     * 获取所有评估统计数据
     */
    @Select("SELECT COUNT(*) FROM t_evaluation WHERE overall_score IS NOT NULL")
    Integer countCompleted();

    /**
     * 获取平均分
     */
    @Select("SELECT AVG(overall_score) FROM t_evaluation WHERE overall_score IS NOT NULL")
    Double selectAverageScore();
}
