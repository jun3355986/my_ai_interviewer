package com.aiinterviewer.interview.mapper;

import com.aiinterviewer.interview.entity.InterviewLineage;
import com.aiinterviewer.interview.projection.LineageSummaryRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    @Select("""
            SELECT COUNT(*)
            FROM t_interview_lineage
            WHERE user_id = #{userId}
              AND (archived IS NULL OR archived = FALSE)
            """)
    Long countOwnedByUser(@Param("userId") Long userId);

    @Select("""
            SELECT MAX(last_business_activity_at)
            FROM t_interview_lineage
            WHERE user_id = #{userId}
            """)
    java.time.LocalDateTime selectLatestActivityAt(@Param("userId") Long userId);

    @Select("""
            SELECT TO_CHAR(created_at, 'YYYY-MM-DD') AS trend_date,
                   COUNT(*) AS trend_count
            FROM t_interview_lineage
            WHERE user_id = #{userId}
              AND created_at >= #{since}
            GROUP BY TO_CHAR(created_at, 'YYYY-MM-DD')
            """)
    List<TrendCountRow> countDailyTrendSince(
            @Param("userId") Long userId,
            @Param("since") java.time.LocalDateTime since);

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    class TrendCountRow {
        private String trendDate;
        private Long trendCount;
    }
}
