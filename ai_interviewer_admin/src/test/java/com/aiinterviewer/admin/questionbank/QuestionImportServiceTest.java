package com.aiinterviewer.admin.questionbank;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.admin.questionbank.entity.QuestionBankItem;
import com.aiinterviewer.admin.questionbank.entity.QuestionImportBatch;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QuestionImportServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private QuestionImportService questionImportService;

    @Autowired
    private QuestionService questionService;

    @Test
    void validCsvCreatesImportBatchAndQuestionRecords() {
        QuestionImportBatch batch = questionImportService.importCsv(
                "sample_questions.csv",
                getClass().getResourceAsStream("/questionbank/sample_questions.csv"),
                7L);

        assertThat(batch.getStatus()).isEqualTo("SUCCESS");
        assertThat(batch.getTotalCount()).isEqualTo(2);
        assertThat(batch.getSuccessCount()).isEqualTo(2);
        assertThat(batch.getFailedCount()).isZero();
        assertThat(batch.getErrorMessage()).isNull();

        List<String> importedQuestions = importedQuestionTexts(batch.getId());

        assertThat(importedQuestions).containsExactlyInAnyOrder(
                "请解释 Java 线程池的核心参数",
                "Spring Bean 的生命周期有哪些关键阶段");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_question_import_batch WHERE batch_no = ? AND imported_by = ?",
                Integer.class,
                batch.getBatchNo(),
                7L)).isOne();

        assertThat(questionService.getQuestion(questionIdByText("请解释 Java 线程池的核心参数")).getTags())
                .containsExactly("Java", "Concurrency");
    }

    @Test
    void markdownImportParsesQuestionsAsPendingReview() {
        QuestionImportBatch batch = questionImportService.importQuestionFile(
                "backend_questions.md",
                text("""
                        题目：Redis 缓存雪崩怎么解决？
                        参考答案：预热、随机过期、限流降级。
                        题型：TECHNICAL
                        难度：HARD
                        技能领域：Redis
                        标签：Redis;缓存

                        问：Java 线程池的核心参数有哪些？
                        答：corePoolSize、maximumPoolSize、queue、handler 等。
                        类型：TECHNICAL
                        难度：MEDIUM
                        技能领域：Java
                        标签：Java;并发
                        """),
                21L);

        assertThat(batch.getStatus()).isEqualTo("SUCCESS");
        assertThat(batch.getTotalCount()).isEqualTo(2);
        assertThat(batch.getSuccessCount()).isEqualTo(2);
        assertThat(batch.getFailedCount()).isZero();

        List<String> importedQuestions = importedQuestionTexts(batch.getId());
        assertThat(importedQuestions).containsExactlyInAnyOrder(
                "Redis 缓存雪崩怎么解决？",
                "Java 线程池的核心参数有哪些？");

        QuestionBankItem redisQuestion = questionService.getQuestion(questionIdByText("Redis 缓存雪崩怎么解决？"));
        assertThat(redisQuestion.getStatus()).isEqualTo(QuestionBankItem.STATUS_PENDING_REVIEW);
        assertThat(redisQuestion.getVectorSyncStatus()).isEqualTo("PENDING");
        assertThat(redisQuestion.getAnswerReference()).contains("预热");
        assertThat(redisQuestion.getDifficulty()).isEqualTo("HARD");
        assertThat(redisQuestion.getSkillArea()).isEqualTo("Redis");
        assertThat(redisQuestion.getTags()).containsExactly("Redis", "缓存");
    }

    @Test
    void txtImportRejectsUnsupportedFreeFormBlocksWithRowLikeErrors() {
        QuestionImportBatch batch = questionImportService.importQuestionFile(
                "bad.txt",
                text("""
                        这是一段没有题目标记的说明文字。

                        题目：
                        答案：缺少题目内容
                        """),
                22L);

        assertThat(batch.getStatus()).isEqualTo("FAILED");
        assertThat(batch.getTotalCount()).isOne();
        assertThat(batch.getSuccessCount()).isZero();
        assertThat(batch.getFailedCount()).isOne();
        assertThat(batch.getErrorMessage()).contains("题目内容不能为空");
    }

    @Test
    void robustCsvBoundariesImportSuccessfully() {
        QuestionImportBatch batch = questionImportService.importCsv(
                "robust.csv",
                csv("\uFEFFquestion_text,answer_reference,question_type,difficulty,tags,skill_area,job_id,status\r\n"
                        + "\"带逗号,题目\",\"第一行\r\n第二行 \"\"引用\"\"\",TECHNICAL,MEDIUM,Java;CSV,Java,101,1\r\n"),
                11L);

        assertThat(batch.getStatus()).isEqualTo("SUCCESS");
        assertThat(batch.getTotalCount()).isOne();
        assertThat(batch.getSuccessCount()).isOne();
        assertThat(batch.getFailedCount()).isZero();
        assertThat(batch.getErrorMessage()).isNull();

        Long questionId = questionIdByText("带逗号,题目");
        assertThat(questionService.getQuestion(questionId).getAnswerReference())
                .isEqualTo("第一行\r\n第二行 \"引用\"");
    }

    @Test
    void rowMissingQuestionTextIsRejectedWithRowNumber() {
        QuestionImportBatch batch = questionImportService.importCsv(
                "missing-question-text.csv",
                csv("""
                        question_text,answer_reference,question_type,difficulty,tags,skill_area,job_id,status
                        ,参考答案,TECHNICAL,MEDIUM,Java;Spring,Java,101,1
                        """),
                8L);

        assertThat(batch.getStatus()).isEqualTo("FAILED");
        assertThat(batch.getTotalCount()).isEqualTo(1);
        assertThat(batch.getSuccessCount()).isZero();
        assertThat(batch.getFailedCount()).isOne();
        assertThat(batch.getErrorMessage()).contains("第2行").contains("题目内容不能为空");
        assertThat(importedQuestionTexts(batch.getId())).isEmpty();
    }

    @Test
    void duplicateQuestionTextInSameBatchIsRejectedAndUniqueRowsAreImported() {
        QuestionImportBatch batch = questionImportService.importCsv(
                "duplicate-question.csv",
                csv("""
                        question_text,answer_reference,question_type,difficulty,tags,skill_area,job_id,status
                        同批次重复题目,参考答案一,TECHNICAL,MEDIUM,Java;Spring,Java,101,1
                        同批次重复题目,参考答案二,TECHNICAL,HARD,Java,Java,101,1
                        唯一题目,参考答案三,BEHAVIORAL,EASY,Communication,Communication,,1
                        """),
                9L);

        assertThat(batch.getStatus()).isEqualTo("PARTIAL_FAILED");
        assertThat(batch.getTotalCount()).isEqualTo(3);
        assertThat(batch.getSuccessCount()).isEqualTo(2);
        assertThat(batch.getFailedCount()).isOne();
        assertThat(batch.getErrorMessage()).contains("第3行").contains("重复题目内容");
        assertThat(importedQuestionTexts(batch.getId())).containsExactlyInAnyOrder("同批次重复题目", "唯一题目");
    }

    @Test
    void partialFailureRecordsFailedRowCountAndDoesNotCreateInvalidRows() {
        QuestionImportBatch batch = questionImportService.importCsv(
                "partial-failure.csv",
                csv("""
                        question_text,answer_reference,question_type,difficulty,tags,skill_area,job_id,status
                        有效题目一,参考答案一,TECHNICAL,MEDIUM,Java;Spring,Java,101,1
                        缺少难度题目,参考答案二,TECHNICAL,,Java,Java,101,1
                        有效题目二,参考答案三,BEHAVIORAL,EASY,Communication,Communication,,1
                        """),
                10L);

        assertThat(batch.getStatus()).isEqualTo("PARTIAL_FAILED");
        assertThat(batch.getTotalCount()).isEqualTo(3);
        assertThat(batch.getSuccessCount()).isEqualTo(2);
        assertThat(batch.getFailedCount()).isOne();
        assertThat(batch.getErrorMessage()).contains("第3行").contains("难度不能为空");
        assertThat(importedQuestionTexts(batch.getId())).containsExactlyInAnyOrder("有效题目一", "有效题目二");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_question_bank WHERE question_text = '缺少难度题目' AND deleted_at IS NULL",
                Integer.class)).isZero();
    }

    @Test
    void parseStageRowErrorsArePartialFailuresAndValidRowsStillImport() {
        QuestionImportBatch batch = questionImportService.importCsv(
                "parse-partial-failure.csv",
                csv("""
                        question_text,answer_reference,question_type,difficulty,tags,skill_area,job_id,status
                        解析有效题目一,参考答案一,TECHNICAL,MEDIUM,Java,Java,101,1
                        job无效题目,参考答案二,TECHNICAL,MEDIUM,Java,Java,bad,1
                        状态无效题目,参考答案三,TECHNICAL,MEDIUM,Java,Java,101,bad
                        列数错误题目,参考答案四,TECHNICAL,MEDIUM,Java,Java,101
                        解析有效题目二,参考答案五,BEHAVIORAL,EASY,Communication,Communication,,1
                        """),
                12L);

        assertThat(batch.getStatus()).isEqualTo("PARTIAL_FAILED");
        assertThat(batch.getTotalCount()).isEqualTo(5);
        assertThat(batch.getSuccessCount()).isEqualTo(2);
        assertThat(batch.getFailedCount()).isEqualTo(3);
        assertThat(batch.getErrorMessage())
                .contains("第3行")
                .contains("job_id 必须是数字")
                .contains("第4行")
                .contains("status 必须是数字")
                .contains("第5行")
                .contains("列数不匹配");
        assertThat(importedQuestionTexts(batch.getId())).containsExactlyInAnyOrder("解析有效题目一", "解析有效题目二");
        assertThat(questionExists("job无效题目")).isFalse();
        assertThat(questionExists("状态无效题目")).isFalse();
        assertThat(questionExists("列数错误题目")).isFalse();
    }

    @Test
    void manyInvalidRowsAreFailedAndErrorMessageIsTruncated() {
        StringBuilder content = new StringBuilder(
                "question_text,answer_reference,question_type,difficulty,tags,skill_area,job_id,status\n");
        for (int i = 0; i < 700; i++) {
            content.append("错误题目")
                    .append(i)
                    .append(",参考答案,TECHNICAL,MEDIUM,Java,Java,bad,1\n");
        }

        QuestionImportBatch batch = questionImportService.importCsv(
                "many-invalid-rows.csv",
                csv(content.toString()),
                13L);

        assertThat(batch.getStatus()).isEqualTo("FAILED");
        assertThat(batch.getTotalCount()).isEqualTo(700);
        assertThat(batch.getSuccessCount()).isZero();
        assertThat(batch.getFailedCount()).isEqualTo(700);
        assertThat(batch.getErrorMessage())
                .hasSizeLessThanOrEqualTo(8000)
                .endsWith("...(错误信息已截断)");
        assertThat(importedQuestionTexts(batch.getId())).isEmpty();
    }

    private ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private ByteArrayInputStream text(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> importedQuestionTexts(Long batchId) {
        return jdbcTemplate.queryForList(
                """
                SELECT question_text
                FROM t_question_bank
                WHERE source_type = 'IMPORT'
                  AND source_batch_id = ?
                  AND deleted_at IS NULL
                ORDER BY id
                """,
                String.class,
                batchId);
    }

    private Long questionIdByText(String questionText) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_question_bank WHERE question_text = ? AND deleted_at IS NULL",
                Long.class,
                questionText);
    }

    private boolean questionExists(String questionText) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_question_bank WHERE question_text = ? AND deleted_at IS NULL",
                Integer.class,
                questionText) > 0;
    }
}
