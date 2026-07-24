package com.aiinterviewer.interview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.common.model.PageResult;
import com.aiinterviewer.common.model.Result;
import com.aiinterviewer.interview.dto.BranchTranscriptDTO;
import com.aiinterviewer.interview.dto.LineageSummaryDTO;
import com.aiinterviewer.interview.dto.LineageTreeDTO;
import com.aiinterviewer.interview.service.InterviewHistoryService;
import com.aiinterviewer.interview.service.InterviewService;
import com.aiinterviewer.interview.service.LineageTreeService;
import com.aiinterviewer.interview.service.SSEProxyService;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

class InterviewHistoryControllerTest {

    @Test
    void newHistoryEndpointsRequireGatewayAuthenticatedUserHeader() {
        for (String methodName : List.of(
                "listLineages", "getLineageTree", "getBranchTranscript")) {
            Method method = Arrays.stream(InterviewController.class.getDeclaredMethods())
                    .filter(candidate -> methodName.equals(candidate.getName()))
                    .findFirst()
                    .orElseThrow();

            assertThat(Arrays.stream(method.getParameters())
                    .filter(parameter -> Long.class.equals(parameter.getType()))
                    .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                    .filter(annotation -> annotation != null
                            && "X-User-Id".equals(annotation.value())
                            && annotation.required()))
                    .as(methodName + " must not fall back to user 1")
                    .hasSize(1);
        }
    }

    @Test
    void everyInterviewQueryParameterHasAnExplicitRuntimeName() {
        for (Method method : InterviewController.class.getDeclaredMethods()) {
            for (var parameter : method.getParameters()) {
                RequestParam annotation = parameter.getAnnotation(RequestParam.class);
                if (annotation == null) {
                    continue;
                }
                assertThat(annotation.value())
                        .as(method.getName() + " request parameter must not depend on -parameters")
                        .isNotBlank();
            }
        }
    }

    @Test
    void exposesPagedLineageHistoryForAuthenticatedUser() {
        InterviewHistoryService historyService = mock(InterviewHistoryService.class);
        LineageSummaryDTO summary = new LineageSummaryDTO();
        summary.setLineageId("lineage-1");
        PageResult<LineageSummaryDTO> page = PageResult.of(2L, 20L, 21L, List.of(summary));
        when(historyService.listLineages(42L, 2L, 20L, "Java", "score", "active"))
                .thenReturn(page);

        InterviewController controller = new InterviewController(
                mock(SSEProxyService.class),
                mock(InterviewService.class),
                historyService,
                mock(LineageTreeService.class));

        Result<PageResult<LineageSummaryDTO>> result = controller.listLineages(
                42L,
                2L,
                20L,
                "Java",
                "score",
                "active");

        assertThat(result.getData()).isSameAs(page);
        verify(historyService).listLineages(42L, 2L, 20L, "Java", "score", "active");
    }

    @Test
    void exposesOwnedBranchTranscriptWithoutTriggeringResumeOrChat() {
        InterviewHistoryService historyService = mock(InterviewHistoryService.class);
        BranchTranscriptDTO transcript = new BranchTranscriptDTO();
        transcript.setBranchId("branch-1");
        when(historyService.getBranchTranscript("branch-1", 42L)).thenReturn(transcript);

        InterviewController controller = new InterviewController(
                mock(SSEProxyService.class),
                mock(InterviewService.class),
                historyService,
                mock(LineageTreeService.class));

        Result<BranchTranscriptDTO> result = controller.getBranchTranscript("branch-1", 42L);

        assertThat(result.getData()).isSameAs(transcript);
        verify(historyService).getBranchTranscript("branch-1", 42L);
    }

    @Test
    void exposesOwnedLineageTree() {
        LineageTreeService treeService = mock(LineageTreeService.class);
        LineageTreeDTO tree = new LineageTreeDTO();
        tree.setLineageId("lineage-1");
        when(treeService.getTree("lineage-1", 42L)).thenReturn(tree);
        InterviewController controller = new InterviewController(
                mock(SSEProxyService.class),
                mock(InterviewService.class),
                mock(InterviewHistoryService.class),
                treeService);

        Result<LineageTreeDTO> result = controller.getLineageTree("lineage-1", 42L);

        assertThat(result.getData()).isSameAs(tree);
        verify(treeService).getTree("lineage-1", 42L);
    }
}
