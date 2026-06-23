package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiinterviewer.interview.controller.InterviewController;
import com.aiinterviewer.interview.dto.ChatRequest;
import com.aiinterviewer.interview.dto.PythonChatRequest;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.mapper.InterviewMessageMapper;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import com.aiinterviewer.interview.mapper.ScoreRecordMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.reactive.function.client.WebClient;

class InterviewUsernamePropagationTest {

    @Test
    void chatEndpointAcceptsGatewayUsernameHeader() {
        Method chatMethod = Arrays.stream(InterviewController.class.getDeclaredMethods())
                .filter(method -> "chat".equals(method.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(Arrays.stream(chatMethod.getParameters())
                .filter(parameter -> String.class.equals(parameter.getType()))
                .filter(parameter -> {
                    RequestHeader header = parameter.getAnnotation(RequestHeader.class);
                    return header != null && "X-User-Name".equals(header.value());
                }))
                .as("chat endpoint should accept gateway propagated X-User-Name")
                .hasSize(1);
    }

    @Test
    void resumeEndpointAcceptsGatewayUsernameHeader() {
        Method resumeMethod = Arrays.stream(InterviewController.class.getDeclaredMethods())
                .filter(method -> "resumeInterview".equals(method.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(Arrays.stream(resumeMethod.getParameters())
                .filter(parameter -> String.class.equals(parameter.getType()))
                .filter(parameter -> {
                    RequestHeader header = parameter.getAnnotation(RequestHeader.class);
                    return header != null && "X-User-Name".equals(header.value());
                }))
                .as("resume endpoint should accept gateway propagated X-User-Name")
                .hasSize(1);
    }

    @Test
    void pythonChatRequestIncludesGatewayUsername() throws Exception {
        SSEProxyService service = new SSEProxyService(
                mock(WebClient.class),
                new ObjectMapper(),
                mock(InterviewSessionMapper.class),
                mock(InterviewMessageMapper.class),
                mock(ScoreRecordMapper.class));

        Method buildPythonRequest = SSEProxyService.class.getDeclaredMethod(
                "buildPythonRequest",
                ChatRequest.class,
                InterviewSession.class,
                Long.class,
                String.class);
        buildPythonRequest.setAccessible(true);

        ChatRequest request = new ChatRequest();
        request.setMessage("请开始面试");

        InterviewSession session = new InterviewSession();
        session.setId("java-session-001");
        session.setPythonSessionId("py-session-001");
        session.setResumeContent("resume");
        session.setJobRequirements("job");
        session.setCandidateName("candidate");

        PythonChatRequest pythonRequest = (PythonChatRequest) buildPythonRequest.invoke(
                service,
                request,
                session,
                42L,
                "alice");

        assertThat(pythonRequest.getUsername()).isEqualTo("alice");
    }

    @Test
    void pythonResumeRequestIncludesTraceCorrelationFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SSEProxyService service = new SSEProxyService(
                mock(WebClient.class),
                objectMapper,
                mock(InterviewSessionMapper.class),
                mock(InterviewMessageMapper.class),
                mock(ScoreRecordMapper.class));

        Method buildPythonResumeRequest = SSEProxyService.class.getDeclaredMethod(
                "buildPythonResumeRequest",
                InterviewSession.class,
                Long.class,
                String.class);
        buildPythonResumeRequest.setAccessible(true);

        InterviewSession session = new InterviewSession();
        session.setId("java-session-002");
        session.setPythonSessionId("py-session-002");

        Object pythonRequest = buildPythonResumeRequest.invoke(
                service,
                session,
                42L,
                "alice");
        JsonNode json = objectMapper.valueToTree(pythonRequest);

        assertThat(json.get("session_id").asText()).isEqualTo("py-session-002");
        assertThat(json.get("request_id").asText()).isNotBlank();
        assertThat(json.get("java_session_id").asText()).isEqualTo("java-session-002");
        assertThat(json.get("user_id").asLong()).isEqualTo(42L);
        assertThat(json.get("username").asText()).isEqualTo("alice");
        assertThat(json.get("business_type").asText()).isEqualTo("interview");
        assertThat(json.get("entrypoint").asText()).isEqualTo("interview_resume");
    }
}
