package com.aiinterviewer.interview.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class WebClientTurnModelClientSnapshotTest {

    @Test
    void sendsTurnSnapshotAndCorrelationAndParsesExistingSseContract() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer python = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        python.createContext("/interview/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    event: status
                    data: {"session_id":"branch-1","stage":"project_qna","turn_id":"turn-1"}

                    event: chunk
                    data: {"content":"next question","turn_id":"turn-1"}

                    event: score
                    data: {"score":45,"feedback":"需要继续追问","is_followup":true,"turn_id":"turn-1"}

                    event: result
                    data: {"next_stage":"project_qna","next_question":"next question","turn_id":"turn-1","post_turn_state":{"current_stage":"project_qna","branch_status":1,"project_questions_count":2,"target_project_questions":3,"current_followup_count":1,"project_questions_pool":["project-after"],"technical_questions_pool":[{"id":"tech-after"}]}}

                    event: done
                    data: {"stage":"project_qna","is_interview_complete":false,"turn_id":"turn-1"}

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        python.start();
        try {
            ObjectMapper mapper = new ObjectMapper();
            WebClientTurnModelClient client = new WebClientTurnModelClient(
                    WebClient.builder().build(),
                    mapper,
                    "http://127.0.0.1:" + python.getAddress().getPort());
            BranchSnapshot snapshot = new BranchSnapshot(
                    1,
                    "turn-1",
                    "branch-1",
                    "lineage-1",
                    3L,
                    9L,
                    42L,
                    "alice",
                    "Candidate",
                    "Resume",
                    "Job",
                    "project_qna",
                    1,
                    1,
                    3,
                    0,
                    List.of("project-next"),
                    List.of(Map.of("id", "tech-next", "text", "Technical")),
                    List.of(new BranchSnapshotMessage(
                            9L,
                            "branch-1",
                            "ai",
                            "current question",
                            "project_qna",
                            "ai_question",
                            true,
                            Map.of(),
                            1,
                            1)),
                    List.of());
            TurnModelCommand command = new TurnModelCommand(
                    "turn-1",
                    "req-1",
                    "agent-1",
                    "branch-1",
                    "lineage-1",
                    42L,
                    "alice",
                    "stale-python-session",
                    "candidate answer",
                    "Candidate",
                    "Resume",
                    "Job",
                    "project_qna",
                    snapshot);

            TurnModelResult result = client.process(command);
            JsonNode json = mapper.readTree(requestBody.get());

            assertThat(json.get("turn_id").asText()).isEqualTo("turn-1");
            assertThat(json.get("branch_snapshot").get("schema_version").asInt()).isEqualTo(1);
            assertThat(json.get("branch_snapshot").get("messages").get(0).get("id").asLong())
                    .isEqualTo(9L);
            assertThat(json.get("request_id").asText()).isEqualTo("req-1");
            assertThat(json.get("agent_run_id").asText()).isEqualTo("agent-1");
            assertThat(json.get("java_session_id").asText()).isEqualTo("branch-1");
            assertThat(json.get("user_id").asLong()).isEqualTo(42L);
            assertThat(json.get("username").asText()).isEqualTo("alice");
            assertThat(result.aiMessage()).isEqualTo("next question");
            assertThat(result.nextStage()).isEqualTo("project_qna");
            assertThat(result.score()).isEqualTo(45);
            assertThat(result.isFollowup()).isTrue();
            assertThat(result.authoritativeState().projectQuestionsCount()).isEqualTo(2);
            assertThat(result.authoritativeState().currentFollowupCount()).isEqualTo(1);
            assertThat(result.authoritativeState().projectQuestionsPool())
                    .containsExactly("project-after");
            assertThat(result.authoritativeState().technicalQuestionsPool())
                    .containsExactly(Map.of("id", "tech-after"));
        } finally {
            python.stop(0);
        }
    }
}
