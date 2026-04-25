package com.aiinterviewer.admin.dashboard.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class DashboardOverviewResponse {

    private Long userCount = 0L;
    private Long jobCount = 0L;
    private Long resumeCount = 0L;
    private Long interviewCount = 0L;
    private Long evaluationCount = 0L;
    private List<ScoreRangeCount> scoreDistribution = new ArrayList<>();
    private List<DailyInterviewCount> interviewTrend = new ArrayList<>();
    private List<RecentErrorSession> recentErrors = new ArrayList<>();

    @Data
    public static class ScoreRangeCount {

        private String range;
        private Long count;
    }

    @Data
    public static class DailyInterviewCount {

        private LocalDate date;
        private Long count;
    }

    @Data
    public static class RecentErrorSession {

        private String sessionId;
        private Long userId;
        private Long jobId;
        private String candidateName;
        private Integer status;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;
        private String reason;
    }
}
