package com.aiinterviewer.admin.dashboard.mapper;

import com.aiinterviewer.admin.dashboard.dto.DashboardOverviewResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface DashboardMapper {

    Long countUsers();

    Long countJobs();

    Long countResumes();

    Long countInterviews();

    Long countEvaluations();

    List<DashboardOverviewResponse.ScoreRangeCount> selectScoreDistribution();

    List<DashboardOverviewResponse.DailyInterviewCount> selectInterviewTrend(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    List<DashboardOverviewResponse.RecentErrorSession> selectRecentErrors(@Param("limit") int limit);
}
