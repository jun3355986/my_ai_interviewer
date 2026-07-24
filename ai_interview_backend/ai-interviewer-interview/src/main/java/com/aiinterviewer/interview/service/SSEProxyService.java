package com.aiinterviewer.interview.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.interview.dto.ChatRequest;
import com.aiinterviewer.interview.dto.PythonChatRequest;
import com.aiinterviewer.interview.entity.InterviewLineage;
import com.aiinterviewer.interview.entity.InterviewMessage;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.entity.ScoreRecord;
import com.aiinterviewer.interview.mapper.InterviewLineageMapper;
import com.aiinterviewer.interview.mapper.InterviewMessageMapper;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import com.aiinterviewer.interview.mapper.ScoreRecordMapper;
import com.aiinterviewer.interview.sse.SSEEventType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final InterviewLineageMapper lineageMapper;
    private final InterviewMessageMapper messageMapper;
    private final ScoreRecordMapper scoreRecordMapper;
    private final CompatibilitySessionWriteGuard writeGuard;

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
        return proxyChat(request, userId, null);
    }

    /**
     * 代理统一对话接口
     *
     * @param request  聊天请求
     * @param userId   用户ID
     * @param username 网关透传用户名
     * @return SSE事件流
     */
    public Flux<ServerSentEvent<String>> proxyChat(ChatRequest request, Long userId, String username) {
        // 1. 获取或创建会话
        InterviewSession session = getOrCreateSession(request, userId);

        // 2. 在当前 lineage + branch 所有权锁内保存用户消息
        CompatibilityAnswerContext answerContext = writeGuard.executeOwnedActive(
                session.getId(),
                session.getLineageId(),
                userId,
                locked -> new CompatibilityAnswerContext(
                        findLatestCompletedQuestionMessageId(locked.session().getId()),
                        saveUserMessage(
                                locked.session().getId(),
                                request.getMessage(),
                                locked.session().getStage())));

        // 3. 构建Python请求
        PythonChatRequest pythonRequest = buildPythonRequest(request, session, userId, username);

        // 用于收集AI响应
        AtomicReference<StringBuilder> aiResponseRef = new AtomicReference<>(new StringBuilder());
        AtomicReference<Map<String, Object>> aiMetadataRef = new AtomicReference<>();
        AtomicReference<String> currentStageRef = new AtomicReference<>(session.getStage());
        AtomicReference<String> lastQuestionRef = new AtomicReference<>(session.getLastQuestion());
        AtomicBoolean completedRef = new AtomicBoolean(false);

        // 4. 转发请求到Python后端并透传SSE
        return webClient.post()
                .uri(pythonBaseUrl + "/interview/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(pythonRequest)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .timeout(Duration.ofMinutes(10))
                .doOnNext(event -> handleSSEEvent(
                        event,
                        session,
                        aiResponseRef,
                        aiMetadataRef,
                        currentStageRef,
                        lastQuestionRef,
                        completedRef,
                        userId,
                        request.getMessage(),
                        answerContext))
                .doOnComplete(() -> writeGuard.executeOwnedActive(
                        session.getId(),
                        session.getLineageId(),
                        userId,
                        locked -> {
                            completeCompatibilityChat(
                                    locked,
                                    aiResponseRef.get().toString(),
                                    aiMetadataRef.get(),
                                    currentStageRef.get(),
                                    lastQuestionRef.get(),
                                    completedRef.get());
                            return null;
                        }))
                .doOnError(e -> {
                    log.error("SSE proxy error for session {}: {}", session.getId(), e.getMessage());
                })
                .onErrorResume(e -> {
                    log.error("SSE error, returning error event", e);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event(SSEEventType.ERROR.getValue())
                            .data(sanitizedProxyError(
                                    "PROXY_ERROR",
                                    "面试服务暂时不可用，请稍后重试"))
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
        return proxyResume(sessionId, userId, null);
    }

    /**
     * 代理恢复会话接口
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param username  网关透传用户名
     * @return SSE事件流
     */
    public Flux<ServerSentEvent<String>> proxyResume(String sessionId, Long userId, String username) {
        // 1. 验证会话存在且属于该用户
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            session = sessionMapper.selectByPythonSessionId(sessionId);
        }
        if (session == null) {
            return Flux.error(new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        }
        try {
            requireCurrentOwnership(session, userId);
        } catch (BusinessException exception) {
            return Flux.error(exception);
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
                .bodyValue(buildPythonResumeRequest(activeSession, userId, username))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .timeout(Duration.ofMinutes(5))
                .doOnNext(event -> {
                    assertCompatibilityStreamOwnership(activeSession, userId);
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
                .doOnComplete(() -> writeGuard.executeOwnedActive(
                        activeSession.getId(),
                        activeSession.getLineageId(),
                        userId,
                        locked -> {
                            InterviewSession current = locked.session();
                            current.setStage(currentStageRef.get());
                            current.setUpdatedAt(LocalDateTime.now());
                            sessionMapper.updateById(current);
                            return null;
                        }))
                .onErrorResume(e -> {
                    log.error("Resume proxy error", e);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event(SSEEventType.ERROR.getValue())
                            .data(sanitizedProxyError(
                                    "RESUME_ERROR",
                                    "面试恢复服务暂时不可用，请稍后重试"))
                            .build());
                });
    }

    private String sanitizedProxyError(String code, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("code", code, "message", message));
        } catch (Exception serializationFailure) {
            log.error("Failed to serialize sanitized compatibility proxy error", serializationFailure);
            return "{\"code\":\"PROXY_ERROR\",\"message\":\"服务暂时不可用\"}";
        }
    }

    private Map<String, Object> buildPythonResumeRequest(
            InterviewSession session,
            Long userId,
            String username) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("session_id", session.getPythonSessionId());
        request.put("request_id", IdUtil.fastSimpleUUID());
        request.put("java_session_id", session.getId());
        request.put("user_id", userId);
        request.put("username", username);
        request.put("business_type", "interview");
        request.put("entrypoint", "interview_resume");
        return request;
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
            requireCurrentOwnership(session, userId);
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
        String sessionId = IdUtil.fastSimpleUUID();
        LocalDateTime now = LocalDateTime.now();

        InterviewLineage lineage = new InterviewLineage();
        lineage.setId(sessionId);
        lineage.setUserId(userId);
        lineage.setRootSessionId(sessionId);
        lineage.setLastBusinessActivityAt(now);
        lineage.setArchived(false);
        lineage.setCreatedAt(now);
        lineage.setUpdatedAt(now);
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
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
        session.setLineageId(sessionId);
        session.setParentSessionId(null);
        session.setForkPointMessageId(null);
        session.setForkTriggerMessageId(null);
        session.setBranchLabel("原始分支");
        session.setBranchVersion(1L);
        session.setLastBusinessActivityAt(now);
        session.setLegacyMigrated(false);
        session.setStartedAt(now);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);

        writeGuard.createSession(lineage, session);
        log.info("Created new session: {}", session.getId());

        return session;
    }

    /**
     * 构建Python请求
     */
    private PythonChatRequest buildPythonRequest(
            ChatRequest request,
            InterviewSession session,
            Long userId,
            String username) {
        return PythonChatRequest.builder()
                .sessionId(session.getPythonSessionId())
                .requestId(IdUtil.fastSimpleUUID())
                .agentRunId(IdUtil.fastSimpleUUID())
                .javaSessionId(session.getId())
                .userId(userId)
                .username(username)
                .businessType("interview")
                .entrypoint("interview_chat")
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
            AtomicReference<Map<String, Object>> aiMetadataRef,
            AtomicReference<String> currentStageRef,
            AtomicReference<String> lastQuestionRef,
            AtomicBoolean completedRef,
            Long userId,
            String userQuestion,
            CompatibilityAnswerContext answerContext) {

        String eventType = event.event();
        String eventData = event.data();

        if (eventType == null || eventData == null) {
            return;
        }

        try {
            JsonNode data = objectMapper.readTree(eventData);

            switch (eventType) {
                case "status" -> handleStatusEvent(
                        data, session, currentStageRef, userId);
                case "question" -> handleQuestionEvent(
                        data,
                        aiResponseRef,
                        aiMetadataRef,
                        lastQuestionRef,
                        session,
                        userId);
                case "chunk" -> handleChunkEvent(
                        data, session, aiResponseRef, lastQuestionRef, userId);
                case "score" -> handleScoreEvent(
                        data,
                        session,
                        lastQuestionRef,
                        userId,
                        userQuestion,
                        answerContext);
                case "result" -> handleResultEvent(
                        data, session, currentStageRef, lastQuestionRef, userId);
                case "done" -> handleDoneEvent(
                        data, session, currentStageRef, completedRef, userId);
                default -> log.debug("Unknown event type: {}", eventType);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse SSE event data: {}", eventData, e);
        }
    }

    /**
     * 处理状态事件
     */
    private void handleStatusEvent(
            JsonNode data,
            InterviewSession session,
            AtomicReference<String> currentStageRef,
            Long userId) {
        if (data.has("stage")) {
            currentStageRef.set(data.get("stage").asText());
        }
        if (data.has("session_id") && session.getPythonSessionId() == null) {
            String pythonSessionId = data.get("session_id").asText();
            writeGuard.executeOwnedActive(
                    session.getId(),
                    session.getLineageId(),
                    userId,
                    locked -> {
                        InterviewSession current = locked.session();
                        if (current.getPythonSessionId() == null) {
                            current.setPythonSessionId(pythonSessionId);
                            sessionMapper.updateById(current);
                        }
                        return null;
                    });
            session.setPythonSessionId(pythonSessionId);
            log.info("Linked Python session {} to local session {}", pythonSessionId, session.getId());
        } else {
            assertCompatibilityStreamOwnership(session, userId);
        }
    }

    /**
     * 处理文本块事件
     */
    private void handleChunkEvent(
            JsonNode data,
            InterviewSession session,
            AtomicReference<StringBuilder> aiResponseRef,
            AtomicReference<String> lastQuestionRef,
            Long userId) {
        assertCompatibilityStreamOwnership(session, userId);
        if (data.has("content")) {
            String content = data.get("content").asText();
            StringBuilder response = aiResponseRef.get();
            String lastQuestion = lastQuestionRef.get();
            if (StrUtil.isNotBlank(lastQuestion)
                    && response.toString().equals(lastQuestion)
                    && lastQuestion.startsWith(content)) {
                response.setLength(0);
            }
            response.append(content);
        }
    }

    /**
     * 处理结构化题目事件
     */
    private void handleQuestionEvent(
            JsonNode data,
            AtomicReference<StringBuilder> aiResponseRef,
            AtomicReference<Map<String, Object>> aiMetadataRef,
            AtomicReference<String> lastQuestionRef,
            InterviewSession session,
            Long userId) {
        assertCompatibilityStreamOwnership(session, userId);
        if (!data.has("question") || data.get("question").isNull()) {
            return;
        }
        JsonNode question = data.get("question");
        aiMetadataRef.set(objectMapper.convertValue(
                question,
                new TypeReference<Map<String, Object>>() {}));
        if (question.has("text") && !question.get("text").isNull()) {
            String text = question.get("text").asText();
            lastQuestionRef.set(text);
            if (aiResponseRef.get().isEmpty()) {
                aiResponseRef.get().append(text);
            }
        }
    }

    /**
     * 处理评分事件 - 持久化到数据库
     */
    private void handleScoreEvent(
            JsonNode data,
            InterviewSession session,
            AtomicReference<String> lastQuestionRef,
            Long userId,
            String userQuestion,
            CompatibilityAnswerContext answerContext) {

        int score = data.has("score") ? data.get("score").asInt() : 0;
        String feedback = data.has("feedback") ? data.get("feedback").asText() : "";

        String scoredQuestion = null;
        if (data.has("question") && !data.get("question").isNull()) {
            scoredQuestion = data.get("question").asText();
        }
        if (StrUtil.isBlank(scoredQuestion)) {
            scoredQuestion = lastQuestionRef.get();
        }
        String eventQuestion = scoredQuestion;

        writeGuard.executeOwnedActive(
                session.getId(),
                session.getLineageId(),
                userId,
                locked -> {
                    String persistedQuestion = eventQuestion;
                    if (StrUtil.isBlank(persistedQuestion)) {
                        persistedQuestion = messageMapper.selectLatestAIMessageContent(
                                locked.session().getId());
                    }
                    if (StrUtil.isBlank(persistedQuestion)) {
                        persistedQuestion = "未知问题";
                        log.warn(
                                "No question found when saving score, fallback to placeholder. sessionId={}",
                                locked.session().getId());
                    }

                    ScoreRecord record = new ScoreRecord();
                    record.setSessionId(locked.session().getId());
                    record.setQuestionIndex(
                            scoreRecordMapper.getMaxQuestionIndex(locked.session().getId()) + 1);
                    record.setQuestionType(locked.session().getStage());
                    record.setQuestion(persistedQuestion);
                    record.setAnswer(userQuestion);
                    record.setScore(score);
                    record.setFeedback(feedback);
                    record.setIsFollowup(false);
                    record.setQuestionMessageId(answerContext.questionMessageId());
                    record.setAnswerMessageId(answerContext.answerMessageId());
                    record.setCreatedAt(LocalDateTime.now());
                    scoreRecordMapper.insert(record);
                    return null;
                });
        log.debug("Saved score record: sessionId={}, score={}", session.getId(), score);
    }

    /**
     * 处理结果事件
     */
    private void handleResultEvent(
            JsonNode data,
            InterviewSession session,
            AtomicReference<String> currentStageRef,
            AtomicReference<String> lastQuestionRef,
            Long userId) {
        assertCompatibilityStreamOwnership(session, userId);
        if (data.has("next_stage")) {
            currentStageRef.set(data.get("next_stage").asText());
        }
        if (data.has("next_question")) {
            lastQuestionRef.set(data.get("next_question").asText());
        }
    }

    /**
     * 处理完成事件
     */
    private void handleDoneEvent(
            JsonNode data,
            InterviewSession session,
            AtomicReference<String> currentStageRef,
            AtomicBoolean completedRef,
            Long userId) {
        assertCompatibilityStreamOwnership(session, userId);
        if (data.has("stage")) {
            currentStageRef.set(data.get("stage").asText());
        }
        if (data.has("is_interview_complete") && data.get("is_interview_complete").asBoolean()) {
            completedRef.set(true);
        }
    }

    /**
     * 保存用户消息
     */
    private Long saveUserMessage(String sessionId, String content, String stage) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole("human");
        message.setContent(content);
        message.setStage(stage);
        int sequence = messageMapper.getMaxSequence(sessionId) + 1;
        message.setSequence(sequence);
        message.setMessageType(isInitialSystemTrigger(sequence, content)
                ? "system_trigger"
                : "candidate_answer");
        message.setExpectsResponse(false);
        message.setDeliveryStatus("completed");
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message.getId();
    }

    private Long findLatestCompletedQuestionMessageId(String sessionId) {
        LambdaQueryWrapper<InterviewMessage> query = new LambdaQueryWrapper<>();
        query.eq(InterviewMessage::getSessionId, sessionId)
                .eq(InterviewMessage::getMessageType, "ai_question")
                .eq(InterviewMessage::getDeliveryStatus, "completed")
                .orderByDesc(InterviewMessage::getSequence)
                .last("LIMIT 1");
        return messageMapper.selectList(query).stream()
                .findFirst()
                .map(InterviewMessage::getId)
                .orElse(null);
    }

    /**
     * 保存AI消息
     */
    private void saveAIMessage(
            InterviewSession session,
            String content,
            String stage,
            Map<String, Object> metadata) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(session.getId());
        message.setRole("ai");
        message.setContent(content);
        message.setStage(stage);
        message.setSequence(messageMapper.getMaxSequence(session.getId()) + 1);
        boolean completed = Integer.valueOf(2).equals(session.getStatus());
        message.setMessageType(completed ? "final_summary" : "ai_question");
        message.setExpectsResponse(!completed);
        message.setDeliveryStatus("completed");
        message.setMetadata(metadata);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    private void completeCompatibilityChat(
            CompatibilitySessionWriteGuard.LockedSession locked,
            String aiResponse,
            Map<String, Object> aiMetadata,
            String stage,
            String lastQuestion,
            boolean completed) {
        InterviewSession session = locked.session();
        if (completed) {
            session.setStatus(2);
            session.setFinishedAt(LocalDateTime.now());
        }
        if (StrUtil.isNotBlank(lastQuestion)) {
            session.setLastQuestion(lastQuestion);
        }
        if (StrUtil.isNotBlank(aiResponse)) {
            saveAIMessage(session, aiResponse, stage, aiMetadata);
        }

        LocalDateTime completedAt = LocalDateTime.now();
        session.setStage(stage);
        session.setBranchVersion(
                session.getBranchVersion() == null
                        ? 1L
                        : session.getBranchVersion() + 1);
        session.setLastBusinessActivityAt(completedAt);
        session.setUpdatedAt(completedAt);
        sessionMapper.updateById(session);

        InterviewLineage lineage = locked.lineage();
        lineage.setLastBusinessActivityAt(completedAt);
        lineage.setUpdatedAt(completedAt);
        lineageMapper.updateById(lineage);
    }

    private void assertCompatibilityStreamOwnership(InterviewSession session, Long userId) {
        writeGuard.executeOwnedActive(
                session.getId(),
                session.getLineageId(),
                userId,
                ignored -> null);
    }

    private void requireCurrentOwnership(InterviewSession session, Long userId) {
        InterviewLineage lineage = lineageMapper.selectById(session.getLineageId());
        if (!Objects.equals(session.getUserId(), userId)
                || lineage == null
                || !Objects.equals(lineage.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该会话");
        }
    }

    private boolean isInitialSystemTrigger(int sequence, String content) {
        if (sequence != 1 || content == null) {
            return false;
        }
        String normalized = content.trim();
        return "我准备好了".equals(normalized)
                || "好的，请开始。".equals(normalized)
                || "开始面试".equals(normalized);
    }

    private record CompatibilityAnswerContext(
            Long questionMessageId,
            Long answerMessageId) {
    }
}
