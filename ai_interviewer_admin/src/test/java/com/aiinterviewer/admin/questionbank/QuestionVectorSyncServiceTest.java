package com.aiinterviewer.admin.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.admin.questionbank.client.PythonQuestionBankClient;
import com.aiinterviewer.admin.questionbank.dto.QuestionCreateRequest;
import com.aiinterviewer.admin.questionbank.entity.QuestionBankItem;
import com.aiinterviewer.admin.questionbank.entity.QuestionVectorSyncRecord;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@SuppressWarnings("unchecked")
class QuestionVectorSyncServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private QuestionVectorSyncService questionVectorSyncService;

    @MockBean
    private PythonQuestionBankClient pythonQuestionBankClient;

    @Test
    void syncOnlySendsEnabledEligibleQuestions() {
        Long enabledId = questionService.createQuestion(createRequest("启用待同步题目", 1));
        Long disabledId = questionService.createQuestion(createRequest("禁用题目", 0));
        Long syncedId = questionService.createQuestion(createRequest("已同步题目", 1));
        jdbcTemplate.update("UPDATE t_question_bank SET vector_sync_status = 'SYNCED' WHERE id = ?", syncedId);
        when(pythonQuestionBankClient.syncQuestions(anyList()))
                .thenReturn(PythonQuestionBankClient.SyncResponse.success("vector-store-test"));

        QuestionVectorSyncService.SyncResult result = questionVectorSyncService.syncPendingQuestions();

        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();

        ArgumentCaptor<List<QuestionBankItem>> questionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pythonQuestionBankClient).syncQuestions(questionsCaptor.capture());
        assertThat(questionsCaptor.getValue()).extracting(QuestionBankItem::getId).containsExactly(enabledId);
        assertThat(questionVectorStatus(disabledId)).isEqualTo("PENDING");
    }

    @Test
    void syncExcludesDeletedQuestions() {
        Long keptId = questionService.createQuestion(createRequest("未删除题目", 1));
        Long deletedId = questionService.createQuestion(createRequest("已删除题目", 1));
        questionService.deleteQuestion(deletedId);
        when(pythonQuestionBankClient.syncQuestions(anyList()))
                .thenReturn(PythonQuestionBankClient.SyncResponse.success("vector-store-test"));

        questionVectorSyncService.syncPendingQuestions();

        ArgumentCaptor<List<QuestionBankItem>> questionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pythonQuestionBankClient).syncQuestions(questionsCaptor.capture());
        assertThat(questionsCaptor.getValue()).extracting(QuestionBankItem::getId).containsExactly(keptId);
    }

    @Test
    void successfulSyncMarksQuestionSyncedAndCreatesSyncRecord() {
        Long questionId = questionService.createQuestion(createRequest("成功同步题目", 1));
        when(pythonQuestionBankClient.syncQuestions(anyList()))
                .thenReturn(PythonQuestionBankClient.SyncResponse.success("vector-store-001"));

        QuestionVectorSyncService.SyncResult result = questionVectorSyncService.syncPendingQuestions();

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getTotalCount()).isOne();
        assertThat(result.getSuccessCount()).isOne();
        assertThat(result.getFailedCount()).isZero();
        assertThat(questionVectorStatus(questionId)).isEqualTo("SYNCED");
        assertThat(questionVectorError(questionId)).isNull();

        QuestionVectorSyncRecord record = syncRecord(questionId);
        assertThat(record.getSyncStatus()).isEqualTo("SYNCED");
        assertThat(record.getVectorStoreId()).isEqualTo("vector-store-001");
        assertThat(record.getErrorMessage()).isNull();
        assertThat(record.getLastSyncedAt()).isNotNull();
    }

    @Test
    void failedSyncMarksQuestionFailedAndWritesFailureReason() {
        Long questionId = questionService.createQuestion(createRequest("失败同步题目", 1));
        when(pythonQuestionBankClient.syncQuestions(anyList()))
                .thenThrow(new IllegalStateException("python service unavailable"));

        QuestionVectorSyncService.SyncResult result = questionVectorSyncService.syncPendingQuestions();

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getTotalCount()).isOne();
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isOne();
        assertThat(result.getErrorMessage()).contains("python service unavailable");
        assertThat(questionVectorStatus(questionId)).isEqualTo("FAILED");
        assertThat(questionVectorError(questionId)).contains("python service unavailable");

        QuestionVectorSyncRecord record = syncRecord(questionId);
        assertThat(record.getSyncStatus()).isEqualTo("FAILED");
        assertThat(record.getErrorMessage()).contains("python service unavailable");
        assertThat(record.getRetryCount()).isOne();
    }

    @Test
    void syncRecordCountsAndStatusReflectPartialFailure() {
        Long successId = questionService.createQuestion(createRequest("局部成功题目", 1));
        Long failedId = questionService.createQuestion(createRequest("局部失败题目", 1));
        when(pythonQuestionBankClient.syncQuestions(anyList()))
                .thenReturn(new PythonQuestionBankClient.SyncResponse(
                        "PARTIAL_FAILED",
                        2,
                        1,
                        1,
                        "partial failure",
                        List.of(new PythonQuestionBankClient.QuestionSyncResult(successId, "SYNCED", "vector-store-ok", null)),
                        List.of(new PythonQuestionBankClient.QuestionSyncResult(failedId, "FAILED", null, "embedding failed"))));

        QuestionVectorSyncService.SyncResult result = questionVectorSyncService.syncPendingQuestions();

        assertThat(result.getStatus()).isEqualTo("PARTIAL_FAILED");
        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isOne();
        assertThat(result.getFailedCount()).isOne();
        assertThat(questionVectorStatus(successId)).isEqualTo("SYNCED");
        assertThat(questionVectorStatus(failedId)).isEqualTo("FAILED");
        assertThat(syncRecord(successId).getSyncStatus()).isEqualTo("SYNCED");
        assertThat(syncRecord(failedId).getSyncStatus()).isEqualTo("FAILED");
    }

    private QuestionCreateRequest createRequest(String questionText, Integer status) {
        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setQuestionText(questionText);
        request.setAnswerReference("参考答案");
        request.setQuestionType("TECHNICAL");
        request.setDifficulty("MEDIUM");
        request.setSkillArea("Java");
        request.setJobId(101L);
        request.setStatus(status);
        request.setCreatedBy(1L);
        request.setTags(List.of("Java", "集合"));
        return request;
    }

    private String questionVectorStatus(Long questionId) {
        return jdbcTemplate.queryForObject(
                "SELECT vector_sync_status FROM t_question_bank WHERE id = ?",
                String.class,
                questionId);
    }

    private String questionVectorError(Long questionId) {
        return jdbcTemplate.queryForObject(
                "SELECT vector_sync_error FROM t_question_bank WHERE id = ?",
                String.class,
                questionId);
    }

    private QuestionVectorSyncRecord syncRecord(Long questionId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT question_id, sync_status, vector_store_id, error_message, retry_count,
                       last_synced_at::timestamp AS last_synced_at,
                       created_at::timestamp AS created_at,
                       updated_at::timestamp AS updated_at
                FROM t_question_vector_sync_record
                WHERE question_id = ?
                """,
                (rs, rowNum) -> {
                    QuestionVectorSyncRecord record = new QuestionVectorSyncRecord();
                    record.setQuestionId(rs.getLong("question_id"));
                    record.setSyncStatus(rs.getString("sync_status"));
                    record.setVectorStoreId(rs.getString("vector_store_id"));
                    record.setErrorMessage(rs.getString("error_message"));
                    record.setRetryCount(rs.getInt("retry_count"));
                    record.setLastSyncedAt(rs.getTimestamp("last_synced_at") == null
                            ? null
                            : rs.getTimestamp("last_synced_at").toLocalDateTime());
                    record.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    record.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                    return record;
                },
                questionId);
    }
}
