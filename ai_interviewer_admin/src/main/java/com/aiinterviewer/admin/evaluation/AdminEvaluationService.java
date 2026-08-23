package com.aiinterviewer.admin.evaluation;

import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.evaluation.mapper.AdminEvaluationMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminEvaluationService {

    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 20L;
    private static final long MAX_SIZE = 100L;
    private static final long MAX_CURRENT = 1_000_000L;

    private final AdminEvaluationMapper adminEvaluationMapper;

    public PageResult<AdminEvaluationListItem> listEvaluations(AdminEvaluationQuery query) {
        AdminEvaluationQuery safeQuery = query == null ? new AdminEvaluationQuery() : query;
        safeQuery.normalizeFilters();
        long current = safeQuery.normalizedCurrent();
        long size = safeQuery.normalizedSize();
        Long total = adminEvaluationMapper.countEvaluations(safeQuery);
        List<AdminEvaluationListItem> records =
                adminEvaluationMapper.selectEvaluations(safeQuery, size, safeOffset(current, size));
        return PageResult.of(current, size, total == null ? 0L : total, records);
    }

    public AdminEvaluationListItem findEvaluationById(Long evaluationId) {
        return adminEvaluationMapper.selectEvaluationById(evaluationId);
    }

    public AdminEvaluationListItem findEvaluationBySessionId(String sessionId) {
        return adminEvaluationMapper.selectEvaluationBySessionId(sessionId);
    }

    private long safeOffset(long current, long size) {
        try {
            return Math.multiplyExact(current - 1, size);
        } catch (ArithmeticException ex) {
            return (MAX_CURRENT - 1) * MAX_SIZE;
        }
    }

    private static String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    @Data
    public static class AdminEvaluationQuery {

        private String recommendation;
        private Integer minOverallScore;
        private Integer maxOverallScore;
        private Long current = DEFAULT_CURRENT;
        private Long size = DEFAULT_SIZE;

        void normalizeFilters() {
            recommendation = normalizeText(recommendation);
        }

        long normalizedCurrent() {
            if (current == null || current < 1) {
                return DEFAULT_CURRENT;
            }
            return Math.min(current, MAX_CURRENT);
        }

        long normalizedSize() {
            if (size == null || size < 1) {
                return DEFAULT_SIZE;
            }
            return Math.min(size, MAX_SIZE);
        }
    }

    @Data
    public static class AdminEvaluationListItem {

        private Long id;
        private String sessionId;
        private Long userId;
        private String username;
        private Long jobId;
        private String jobTitle;
        private Integer overallScore;
        private Integer technicalScore;
        private Integer communicationScore;
        private Integer logicScore;
        private Integer experienceScore;
        private String summary;
        private String strengths;
        private String weaknesses;
        private String recommendation;
        private String detailedFeedback;
        private Integer totalQuestions;
        private Integer answeredQuestions;
        private BigDecimal averageScore;
        private Integer durationMinutes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
