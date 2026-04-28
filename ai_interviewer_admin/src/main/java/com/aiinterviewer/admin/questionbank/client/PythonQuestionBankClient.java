package com.aiinterviewer.admin.questionbank.client;

import com.aiinterviewer.admin.questionbank.entity.QuestionBankItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonQuestionBankClient {

    private final RestClient restClient;
    private final String syncUrl;
    private final String deleteUrl;

    public PythonQuestionBankClient(
            RestClient.Builder restClientBuilder,
            @Value("${python-ai.question-bank-sync-url:http://localhost:8000/admin/question-bank/sync}") String syncUrl,
            @Value("${python-ai.question-bank-delete-url:http://localhost:8000/admin/question-bank/delete}") String deleteUrl) {
        this.restClient = restClientBuilder.build();
        this.syncUrl = syncUrl;
        this.deleteUrl = deleteUrl;
    }

    public SyncResponse syncQuestions(List<QuestionBankItem> questions) {
        SyncResponse response = restClient.post()
                .uri(syncUrl)
                .body(new SyncRequest(toPayload(questions)))
                .retrieve()
                .body(SyncResponse.class);
        return response == null
                ? SyncResponse.failure("Python question bank sync returned empty response", questions.size())
                : response;
    }

    public DeleteResponse deleteQuestions(List<QuestionBankItem> questions) {
        DeleteResponse response = restClient.post()
                .uri(deleteUrl)
                .body(new DeleteRequest(toDeletePayload(questions)))
                .retrieve()
                .body(DeleteResponse.class);
        return response == null
                ? DeleteResponse.failure("Python question bank delete returned empty response", questions.size())
                : response;
    }

    private List<QuestionPayload> toPayload(List<QuestionBankItem> questions) {
        return questions.stream()
                .map(question -> new QuestionPayload(
                        question.getId(),
                        question.getQuestionText(),
                        question.getAnswerReference(),
                        question.getQuestionType(),
                        question.getDifficulty(),
                        question.getTags() == null ? List.of() : question.getTags(),
                        question.getSkillArea()))
                .toList();
    }

    private List<DeletePayload> toDeletePayload(List<QuestionBankItem> questions) {
        return questions.stream()
                .map(question -> new DeletePayload(question.getId()))
                .toList();
    }

    public record SyncRequest(List<QuestionPayload> questions) {
    }

    public record QuestionPayload(
            Long id,
            @JsonProperty("question_text") String questionText,
            @JsonProperty("answer_reference") String answerReference,
            @JsonProperty("question_type") String questionType,
            String difficulty,
            List<String> tags,
            @JsonProperty("skill_area") String skillArea) {
    }

    public record DeleteRequest(List<DeletePayload> questions) {
    }

    public record DeletePayload(Long id) {
    }

    public record QuestionSyncResult(
            Long id,
            String status,
            @JsonProperty("vector_store_id") String vectorStoreId,
            @JsonProperty("error_message") String errorMessage) {
    }

    public record SyncResponse(
            String status,
            @JsonProperty("total_count") int totalCount,
            @JsonProperty("success_count") int successCount,
            @JsonProperty("failed_count") int failedCount,
            @JsonProperty("error_message") String errorMessage,
            @JsonProperty("success_questions") List<QuestionSyncResult> successQuestions,
            @JsonProperty("failed_questions") List<QuestionSyncResult> failedQuestions) {

        public static SyncResponse success(String vectorStoreId) {
            return new SyncResponse("SUCCESS", 0, 0, 0, null, List.of(
                    new QuestionSyncResult(null, "SYNCED", vectorStoreId, null)), List.of());
        }

        public static SyncResponse failure(String errorMessage, int totalCount) {
            return new SyncResponse("FAILED", totalCount, 0, totalCount, errorMessage, List.of(), List.of());
        }

        public List<QuestionSyncResult> successQuestions() {
            return successQuestions == null ? List.of() : successQuestions;
        }

        public List<QuestionSyncResult> failedQuestions() {
            return failedQuestions == null ? List.of() : failedQuestions;
        }
    }

    public record DeleteResponse(
            String status,
            @JsonProperty("total_count") int totalCount,
            @JsonProperty("success_count") int successCount,
            @JsonProperty("failed_count") int failedCount,
            @JsonProperty("error_message") String errorMessage) {

        public static DeleteResponse success(int totalCount) {
            return new DeleteResponse("SUCCESS", totalCount, totalCount, 0, null);
        }

        public static DeleteResponse failure(String errorMessage, int totalCount) {
            return new DeleteResponse("FAILED", totalCount, 0, totalCount, errorMessage);
        }
    }
}
