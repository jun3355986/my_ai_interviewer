package com.aiinterviewer.admin.systemconfig;

import static org.assertj.core.api.Assertions.assertThat;

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
        InterviewStrategyService.DefaultInterviewStrategyRequest request =
                new InterviewStrategyService.DefaultInterviewStrategyRequest();
        request.setStrategyName("Default technical interview");
        request.setJobType("backend");
        request.setQuestionTypes(List.of("TECHNICAL", "PROJECT", "SYSTEM_DESIGN"));
        request.setQuestionCount(12);
        request.setDurationMinutes(45);
        request.setPromptTemplate("Ask structured technical questions");
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
}
