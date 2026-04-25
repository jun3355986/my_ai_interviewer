package com.aiinterviewer.admin.systemconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterviewStrategyServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private InterviewStrategyService interviewStrategyService;

    @Test
    void strategyConfigStoresDefaultTechnicalQuestionTypesCountsAndDifficultyRatio() {
        InterviewStrategyService.DefaultInterviewStrategyRequest request = validStrategyRequest();
        Map<String, Integer> difficultyRatio = new LinkedHashMap<>();
        difficultyRatio.put("easy", 20);
        difficultyRatio.put("medium", 50);
        difficultyRatio.put("hard", 30);
        request.setDifficultyRatio(difficultyRatio);
        request.setUpdatedBy(99L);

        interviewStrategyService.saveDefaultStrategy(request);

        InterviewStrategyService.DefaultInterviewStrategyResponse saved =
                interviewStrategyService.getDefaultStrategy();
        assertThat(saved.getStrategyCode()).isEqualTo("DEFAULT_TECHNICAL");
        assertThat(saved.getStrategyName()).isEqualTo("Default technical interview");
        assertThat(saved.getJobType()).isEqualTo("backend");
        assertThat(saved.getQuestionTypes()).containsExactly("TECHNICAL", "PROJECT", "SYSTEM_DESIGN");
        assertThat(saved.getQuestionCount()).isEqualTo(12);
        assertThat(saved.getDurationMinutes()).isEqualTo(45);
        assertThat(saved.getDifficultyRatio()).containsEntry("easy", 20)
                .containsEntry("medium", 50)
                .containsEntry("hard", 30);

        request.setQuestionCount(10);
        request.setQuestionTypes(List.of("TECHNICAL", "CODING"));
        request.setDifficultyRatio(Map.of("easy", 30, "medium", 50, "hard", 20));
        interviewStrategyService.saveDefaultStrategy(request);

        InterviewStrategyService.DefaultInterviewStrategyResponse updated =
                interviewStrategyService.getDefaultStrategy();
        assertThat(updated.getQuestionTypes()).containsExactly("TECHNICAL", "CODING");
        assertThat(updated.getQuestionCount()).isEqualTo(10);
        assertThat(updated.getDifficultyRatio()).containsEntry("hard", 20);
    }

    @Test
    void invalidRatioTotalIsRejected() {
        InterviewStrategyService.DefaultInterviewStrategyRequest request = validStrategyRequest();
        request.setDifficultyRatio(Map.of("easy", 20, "medium", 20, "hard", 20));

        assertThatThrownBy(() -> interviewStrategyService.saveDefaultStrategy(request))
                .hasMessageContaining("难度比例合计必须为100");
    }

    @Test
    void negativeRatioIsRejected() {
        InterviewStrategyService.DefaultInterviewStrategyRequest request = validStrategyRequest();
        request.setDifficultyRatio(Map.of("easy", 80, "hard", -20, "medium", 40));

        assertThatThrownBy(() -> interviewStrategyService.saveDefaultStrategy(request))
                .hasMessageContaining("难度比例不能为负数");
    }

    @Test
    void blankAndDuplicateQuestionTypesAreRejected() {
        InterviewStrategyService.DefaultInterviewStrategyRequest blankRequest = validStrategyRequest();
        blankRequest.setQuestionTypes(List.of("TECHNICAL", " "));

        assertThatThrownBy(() -> interviewStrategyService.saveDefaultStrategy(blankRequest))
                .hasMessageContaining("默认技术题型不能为空");

        InterviewStrategyService.DefaultInterviewStrategyRequest duplicateRequest = validStrategyRequest();
        duplicateRequest.setQuestionTypes(List.of("TECHNICAL", " technical "));

        assertThatThrownBy(() -> interviewStrategyService.saveDefaultStrategy(duplicateRequest))
                .hasMessageContaining("默认技术题型不能重复");
    }

    @Test
    void malformedOrUnexpectedScoringRuleReturnsDefaultsInsteadOfThrowing() {
        jdbcTemplate.update(
                """
                INSERT INTO t_interview_strategy_config
                    (strategy_code, strategy_name, job_type, difficulty, question_count,
                     duration_minutes, prompt_template, scoring_rule, enabled)
                VALUES
                    ('DEFAULT_TECHNICAL', 'Unexpected scoring rule', 'backend', 'DEFAULT', 10,
                     45, 'prompt', CAST('{"questionTypes":{"bad":true},"difficultyRatio":["bad"]}' AS jsonb), TRUE)
                """);

        InterviewStrategyService.DefaultInterviewStrategyResponse response =
                interviewStrategyService.getDefaultStrategy();

        assertThat(response.getQuestionTypes()).isEmpty();
        assertThat(response.getDifficultyRatio()).isEmpty();
    }

    private InterviewStrategyService.DefaultInterviewStrategyRequest validStrategyRequest() {
        InterviewStrategyService.DefaultInterviewStrategyRequest request =
                new InterviewStrategyService.DefaultInterviewStrategyRequest();
        request.setStrategyName("Default technical interview");
        request.setJobType("backend");
        request.setQuestionTypes(List.of("TECHNICAL", "PROJECT", "SYSTEM_DESIGN"));
        request.setQuestionCount(12);
        request.setDurationMinutes(45);
        request.setPromptTemplate("Ask structured technical questions");
        request.setDifficultyRatio(Map.of("easy", 20, "medium", 50, "hard", 30));
        request.setUpdatedBy(99L);
        return request;
    }
}
