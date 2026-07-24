package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.common.model.PageResult;
import com.aiinterviewer.interview.dto.BranchTranscriptDTO;
import com.aiinterviewer.interview.dto.LineageSummaryDTO;
import com.aiinterviewer.interview.entity.InterviewLineage;
import com.aiinterviewer.interview.entity.InterviewMessage;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.mapper.InterviewLineageMapper;
import com.aiinterviewer.interview.mapper.InterviewMessageMapper;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import com.aiinterviewer.interview.projection.LineageSummaryRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterviewHistoryServiceTest {

    @Test
    void keepsAmbiguousLegacyMessagesVisibleButNotForkable() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewMessageMapper messageMapper = mock(InterviewMessageMapper.class);
        InterviewHistoryService service = new InterviewHistoryService(
                sessionMapper,
                messageMapper,
                ownedLineageMapper(42L));

        InterviewSession legacy = session(
                "legacy-session",
                42L,
                "legacy-session",
                null,
                null,
                "原始分支");
        InterviewMessage ambiguousAnswer = message(
                1L,
                "legacy-session",
                1,
                "candidate_answer",
                "旧回答",
                false);
        ambiguousAnswer.setMetadata(Map.of("legacyForkEligible", false));
        when(sessionMapper.selectById("legacy-session")).thenReturn(legacy);
        when(messageMapper.selectBySessionId("legacy-session"))
                .thenReturn(List.of(ambiguousAnswer));

        BranchTranscriptDTO transcript = service.getBranchTranscript("legacy-session", 42L);

        assertThat(transcript.getMessages()).hasSize(1);
        assertThat(transcript.getMessages().get(0).getForkable()).isFalse();
        assertThat(transcript.getMessages().get(0).getContent()).isEqualTo("旧回答");
    }

    @Test
    void composesFocusedBranchTranscriptFromImmutableAncestorPrefixAndBranchDelta() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewMessageMapper messageMapper = mock(InterviewMessageMapper.class);
        InterviewHistoryService service = new InterviewHistoryService(
                sessionMapper,
                messageMapper,
                ownedLineageMapper(42L));

        InterviewSession root = session(
                "root-session",
                42L,
                "lineage-1",
                null,
                null,
                "原始分支");
        InterviewSession child = session(
                "child-session",
                42L,
                "lineage-1",
                "root-session",
                2L,
                "分支 1");

        when(sessionMapper.selectById("child-session")).thenReturn(child);
        when(sessionMapper.selectById("root-session")).thenReturn(root);
        when(messageMapper.selectBySessionId("root-session")).thenReturn(List.of(
                message(1L, "root-session", 1, "ai_question", "问题 1", true),
                message(2L, "root-session", 2, "candidate_answer", "回答 1", false),
                message(3L, "root-session", 3, "ai_question", "不会被子分支继承的问题", true)));
        when(messageMapper.selectBySessionId("child-session")).thenReturn(List.of(
                message(4L, "child-session", 1, "candidate_answer", "分支回答", false),
                message(5L, "child-session", 2, "ai_question", "分支问题", true)));

        BranchTranscriptDTO transcript = service.getBranchTranscript("child-session", 42L);

        assertThat(transcript.getLineageId()).isEqualTo("lineage-1");
        assertThat(transcript.getBranchId()).isEqualTo("child-session");
        assertThat(transcript.getBranchLabel()).isEqualTo("分支 1");
        assertThat(transcript.getMessages())
                .extracting(message -> message.getId())
                .containsExactly(1L, 2L, 4L, 5L);
        assertThat(transcript.getMessages())
                .extracting(message -> message.getOwningBranchId())
                .containsExactly("root-session", "root-session", "child-session", "child-session");
        assertThat(transcript.getMessages())
                .extracting(message -> message.getInherited())
                .containsExactly(true, true, false, false);
        assertThat(transcript.getMessages().get(0).getForkable()).isTrue();
        assertThat(transcript.getMessages().get(1).getForkable()).isTrue();
        assertThat(transcript.getMessages().get(0).getForkPointMessageId()).isEqualTo(1L);
        assertThat(transcript.getMessages().get(1).getForkPointMessageId()).isEqualTo(1L);
    }

    @Test
    void requiresExactPromptStateAndImmediatePromptForForkability() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewMessageMapper messageMapper = mock(InterviewMessageMapper.class);
        InterviewHistoryService service = new InterviewHistoryService(
                sessionMapper,
                messageMapper,
                ownedLineageMapper(42L));

        InterviewSession root = session(
                "root-session",
                42L,
                "lineage-1",
                null,
                null,
                "原始分支");
        InterviewMessage exactPrompt = message(
                1L, "root-session", 1, "ai_question", "可分叉问题", true);
        InterviewMessage exactAnswer = message(
                2L, "root-session", 2, "candidate_answer", "可编辑回答", false);
        InterviewMessage missingStatePrompt = message(
                3L, "root-session", 3, "ai_question", "缺少状态的问题", true);
        missingStatePrompt.setMetadata(Map.of("id", "missing-state"));
        InterviewMessage answerAfterMissingState = message(
                4L, "root-session", 4, "candidate_answer", "不可分叉回答", false);
        InterviewMessage feedback = message(
                5L, "root-session", 5, "ai_feedback", "反馈", false);
        InterviewMessage answerAfterFeedback = message(
                6L, "root-session", 6, "candidate_answer", "前一条不是问题", false);
        InterviewMessage summary = message(
                7L, "root-session", 7, "ai_summary", "总结", false);
        InterviewMessage system = message(
                8L, "root-session", 8, "system", "系统消息", false);
        InterviewMessage nonResponseQuestion = message(
                9L, "root-session", 9, "ai_question", "无需回答的问题", false);
        InterviewMessage answerAfterNonResponseQuestion = message(
                10L, "root-session", 10, "candidate_answer", "不应分叉", false);

        when(sessionMapper.selectById("root-session")).thenReturn(root);
        when(messageMapper.selectBySessionId("root-session")).thenReturn(List.of(
                exactPrompt,
                exactAnswer,
                missingStatePrompt,
                answerAfterMissingState,
                feedback,
                answerAfterFeedback,
                summary,
                system,
                nonResponseQuestion,
                answerAfterNonResponseQuestion));

        BranchTranscriptDTO transcript = service.getBranchTranscript("root-session", 42L);

        assertThat(transcript.getMessages())
                .extracting(message -> message.getForkable())
                .containsExactly(
                        true, true, false, false, false, false,
                        false, false, false, false);
        assertThat(transcript.getMessages())
                .extracting(message -> message.getForkPointMessageId())
                .containsExactly(
                        1L, 1L, null, null, null, null,
                        null, null, null, null);
    }

    @Test
    void nestedForkCanAttachToAnswerOwnerWhileCuttingAtAncestorOwnedPrompt() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewMessageMapper messageMapper = mock(InterviewMessageMapper.class);
        InterviewHistoryService service = new InterviewHistoryService(
                sessionMapper,
                messageMapper,
                ownedLineageMapper(42L));

        InterviewSession root = session(
                "root", 42L, "lineage-1", null, null, "原始分支");
        InterviewSession firstChild = session(
                "first-child", 42L, "lineage-1", "root", 3L, "分支 1");
        InterviewSession nested = session(
                "nested", 42L, "lineage-1", "first-child", 3L, "分支 2");

        when(sessionMapper.selectById("nested")).thenReturn(nested);
        when(sessionMapper.selectById("first-child")).thenReturn(firstChild);
        when(sessionMapper.selectById("root")).thenReturn(root);
        when(messageMapper.selectBySessionId("root")).thenReturn(List.of(
                message(1L, "root", 1, "ai_question", "根问题一", true),
                message(2L, "root", 2, "candidate_answer", "根回答一", false),
                message(3L, "root", 3, "ai_question", "祖先 Fork Point", true),
                message(4L, "root", 4, "candidate_answer", "源分支后续回答", false)));
        when(messageMapper.selectBySessionId("first-child")).thenReturn(List.of(
                message(5L, "first-child", 1, "candidate_answer", "被选择的子分支回答", false),
                message(6L, "first-child", 2, "ai_question", "不应继承的子分支后续", true)));
        when(messageMapper.selectBySessionId("nested")).thenReturn(List.of(
                message(7L, "nested", 1, "candidate_answer", "嵌套分支新回答", false),
                message(8L, "nested", 2, "ai_question", "嵌套分支新问题", true)));

        BranchTranscriptDTO transcript = service.getBranchTranscript("nested", 42L);

        assertThat(transcript.getMessages())
                .extracting(message -> message.getId())
                .containsExactly(1L, 2L, 3L, 7L, 8L);
        assertThat(transcript.getMessages())
                .extracting(message -> message.getOwningBranchId())
                .containsExactly("root", "root", "root", "nested", "nested");
        assertThat(transcript.getMessages())
                .extracting(message -> message.getInherited())
                .containsExactly(true, true, true, false, false);
    }

    @Test
    void returnsPagedLineageSummariesWithBestScoreAndLatestActiveProgress() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewMessageMapper messageMapper = mock(InterviewMessageMapper.class);
        InterviewLineageMapper lineageMapper = mock(InterviewLineageMapper.class);
        InterviewHistoryService service = new InterviewHistoryService(
                sessionMapper,
                messageMapper,
                lineageMapper);

        LineageSummaryRow row = new LineageSummaryRow();
        row.setLineageId("lineage-1");
        row.setRootSessionId("root-session");
        row.setCandidateName("测试候选人");
        row.setJobId(10L);
        row.setJobTitle("Java 后端工程师");
        row.setBranchCount(3L);
        row.setActiveBranchCount(1L);
        row.setCompletedBranchCount(2L);
        row.setBestCompletedScore(88);
        row.setLatestActivityAt(LocalDateTime.of(2026, 7, 23, 20, 0));
        row.setFocusedBranchId("active-branch");
        row.setFocusedBranchStage("project_qna");
        row.setFocusedBranchStatus(1);
        row.setProjectQuestionsCount(2);
        row.setTargetProjectQuestions(5);

        when(lineageMapper.selectSummaryPage(42L, "Java", "score", "active", 10L, 0L))
                .thenReturn(List.of(row));
        when(lineageMapper.countSummaries(42L, "Java", "active")).thenReturn(1L);

        PageResult<LineageSummaryDTO> result = service.listLineages(
                42L,
                1L,
                10L,
                "Java",
                "score",
                "ACTIVE");

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        LineageSummaryDTO summary = result.getRecords().get(0);
        assertThat(summary.getLineageId()).isEqualTo("lineage-1");
        assertThat(summary.getBestCompletedScore()).isEqualTo(88);
        assertThat(summary.getFocusedBranchId()).isEqualTo("active-branch");
        assertThat(summary.getFocusedBranchProgress()).isEqualTo(36);
        assertThat(summary.getBranchCount()).isEqualTo(3L);
        verify(lineageMapper).selectSummaryPage(42L, "Java", "score", "active", 10L, 0L);
    }

    private static InterviewSession session(
            String id,
            Long userId,
            String lineageId,
            String parentSessionId,
            Long forkPointMessageId,
            String branchLabel) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setUserId(userId);
        session.setLineageId(lineageId);
        session.setParentSessionId(parentSessionId);
        session.setForkPointMessageId(forkPointMessageId);
        session.setBranchLabel(branchLabel);
        session.setStage("project_qna");
        session.setStatus(1);
        session.setBranchVersion(1L);
        return session;
    }

    private static InterviewLineageMapper ownedLineageMapper(Long userId) {
        InterviewLineageMapper mapper = mock(InterviewLineageMapper.class);
        when(mapper.selectById(any())).thenAnswer(invocation -> {
            InterviewLineage lineage = new InterviewLineage();
            lineage.setId(invocation.getArgument(0).toString());
            lineage.setUserId(userId);
            return lineage;
        });
        return mapper;
    }

    private static InterviewMessage message(
            Long id,
            String sessionId,
            int sequence,
            String messageType,
            String content,
            boolean expectsResponse) {
        InterviewMessage message = new InterviewMessage();
        message.setId(id);
        message.setSessionId(sessionId);
        message.setSequence(sequence);
        message.setRole(messageType.startsWith("ai_") ? "ai" : "human");
        message.setMessageType(messageType);
        message.setContent(content);
        message.setStage("project_qna");
        message.setExpectsResponse(expectsResponse);
        message.setDeliveryStatus("completed");
        if ("ai_question".equals(messageType)) {
            message.setMetadata(Map.of(
                    "id", "question-" + id,
                    "_postTurnStateV1", Map.of(
                            "schemaVersion", 1,
                            "currentStage", "project_qna",
                            "branchStatus", 1,
                            "projectQuestionsCount", 1,
                            "targetProjectQuestions", 5,
                            "currentFollowupCount", 0,
                            "projectQuestionsPool", List.of("下一道项目题"),
                            "technicalQuestionsPool", List.of())));
        }
        message.setCreatedAt(LocalDateTime.of(2026, 7, 17, 11, sequence, 0));
        return message;
    }
}
