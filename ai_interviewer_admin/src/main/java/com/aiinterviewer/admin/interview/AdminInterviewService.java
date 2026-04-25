package com.aiinterviewer.admin.interview;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.interview.dto.InterviewDiagnosisResponse;
import com.aiinterviewer.admin.interview.mapper.AdminInterviewMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminInterviewService {

    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_IN_PROGRESS = 1;
    private static final int STATUS_CANCELED = 3;
    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 20L;
    private static final long MAX_SIZE = 100L;
    private static final long MAX_CURRENT = 1_000_000L;
    private static final long EARLY_CONCLUSION_MINUTES = 2L;

    private final AdminInterviewMapper adminInterviewMapper;
    private final ObjectMapper objectMapper;

    public PageResult<AdminInterviewListItem> listInterviews(AdminInterviewQuery query) {
        AdminInterviewQuery safeQuery = query == null ? new AdminInterviewQuery() : query;
        safeQuery.normalizeFilters();
        long current = safeQuery.normalizedCurrent();
        long size = safeQuery.normalizedSize();
        Long total = adminInterviewMapper.countInterviews(safeQuery);
        List<AdminInterviewListItem> records =
                adminInterviewMapper.selectInterviews(safeQuery, size, safeOffset(current, size));
        return PageResult.of(current, size, total == null ? 0L : total, records);
    }

    public AdminInterviewDetail getInterviewDetail(String sessionId) {
        ensureSessionId(sessionId);
        AdminInterviewDetail detail = adminInterviewMapper.selectInterviewDetail(sessionId);
        if (detail == null) {
            throw new AdminBusinessException(404, "面试会话不存在");
        }
        detail.setMessages(adminInterviewMapper.selectMessages(sessionId));
        detail.setScoreRecords(adminInterviewMapper.selectScoreRecords(sessionId));
        detail.setEvaluation(adminInterviewMapper.selectEvaluationSummary(sessionId));
        return detail;
    }

    @Transactional(readOnly = true)
    public InterviewDiagnosisResponse diagnoseInterview(String sessionId) {
        ensureSessionId(sessionId);
        DiagnosisSessionSnapshot snapshot = adminInterviewMapper.selectDiagnosisSnapshot(sessionId);
        if (snapshot == null) {
            throw new AdminBusinessException(404, "面试会话不存在");
        }

        InterviewDiagnosisResponse response = new InterviewDiagnosisResponse();
        response.setSessionId(sessionId);
        boolean technicalDiagnosisApplicable = isTechnicalDiagnosisApplicable(snapshot);
        response.setMissingTechnicalQuestions(technicalDiagnosisApplicable && !snapshot.hasTechnicalScoreRecord());
        response.setEmptyTechnicalPool(
                technicalDiagnosisApplicable && isEmptyOrInvalidJsonArray(snapshot.getTechnicalQuestionsPoolJson()));
        response.setMissingScores(isMissingScores(snapshot));
        response.setEarlyConcludedStage(isEarlyConcluded(snapshot));

        List<String> findings = new ArrayList<>();
        if (response.isMissingTechnicalQuestions()) {
            findings.add("MISSING_TECHNICAL_QUESTIONS");
        }
        if (response.isEmptyTechnicalPool()) {
            findings.add("EMPTY_TECHNICAL_POOL");
        }
        if (response.isMissingScores()) {
            findings.add("MISSING_SCORES");
        }
        if (response.isEarlyConcludedStage()) {
            findings.add("EARLY_CONCLUDED_STAGE");
        }
        response.setFindings(findings);
        return response;
    }

    @Transactional
    @AdminAudit(module = "INTERVIEW", operation = "CANCEL", targetType = "INTERVIEW_SESSION", targetIdParam = "sessionId")
    public void cancelInterview(String sessionId) {
        ensureCancelableSession(sessionId);
        int updated = adminInterviewMapper.cancelSession(sessionId);
        if (updated == 0) {
            ensureCancelableSession(sessionId);
            throw new AdminBusinessException(409, "当前面试状态不能取消");
        }
    }

    private boolean isTechnicalDiagnosisApplicable(DiagnosisSessionSnapshot snapshot) {
        if (snapshot.getStatus() != null && snapshot.getStatus() == STATUS_COMPLETED) {
            return true;
        }
        String stage = normalizeText(snapshot.getStage());
        return stage != null
                && (stage.contains("technical")
                        || stage.equals("concluded")
                        || stage.equals("completed")
                        || stage.equals("final"));
    }

    private boolean isMissingScores(DiagnosisSessionSnapshot snapshot) {
        return snapshot.getStatus() != null
                && snapshot.getStatus() == STATUS_COMPLETED
                && (snapshot.getScoreRecordCount() == null
                        || snapshot.getScoreRecordCount() == 0
                        || snapshot.getNullScoreCount() != null && snapshot.getNullScoreCount() > 0);
    }

    private boolean isEarlyConcluded(DiagnosisSessionSnapshot snapshot) {
        if (snapshot.getStatus() == null || snapshot.getStatus() != STATUS_COMPLETED) {
            return false;
        }
        if (snapshot.getStartedAt() != null && snapshot.getFinishedAt() != null) {
            long durationMinutes = Duration.between(snapshot.getStartedAt(), snapshot.getFinishedAt()).toMinutes();
            return durationMinutes < EARLY_CONCLUSION_MINUTES;
        }
        String stage = snapshot.getStage();
        return StringUtils.hasText(stage)
                && (stage.equalsIgnoreCase("completed")
                        || stage.equalsIgnoreCase("concluded")
                        || stage.equalsIgnoreCase("final"));
    }

    private boolean isEmptyOrInvalidJsonArray(String json) {
        if (!StringUtils.hasText(json)) {
            return true;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node == null || node.isNull() || !node.isArray() || node.isEmpty();
        } catch (Exception ex) {
            return true;
        }
    }

    private void ensureCancelableSession(String sessionId) {
        ensureSessionId(sessionId);
        Integer status = adminInterviewMapper.selectSessionStatus(sessionId);
        if (status == null) {
            throw new AdminBusinessException(404, "面试会话不存在");
        }
        if (status == STATUS_COMPLETED) {
            throw new AdminBusinessException(409, "已完成面试不能取消");
        }
        if (status == STATUS_CANCELED) {
            throw new AdminBusinessException(409, "面试会话已取消");
        }
        if (status != STATUS_IN_PROGRESS) {
            throw new AdminBusinessException(409, "当前面试状态不能取消");
        }
    }

    private void ensureSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new AdminBusinessException(400, "面试会话ID不能为空");
        }
    }

    private static String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private long safeOffset(long current, long size) {
        try {
            return Math.multiplyExact(current - 1, size);
        } catch (ArithmeticException ex) {
            return (MAX_CURRENT - 1) * MAX_SIZE;
        }
    }

    @Data
    public static class AdminInterviewQuery {

        private Long userId;
        private Long jobId;
        private String stage;
        private Integer status;
        private LocalDateTime startedFrom;
        private LocalDateTime startedTo;
        private Long current = DEFAULT_CURRENT;
        private Long size = DEFAULT_SIZE;

        void normalizeFilters() {
            stage = normalizeText(stage);
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
    public static class AdminInterviewListItem {

        private String id;
        private Long userId;
        private String username;
        private Long jobId;
        private String jobTitle;
        private String candidateName;
        private String stage;
        private Integer status;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class AdminInterviewDetail {

        private String id;
        private Long userId;
        private String username;
        private Long resumeId;
        private Long jobId;
        private String jobTitle;
        private String candidateName;
        private String stage;
        private Integer status;
        private String resumeContent;
        private String jobRequirements;
        private Integer projectQuestionsCount;
        private Integer targetProjectQuestions;
        private String projectQuestionsPoolJson;
        private String technicalQuestionsPoolJson;
        private Integer currentFollowupCount;
        private String pythonSessionId;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<AdminInterviewMessageItem> messages = List.of();
        private List<AdminScoreRecordItem> scoreRecords = List.of();
        private AdminEvaluationSummary evaluation;
    }

    @Data
    public static class AdminInterviewMessageItem {

        private Long id;
        private String sessionId;
        private String role;
        private String content;
        private String stage;
        private Integer sequence;
        private LocalDateTime createdAt;
    }

    @Data
    public static class AdminScoreRecordItem {

        private Long id;
        private String sessionId;
        private Integer questionIndex;
        private String questionType;
        private String question;
        private String answer;
        private Integer score;
        private String feedback;
        private Boolean followup;
        private LocalDateTime createdAt;
    }

    @Data
    public static class AdminEvaluationSummary {

        private Long id;
        private String sessionId;
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

    @Data
    public static class DiagnosisSessionSnapshot {

        private String id;
        private String stage;
        private Integer status;
        private String technicalQuestionsPoolJson;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long scoreRecordCount;
        private Long nullScoreCount;
        private Long technicalScoreRecordCount;

        public boolean hasTechnicalScoreRecord() {
            return technicalScoreRecordCount != null && technicalScoreRecordCount > 0;
        }
    }
}
