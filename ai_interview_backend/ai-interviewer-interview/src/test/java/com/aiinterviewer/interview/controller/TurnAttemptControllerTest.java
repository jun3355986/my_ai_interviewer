package com.aiinterviewer.interview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.common.model.Result;
import com.aiinterviewer.interview.dto.CreateTurnAttemptRequest;
import com.aiinterviewer.interview.dto.CreateForkAttemptRequest;
import com.aiinterviewer.interview.dto.ForkAttemptDTO;
import com.aiinterviewer.interview.dto.RetryTurnAttemptRequest;
import com.aiinterviewer.interview.dto.TurnAttemptDTO;
import com.aiinterviewer.interview.service.TurnAttemptService;
import com.aiinterviewer.interview.service.ForkAttemptService;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;

class TurnAttemptControllerTest {

    @Test
    void everyTurnAttemptEndpointRequiresAuthenticatedUserHeader() {
        for (Method method : TurnAttemptController.class.getDeclaredMethods()) {
            if (!List.of("create", "createFork", "get", "events", "retry", "cancel", "discard")
                    .contains(method.getName())) {
                continue;
            }
            assertThat(Arrays.stream(method.getParameters())
                    .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                    .filter(annotation -> annotation != null
                            && "X-User-Id".equals(annotation.value())
                            && annotation.required()))
                    .as(method.getName() + " must require X-User-Id")
                    .hasSize(1);
        }
    }

    @Test
    void delegatesCreateRetryCancelAndDiscardWithUserScope() {
        TurnAttemptService service = mock(TurnAttemptService.class);
        TurnAttemptController controller = new TurnAttemptController(
                service,
                mock(ForkAttemptService.class));
        CreateTurnAttemptRequest create = new CreateTurnAttemptRequest();
        RetryTurnAttemptRequest retry = new RetryTurnAttemptRequest();
        TurnAttemptDTO dto = new TurnAttemptDTO();
        dto.setTurnId("turn-1");
        when(service.create("branch-1", 42L, "alice", create)).thenReturn(dto);
        when(service.retry("turn-1", 42L, "alice", retry)).thenReturn(dto);
        when(service.cancel("turn-1", 42L)).thenReturn(dto);
        when(service.discard("turn-1", 42L)).thenReturn(dto);

        Result<TurnAttemptDTO> created = controller.create("branch-1", 42L, "alice", create);
        Result<TurnAttemptDTO> retried = controller.retry("turn-1", 42L, "alice", retry);
        Result<TurnAttemptDTO> cancelled = controller.cancel("turn-1", 42L);
        Result<TurnAttemptDTO> discarded = controller.discard("turn-1", 42L);

        assertThat(created.getData()).isSameAs(dto);
        assertThat(retried.getData()).isSameAs(dto);
        assertThat(cancelled.getData()).isSameAs(dto);
        assertThat(discarded.getData()).isSameAs(dto);
        verify(service).create("branch-1", 42L, "alice", create);
        verify(service).retry("turn-1", 42L, "alice", retry);
        verify(service).cancel("turn-1", 42L);
        verify(service).discard("turn-1", 42L);
    }

    @Test
    void delegatesExplicitForkSubmissionWithAuthenticatedScope() {
        TurnAttemptService turnAttemptService = mock(TurnAttemptService.class);
        ForkAttemptService forkAttemptService = mock(ForkAttemptService.class);
        TurnAttemptController controller = new TurnAttemptController(
                turnAttemptService,
                forkAttemptService);
        CreateForkAttemptRequest request = new CreateForkAttemptRequest();
        ForkAttemptDTO dto = new ForkAttemptDTO();
        dto.setBranchId("child");
        when(forkAttemptService.create("focused", 42L, "alice", request)).thenReturn(dto);

        Result<ForkAttemptDTO> result = controller.createFork(
                "focused", 42L, "alice", request);

        assertThat(result.getData()).isSameAs(dto);
        verify(forkAttemptService).create("focused", 42L, "alice", request);
    }
}
