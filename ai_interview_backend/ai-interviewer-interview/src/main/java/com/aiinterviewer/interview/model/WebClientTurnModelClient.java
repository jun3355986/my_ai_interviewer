package com.aiinterviewer.interview.model;

import com.aiinterviewer.interview.dto.PythonChatRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WebClientTurnModelClient implements TurnModelClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String pythonBaseUrl;

    public WebClientTurnModelClient(
            WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${python.ai.base-url:${python-ai.base-url:http://localhost:8000}}")
                    String pythonBaseUrl) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.pythonBaseUrl = pythonBaseUrl;
    }

    @Override
    public TurnModelResult process(TurnModelCommand command) throws Exception {
        PythonChatRequest request = PythonChatRequest.builder()
                .sessionId(command.pythonSessionId())
                .requestId(command.requestId())
                .agentRunId(command.agentRunId())
                .turnId(command.turnId())
                .branchSnapshot(command.branchSnapshot())
                .javaSessionId(command.branchId())
                .userId(command.userId())
                .username(command.username())
                .businessType("interview")
                .entrypoint("turn_attempt")
                .message(command.candidateAnswer())
                .resumeContent(command.resumeContent())
                .jobRequirements(command.jobRequirements())
                .candidateName(command.candidateName())
                .build();

        List<ServerSentEvent<String>> events = webClient.post()
                .uri(pythonBaseUrl + "/interview/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .collectList()
                .block(Duration.ofMinutes(10));
        if (events == null) {
            throw new IllegalStateException("Python model returned no events");
        }

        StringBuilder aiMessage = new StringBuilder();
        AtomicReference<String> nextStage = new AtomicReference<>(command.currentStage());
        AtomicReference<String> pythonSessionId = new AtomicReference<>(command.pythonSessionId());
        AtomicReference<Integer> score = new AtomicReference<>();
        AtomicReference<String> feedback = new AtomicReference<>();
        AtomicReference<Boolean> isFollowup = new AtomicReference<>(false);
        AtomicReference<Map<String, Object>> metadata = new AtomicReference<>();
        AtomicReference<AuthoritativeTurnState> authoritativeState = new AtomicReference<>();
        AtomicReference<Boolean> complete = new AtomicReference<>(false);
        String resultStage = null;
        String doneStage = null;
        boolean resultSeen = false;
        boolean doneSeen = false;

        for (ServerSentEvent<String> event : events) {
            if (!StringUtils.hasText(event.event()) || !StringUtils.hasText(event.data())) {
                continue;
            }
            if (doneSeen) {
                throw new IllegalStateException("Python model emitted events after done");
            }
            JsonNode data = objectMapper.readTree(event.data());
            switch (event.event()) {
                case "status" -> {
                    if (data.hasNonNull("session_id")) {
                        pythonSessionId.set(data.get("session_id").asText());
                    }
                    if (data.hasNonNull("stage")) {
                        nextStage.set(data.get("stage").asText());
                    }
                }
                case "question" -> {
                    if (data.hasNonNull("question")) {
                        metadata.set(objectMapper.convertValue(
                                data.get("question"),
                                new TypeReference<Map<String, Object>>() {}));
                    }
                }
                case "chunk" -> {
                    if (data.hasNonNull("content")) {
                        aiMessage.append(data.get("content").asText());
                    }
                }
                case "score" -> {
                    if (data.hasNonNull("score")) {
                        score.set(data.get("score").asInt());
                    }
                    if (data.hasNonNull("feedback")) {
                        feedback.set(data.get("feedback").asText());
                    }
                    if (data.hasNonNull("is_followup")) {
                        isFollowup.set(data.get("is_followup").asBoolean(false));
                    }
                }
                case "result" -> {
                    if (resultSeen || !data.hasNonNull("next_stage")) {
                        throw new IllegalStateException("Python model result event is invalid");
                    }
                    resultSeen = true;
                    resultStage = data.get("next_stage").asText();
                    nextStage.set(resultStage);
                    if (data.hasNonNull("post_turn_state")) {
                        authoritativeState.set(objectMapper.convertValue(
                                data.get("post_turn_state"),
                                AuthoritativeTurnState.class));
                    }
                    if (aiMessage.isEmpty() && data.hasNonNull("next_question")) {
                        aiMessage.append(data.get("next_question").asText());
                    }
                }
                case "done" -> {
                    if (doneSeen || !data.hasNonNull("stage")) {
                        throw new IllegalStateException("Python model done event is invalid");
                    }
                    doneSeen = true;
                    doneStage = data.get("stage").asText();
                    nextStage.set(doneStage);
                    complete.set(data.path("is_interview_complete").asBoolean(false));
                }
                case "error" -> throw new IllegalStateException("Python model reported an error");
                default -> {
                    // Unknown progress events are intentionally ignored by the durable contract.
                }
            }
        }
        if (!resultSeen || !doneSeen) {
            throw new IllegalStateException("Python model stream ended before result/done");
        }
        if (!Objects.equals(resultStage, doneStage)) {
            throw new IllegalStateException("Python model result/done stage mismatch");
        }
        if (complete.get() != "concluded".equals(doneStage)) {
            throw new IllegalStateException("Python model done completion flag is inconsistent");
        }
        if (command.branchSnapshot() != null && authoritativeState.get() == null) {
            throw new IllegalStateException("Python model result omitted authoritative post-turn state");
        }
        return new TurnModelResult(
                aiMessage.toString(),
                nextStage.get(),
                complete.get(),
                score.get(),
                feedback.get(),
                isFollowup.get(),
                metadata.get(),
                pythonSessionId.get(),
                authoritativeState.get());
    }
}
