package com.aiinterviewer.interview.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.interview.dto.ChatRequest;
import com.aiinterviewer.interview.dto.PythonChatRequest;
import com.aiinterviewer.interview.entity.InterviewMessage;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.entity.ScoreRecord;
import com.aiinterviewer.interview.mapper.InterviewMessageMapper;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import com.aiinterviewer.interview.mapper.ScoreRecordMapper;
import com.aiinterviewer.interview.sse.SSEEventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSE代理服务
 *
 * 核心功能：
 * 1. 将请求转发给Python后端
 * 2. 透传Python后端的SSE响应
 * 3. 拦截特定事件进行持久化（评分、状态等）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SSEProxyService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final ScoreRecordMapper scoreRecordMapper;

    @Value("${python.ai.base-url:${python-ai.base-url:http://localhost:8000}}")
    private String pythonBaseUrl;

    @PostConstruct
    public void init() {
        log.info("SSEProxyService initialized with python.ai.base-url: {}", pythonBaseUrl);
    }

    /**
     * 代理统一对话接口
     *
     * @param request 聊天请求
     * @param userId  用户ID
     * @return SSE事件流
     */
    public Flux<ServerSentEvent<String>> proxyChat(ChatRequest request, Long userId) {
        // 1. 获取或创建会话
        InterviewSession session = getOrCreateSession(request, userId);

        // 2. 保存用户消息
        saveUserMessage(session.getId(), request.getMessage(), session.getStage());

        // 3. 构建Python请求
        PythonChatRequest pythonRequest = buildPythonRequest(request, session);

        // 用于收集AI响应
        AtomicReference<StringBuilder> aiResponseRef = new AtomicReference<>(new StringBuilder());
        AtomicReference<String> currentStageRef = new AtomicReference<>(session.getStage());
        AtomicReference<Integer> questionIndexRef = new AtomicReference<>(
                scoreRecordMapper.getMaxQuestionIndex(session.getId()) + 1
        );

        // 4. 转发请求到Python后端并透传SSE
        return webClient.post()
                .uri(pythonBaseUrl + "/interview/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(pythonRequest)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .timeout(Duration.ofMinutes(10))
                .doOnNext(event -> handleSSEEvent(event, session, aiResponseRef, currentStageRef, questionIndexRef, request.getMessage()))
                .doOnComplete(() -> {
                    // 保存AI响应消息
                    String aiResponse = aiResponseRef.get().toString();
                    if (StrUtil.isNotBlank(aiResponse)) {
                        saveAIMessage(session.getId(), aiResponse, currentStageRef.get());
                    }
                    // 更新会话
                    session.setStage(currentStageRef.get());
                    session.setUpdatedAt(LocalDateTime.now());
                    sessionMapper.updateById(session);
                })
                .doOnError(e -> {
                    log.error("SSE proxy error for session {}: {}", session.getId(), e.getMessage());
                })
                .onErrorResume(e -> {
                    log.error("SSE error, returning error event", e);
                    String errorJson = String.format(
                            "{\"code\":\"PROXY_ERROR\",\"message\":\"%s\"}",
                            e.getMessage().replace("\"", "\\\"")
                    );
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event(SSEEventType.ERROR.getValue())
                            .data(errorJson)
                            .build());
                });
    }

    /**
     * 代理恢复会话接口
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return SSE事件流
     */
    public Flux<ServerSentEvent<String>> proxyResume(String sessionId, Long userId) {
        // 1. 验证会话存在且属于该用户
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            session = sessionMapper.selectByPythonSessionId(sessionId);
        }
        if (session == null) {
            return Flux.error(new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        }
        if (!session.getUserId().equals(userId)) {
            return Flux.error(new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该会话"));
        }
        if (session.getStatus() != 1) {
            return Flux.error(new BusinessException(ErrorCode.SESSION_COMPLETED));
        }

        final InterviewSession activeSession = session;
        AtomicReference<String> currentStageRef = new AtomicReference<>(activeSession.getStage());

        // 2. 转发恢复请求到Python后端
        return webClient.post()
                .uri(pythonBaseUrl + "/interview/resume")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"session_id\":\"" + activeSession.getPythonSessionId() + "\"}")
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .timeout(Duration.ofMinutes(5))
                .doOnNext(event -> {
                    // 处理状态更新
                    if (SSEEventType.STATUS.getValue().equals(event.event())) {
                        try {
                            JsonNode data = objectMapper.readTree(event.data());
                            if (data.has("stage")) {
                                currentStageRef.set(data.get("stage").asText());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse status event", e);
                        }
                    }
                })
                .doOnComplete(() -> {
                    activeSession.setStage(currentStageRef.get());
                    activeSession.setUpdatedAt(LocalDateTime.now());
                    sessionMapper.updateById(activeSession);
                })
                .onErrorResume(e -> {
                    log.error("Resume proxy error", e);
                    String errorJson = String.format(
                            "{\"code\":\"RESUME_ERROR\",\"message\":\"%s\"}",
                            e.getMessage().replace("\"", "\\\"")
                    );
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event(SSEEventType.ERROR.getValue())
                            .data(errorJson)
                            .build());
                });
    }

    /**
     * 获取或创建会话
     */
    private InterviewSession getOrCreateSession(ChatRequest request, Long userId) {
        if (StrUtil.isNotBlank(request.getSessionId())) {
            // 使用已有会话
            InterviewSession session = sessionMapper.selectById(request.getSessionId());
            if (session == null) {
                session = sessionMapper.selectByPythonSessionId(request.getSessionId());
            }
            if (session == null) {
                throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
            }
            if (!session.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该会话");
            }
            if (session.getStatus() != 1) {
                throw new BusinessException(ErrorCode.SESSION_COMPLETED);
            }

            // 会话从DB读取时不包含lastQuestion（字段不落库），从最近一条AI消息回填。
            String latestAIMessage = messageMapper.selectLatestAIMessageContent(session.getId());
            if (StrUtil.isNotBlank(latestAIMessage)) {
                session.setLastQuestion(latestAIMessage);
            }
            return session;
        }

        // 创建新会话
        InterviewSession session = new InterviewSession();
        session.setId(IdUtil.fastSimpleUUID());
        session.setUserId(userId);
        session.setResumeId(request.getResumeId());
        session.setJobId(request.getJobId());
        session.setCandidateName(request.getCandidateName());
        session.setStage("opening");
        session.setStatus(1);
        session.setResumeContent(request.getResumeContent());
        session.setJobRequirements(request.getJobRequirements());
        session.setProjectQuestionsCount(0);
        session.setTargetProjectQuestions(5);
        session.setTechnicalQuestionsCount(0);
        session.setCurrentFollowupCount(0);
        session.setPythonSessionId(null); // 由Python后端创建
        session.setStartedAt(LocalDateTime.now());
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        sessionMapper.insert(session);
        log.info("Created new session: {}", session.getId());

        return session;
    }

    /**
     * 构建Python请求
     */
    private PythonChatRequest buildPythonRequest(ChatRequest request, InterviewSession session) {
        return PythonChatRequest.builder()
                .sessionId(session.getPythonSessionId())
                .message(request.getMessage())
                .resumeContent(session.getResumeContent())
                .jobRequirements(session.getJobRequirements())
                .candidateName(session.getCandidateName())
                .build();
    }

    /**
     * 处理SSE事件
     */
    private void handleSSEEvent(
            ServerSentEvent<String> event,
            InterviewSession session,
            AtomicReference<StringBuilder> aiResponseRef,
            AtomicReference<String> currentStageRef,
            AtomicReference<Integer> questionIndexRef,
            String userQuestion) {

        String eventType = event.event();
        String eventData = event.data();

        if (eventType == null || eventData == null) {
            return;
        }

        try {
            JsonNode data = objectMapper.readTree(eventData);

            switch (eventType) {
                case "status" -> handleStatusEvent(data, session, currentStageRef);
                case "chunk" -> handleChunkEvent(data, aiResponseRef);
                case "score" -> handleScoreEvent(data, session, questionIndexRef, userQuestion);
                case "result" -> handleResultEvent(data, session, currentStageRef);
                case "done" -> handleDoneEvent(data, session, currentStageRef);
                default -> log.debug("Unknown event type: {}", eventType);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse SSE event data: {}", eventData, e);
        }
    }

    /**
     * 处理状态事件
     */
    private void handleStatusEvent(JsonNode data, InterviewSession session, AtomicReference<String> currentStageRef) {
        if (data.has("stage")) {
            currentStageRef.set(data.get("stage").asText());
        }
        if (data.has("session_id") && session.getPythonSessionId() == null) {
            // 首次获取Python会话ID
            session.setPythonSessionId(data.get("session_id").asText());
            sessionMapper.updateById(session);
            log.info("Linked Python session {} to local session {}", session.getPythonSessionId(), session.getId());
        }
    }

    /**
     * 处理文本块事件
     */
    private void handleChunkEvent(JsonNode data, AtomicReference<StringBuilder> aiResponseRef) {
        if (data.has("content")) {
            aiResponseRef.get().append(data.get("content").asText());
        }
    }

    /**
     * 处理评分事件 - 持久化到数据库
     */
    private void handleScoreEvent(
            JsonNode data,
            InterviewSession session,
            AtomicReference<Integer> questionIndexRef,
            String userQuestion) {

        int score = data.has("score") ? data.get("score").asInt() : 0;
        String feedback = data.has("feedback") ? data.get("feedback").asText() : "";

        String scoredQuestion = null;
        if (data.has("question") && !data.get("question").isNull()) {
            scoredQuestion = data.get("question").asText();
        }
        if (StrUtil.isBlank(scoredQuestion)) {
            scoredQuestion = session.getLastQuestion();
        }
        if (StrUtil.isBlank(scoredQuestion)) {
            scoredQuestion = messageMapper.selectLatestAIMessageContent(session.getId());
        }
        if (StrUtil.isBlank(scoredQuestion)) {
            scoredQuestion = "未知问题";
            log.warn("No question found when saving score, fallback to placeholder. sessionId={}", session.getId());
        }

        ScoreRecord record = new ScoreRecord();
        record.setSessionId(session.getId());
        record.setQuestionIndex(questionIndexRef.getAndUpdate(i -> i + 1));
        record.setQuestionType(session.getStage());
        record.setQuestion(scoredQuestion);
        record.setAnswer(userQuestion);
        record.setScore(score);
        record.setFeedback(feedback);
        record.setIsFollowup(false);
        record.setCreatedAt(LocalDateTime.now());

        scoreRecordMapper.insert(record);
        log.debug("Saved score record: sessionId={}, score={}", session.getId(), score);
    }

    /**
     * 处理结果事件
     */
    private void handleResultEvent(JsonNode data, InterviewSession session, AtomicReference<String> currentStageRef) {
        if (data.has("next_stage")) {
            currentStageRef.set(data.get("next_stage").asText());
        }
        if (data.has("next_question")) {
            session.setLastQuestion(data.get("next_question").asText());
        }
    }

    /**
     * 处理完成事件
     */
    private void handleDoneEvent(JsonNode data, InterviewSession session, AtomicReference<String> currentStageRef) {
        if (data.has("stage")) {
            currentStageRef.set(data.get("stage").asText());
        }
        if (data.has("is_interview_complete") && data.get("is_interview_complete").asBoolean()) {
            session.setStatus(2); // 已完成
            session.setFinishedAt(LocalDateTime.now());
        }
    }

    /**
     * 保存用户消息
     */
    private void saveUserMessage(String sessionId, String content, String stage) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole("human");
        message.setContent(content);
        message.setStage(stage);
        message.setSequence(messageMapper.getMaxSequence(sessionId) + 1);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    /**
     * 保存AI消息
     */
    private void saveAIMessage(String sessionId, String content, String stage) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole("ai");
        message.setContent(content);
        message.setStage(stage);
        message.setSequence(messageMapper.getMaxSequence(sessionId) + 1);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }
}
