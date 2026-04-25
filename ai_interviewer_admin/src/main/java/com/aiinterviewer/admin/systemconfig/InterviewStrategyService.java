package com.aiinterviewer.admin.systemconfig;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InterviewStrategyService {

    private static final String DEFAULT_STRATEGY_CODE = "DEFAULT_TECHNICAL";
    private static final String DEFAULT_DIFFICULTY = "DEFAULT";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DefaultInterviewStrategyResponse getDefaultStrategy() {
        List<DefaultInterviewStrategyResponse> results = jdbcTemplate.query(
                """
                SELECT id, strategy_code, strategy_name, job_type, difficulty, question_count,
                       duration_minutes, prompt_template, scoring_rule::text AS scoring_rule,
                       enabled, created_by, updated_by, created_at, updated_at
                FROM t_interview_strategy_config
                WHERE strategy_code = ?
                  AND deleted_at IS NULL
                ORDER BY id DESC
                LIMIT 1
                """,
                this::mapDefaultStrategy,
                DEFAULT_STRATEGY_CODE);
        if (results.isEmpty()) {
            DefaultInterviewStrategyResponse defaults = new DefaultInterviewStrategyResponse();
            defaults.setStrategyCode(DEFAULT_STRATEGY_CODE);
            defaults.setStrategyName("Default technical interview");
            defaults.setDifficulty(DEFAULT_DIFFICULTY);
            defaults.setQuestionTypes(List.of());
            defaults.setDifficultyRatio(Map.of());
            defaults.setEnabled(true);
            return defaults;
        }
        return results.getFirst();
    }

    @Transactional
    @AdminAudit(module = "SYSTEM_CONFIG", operation = "UPDATE_INTERVIEW_STRATEGY", targetType = "INTERVIEW_STRATEGY",
            targetId = DEFAULT_STRATEGY_CODE)
    public void saveDefaultStrategy(DefaultInterviewStrategyRequest request) {
        validateRequest(request);
        String scoringRule = toScoringRuleJson(request.getQuestionTypes(), request.getDifficultyRatio());
        Long existingId = findDefaultStrategyId();
        if (existingId == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO t_interview_strategy_config
                        (strategy_code, strategy_name, job_type, difficulty, question_count,
                         duration_minutes, prompt_template, scoring_rule, enabled, created_by,
                         updated_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), TRUE, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    DEFAULT_STRATEGY_CODE,
                    request.getStrategyName(),
                    request.getJobType(),
                    DEFAULT_DIFFICULTY,
                    request.getQuestionCount(),
                    request.getDurationMinutes(),
                    request.getPromptTemplate(),
                    scoringRule,
                    request.getUpdatedBy(),
                    request.getUpdatedBy());
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE t_interview_strategy_config
                SET strategy_name = ?,
                    job_type = ?,
                    difficulty = ?,
                    question_count = ?,
                    duration_minutes = ?,
                    prompt_template = ?,
                    scoring_rule = CAST(? AS jsonb),
                    enabled = TRUE,
                    updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND deleted_at IS NULL
                """,
                request.getStrategyName(),
                request.getJobType(),
                DEFAULT_DIFFICULTY,
                request.getQuestionCount(),
                request.getDurationMinutes(),
                request.getPromptTemplate(),
                scoringRule,
                request.getUpdatedBy(),
                existingId);
    }

    private Long findDefaultStrategyId() {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT id
                FROM t_interview_strategy_config
                WHERE strategy_code = ?
                  AND deleted_at IS NULL
                ORDER BY id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getLong("id"),
                DEFAULT_STRATEGY_CODE);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private void validateRequest(DefaultInterviewStrategyRequest request) {
        if (request == null) {
            throw new AdminBusinessException(400, "面试策略参数不能为空");
        }
        if (!StringUtils.hasText(request.getStrategyName())) {
            throw new AdminBusinessException(400, "策略名称不能为空");
        }
        if (request.getQuestionTypes() == null || request.getQuestionTypes().isEmpty()) {
            throw new AdminBusinessException(400, "默认技术题型不能为空");
        }
        if (request.getQuestionCount() == null || request.getQuestionCount() < 1) {
            throw new AdminBusinessException(400, "默认题目数量必须大于0");
        }
        if (request.getDurationMinutes() == null || request.getDurationMinutes() < 1) {
            throw new AdminBusinessException(400, "默认面试时长必须大于0");
        }
        if (request.getDifficultyRatio() == null || request.getDifficultyRatio().isEmpty()) {
            throw new AdminBusinessException(400, "难度比例不能为空");
        }
        int ratioTotal = request.getDifficultyRatio().values().stream()
                .mapToInt(value -> value == null ? 0 : value)
                .sum();
        if (ratioTotal != 100) {
            throw new AdminBusinessException(400, "难度比例合计必须为100");
        }
    }

    private DefaultInterviewStrategyResponse mapDefaultStrategy(ResultSet rs, int rowNum) throws SQLException {
        DefaultInterviewStrategyResponse response = new DefaultInterviewStrategyResponse();
        response.setId(rs.getLong("id"));
        response.setStrategyCode(rs.getString("strategy_code"));
        response.setStrategyName(rs.getString("strategy_name"));
        response.setJobType(rs.getString("job_type"));
        response.setDifficulty(rs.getString("difficulty"));
        response.setQuestionCount(rs.getInt("question_count"));
        response.setDurationMinutes(rs.getInt("duration_minutes"));
        response.setPromptTemplate(rs.getString("prompt_template"));
        response.setEnabled(rs.getBoolean("enabled"));
        response.setCreatedBy(readNullableLong(rs, "created_by"));
        response.setUpdatedBy(readNullableLong(rs, "updated_by"));
        response.setCreatedAt(readNullableDateTime(rs, "created_at"));
        response.setUpdatedAt(readNullableDateTime(rs, "updated_at"));
        Map<String, Object> scoringRule = readScoringRule(rs.getString("scoring_rule"));
        response.setQuestionTypes(readQuestionTypes(scoringRule));
        response.setDifficultyRatio(readDifficultyRatio(scoringRule));
        return response;
    }

    private Long readNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime readNullableDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String toScoringRuleJson(List<String> questionTypes, Map<String, Integer> difficultyRatio) {
        Map<String, Object> scoringRule = new LinkedHashMap<>();
        scoringRule.put("questionTypes", questionTypes);
        scoringRule.put("difficultyRatio", difficultyRatio);
        try {
            return objectMapper.writeValueAsString(scoringRule);
        } catch (JsonProcessingException ex) {
            throw new AdminBusinessException(400, "面试策略JSON格式不合法", ex);
        }
    }

    private Map<String, Object> readScoringRule(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private List<String> readQuestionTypes(Map<String, Object> scoringRule) {
        Object value = scoringRule.get("questionTypes");
        if (value == null) {
            return List.of();
        }
        return objectMapper.convertValue(value, new TypeReference<>() {
        });
    }

    private Map<String, Integer> readDifficultyRatio(Map<String, Object> scoringRule) {
        Object value = scoringRule.get("difficultyRatio");
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, new TypeReference<>() {
        });
    }

    @Data
    public static class DefaultInterviewStrategyRequest {

        private String strategyName;
        private String jobType;
        private List<String> questionTypes = List.of();
        private Integer questionCount;
        private Integer durationMinutes;
        private String promptTemplate;
        private Map<String, Integer> difficultyRatio = Map.of();
        private Long updatedBy;
    }

    @Data
    public static class DefaultInterviewStrategyResponse {

        private Long id;
        private String strategyCode;
        private String strategyName;
        private String jobType;
        private String difficulty;
        private List<String> questionTypes = List.of();
        private Integer questionCount;
        private Integer durationMinutes;
        private String promptTemplate;
        private Map<String, Integer> difficultyRatio = Map.of();
        private Boolean enabled;
        private Long createdBy;
        private Long updatedBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
