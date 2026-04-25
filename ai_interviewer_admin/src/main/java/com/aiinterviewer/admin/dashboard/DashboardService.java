package com.aiinterviewer.admin.dashboard;

import com.aiinterviewer.admin.dashboard.dto.DashboardOverviewResponse;
import com.aiinterviewer.admin.dashboard.mapper.DashboardMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final int TREND_DAYS = 30;
    private static final int RECENT_ERROR_LIMIT = 10;
    private static final List<String> SCORE_RANGES = List.of("0-59", "60-69", "70-79", "80-89", "90-100");

    private final ObjectProvider<DashboardMapper> dashboardMapperProvider;

    public DashboardService(ObjectProvider<DashboardMapper> dashboardMapperProvider) {
        this.dashboardMapperProvider = dashboardMapperProvider;
    }

    public DashboardOverviewResponse getOverview() {
        DashboardMapper dashboardMapper = dashboardMapper();
        DashboardOverviewResponse response = new DashboardOverviewResponse();
        response.setUserCount(safeCount(dashboardMapper.countUsers()));
        response.setJobCount(safeCount(dashboardMapper.countJobs()));
        response.setResumeCount(safeCount(dashboardMapper.countResumes()));
        response.setInterviewCount(safeCount(dashboardMapper.countInterviews()));
        response.setEvaluationCount(safeCount(dashboardMapper.countEvaluations()));
        response.setScoreDistribution(buildScoreDistribution(dashboardMapper));
        response.setInterviewTrend(buildInterviewTrend(dashboardMapper));
        response.setRecentErrors(dashboardMapper.selectRecentErrors(RECENT_ERROR_LIMIT));
        return response;
    }

    private List<DashboardOverviewResponse.ScoreRangeCount> buildScoreDistribution(DashboardMapper dashboardMapper) {
        Map<String, Long> counts = dashboardMapper.selectScoreDistribution().stream()
                .collect(Collectors.toMap(
                        DashboardOverviewResponse.ScoreRangeCount::getRange,
                        item -> safeCount(item.getCount())));
        return SCORE_RANGES.stream()
                .map(range -> scoreRange(range, counts.getOrDefault(range, 0L)))
                .toList();
    }

    private List<DashboardOverviewResponse.DailyInterviewCount> buildInterviewTrend(DashboardMapper dashboardMapper) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(TREND_DAYS - 1L);
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();
        Map<LocalDate, DashboardOverviewResponse.DailyInterviewCount> actualCounts =
                dashboardMapper.selectInterviewTrend(startTime, endTime).stream()
                        .collect(Collectors.toMap(
                                DashboardOverviewResponse.DailyInterviewCount::getDate,
                                Function.identity(),
                                (left, right) -> left,
                                LinkedHashMap::new));

        return startDate.datesUntil(today.plusDays(1))
                .map(date -> dailyCount(date, actualCounts.get(date)))
                .toList();
    }

    private DashboardOverviewResponse.ScoreRangeCount scoreRange(String range, Long count) {
        DashboardOverviewResponse.ScoreRangeCount item = new DashboardOverviewResponse.ScoreRangeCount();
        item.setRange(range);
        item.setCount(safeCount(count));
        return item;
    }

    private DashboardOverviewResponse.DailyInterviewCount dailyCount(
            LocalDate date,
            DashboardOverviewResponse.DailyInterviewCount actual) {
        DashboardOverviewResponse.DailyInterviewCount item = new DashboardOverviewResponse.DailyInterviewCount();
        item.setDate(date);
        item.setCount(actual == null ? 0L : safeCount(actual.getCount()));
        return item;
    }

    private Long safeCount(Long count) {
        return count == null ? 0L : count;
    }

    private DashboardMapper dashboardMapper() {
        DashboardMapper dashboardMapper = dashboardMapperProvider.getIfAvailable();
        if (dashboardMapper == null) {
            throw new IllegalStateException("DashboardMapper is required for dashboard overview queries");
        }
        return dashboardMapper;
    }
}
