package com.aiinterviewer.admin.questionbank;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.questionbank.dto.QuestionCreateRequest;
import com.aiinterviewer.admin.questionbank.dto.QuestionMediaRequest;
import com.aiinterviewer.admin.questionbank.dto.QuestionUpdateRequest;
import com.aiinterviewer.admin.questionbank.entity.QuestionBankItem;
import com.aiinterviewer.admin.questionbank.entity.QuestionMedia;
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
    private static final String IMPORT_SOURCE_TYPE = "IMPORT";
    private static final String DEFAULT_TAG_TYPE = "GENERAL";

    private final QuestionMapper questionMapper;

    public PageResult<QuestionBankItem> listQuestions(QuestionQuery query) {
        QuestionQuery safeQuery = query == null ? new QuestionQuery() : query;
        long current = safeQuery.normalizedCurrent();
        long size = safeQuery.normalizedSize();
        Long total = questionMapper.countQuestions(safeQuery);
        List<QuestionBankItem> records = questionMapper.selectQuestions(safeQuery, size, safeOffset(current, size));
        hydrateTags(records);
        hydrateMedia(records);
        return PageResult.of(current, size, total == null ? 0L : total, records);
    }

    public QuestionBankItem getQuestion(Long questionId) {
        ensureQuestionId(questionId);
        QuestionBankItem question = questionMapper.selectQuestionById(questionId);
        if (question == null) {
            throw new AdminBusinessException(404, "题目不存在");
        }
        hydrateTags(List.of(question));
        hydrateMedia(List.of(question));
        return question;
    }

    @Transactional
    @AdminAudit(module = "QUESTION_BANK", operation = "CREATE", targetType = "QUESTION", targetIdFromResult = true)
    public Long createQuestion(QuestionCreateRequest request) {
        return createQuestion(request, DEFAULT_SOURCE_TYPE, null);
    }

    Long createImportedQuestion(QuestionCreateRequest request, Long sourceBatchId) {
        return createQuestion(request, IMPORT_SOURCE_TYPE, sourceBatchId);
    }

    private Long createQuestion(QuestionCreateRequest request, String sourceType, Long sourceBatchId) {
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
        item.setSourceType(sourceType);
        item.setSourceBatchId(sourceBatchId);
        item.setCreatedBy(request.getCreatedBy());
        int inserted = questionMapper.insertQuestion(item);
        if (inserted == 0 || item.getId() == null) {
            throw new AdminBusinessException(500, "题目创建失败");
        }
        replaceTags(item.getId(), request.getTags());
        replaceMedia(item.getId(), request.getMedia(), request.getCreatedBy());
        return item.getId();
    }

    @Transactional
    @AdminAudit(module = "QUESTION_BANK", operation = "UPDATE", targetType = "QUESTION", targetIdParam = "questionId")
    public void updateQuestion(Long questionId, QuestionUpdateRequest request) {
        ensureQuestionExists(questionId);
        validateUpdateRequest(request);
        QuestionBankItem item = new QuestionBankItem();
        item.setId(questionId);
        if (request.isAnswerReferenceSet()) {
            item.setAnswerReference(trimToNull(request.getAnswerReference()));
        }
        if (request.getDifficulty() != null) {
            item.setDifficulty(request.getDifficulty().trim());
        }
        item.setStatus(request.getStatus());
        item.setUpdatedBy(request.getUpdatedBy());
        item.setVectorSyncStatus(vectorStatusForUpdate(request.getStatus()));
        int updated = questionMapper.updateQuestion(
                item,
                request.isAnswerReferenceSet(),
                request.getDifficulty() != null,
                request.getStatus() != null);
        if (updated == 0) {
            throw new AdminBusinessException(500, "题目更新失败");
        }
        if (request.isTagsSet() && request.getTags() != null) {
            replaceTags(questionId, request.getTags());
        }
        if (request.isMediaSet()) {
            replaceMedia(questionId, request.getMedia(), request.getUpdatedBy());
        }
    }

    @Transactional
    @AdminAudit(module = "QUESTION_BANK", operation = "APPROVE", targetType = "QUESTION", targetIdParam = "questionId")
    public void approveQuestion(Long questionId, Long updatedBy) {
        updateQuestionStatus(questionId, QuestionBankItem.STATUS_ENABLED, updatedBy);
    }

    @Transactional
    @AdminAudit(module = "QUESTION_BANK", operation = "REJECT", targetType = "QUESTION", targetIdParam = "questionId")
    public void rejectQuestion(Long questionId, Long updatedBy) {
        updateQuestionStatus(questionId, QuestionBankItem.STATUS_REJECTED, updatedBy);
    }

    @Transactional
    @AdminAudit(module = "QUESTION_BANK", operation = "PUBLISH", targetType = "QUESTION", targetIdParam = "questionId")
    public void publishQuestion(Long questionId, Long updatedBy) {
        updateQuestionStatus(questionId, QuestionBankItem.STATUS_ENABLED, updatedBy);
    }

    @Transactional
    @AdminAudit(module = "QUESTION_BANK", operation = "UNPUBLISH", targetType = "QUESTION", targetIdParam = "questionId")
    public void unpublishQuestion(Long questionId, Long updatedBy) {
        updateQuestionStatus(questionId, QuestionBankItem.STATUS_DISABLED, updatedBy);
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
        if (request.getDifficulty() != null && !StringUtils.hasText(request.getDifficulty())) {
            throw new AdminBusinessException(400, "难度不能为空");
        }
        if (request.getStatus() != null && !isValidStatus(request.getStatus())) {
            throw new AdminBusinessException(400, "状态不合法");
        }
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
        if (!isValidStatus(status)) {
            throw new AdminBusinessException(400, "状态不合法");
        }
    }

    private boolean isValidStatus(Integer status) {
        return status != null
                && (status == QuestionBankItem.STATUS_DISABLED
                || status == QuestionBankItem.STATUS_ENABLED
                || status == QuestionBankItem.STATUS_PENDING_REVIEW
                || status == QuestionBankItem.STATUS_REJECTED);
    }

    private void updateQuestionStatus(Long questionId, Integer status, Long updatedBy) {
        ensureQuestionExists(questionId);
        QuestionUpdateRequest request = new QuestionUpdateRequest();
        request.setStatus(status);
        request.setUpdatedBy(updatedBy);
        updateQuestion(questionId, request);
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

    private void replaceMedia(Long questionId, List<QuestionMediaRequest> rawMedia, Long createdBy) {
        questionMapper.deleteQuestionMedia(questionId);
        List<QuestionMediaRequest> normalized = normalizeMedia(rawMedia);
        for (int index = 0; index < normalized.size(); index++) {
            QuestionMediaRequest request = normalized.get(index);
            QuestionMedia media = new QuestionMedia();
            media.setQuestionId(questionId);
            media.setMediaType(normalizeMediaType(request.type()));
            media.setMediaUrl(request.url().trim());
            media.setCaption(trimToNull(request.caption()));
            media.setAltText(trimToNull(request.alt()));
            media.setSortOrder(index);
            media.setCreatedBy(createdBy);
            questionMapper.insertQuestionMedia(media);
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

    private List<QuestionMediaRequest> normalizeMedia(List<QuestionMediaRequest> rawMedia) {
        if (rawMedia == null || rawMedia.isEmpty()) {
            return List.of();
        }
        List<QuestionMediaRequest> normalized = new ArrayList<>();
        for (QuestionMediaRequest media : rawMedia) {
            if (media == null || !StringUtils.hasText(media.url())) {
                continue;
            }
            String url = media.url().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new AdminBusinessException(400, "图片 URL 只支持 http:// 或 https://");
            }
            normalized.add(media);
        }
        return normalized;
    }

    private String normalizeMediaType(String mediaType) {
        return StringUtils.hasText(mediaType) ? mediaType.trim().toLowerCase(Locale.ROOT) : "image";
    }

    private String vectorStatusForUpdate(Integer status) {
        return status == null || status == QuestionBankItem.STATUS_ENABLED
                ? QuestionBankItem.VECTOR_SYNC_PENDING
                : QuestionBankItem.VECTOR_SYNC_DELETE_PENDING;
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

    private void hydrateMedia(List<QuestionBankItem> questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        List<Long> questionIds = questions.stream()
                .map(QuestionBankItem::getId)
                .toList();
        Map<Long, List<QuestionMedia>> mediaByQuestionId = new LinkedHashMap<>();
        for (QuestionMedia media : questionMapper.selectMediaByQuestionIds(questionIds)) {
            mediaByQuestionId.computeIfAbsent(media.getQuestionId(), ignored -> new ArrayList<>()).add(media);
        }
        questions.forEach(question -> question.setMedia(mediaByQuestionId.getOrDefault(question.getId(), List.of())));
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
