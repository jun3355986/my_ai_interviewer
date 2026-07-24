package com.aiinterviewer.interview.mapper;

import com.aiinterviewer.interview.entity.InterviewLineage;
import com.aiinterviewer.interview.projection.LineageSummaryRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InterviewLineageMapper extends BaseMapper<InterviewLineage> {

    @Select("""
            SELECT *
            FROM t_interview_lineage
            WHERE id = #{lineageId}
              AND user_id = #{userId}
            FOR UPDATE
            """)
    InterviewLineage selectOwnedForUpdate(
            @Param("lineageId") String lineageId,
            @Param("userId") Long userId);

    List<LineageSummaryRow> selectSummaryPage(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("sortBy") String sortBy,
            @Param("status") String status,
            @Param("limit") Long limit,
            @Param("offset") Long offset);

    Long countSummaries(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("status") String status);
}
