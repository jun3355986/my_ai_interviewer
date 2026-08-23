package com.aiinterviewer.interview.service;

import com.aiinterviewer.interview.dto.PracticeStatsDTO;
import com.aiinterviewer.interview.mapper.InterviewLineageMapper;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人练习统计服务
 *
 * 供普通用户客户端首页展示：总练习次数、进行中练习、最近活动时间与近 14 天练习趋势。
 */
@Service
@RequiredArgsConstructor
public class PracticeStatsService {

    private static final DateTimeFormatter TREND_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final InterviewLineageMapper lineageMapper;
    private final InterviewSessionMapper sessionMapper;

    public PracticeStatsDTO getStats(Long userId) {
        PracticeStatsDTO stats = new PracticeStatsDTO();
        Long total = lineageMapper.countOwnedByUser(userId);
        stats.setTotalLineages(total == null ? 0L : total);
        Long active = sessionMapper.countActiveLineages(userId);
        stats.setActiveLineages(active == null ? 0L : active);
        stats.setLatestActivityAt(lineageMapper.selectLatestActivityAt(userId));
        stats.setDailyTrend(buildDailyTrend(userId));
        return stats;
    }

    private List<PracticeStatsDTO.TrendPoint> buildDailyTrend(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(13);
        Map<String, Long> countsByDate = new HashMap<>();
        for (InterviewLineageMapper.TrendCountRow row
                : lineageMapper.countDailyTrendSince(userId, firstDay.atStartOfDay())) {
            countsByDate.put(row.getTrendDate(), row.getTrendCount());
        }
        List<PracticeStatsDTO.TrendPoint> trend = new ArrayList<>(14);
        for (int offset = 0; offset < 14; offset++) {
            String date = firstDay.plusDays(offset).format(TREND_DATE);
            trend.add(new PracticeStatsDTO.TrendPoint(
                    date,
                    countsByDate.getOrDefault(date, 0L)));
        }
        return trend;
    }
}
