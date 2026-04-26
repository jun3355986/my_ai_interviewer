package com.aiinterviewer.admin.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.questionbank.dto.QuestionCreateRequest;
import com.aiinterviewer.admin.questionbank.dto.QuestionUpdateRequest;
import com.aiinterviewer.admin.questionbank.entity.QuestionBankItem;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QuestionServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private QuestionService questionService;

    @Test
    void createQuestionRequiresTextTypeDifficultyAndStatus() {
        QuestionCreateRequest blankText = createRequest();
        blankText.setQuestionText(" ");
        assertThatThrownBy(() -> questionService.createQuestion(blankText))
                .hasMessageContaining("题目内容不能为空");

        QuestionCreateRequest missingType = createRequest();
        missingType.setQuestionType(null);
        assertThatThrownBy(() -> questionService.createQuestion(missingType))
                .hasMessageContaining("题目类型不能为空");

        QuestionCreateRequest missingDifficulty = createRequest();
        missingDifficulty.setDifficulty(null);
        assertThatThrownBy(() -> questionService.createQuestion(missingDifficulty))
                .hasMessageContaining("难度不能为空");

        QuestionCreateRequest missingStatus = createRequest();
        missingStatus.setStatus(null);
        assertThatThrownBy(() -> questionService.createQuestion(missingStatus))
                .hasMessageContaining("状态不能为空");
    }

    @Test
    void updateQuestionChangesAnswerReferenceTagsDifficultyAndStatus() {
        Long questionId = questionService.createQuestion(createRequest());
        QuestionBankItem beforeUpdate = questionService.getQuestion(questionId);

        QuestionUpdateRequest update = new QuestionUpdateRequest();
        update.setAnswerReference("双亲委派、加载、验证、准备、解析、初始化");
        update.setDifficulty("HARD");
        update.setStatus(0);
        update.setTags(List.of("JVM", "架构"));
        questionService.updateQuestion(questionId, update);

        QuestionBankItem updated = questionService.getQuestion(questionId);

        assertThat(updated.getQuestionText()).isEqualTo(beforeUpdate.getQuestionText());
        assertThat(updated.getQuestionType()).isEqualTo(beforeUpdate.getQuestionType());
        assertThat(updated.getSkillArea()).isEqualTo(beforeUpdate.getSkillArea());
        assertThat(updated.getJobId()).isEqualTo(beforeUpdate.getJobId());
        assertThat(updated.getAnswerReference()).contains("双亲委派");
        assertThat(updated.getDifficulty()).isEqualTo("HARD");
        assertThat(updated.getStatus()).isZero();
        assertThat(updated.getVectorSyncStatus()).isEqualTo("DELETE_PENDING");
        assertThat(updated.getVectorSyncError()).isNull();
        assertThat(updated.getTags()).containsExactly("JVM", "架构");
        assertThat(updated.isEligibleForVectorSync()).isFalse();
    }

    @Test
    void updateQuestionCanClearAnswerReferenceAndLeaveTagsUnchangedWhenTagsAreNull() {
        Long questionId = questionService.createQuestion(createRequest());

        QuestionUpdateRequest update = new QuestionUpdateRequest();
        update.setAnswerReference(null);
        update.setTags(null);
        questionService.updateQuestion(questionId, update);

        QuestionBankItem updated = questionService.getQuestion(questionId);

        assertThat(updated.getAnswerReference()).isNull();
        assertThat(updated.getTags()).containsExactly("Java", "数据库");
        assertThat(updated.getVectorSyncStatus()).isEqualTo("PENDING");
    }

    @Test
    void listQuestionFiltersByTypeDifficultyTagStatusJobIdAndKeyword() {
        Long targetId = questionService.createQuestion(createRequest());

        QuestionCreateRequest behavior = createRequest();
        behavior.setQuestionText("请描述一次跨团队沟通经历");
        behavior.setQuestionType("BEHAVIORAL");
        behavior.setDifficulty("EASY");
        behavior.setJobId(202L);
        behavior.setTags(List.of("沟通"));
        questionService.createQuestion(behavior);

        QuestionQuery query = new QuestionQuery();
        query.setQuestionType("TECHNICAL");
        query.setDifficulty("MEDIUM");
        query.setTag("Java");
        query.setStatus(1);
        query.setJobId(101L);
        query.setKeyword("索引");

        PageResult<QuestionBankItem> result = questionService.listQuestions(query);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).extracting(QuestionBankItem::getId).containsExactly(targetId);
        assertThat(result.getRecords().getFirst().getTags()).containsExactly("Java", "数据库");
    }

    @Test
    void tagsAreReusedAndFilteredCaseInsensitively() {
        Long firstId = questionService.createQuestion(createRequest());

        QuestionCreateRequest second = createRequest();
        second.setQuestionText("请说明 Java Stream 的常见使用场景");
        second.setTags(List.of(" java ", "JAVA"));
        Long secondId = questionService.createQuestion(second);

        Integer javaTagCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_question_tag WHERE lower(tag_name) = 'java' AND deleted_at IS NULL",
                Integer.class);
        QuestionQuery query = new QuestionQuery();
        query.setTag("java");

        PageResult<QuestionBankItem> result = questionService.listQuestions(query);

        assertThat(javaTagCount).isOne();
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).extracting(QuestionBankItem::getId).containsExactly(secondId, firstId);
        assertThat(result.getRecords()).allSatisfy(question -> assertThat(question.getTags()).contains("Java"));
    }

    @Test
    void disabledQuestionIsNotEligibleForVectorSync() {
        QuestionCreateRequest request = createRequest();
        request.setStatus(0);
        Long questionId = questionService.createQuestion(request);

        QuestionBankItem disabled = questionService.getQuestion(questionId);

        assertThat(disabled.getStatus()).isZero();
        assertThat(disabled.isEligibleForVectorSync()).isFalse();
    }

    @Test
    void syncedQuestionUpdateInvalidatesVectorSyncAsPendingWhenEnabled() {
        Long questionId = questionService.createQuestion(createRequest());
        jdbcTemplate.update(
                "UPDATE t_question_bank SET vector_sync_status = 'SYNCED', vector_sync_error = 'old error' WHERE id = ?",
                questionId);

        QuestionUpdateRequest update = new QuestionUpdateRequest();
        update.setDifficulty("HARD");
        update.setStatus(1);
        questionService.updateQuestion(questionId, update);

        QuestionBankItem updated = questionService.getQuestion(questionId);

        assertThat(updated.getDifficulty()).isEqualTo("HARD");
        assertThat(updated.getStatus()).isEqualTo(1);
        assertThat(updated.getVectorSyncStatus()).isEqualTo("PENDING");
        assertThat(updated.getVectorSyncError()).isNull();
        assertThat(updated.isEligibleForVectorSync()).isTrue();
    }

    @Test
    void disabledOrDeletedQuestionBecomesDeletePendingAndNotEligible() {
        Long disabledId = questionService.createQuestion(createRequest());
        QuestionUpdateRequest disable = new QuestionUpdateRequest();
        disable.setStatus(0);
        questionService.updateQuestion(disabledId, disable);

        QuestionBankItem disabled = questionService.getQuestion(disabledId);

        assertThat(disabled.getVectorSyncStatus()).isEqualTo("DELETE_PENDING");
        assertThat(disabled.getVectorSyncError()).isNull();
        assertThat(disabled.isEligibleForVectorSync()).isFalse();

        Long deletedId = questionService.createQuestion(createRequest());
        questionService.deleteQuestion(deletedId);

        String deletedVectorStatus = jdbcTemplate.queryForObject(
                "SELECT vector_sync_status FROM t_question_bank WHERE id = ?",
                String.class,
                deletedId);
        String deletedVectorError = jdbcTemplate.queryForObject(
                "SELECT vector_sync_error FROM t_question_bank WHERE id = ?",
                String.class,
                deletedId);

        assertThat(deletedVectorStatus).isEqualTo("DELETE_PENDING");
        assertThat(deletedVectorError).isNull();
    }

    @Test
    void softDeleteHidesQuestionFromDefaultList() {
        Long questionId = questionService.createQuestion(createRequest());

        questionService.deleteQuestion(questionId);

        QuestionQuery query = new QuestionQuery();
        PageResult<QuestionBankItem> result = questionService.listQuestions(query);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        assertThatThrownBy(() -> questionService.getQuestion(questionId))
                .hasMessageContaining("题目不存在");
    }

    private QuestionCreateRequest createRequest() {
        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setQuestionText("请解释 PostgreSQL 索引为什么能提升查询性能");
        request.setAnswerReference("B-tree 索引可以减少扫描范围");
        request.setQuestionType("TECHNICAL");
        request.setDifficulty("MEDIUM");
        request.setSkillArea("Database");
        request.setJobId(101L);
        request.setStatus(1);
        request.setCreatedBy(1L);
        request.setTags(List.of("Java", "数据库"));
        return request;
    }
}
