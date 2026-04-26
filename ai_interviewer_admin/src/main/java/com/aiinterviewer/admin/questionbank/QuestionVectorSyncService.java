package com.aiinterviewer.admin.questionbank;

import com.aiinterviewer.admin.questionbank.client.PythonQuestionBankClient;
import com.aiinterviewer.admin.questionbank.entity.QuestionBankItem;
import com.aiinterviewer.admin.questionbank.entity.QuestionVectorSyncRecord;
import com.aiinterviewer.admin.questionbank.mapper.QuestionMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuestionVectorSyncService {

    private static final String STATUS_SYNCED = "SYNCED";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_LENGTH = 4000;

    private final QuestionMapper questionMapper;
    private final PythonQuestionBankClient pythonQuestionBankClient;

    @Transactional
    public SyncResult syncPendingQuestions() {
        List<QuestionBankItem> questions = questionMapper.selectQuestionsEligibleForVectorSync();
        hydrateTags(questions);
        if (questions.isEmpty()) {
            return SyncResult.success(0, 0);
        }

        try {
            PythonQuestionBankClient.SyncResponse response = pythonQuestionBankClient.syncQuestions(questions);
            return applyResponse(questions, response);
        } catch (Exception ex) {
            String errorMessage = truncateError(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            SyncResult result = SyncResult.failed(questions.size(), errorMessage);
            for (QuestionBankItem question : questions) {
                markFailed(question.getId(), errorMessage, result);
            }
            return result;
        }
    }

    private SyncResult applyResponse(
            List<QuestionBankItem> questions,
            PythonQuestionBankClient.SyncResponse response) {
        Map<Long, QuestionBankItem> questionById = new LinkedHashMap<>();
        questions.forEach(question -> questionById.put(question.getId(), question));

        List<PythonQuestionBankClient.QuestionSyncResult> successResults = response.successQuestions();
        List<PythonQuestionBankClient.QuestionSyncResult> failedResults = response.failedQuestions();
        int successCount = 0;
        int failedCount = 0;
        Set<Long> handledIds = new HashSet<>();

        if (shouldTreatAllAsSuccess(response, successResults, failedResults)) {
            String vectorStoreId = successResults.isEmpty() ? null : successResults.getFirst().vectorStoreId();
            for (QuestionBankItem question : questions) {
                handledIds.add(question.getId());
                successCount++;
            }
        } else {
            for (PythonQuestionBankClient.QuestionSyncResult result : successResults) {
                if (result.id() != null && questionById.containsKey(result.id())) {
                    handledIds.add(result.id());
                    successCount++;
                }
            }
        }

        for (PythonQuestionBankClient.QuestionSyncResult result : failedResults) {
            if (result.id() != null && questionById.containsKey(result.id())) {
                handledIds.add(result.id());
                failedCount++;
            }
        }

        for (QuestionBankItem question : questions) {
            if (handledIds.contains(question.getId())) {
                continue;
            }
            if ("SUCCESS".equals(response.status())) {
                successCount++;
            } else if (shouldTreatRemainingAsFailed(response)) {
                failedCount++;
            }
        }

        SyncResult syncResult = SyncResult.of(questions.size(), successCount, failedCount, response.errorMessage());
        applyQuestionOutcomes(questions, response, successResults, failedResults, syncResult);
        return syncResult;
    }

    private void applyQuestionOutcomes(
            List<QuestionBankItem> questions,
            PythonQuestionBankClient.SyncResponse response,
            List<PythonQuestionBankClient.QuestionSyncResult> successResults,
            List<PythonQuestionBankClient.QuestionSyncResult> failedResults,
            SyncResult syncResult) {
        Map<Long, PythonQuestionBankClient.QuestionSyncResult> successById = new LinkedHashMap<>();
        successResults.stream()
                .filter(result -> result.id() != null)
                .forEach(result -> successById.put(result.id(), result));
        Map<Long, PythonQuestionBankClient.QuestionSyncResult> failureById = new LinkedHashMap<>();
        failedResults.stream()
                .filter(result -> result.id() != null)
                .forEach(result -> failureById.put(result.id(), result));

        boolean allSuccess = shouldTreatAllAsSuccess(response, successResults, failedResults);
        String sharedVectorStoreId = successResults.isEmpty() ? null : successResults.getFirst().vectorStoreId();
        for (QuestionBankItem question : questions) {
            PythonQuestionBankClient.QuestionSyncResult success = successById.get(question.getId());
            PythonQuestionBankClient.QuestionSyncResult failure = failureById.get(question.getId());
            if (success != null) {
                markSynced(question.getId(), success.vectorStoreId(), syncResult);
            } else if (failure != null) {
                markFailed(question.getId(), failureMessage(failure, response), syncResult);
            } else if (allSuccess || "SUCCESS".equals(response.status())) {
                markSynced(question.getId(), allSuccess ? sharedVectorStoreId : null, syncResult);
            } else if (shouldTreatRemainingAsFailed(response)) {
                markFailed(question.getId(), fallbackErrorMessage(response), syncResult);
            }
        }
    }

    private boolean shouldTreatAllAsSuccess(
            PythonQuestionBankClient.SyncResponse response,
            List<PythonQuestionBankClient.QuestionSyncResult> successResults,
            List<PythonQuestionBankClient.QuestionSyncResult> failedResults) {
        return "SUCCESS".equals(response.status())
                && failedResults.isEmpty()
                && (successResults.isEmpty()
                || successResults.stream().allMatch(result -> result.id() == null));
    }

    private boolean shouldTreatRemainingAsFailed(PythonQuestionBankClient.SyncResponse response) {
        return "FAILED".equals(response.status()) || "PARTIAL_FAILED".equals(response.status());
    }

    private void markSynced(Long questionId, String vectorStoreId, SyncResult syncResult) {
        questionMapper.updateQuestionVectorSyncStatus(questionId, STATUS_SYNCED, null);
        QuestionVectorSyncRecord record = new QuestionVectorSyncRecord();
        record.setQuestionId(questionId);
        record.setSyncStatus(STATUS_SYNCED);
        record.setVectorStoreId(vectorStoreId);
        applyCounts(record, syncResult);
        questionMapper.upsertQuestionVectorSyncRecord(record);
    }

    private void markFailed(Long questionId, String errorMessage, SyncResult syncResult) {
        String safeErrorMessage = truncateError(errorMessage);
        questionMapper.updateQuestionVectorSyncStatus(questionId, STATUS_FAILED, safeErrorMessage);
        QuestionVectorSyncRecord record = new QuestionVectorSyncRecord();
        record.setQuestionId(questionId);
        record.setSyncStatus(STATUS_FAILED);
        record.setErrorMessage(safeErrorMessage);
        applyCounts(record, syncResult);
        questionMapper.upsertQuestionVectorSyncRecord(record);
    }

    private void applyCounts(QuestionVectorSyncRecord record, SyncResult syncResult) {
        record.setTotalCount(syncResult.getTotalCount());
        record.setSuccessCount(syncResult.getSuccessCount());
        record.setFailedCount(syncResult.getFailedCount());
    }

    private String failureMessage(
            PythonQuestionBankClient.QuestionSyncResult result,
            PythonQuestionBankClient.SyncResponse response) {
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            return truncateError(result.errorMessage());
        }
        return fallbackErrorMessage(response);
    }

    private String fallbackErrorMessage(PythonQuestionBankClient.SyncResponse response) {
        return truncateError(response.errorMessage() == null || response.errorMessage().isBlank()
                ? "Python question bank sync failed"
                : response.errorMessage());
    }

    private String truncateError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() <= MAX_ERROR_LENGTH
                ? errorMessage
                : errorMessage.substring(0, MAX_ERROR_LENGTH);
    }

    private void hydrateTags(List<QuestionBankItem> questions) {
        if (questions.isEmpty()) {
            return;
        }
        List<Long> questionIds = questions.stream()
                .map(QuestionBankItem::getId)
                .toList();
        Map<Long, List<String>> tagsByQuestionId = new LinkedHashMap<>();
        for (QuestionService.QuestionTagNameRow row : questionMapper.selectTagNamesByQuestionIds(questionIds)) {
            tagsByQuestionId.computeIfAbsent(row.getQuestionId(), ignored -> new ArrayList<>()).add(row.getTagName());
        }
        questions.forEach(question -> question.setTags(tagsByQuestionId.getOrDefault(question.getId(), List.of())));
    }

    @Data
    public static class SyncResult {

        private String status;
        private int totalCount;
        private int successCount;
        private int failedCount;
        private String errorMessage;

        static SyncResult success(int totalCount, int successCount) {
            return of(totalCount, successCount, 0, null);
        }

        static SyncResult failed(int totalCount, String errorMessage) {
            return of(totalCount, 0, totalCount, errorMessage);
        }

        static SyncResult of(int totalCount, int successCount, int failedCount, String errorMessage) {
            SyncResult result = new SyncResult();
            result.setTotalCount(totalCount);
            result.setSuccessCount(successCount);
            result.setFailedCount(failedCount);
            result.setErrorMessage(errorMessage);
            if (failedCount == 0) {
                result.setStatus("SUCCESS");
            } else if (successCount == 0) {
                result.setStatus("FAILED");
            } else {
                result.setStatus("PARTIAL_FAILED");
            }
            return result;
        }
    }
}
