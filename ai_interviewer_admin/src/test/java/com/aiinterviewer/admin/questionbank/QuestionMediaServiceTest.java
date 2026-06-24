package com.aiinterviewer.admin.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.questionbank.dto.QuestionCreateRequest;
import com.aiinterviewer.admin.questionbank.dto.QuestionMediaRequest;
import com.aiinterviewer.admin.questionbank.dto.QuestionUpdateRequest;
import com.aiinterviewer.admin.questionbank.entity.QuestionBankItem;
import com.aiinterviewer.admin.questionbank.entity.QuestionMedia;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QuestionMediaServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private QuestionService questionService;

    @Test
    void createQuestionPersistsAndReturnsMediaInSortOrder() {
        QuestionCreateRequest request = createRequest();
        request.setMedia(List.of(
                new QuestionMediaRequest("image", "https://example.com/figure-a.png", "图 A", "Redis 限流图 A"),
                new QuestionMediaRequest("IMAGE", " https://example.com/figure-b.png ", "图 B", "Redis 限流图 B")));

        Long questionId = questionService.createQuestion(request);

        QuestionBankItem item = questionService.getQuestion(questionId);
        assertThat(item.getMedia()).extracting(QuestionMedia::getMediaUrl)
                .containsExactly("https://example.com/figure-a.png", "https://example.com/figure-b.png");
        assertThat(item.getMedia()).extracting(QuestionMedia::getCaption)
                .containsExactly("图 A", "图 B");
        assertThat(item.getMedia()).extracting(QuestionMedia::getMediaType)
                .containsExactly("image", "image");
        assertThat(item.getVectorSyncStatus()).isEqualTo(QuestionBankItem.VECTOR_SYNC_PENDING);
    }

    @Test
    void updateQuestionCanReplaceMediaAndListHydratesMedia() {
        Long questionId = questionService.createQuestion(createRequest());
        QuestionUpdateRequest update = new QuestionUpdateRequest();
        update.setMedia(List.of(new QuestionMediaRequest("image", "https://example.com/new.png", "新图", "新图说明")));

        questionService.updateQuestion(questionId, update);

        QuestionBankItem detail = questionService.getQuestion(questionId);
        QuestionQuery query = new QuestionQuery();
        PageResult<QuestionBankItem> page = questionService.listQuestions(query);

        assertThat(detail.getMedia()).extracting(QuestionMedia::getMediaUrl)
                .containsExactly("https://example.com/new.png");
        assertThat(page.getRecords().getFirst().getMedia()).extracting(QuestionMedia::getMediaUrl)
                .containsExactly("https://example.com/new.png");
    }

    @Test
    void createQuestionRejectsNonHttpMediaUrl() {
        QuestionCreateRequest request = createRequest();
        request.setMedia(List.of(new QuestionMediaRequest("image", "ftp://example.com/bad.png", "坏图", "坏图")));

        assertThatThrownBy(() -> questionService.createQuestion(request))
                .hasMessageContaining("图片 URL 只支持 http:// 或 https://");
    }

    private QuestionCreateRequest createRequest() {
        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setQuestionText("请结合下图说明两个 Lua 脚本关系。");
        request.setAnswerReference("入口脚本调用底层限流脚本。");
        request.setQuestionType("TECHNICAL");
        request.setDifficulty("MEDIUM");
        request.setSkillArea("Redis");
        request.setStatus(QuestionBankItem.STATUS_PENDING_REVIEW);
        request.setCreatedBy(1L);
        request.setTags(List.of("Redis", "Lua"));
        return request;
    }
}
