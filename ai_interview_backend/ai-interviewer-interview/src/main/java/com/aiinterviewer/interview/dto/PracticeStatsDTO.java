package com.aiinterviewer.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人练习统计 DTO
 *
 * 面向普通用户客户端首页：总练习次数、进行中数量、最近活动时间和近 14 天练习趋势。
 */
@Data
public class PracticeStatsDTO {

    private Long totalLineages;
    private Long activeLineages;
    private LocalDateTime latestActivityAt;
    private List<TrendPoint> dailyTrend;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrendPoint {
        private String date;
        private Long count;
    }
}
