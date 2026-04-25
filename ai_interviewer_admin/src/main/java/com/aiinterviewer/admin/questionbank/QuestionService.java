package com.aiinterviewer.admin.questionbank;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.questionbank.dto.QuestionCreateRequest;
import com.aiinterviewer.admin.questionbank.dto.QuestionUpdateRequest;
import com.aiinterviewer.admin.questionbank.entity.QuestionBankItem;
import com.aiinterviewer.admin.questionbank.entity.QuestionTag;
import com.aiinterviewer.admin.questionbank.mapper.QuestionMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private static final String DEFAULT_VECTOR_SYNC_STATUS = "PENDING";
    private static final String DEFAULT_SOURCE_TYPE = "MANUAL";
    private static final String DEFAULT_TAG_TYPE = "GENERAL";

    private final QuestionMapper questionMapper;

    public PageResult<QuestionBankItem> listQuestions(QuestionQuery query) {
        QuestionQuery safeQuery = query == null ? new QuestionQuery() : query;
        long current = safeQuery.normalizedCurrent();
        long size = safeQuery.normalizedSize();
        Long total = questionMapper.countQuestions(safeQuery);
        List<QuestionBankItem> records = questionMapper.selectQuestions(safeQuery, size, safeOffset(current, size));
        hydrateTags(records);
        return PageResult.of(current, size, total == null ? 0L : total, records);
    }

    public QuestionBankItem getQuestion(Long questionId) {
        ensureQuestionId(questionId);
        QuestionBankItem question = questionMapper.selectQuestionById(questionId);
        if (question == null) {
            throw new AdminBusinessException(404, "题目不存在");
        }
        hydrateTags(List.of(question));
        return question;
    }

    @Transactional
    @AdminAudit(module = "QUESTION_BANK", operation = "CREATE", targetType = "QUESTION", targetIdFromResult = true)
    public Long createQuestion(QuestionCreateRequest request) {
        validateCreateRequest(request);
        QuestionBankItem item = new QuestionBankItem();
        item.setQuestionCode(generateQuestionCode());
        item.setQuestionText(request.getQuestionText().trim());
        item.setAnswerReference(trimToNull(request.getAnswerReference()));
        item.setQuestionType(request.getQuestionType().trim());
        item.setDifficulty(request.getDifficulty().trim());
        item.setSkillArea(trimToNull(request.getSkillArea()));
        item.setJobId(request.getJobId());
        item.setStatus(request.getStatus());
        item.setVectorSyncStatus(DEFAULT_VECTOR_SYNC_STATUS);
        item.setSourceType(DEFAULT_SOURCE_TYPE);
        item.setCreatedBy(request.getCreatedBy());
        int inserted = questionMapper.insertQuestion(item);
        if (inserted == 0 || item.getId() == null) {
            throw new AdminBusinessException(500, "题目创建失败");
        }
        replaceTags(item.getId(), request.getTags());
        return item.getId();
    }

    @Transactional
    @AdminAudit(module = "QUESTION_BANK", operation = "UPDATE", targetType = "QUESTION", targetIdParam = "questionId")
    public void updateQuestion(Long questionId, QuestionUpdateRequest request) {
        ensureQuestionExists(questionId);
        validateUpdateRequest(request);
        QuestionBankItem item = new QuestionBankItem();
        item.setId(questionId);
        item.setQuestionText(request.getQuestionText().trim());
        item.setAnswerReference(trimToNull(request.getAnswerReference()));
        item.setQuestionType(request.getQuestionType().trim());
        item.setDifficulty(request.getDifficulty().trim());
        item.setSkillArea(trimToNull(request.getSkillArea()));
        item.setJobId(request.getJobId());
        item.setStatus(request.getStatus());
        item.setUpdatedBy(request.getUpdatedBy());
        int updated = questionMapper.updateQuestion(item);
        if (updated == 0) {
            throw new AdminBusinessException(500, "题目更新失败");
        }
        replaceTags(questionId, request.getTags());
    }

    @Transactional
    @AdminAudit(module = "QUESTION_BANK", operation = "DELETE", targetType = "QUESTION", targetIdParam = "questionId")
    public void deleteQuestion(Long questionId) {
        ensureQuestionExists(questionId);
        int updated = questionMapper.softDeleteQuestion(questionId);
        if (updated == 0) {
            throw new AdminBusinessException(500, "题目删除失败");
        }
    }

    private void validateCreateRequest(QuestionCreateRequest request) {
        if (request == null) {
            throw new AdminBusinessException(400, "题目参数不能为空");
        }
        validateRequiredFields(request.getQuestionText(), request.getQuestionType(), request.getDifficulty(), request.getStatus());
    }

    private void validateUpdateRequest(QuestionUpdateRequest request) {
        if (request == null) {
            throw new AdminBusinessException(400, "题目参数不能为空");
        }
        validateRequiredFields(request.getQuestionText(), request.getQuestionType(), request.getDifficulty(), request.getStatus());
    }

    private void validateRequiredFields(String questionText, String questionType, String difficulty, Integer status) {
        if (!StringUtils.hasText(questionText)) {
            throw new AdminBusinessException(400, "题目内容不能为空");
        }
        if (!StringUtils.hasText(questionType)) {
            throw new AdminBusinessException(400, "题目类型不能为空");
        }
        if (!StringUtils.hasText(difficulty)) {
            throw new AdminBusinessException(400, "难度不能为空");
        }
        if (status == null) {
            throw new AdminBusinessException(400, "状态不能为空");
        }
        if (status != 0 && status != 1) {
            throw new AdminBusinessException(400, "状态不合法");
        }
    }

    private void ensureQuestionId(Long questionId) {
        if (questionId == null) {
            throw new AdminBusinessException(400, "题目ID不能为空");
        }
    }

    private void ensureQuestionExists(Long questionId) {
        ensureQuestionId(questionId);
        Integer count = questionMapper.countExistingQuestion(questionId);
        if (count == null || count == 0) {
            throw new AdminBusinessException(404, "题目不存在");
        }
    }

    private void replaceTags(Long questionId, List<String> rawTags) {
        questionMapper.deleteQuestionTags(questionId);
        for (String tagName : normalizeTags(rawTags)) {
            QuestionTag tag = findOrCreateTag(tagName);
            questionMapper.insertQuestionTag(questionId, tag.getId());
        }
    }

    private QuestionTag findOrCreateTag(String tagName) {
        QuestionTag existing = questionMapper.selectTagByName(tagName);
        if (existing != null) {
            return existing;
        }
        QuestionTag tag = new QuestionTag();
        tag.setTagCode(generateTagCode(tagName));
        tag.setTagName(tagName);
        tag.setTagType(DEFAULT_TAG_TYPE);
        int inserted = questionMapper.insertTag(tag);
        if (inserted == 0 || tag.getId() == null) {
            throw new AdminBusinessException(500, "标签创建失败");
        }
        return tag;
    }

    private List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String tag : rawTags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            String trimmed = tag.trim();
            normalized.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return new ArrayList<>(normalized.values());
    }

    private void hydrateTags(List<QuestionBankItem> questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        List<Long> questionIds = questions.stream()
                .map(QuestionBankItem::getId)
                .toList();
        Map<Long, List<String>> tagsByQuestionId = new LinkedHashMap<>();
        for (QuestionTagNameRow row : questionMapper.selectTagNamesByQuestionIds(questionIds)) {
            tagsByQuestionId.computeIfAbsent(row.getQuestionId(), ignored -> new ArrayList<>()).add(row.getTagName());
        }
        questions.forEach(question -> question.setTags(tagsByQuestionId.getOrDefault(question.getId(), List.of())));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String generateQuestionCode() {
        return "QB-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateTagCode(String tagName) {
        String normalized = tagName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("(^-+|-+$)", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = "tag";
        }
        return "TAG-" + normalized + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private long safeOffset(long current, long size) {
        try {
            return Math.multiplyExact(current - 1, size);
        } catch (ArithmeticException ex) {
            return 0L;
        }
    }

    @Data
    public static class QuestionTagNameRow {

        private Long questionId;
        private String tagName;
    }
}
