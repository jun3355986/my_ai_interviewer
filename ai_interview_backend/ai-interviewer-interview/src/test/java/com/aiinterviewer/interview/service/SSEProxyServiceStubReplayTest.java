package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.interview.config.WebClientConfig;
import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.interview.dto.ChatRequest;
import com.aiinterviewer.interview.entity.InterviewMessage;
import com.aiinterviewer.interview.entity.InterviewLineage;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.entity.ScoreRecord;
import com.aiinterviewer.interview.mapper.InterviewLineageMapper;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
                data: {"question":{"id":"stub-self-intro-001","type":"self_introduction","text":"请用 2 分钟介绍一下你的后端项目经验。","media":[{"type":"image","url":"https://example.com/project.png","caption":"项目架构图"}]},"next_stage":"self_introduction"}

                event: chunk
                data: {"content":"请用 2 分钟介绍一下你的后端项目经验。"}

                event: result
                data: {"next_stage":"self_introduction","next_question":"请用 2 分钟介绍一下你的后端项目经验。"}

                event: done
                data: {"stage":"self_introduction","is_interview_complete":false}

                """;
        pythonStub = startPythonStub(responseBody);

        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewLineageMapper lineageMapper = mock(InterviewLineageMapper.class);
        InterviewMessageMapper messageMapper = mock(InterviewMessageMapper.class);
        ScoreRecordMapper scoreRecordMapper = mock(ScoreRecordMapper.class);
        stubNewSessionLocks(sessionMapper, lineageMapper, 42L);
        when(messageMapper.getMaxSequence(any())).thenReturn(0, 1);
        when(scoreRecordMapper.getMaxQuestionIndex(any())).thenReturn(0);

        SSEProxyService service = new SSEProxyService(
                webClient("http://127.0.0.1:" + pythonStub.getAddress().getPort()),
                new ObjectMapper(),
                sessionMapper,
                lineageMapper,
                messageMapper,
                scoreRecordMapper,
                new CompatibilitySessionWriteGuard(sessionMapper, lineageMapper));
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
        assertThat(insertedSession.getLineageId()).isEqualTo(insertedSession.getId());
        assertThat(insertedSession.getBranchLabel()).isEqualTo("原始分支");
        assertThat(insertedSession.getBranchVersion()).isEqualTo(2L);
        assertThat(insertedSession.getLegacyMigrated()).isFalse();
        ArgumentCaptor<InterviewLineage> lineageCaptor =
                ArgumentCaptor.forClass(InterviewLineage.class);
        verify(lineageMapper).insert(lineageCaptor.capture());
        assertThat(lineageCaptor.getValue().getId()).isEqualTo(insertedSession.getId());
        assertThat(lineageCaptor.getValue().getRootSessionId()).isEqualTo(insertedSession.getId());
        assertThat(linkedSession.getPythonSessionId()).isEqualTo("stub-java-session-001");
        assertThat(completedSession.getPythonSessionId()).isEqualTo("stub-java-session-001");
        assertThat(completedSession.getStage()).isEqualTo("self_introduction");
        assertThat(completedSession.getStatus()).isEqualTo(1);

        ArgumentCaptor<InterviewMessage> messageCaptor = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(messageMapper, times(2)).insert(messageCaptor.capture());

        assertThat(messageCaptor.getAllValues()).extracting(InterviewMessage::getRole)
                .containsExactly("human", "ai");
        assertThat(messageCaptor.getAllValues()).extracting(InterviewMessage::getMessageType)
                .containsExactly("system_trigger", "ai_question");
        assertThat(messageCaptor.getAllValues()).extracting(InterviewMessage::getDeliveryStatus)
                .containsExactly("completed", "completed");
        assertThat(messageCaptor.getAllValues().get(1).getExpectsResponse()).isTrue();
        assertThat(messageCaptor.getAllValues().get(1).getContent())
                .isEqualTo("请用 2 分钟介绍一下你的后端项目经验。");
        assertThat(messageCaptor.getAllValues().get(1).getStage())
                .isEqualTo("self_introduction");
        assertThat(messageCaptor.getAllValues().get(1).getMetadata())
                .containsKey("media");
        assertThat(messageCaptor.getAllValues().get(1).getMetadata().get("media").toString())
                .contains("https://example.com/project.png");
        verify(scoreRecordMapper, never()).getMaxQuestionIndex(eq(insertedSession.getId()));
    }

    @Test
    void compatibilityChatLinksScoreToPersistedQuestionAndAnswerMessages() throws Exception {
        String responseBody = """
                event: score
                data: {"question":"legacy question","score":86,"feedback":"good"}

                event: done
                data: {"stage":"project_qna","is_interview_complete":false}

                """;
        pythonStub = startPythonStub(responseBody);

        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewLineageMapper lineageMapper = mock(InterviewLineageMapper.class);
        InterviewMessageMapper messageMapper = mock(InterviewMessageMapper.class);
        ScoreRecordMapper scoreRecordMapper = mock(ScoreRecordMapper.class);
        InterviewSession session = new InterviewSession();
        session.setId("existing-session");
        session.setUserId(42L);
        session.setStage("project_qna");
        session.setStatus(1);
        session.setLineageId("lineage-1");
        session.setBranchVersion(1L);
        session.setLastQuestion("legacy question");
        when(sessionMapper.selectById("existing-session")).thenReturn(session);
        when(lineageMapper.selectById("lineage-1")).thenReturn(lineage("lineage-1", 42L));
        stubLockedOwnership(sessionMapper, lineageMapper, session, 42L);
        when(messageMapper.getMaxSequence("existing-session")).thenReturn(2);
        when(scoreRecordMapper.getMaxQuestionIndex("existing-session")).thenReturn(0);
        InterviewMessage question = new InterviewMessage();
        question.setId(101L);
        question.setSessionId("existing-session");
        question.setRole("ai");
        question.setMessageType("ai_question");
        question.setDeliveryStatus("completed");
        when(messageMapper.selectList(any())).thenReturn(List.of(question));
        doAnswer(invocation -> {
            InterviewMessage inserted = invocation.getArgument(0);
            inserted.setId(202L);
            return 1;
        }).when(messageMapper).insert(any(InterviewMessage.class));

        SSEProxyService service = new SSEProxyService(
                webClient("http://127.0.0.1:" + pythonStub.getAddress().getPort()),
                new ObjectMapper(),
                sessionMapper,
                lineageMapper,
                messageMapper,
                scoreRecordMapper,
                new CompatibilitySessionWriteGuard(sessionMapper, lineageMapper));
        ReflectionTestUtils.setField(
                service,
                "pythonBaseUrl",
                "http://127.0.0.1:" + pythonStub.getAddress().getPort());
        ChatRequest request = new ChatRequest();
        request.setSessionId("existing-session");
        request.setMessage("legacy answer");

        service.proxyChat(request, 42L, "alice").collectList().block();

        ArgumentCaptor<ScoreRecord> scoreCaptor = ArgumentCaptor.forClass(ScoreRecord.class);
        verify(scoreRecordMapper).insert(scoreCaptor.capture());
        assertThat(scoreCaptor.getValue().getQuestionMessageId()).isEqualTo(101L);
        assertThat(scoreCaptor.getValue().getAnswerMessageId()).isEqualTo(202L);
    }

    @Test
    void compatibilityProxyErrorsDoNotExposeProviderOrStorageDetails() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewLineageMapper lineageMapper = mock(InterviewLineageMapper.class);
        InterviewMessageMapper messageMapper = mock(InterviewMessageMapper.class);
        ScoreRecordMapper scoreRecordMapper = mock(ScoreRecordMapper.class);
        AtomicReference<InterviewSession> newSession =
                stubNewSessionLocks(sessionMapper, lineageMapper, 42L);
        when(messageMapper.getMaxSequence(any())).thenReturn(0);
        when(scoreRecordMapper.getMaxQuestionIndex(any())).thenReturn(0);
        InterviewSession resumable = new InterviewSession();
        resumable.setId("resume-session");
        resumable.setPythonSessionId("python-resume-session");
        resumable.setUserId(42L);
        resumable.setLineageId("resume-lineage");
        resumable.setStatus(1);
        resumable.setStage("project_qna");
        when(sessionMapper.selectById("resume-session")).thenReturn(resumable);
        when(lineageMapper.selectById("resume-lineage"))
                .thenReturn(lineage("resume-lineage", 42L));
        when(sessionMapper.selectByIdForUpdate("resume-session")).thenReturn(resumable);
        when(lineageMapper.selectOwnedForUpdate("resume-lineage", 42L))
                .thenReturn(lineage("resume-lineage", 42L));
        when(sessionMapper.selectByIdForUpdate(any())).thenAnswer(invocation -> {
            if ("resume-session".equals(invocation.getArgument(0))) {
                return resumable;
            }
            return newSession.get();
        });

        WebClient failingClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new RuntimeException(
                        "sqlite:////secret/cache.db UNIQUE constraint failed")))
                .build();
        SSEProxyService service = new SSEProxyService(
                failingClient,
                new ObjectMapper(),
                sessionMapper,
                lineageMapper,
                messageMapper,
                scoreRecordMapper,
                new CompatibilitySessionWriteGuard(sessionMapper, lineageMapper));

        ChatRequest request = new ChatRequest();
        request.setMessage("compatibility answer");
        List<ServerSentEvent<String>> chatEvents = service.proxyChat(request, 42L, "alice")
                .collectList()
                .block();
        List<ServerSentEvent<String>> resumeEvents = service.proxyResume(
                        "resume-session",
                        42L,
                        "alice")
                .collectList()
                .block();

        assertThat(chatEvents).singleElement().satisfies(event -> {
            assertThat(event.event()).isEqualTo("error");
            assertThat(event.data()).contains("PROXY_ERROR", "面试服务暂时不可用");
            assertThat(event.data()).doesNotContain("secret", "sqlite", "constraint");
        });
        assertThat(resumeEvents).singleElement().satisfies(event -> {
            assertThat(event.event()).isEqualTo("error");
            assertThat(event.data()).contains("RESUME_ERROR", "面试恢复服务暂时不可用");
            assertThat(event.data()).doesNotContain("secret", "sqlite", "constraint");
        });
    }

    @Test
    void compatibilityExistingSessionRejectsLineageOnlyOwnershipDriftBeforePersistence() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewLineageMapper lineageMapper = mock(InterviewLineageMapper.class);
        InterviewMessageMapper messageMapper = mock(InterviewMessageMapper.class);
        ScoreRecordMapper scoreRecordMapper = mock(ScoreRecordMapper.class);
        InterviewSession session = existingSession("drift-session", "drift-lineage", 42L);
        when(sessionMapper.selectById("drift-session")).thenReturn(session);
        when(lineageMapper.selectById("drift-lineage"))
                .thenReturn(lineage("drift-lineage", 99L));
        SSEProxyService service = new SSEProxyService(
                WebClient.builder().exchangeFunction(request -> {
                    throw new AssertionError("Python must not be called after ownership drift");
                }).build(),
                new ObjectMapper(),
                sessionMapper,
                lineageMapper,
                messageMapper,
                scoreRecordMapper,
                new CompatibilitySessionWriteGuard(sessionMapper, lineageMapper));
        ChatRequest request = new ChatRequest();
        request.setSessionId("drift-session");
        request.setMessage("must not persist");

        assertDenied(() -> service.proxyChat(request, 42L, "alice"));
        assertDenied(() -> service.proxyResume("drift-session", 42L, "alice").blockLast());

        verify(messageMapper, never()).insert(any(InterviewMessage.class));
        verify(sessionMapper, never()).updateById(any(InterviewSession.class));
    }

    @Test
    void compatibilityChatStopsAsyncPersistenceWhenLineageOwnershipDriftsMidStream()
            throws Exception {
        pythonStub = startPythonStub("""
                event: score
                data: {"question":"legacy question","score":86,"feedback":"good"}

                event: done
                data: {"stage":"project_qna","is_interview_complete":false}

                """);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewLineageMapper lineageMapper = mock(InterviewLineageMapper.class);
        InterviewMessageMapper messageMapper = mock(InterviewMessageMapper.class);
        ScoreRecordMapper scoreRecordMapper = mock(ScoreRecordMapper.class);
        InterviewSession session = existingSession("live-drift", "lineage-live", 42L);
        session.setLastQuestion("legacy question");
        when(sessionMapper.selectById("live-drift")).thenReturn(session);
        when(lineageMapper.selectById("lineage-live"))
                .thenReturn(lineage("lineage-live", 42L));
        when(lineageMapper.selectOwnedForUpdate("lineage-live", 42L))
                .thenReturn(lineage("lineage-live", 42L), null);
        when(sessionMapper.selectByIdForUpdate("live-drift")).thenReturn(session);
        when(messageMapper.getMaxSequence("live-drift")).thenReturn(2);
        when(scoreRecordMapper.getMaxQuestionIndex("live-drift")).thenReturn(0);
        SSEProxyService service = new SSEProxyService(
                webClient("http://127.0.0.1:" + pythonStub.getAddress().getPort()),
                new ObjectMapper(),
                sessionMapper,
                lineageMapper,
                messageMapper,
                scoreRecordMapper,
                new CompatibilitySessionWriteGuard(sessionMapper, lineageMapper));
        ReflectionTestUtils.setField(
                service,
                "pythonBaseUrl",
                "http://127.0.0.1:" + pythonStub.getAddress().getPort());
        ChatRequest request = new ChatRequest();
        request.setSessionId("live-drift");
        request.setMessage("authorized before drift");

        List<ServerSentEvent<String>> events = service.proxyChat(request, 42L, "alice")
                .collectList()
                .block();

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.event()).isEqualTo("error"));
        verify(messageMapper, times(1)).insert(any(InterviewMessage.class));
        verify(scoreRecordMapper, never()).insert(any(ScoreRecord.class));
        verify(sessionMapper, never()).updateById(any(InterviewSession.class));
    }

    private static InterviewSession existingSession(
            String id,
            String lineageId,
            Long userId) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setLineageId(lineageId);
        session.setUserId(userId);
        session.setStatus(1);
        session.setStage("project_qna");
        session.setBranchVersion(1L);
        return session;
    }

    private static InterviewLineage lineage(String id, Long userId) {
        InterviewLineage lineage = new InterviewLineage();
        lineage.setId(id);
        lineage.setUserId(userId);
        return lineage;
    }

    private static AtomicReference<InterviewSession> stubNewSessionLocks(
            InterviewSessionMapper sessionMapper,
            InterviewLineageMapper lineageMapper,
            Long userId) {
        AtomicReference<InterviewSession> insertedSession = new AtomicReference<>();
        doAnswer(invocation -> {
            insertedSession.set(invocation.getArgument(0));
            return 1;
        }).when(sessionMapper).insert(any(InterviewSession.class));
        when(sessionMapper.selectByIdForUpdate(any())).thenAnswer(
                invocation -> insertedSession.get());
        when(lineageMapper.selectOwnedForUpdate(any(), eq(userId))).thenAnswer(
                invocation -> lineage(invocation.getArgument(0), userId));
        return insertedSession;
    }

    private static void stubLockedOwnership(
            InterviewSessionMapper sessionMapper,
            InterviewLineageMapper lineageMapper,
            InterviewSession session,
            Long userId) {
        when(sessionMapper.selectByIdForUpdate(session.getId())).thenReturn(session);
        when(lineageMapper.selectOwnedForUpdate(session.getLineageId(), userId))
                .thenReturn(lineage(session.getLineageId(), userId));
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(2003);
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
