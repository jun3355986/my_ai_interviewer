package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.interview.config.WebClientConfig;
import com.aiinterviewer.interview.dto.ChatRequest;
import com.aiinterviewer.interview.entity.InterviewMessage;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.mapper.InterviewMessageMapper;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import com.aiinterviewer.interview.mapper.ScoreRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

class SSEProxyServiceStubReplayTest {

    private HttpServer pythonStub;

    @AfterEach
    void stopPythonStub() {
        if (pythonStub != null) {
            pythonStub.stop(0);
        }
    }

    @Test
    void proxyChatConsumesStubSseAndPersistsSessionMessagesAndStage() throws Exception {
        String responseBody = """
                event: status
                data: {"session_id":"stub-java-session-001","stage":"opening"}

                event: question
                data: {"question":{"id":"stub-self-intro-001","type":"self_introduction","text":"请用 2 分钟介绍一下你的后端项目经验。"},"next_stage":"self_introduction"}

                event: chunk
                data: {"content":"请用 2 分钟介绍一下你的后端项目经验。"}

                event: result
                data: {"next_stage":"self_introduction","next_question":"请用 2 分钟介绍一下你的后端项目经验。"}

                event: done
                data: {"stage":"self_introduction","is_interview_complete":false}

                """;
        pythonStub = startPythonStub(responseBody);

        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewMessageMapper messageMapper = mock(InterviewMessageMapper.class);
        ScoreRecordMapper scoreRecordMapper = mock(ScoreRecordMapper.class);
        when(messageMapper.getMaxSequence(any())).thenReturn(0, 1);
        when(scoreRecordMapper.getMaxQuestionIndex(any())).thenReturn(0);

        SSEProxyService service = new SSEProxyService(
                webClient("http://127.0.0.1:" + pythonStub.getAddress().getPort()),
                new ObjectMapper(),
                sessionMapper,
                messageMapper,
                scoreRecordMapper);
        ReflectionTestUtils.setField(service, "pythonBaseUrl", "http://127.0.0.1:" + pythonStub.getAddress().getPort());

        ChatRequest request = new ChatRequest();
        request.setMessage("好的，请开始。");
        request.setResumeContent("熟悉 Java、Spring Boot、Redis。");
        request.setJobRequirements("Java 后端工程师");
        request.setCandidateName("测试候选人");

        List<ServerSentEvent<String>> events = service.proxyChat(request, 42L, "alice").collectList().block();

        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("status", "question", "chunk", "result", "done");

        ArgumentCaptor<InterviewSession> insertCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        ArgumentCaptor<InterviewSession> updateCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionMapper).insert(insertCaptor.capture());
        verify(sessionMapper, times(2)).updateById(updateCaptor.capture());

        InterviewSession insertedSession = insertCaptor.getValue();
        InterviewSession linkedSession = updateCaptor.getAllValues().get(0);
        InterviewSession completedSession = updateCaptor.getAllValues().get(1);
        assertThat(insertedSession.getUserId()).isEqualTo(42L);
        assertThat(linkedSession.getPythonSessionId()).isEqualTo("stub-java-session-001");
        assertThat(completedSession.getPythonSessionId()).isEqualTo("stub-java-session-001");
        assertThat(completedSession.getStage()).isEqualTo("self_introduction");
        assertThat(completedSession.getStatus()).isEqualTo(1);

        ArgumentCaptor<InterviewMessage> messageCaptor = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(messageMapper, times(2)).insert(messageCaptor.capture());

        assertThat(messageCaptor.getAllValues()).extracting(InterviewMessage::getRole)
                .containsExactly("human", "ai");
        assertThat(messageCaptor.getAllValues().get(1).getContent())
                .isEqualTo("请用 2 分钟介绍一下你的后端项目经验。");
        assertThat(messageCaptor.getAllValues().get(1).getStage())
                .isEqualTo("self_introduction");
        verify(scoreRecordMapper).getMaxQuestionIndex(eq(insertedSession.getId()));
    }

    private static WebClient webClient(String pythonBaseUrl) throws Exception {
        WebClientConfig config = new WebClientConfig();
        setField(config, "pythonBaseUrl", pythonBaseUrl);
        setField(config, "connectTimeout", 5000);
        setField(config, "readTimeout", 30000);
        setField(config, "writeTimeout", 30000);
        return config.webClient();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static HttpServer startPythonStub(String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/interview/chat", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream;charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        return server;
    }
}
