package com.aiinterviewer.interview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.common.model.Result;
import com.aiinterviewer.interview.dto.CreateStartAttemptRequest;
import com.aiinterviewer.interview.dto.StartAttemptDTO;
import com.aiinterviewer.interview.service.StartAttemptService;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class StartAttemptControllerTest {

    @Test
    void startRequiresGatewayAuthenticatedUserAndDelegatesExactPayload() {
        Method method = Arrays.stream(StartAttemptController.class.getDeclaredMethods())
                .filter(candidate -> "start".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(annotation -> annotation != null
                        && "X-User-Id".equals(annotation.value())
                        && annotation.required()))
                .hasSize(1);

        StartAttemptService service = mock(StartAttemptService.class);
        CreateStartAttemptRequest request = new CreateStartAttemptRequest();
        request.setTurnId("start-controller");
        request.setResumeId(20L);
        request.setJobId(10L);
        StartAttemptDTO expected = new StartAttemptDTO();
        expected.setBranchId("root-1");
        when(service.create(42L, "alice", request)).thenReturn(expected);

        Result<StartAttemptDTO> result = new StartAttemptController(service)
                .start(42L, "alice", request);

        assertThat(result.getData()).isSameAs(expected);
        verify(service).create(42L, "alice", request);
    }

    @Test
    void startIdempotencyConflictsUseTheSameSanitizedConflictHandler() {
        RestControllerAdvice advice = TurnAttemptExceptionHandler.class
                .getAnnotation(RestControllerAdvice.class);

        assertThat(advice).isNotNull();
        assertThat(advice.assignableTypes())
                .contains(StartAttemptController.class, TurnAttemptController.class);
    }
}
