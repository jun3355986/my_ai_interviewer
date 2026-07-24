package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.interview.dto.ComposedAssessmentDTO;
import com.aiinterviewer.interview.dto.LineageTreeDTO;
import com.aiinterviewer.interview.dto.LineageTreeNodeDTO;
import com.aiinterviewer.interview.repository.LineageTreeRepository;
import com.aiinterviewer.interview.repository.LineageTreeRepository.BranchNodeRow;
import com.aiinterviewer.interview.repository.LineageTreeRepository.EvaluationSummaryRow;
import com.aiinterviewer.interview.repository.LineageTreeRepository.TurnStateRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LineageTreeServiceTest {

    @Test
    void returnsStableTreeCountsEvaluationRecoveryAndLatestActiveFocus() {
        LineageTreeRepository repository = mock(LineageTreeRepository.class);
        ComposedAssessmentService assessments = mock(ComposedAssessmentService.class);
        LineageTreeService service = new LineageTreeService(repository, assessments);
        LocalDateTime t1 = LocalDateTime.of(2026, 7, 24, 8, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 7, 24, 9, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 7, 24, 10, 0);

        when(repository.findOwnedRootSessionId("lineage-1", 42L))
                .thenReturn(Optional.of("root"));
        when(repository.findBranches("lineage-1", 42L)).thenReturn(List.of(
                new BranchNodeRow(
                        "root", null, "原始分支", null, null,
                        "concluded", 2, 5L, t1, 5, 5),
                new BranchNodeRow(
                        "sibling", "root", "分支 1", 10L, 10L,
                        "technical_qna", 1, 2L, t3, 3, 5),
                new BranchNodeRow(
                        "nested", "sibling", "分支 2", 20L, 21L,
                        "project_qna", 1, 1L, t2, 2, 5)));
        when(repository.findEvaluation("root", 42L))
                .thenReturn(Optional.of(new EvaluationSummaryRow(88, "根报告")));
        when(repository.findEvaluation("sibling", 42L)).thenReturn(Optional.empty());
        when(repository.findEvaluation("nested", 42L)).thenReturn(Optional.empty());
        when(repository.findLatestRecoverableTurn("root", 42L)).thenReturn(Optional.empty());
        when(repository.findLatestRecoverableTurn("sibling", 42L)).thenReturn(Optional.empty());
        when(repository.findLatestRecoverableTurn("nested", 42L))
                .thenReturn(Optional.of(new TurnStateRow("turn-nested", "FAILED", "MODEL_PROCESSING_FAILED")));
        when(assessments.compose("root", 42L)).thenReturn(List.of(
                assessment("root", false),
                assessment("root", false)));
        when(assessments.compose("sibling", 42L)).thenReturn(List.of(
                assessment("root", true),
                assessment("sibling", false)));
        when(assessments.compose("nested", 42L)).thenReturn(List.of(
                assessment("root", true),
                assessment("sibling", true),
                assessment("nested", false)));

        LineageTreeDTO tree = service.getTree("lineage-1", 42L);

        assertThat(tree.getLineageId()).isEqualTo("lineage-1");
        assertThat(tree.getRootBranchId()).isEqualTo("root");
        assertThat(tree.getFocusedBranchId()).isEqualTo("sibling");
        assertThat(tree.getNodes()).extracting(LineageTreeNodeDTO::getBranchId)
                .containsExactly("root", "sibling", "nested");
        LineageTreeNodeDTO root = tree.getNodes().get(0);
        assertThat(root.getCompletedScore()).isEqualTo(88);
        assertThat(root.getEvaluationSummary()).isEqualTo("根报告");
        assertThat(root.getOwnedAssessmentCount()).isEqualTo(2);
        assertThat(root.getInheritedAssessmentCount()).isZero();
        LineageTreeNodeDTO nested = tree.getNodes().get(2);
        assertThat(nested.getOwnedAssessmentCount()).isEqualTo(1);
        assertThat(nested.getInheritedAssessmentCount()).isEqualTo(2);
        assertThat(nested.getTotalAssessmentCount()).isEqualTo(3);
        assertThat(nested.getRecoverableTurnId()).isEqualTo("turn-nested");
        assertThat(nested.getRecoverableTurnStatus()).isEqualTo("FAILED");
    }

    @Test
    void deniesCrossUserTreeAccess() {
        LineageTreeRepository repository = mock(LineageTreeRepository.class);
        when(repository.findOwnedRootSessionId("lineage-1", 99L)).thenReturn(Optional.empty());
        LineageTreeService service = new LineageTreeService(
                repository,
                mock(ComposedAssessmentService.class));

        assertThatThrownBy(() -> service.getTree("lineage-1", 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(2003);
    }

    private static ComposedAssessmentDTO assessment(String owner, boolean inherited) {
        ComposedAssessmentDTO assessment = new ComposedAssessmentDTO();
        assessment.setOwningBranchId(owner);
        assessment.setInherited(inherited);
        return assessment;
    }
}
