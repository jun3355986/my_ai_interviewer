package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiinterviewer.interview.dto.PracticeStatsDTO;
import com.aiinterviewer.interview.mapper.InterviewLineageMapper;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeStatsServiceTest {

    @Test
    void aggregatesCountsAndZeroFillsFourteenDayTrend() {
        InterviewLineageMapper lineageMapper = mock(InterviewLineageMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        PracticeStatsService service = new PracticeStatsService(lineageMapper, sessionMapper);

        LocalDateTime latest = LocalDateTime.of(2026, 8, 19, 11, 0);
        LocalDate today = LocalDate.now();
        LocalDate todayMinusOne = today.minusDays(1);
        LocalDate todayMinusTen = today.minusDays(10);

        when(lineageMapper.countOwnedByUser(7L)).thenReturn(6L);
        when(sessionMapper.countActiveLineages(7L)).thenReturn(2L);
        when(lineageMapper.selectLatestActivityAt(7L)).thenReturn(latest);
        when(lineageMapper.countDailyTrendSince(eq(7L), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new InterviewLineageMapper.TrendCountRow(today.toString(), 3L),
                        new InterviewLineageMapper.TrendCountRow(todayMinusOne.toString(), 1L),
                        new InterviewLineageMapper.TrendCountRow(todayMinusTen.toString(), 2L)));

        PracticeStatsDTO stats = service.getStats(7L);

        assertThat(stats.getTotalLineages()).isEqualTo(6L);
        assertThat(stats.getActiveLineages()).isEqualTo(2L);
        assertThat(stats.getLatestActivityAt()).isEqualTo(latest);

        List<PracticeStatsDTO.TrendPoint> trend = stats.getDailyTrend();
        assertThat(trend).hasSize(14);
        assertThat(trend.get(0).getDate()).isEqualTo(today.minusDays(13).toString());
        assertThat(trend.get(0).getCount()).isZero();
        assertThat(trend.get(3).getDate()).isEqualTo(todayMinusTen.toString());
        assertThat(trend.get(3).getCount()).isEqualTo(2L);
        assertThat(trend.get(12).getDate()).isEqualTo(todayMinusOne.toString());
        assertThat(trend.get(12).getCount()).isEqualTo(1L);
        assertThat(trend.get(13).getDate()).isEqualTo(today.toString());
        assertThat(trend.get(13).getCount()).isEqualTo(3L);
    }

    @Test
    void emptyHistoryYieldsZeroedStatsWithFullZeroTrend() {
        InterviewLineageMapper lineageMapper = mock(InterviewLineageMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        PracticeStatsService service = new PracticeStatsService(lineageMapper, sessionMapper);

        when(lineageMapper.countOwnedByUser(9L)).thenReturn(0L);
        when(sessionMapper.countActiveLineages(9L)).thenReturn(0L);
        when(lineageMapper.selectLatestActivityAt(9L)).thenReturn(null);
        when(lineageMapper.countDailyTrendSince(eq(9L), any(LocalDateTime.class)))
                .thenReturn(List.of());

        PracticeStatsDTO stats = service.getStats(9L);

        assertThat(stats.getTotalLineages()).isZero();
        assertThat(stats.getActiveLineages()).isZero();
        assertThat(stats.getLatestActivityAt()).isNull();
        assertThat(stats.getDailyTrend()).hasSize(14);
        assertThat(stats.getDailyTrend()).allSatisfy(point ->
                assertThat(point.getCount()).isZero());
    }
}
