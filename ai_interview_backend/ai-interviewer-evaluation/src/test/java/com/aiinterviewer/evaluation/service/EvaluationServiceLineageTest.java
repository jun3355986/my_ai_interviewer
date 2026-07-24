package com.aiinterviewer.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.evaluation.EvaluationApplication;
import com.aiinterviewer.evaluation.dto.EvaluationDTO;
import com.aiinterviewer.evaluation.dto.ScoreDTO;
import com.aiinterviewer.evaluation.entity.Evaluation;
import com.aiinterviewer.evaluation.mapper.EvaluationMapper;
import com.aiinterviewer.interview.dto.ComposedAssessmentDTO;
import com.aiinterviewer.interview.service.ComposedAssessmentService;
import com.aiinterviewer.interview.service.EvaluationBranchGuard;
import com.aiinterviewer.interview.service.InterviewHistoryService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.annotations.Select;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.annotation.Import;

class EvaluationServiceLineageTest {

    @Test
    void evaluationApplicationImportsEveryInterviewDomainServiceRequiredAtRuntime() {
        Import imported = EvaluationApplication.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(imported.value())
                .contains(
                        InterviewHistoryService.class,
                        ComposedAssessmentService.class,
                        EvaluationBranchGuard.class);
    }

    @Test
    void evaluationApplicationCannotRunInterviewMigrationsAgainstAdminHistory() {
        SpringBootApplication application = EvaluationApplication.class.getAnnotation(
                SpringBootApplication.class);

        assertThat(application).isNotNull();
        assertThat(application.exclude()).contains(FlywayAutoConfiguration.class);
    }

    @Test
    void siblingReportsUseTheirOwnComposedAssessmentPathsAndRemainIndependent() {
        EvaluationMapper mapper = mock(EvaluationMapper.class);
        ComposedAssessmentService assessments = mock(ComposedAssessmentService.class);
        EvaluationService service = new EvaluationService(
                mapper, assessments, mock(EvaluationBranchGuard.class));
        when(mapper.selectBySessionId("child-a")).thenReturn(null);
        when(mapper.selectBySessionId("child-b")).thenReturn(null);
        when(assessments.compose("child-a", 42L)).thenReturn(List.of(
                score(1L, "root", true, 1, 80),
                score(2L, "child-a", false, 2, 100)));
        when(assessments.compose("child-b", 42L)).thenReturn(List.of(
                score(1L, "root", true, 1, 80),
                score(3L, "child-b", false, 2, 60)));

        EvaluationDTO first = service.generateReport("child-a", 42L, 10L);
        EvaluationDTO second = service.generateReport("child-b", 42L, 10L);

        assertThat(first.getSessionId()).isEqualTo("child-a");
        assertThat(first.getOverallScore()).isEqualTo(90);
        assertThat(first.getTotalQuestions()).isEqualTo(2);
        assertThat(second.getSessionId()).isEqualTo("child-b");
        assertThat(second.getOverallScore()).isEqualTo(70);
        ArgumentCaptor<Evaluation> reports = ArgumentCaptor.forClass(Evaluation.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(reports.capture());
        assertThat(reports.getAllValues())
                .extracting(Evaluation::getSessionId)
                .containsExactly("child-a", "child-b");
    }

    @Test
    void scoreReadsExposeInheritedAndOwningBranchMetadataInComposedOrder() {
        EvaluationMapper mapper = mock(EvaluationMapper.class);
        ComposedAssessmentService assessments = mock(ComposedAssessmentService.class);
        EvaluationService service = new EvaluationService(
                mapper, assessments, mock(EvaluationBranchGuard.class));
        Evaluation report = new Evaluation();
        report.setSessionId("nested");
        report.setUserId(42L);
        when(mapper.selectBySessionId("nested")).thenReturn(report);
        when(assessments.compose("nested", 42L)).thenReturn(List.of(
                score(1L, "root", true, 1, 80),
                score(4L, "nested", false, 2, 95)));

        List<ScoreDTO> result = service.getScores("nested", 42L);

        assertThat(result).extracting(ScoreDTO::getId).containsExactly(1L, 4L);
        assertThat(result).extracting(ScoreDTO::getSessionId)
                .containsExactly("nested", "nested");
        assertThat(result).extracting(ScoreDTO::getQuestionIndex).containsExactly(1, 2);
        assertThat(result).extracting(ScoreDTO::getOwningBranchId)
                .containsExactly("root", "nested");
        assertThat(result).extracting(ScoreDTO::getInherited)
                .containsExactly(true, false);
    }

    @Test
    void existingReportStillRequiresTheReportUserToMatchTheCaller() {
        EvaluationMapper mapper = mock(EvaluationMapper.class);
        ComposedAssessmentService assessments = mock(ComposedAssessmentService.class);
        EvaluationService service = new EvaluationService(
                mapper, assessments, mock(EvaluationBranchGuard.class));
        Evaluation report = new Evaluation();
        report.setSessionId("child");
        report.setUserId(99L);
        when(mapper.selectBySessionId("child")).thenReturn(report);

        assertThatThrownBy(() -> service.generateReport("child", 42L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(2003);
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(Evaluation.class));
    }

    @Test
    void validatesBranchOwnershipBeforeReturningExistingReportOrScores() {
        EvaluationMapper mapper = mock(EvaluationMapper.class);
        ComposedAssessmentService assessments = mock(ComposedAssessmentService.class);
        EvaluationBranchGuard guard = mock(EvaluationBranchGuard.class);
        EvaluationService service = new EvaluationService(mapper, assessments, guard);
        doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "denied"))
                .when(assessments)
                .verifyOwnership("foreign", 99L);
        doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "denied"))
                .when(guard)
                .lockCompletedOwnedBranch("foreign", 99L);

        assertThatThrownBy(() -> service.getReport("foreign", 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(2003);
        assertThatThrownBy(() -> service.getScores("foreign", 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(2003);
        assertThatThrownBy(() -> service.generateReport("foreign", 99L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(2003);
        verify(mapper, never()).selectBySessionId("foreign");
    }

    @Test
    void activeBranchCannotFreezeAnIncompleteEvaluationReport() {
        EvaluationMapper mapper = mock(EvaluationMapper.class);
        ComposedAssessmentService assessments = mock(ComposedAssessmentService.class);
        EvaluationBranchGuard guard = mock(EvaluationBranchGuard.class);
        EvaluationService service = new EvaluationService(mapper, assessments, guard);
        doThrow(new BusinessException(ErrorCode.EVALUATION_NOT_READY, "active"))
                .when(guard)
                .lockCompletedOwnedBranch("active", 42L);

        assertThatThrownBy(() -> service.generateReport("active", 42L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(6001);
        verify(mapper, never()).selectBySessionId("active");
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(Evaluation.class));
    }

    @Test
    void canonicalizesProductionQnaAndLegacyQuestionTypesForCategoryScores() {
        EvaluationMapper mapper = mock(EvaluationMapper.class);
        ComposedAssessmentService assessments = mock(ComposedAssessmentService.class);
        EvaluationService service = new EvaluationService(
                mapper, assessments, mock(EvaluationBranchGuard.class));
        when(mapper.selectBySessionId("mixed-types")).thenReturn(null);
        when(assessments.compose("mixed-types", 42L)).thenReturn(List.of(
                score(1L, "root", true, 1, 80, "technical_qna"),
                score(2L, "root", true, 2, 100, " TECHNICAL "),
                score(3L, "mixed-types", false, 3, 70, "project_qna"),
                score(4L, "mixed-types", false, 4, 90, "PROJECT")));

        EvaluationDTO report = service.generateReport("mixed-types", 42L, 10L);

        assertThat(report.getTechnicalScore()).isEqualTo(90);
        assertThat(report.getExperienceScore()).isEqualTo(80);
        assertThat(report.getStrengths()).contains("技术基础").contains("项目实战经验");
    }

    @Test
    void equivalentComposedPathsProduceDeterministicCommunicationScores() {
        EvaluationMapper mapper = mock(EvaluationMapper.class);
        ComposedAssessmentService assessments = mock(ComposedAssessmentService.class);
        EvaluationService service = new EvaluationService(
                mapper, assessments, mock(EvaluationBranchGuard.class));
        List<ComposedAssessmentDTO> path = List.of(
                score(1L, "root", true, 1, 80),
                score(2L, "child", false, 2, 100));
        when(assessments.compose("child-a", 42L)).thenReturn(path);
        when(assessments.compose("child-b", 42L)).thenReturn(path);
        when(assessments.compose("child-c", 42L)).thenReturn(path);

        List<Integer> communicationScores = List.of(
                service.generateReport("child-a", 42L, 10L).getCommunicationScore(),
                service.generateReport("child-b", 42L, 10L).getCommunicationScore(),
                service.generateReport("child-c", 42L, 10L).getCommunicationScore());

        assertThat(communicationScores).containsOnly(78);
    }

    @Test
    void reportListsRequireCurrentSessionAndLineageOwnership() throws Exception {
        Method selectByUserId = EvaluationMapper.class.getMethod("selectByUserId", Long.class);
        String sql = String.join(" ", selectByUserId.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ")
                .toLowerCase();

        assertThat(sql)
                .contains("join t_interview_session")
                .contains("join t_interview_lineage")
                .contains("session.user_id = #{userid}")
                .contains("lineage.user_id = #{userid}")
                .contains("evaluation.user_id = #{userid}");
    }

    private static ComposedAssessmentDTO score(
            Long id,
            String owner,
            boolean inherited,
            int order,
            int value) {
        return score(id, owner, inherited, order, value, "technical");
    }

    private static ComposedAssessmentDTO score(
            Long id,
            String owner,
            boolean inherited,
            int order,
            int value,
            String questionType) {
        ComposedAssessmentDTO score = new ComposedAssessmentDTO();
        score.setId(id);
        score.setOwningBranchId(owner);
        score.setInherited(inherited);
        score.setDisplayOrder(order);
        score.setQuestionIndex(order);
        score.setQuestionType(questionType);
        score.setQuestion("question-" + id);
        score.setAnswer("answer-" + id);
        score.setScore(value);
        score.setFeedback("feedback-" + id);
        score.setIsFollowup(false);
        return score;
    }
}
