package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiinterviewer.interview.dto.BranchMessageDTO;
import com.aiinterviewer.interview.dto.BranchTranscriptDTO;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.entity.InterviewTurnAttempt;
import com.aiinterviewer.interview.entity.ScoreRecord;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import com.aiinterviewer.interview.mapper.ScoreRecordMapper;
import com.aiinterviewer.interview.model.BranchSnapshot;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BranchSnapshotComposerTest {

    @Test
    void composesCanonicalAncestryMetadataPoolsCountersAndLinkedAssessments() {
        InterviewHistoryService history = mock(InterviewHistoryService.class);
        InterviewSessionMapper sessions = mock(InterviewSessionMapper.class);
        ScoreRecordMapper scores = mock(ScoreRecordMapper.class);
        TurnAttemptRepository attempts = mock(TurnAttemptRepository.class);
        BranchSnapshotComposer composer = new BranchSnapshotComposer(
                history, sessions, scores, attempts);
        InterviewTurnAttempt attempt = attempt(7L, 20L);
        InterviewSession branch = branch(42L, 7L);
        branch.setProjectQuestionsCount(2);
        branch.setTargetProjectQuestions(5);
        branch.setCurrentFollowupCount(1);
        branch.setProjectQuestionsPool(List.of("下一个项目问题"));
        branch.setTechnicalQuestionsPool(List.of("旧字符串技术题"));
        BranchTranscriptDTO transcript = transcript(List.of(
                message(10L, "root", "ai", "根问题", "ai_question", "completed", 1),
                message(11L, "root", "human", "根回答", "candidate_answer", "completed", 2),
                message(19L, "child", "ai", "失败片段", "ai_feedback", "failed", 3),
                structuredMessage(20L, "child", 4)));
        ScoreRecord inherited = score(100L, "root", 10L, 11L, "project_qna", 88);
        ScoreRecord beyondTail = score(101L, "child", 20L, 21L, "technical_qna", 99);

        when(sessions.selectById("child")).thenReturn(branch);
        when(attempts.findTailMessageId("child")).thenReturn(20L);
        when(history.getBranchTranscript("child", 42L)).thenReturn(transcript);
        when(scores.selectBySessionId("root")).thenReturn(List.of(inherited));
        when(scores.selectBySessionId("child")).thenReturn(List.of(beyondTail));

        BranchSnapshot snapshot = composer.compose(attempt, 42L, "alice");

        assertThat(snapshot.schemaVersion()).isEqualTo(1);
        assertThat(snapshot.turnId()).isEqualTo("turn-snapshot");
        assertThat(snapshot.branchId()).isEqualTo("child");
        assertThat(snapshot.lineageId()).isEqualTo("lineage-1");
        assertThat(snapshot.ownerUserId()).isEqualTo(42L);
        assertThat(snapshot.username()).isEqualTo("alice");
        assertThat(snapshot.projectQuestionsCount()).isEqualTo(2);
        assertThat(snapshot.targetProjectQuestions()).isEqualTo(5);
        assertThat(snapshot.currentFollowupCount()).isEqualTo(1);
        assertThat(snapshot.projectQuestionsPool()).containsExactly("下一个项目问题");
        assertThat(snapshot.technicalQuestionsPool()).containsExactly("旧字符串技术题");
        assertThat(snapshot.messages())
                .extracting(message -> message.id())
                .containsExactly(10L, 11L, 20L);
        assertThat(snapshot.messages())
                .extracting(message -> message.pathOrder())
                .containsExactly(1, 2, 3);
        assertThat(snapshot.messages().getLast().metadata())
                .containsEntry("id", "tech-current")
                .containsEntry("question_type", "MULTIPLE_CHOICE");
        assertThat(snapshot.assessments()).hasSize(1);
        assertThat(snapshot.assessments().getFirst().questionMessageId()).isEqualTo(10L);
        assertThat(snapshot.assessments().getFirst().answerMessageId()).isEqualTo(11L);
        assertThat(snapshot.messages())
                .extracting(message -> message.content())
                .doesNotContain("submitted candidate answer", "失败片段");
    }

    @Test
    void versionAndTailDriftStopCompositionBeforeTranscriptRead() {
        InterviewHistoryService history = mock(InterviewHistoryService.class);
        InterviewSessionMapper sessions = mock(InterviewSessionMapper.class);
        ScoreRecordMapper scores = mock(ScoreRecordMapper.class);
        TurnAttemptRepository attempts = mock(TurnAttemptRepository.class);
        BranchSnapshotComposer composer = new BranchSnapshotComposer(
                history, sessions, scores, attempts);
        InterviewTurnAttempt attempt = attempt(7L, 20L);
        InterviewSession branch = branch(42L, 8L);
        when(sessions.selectById("child")).thenReturn(branch);

        assertThatThrownBy(() -> composer.compose(attempt, 42L, null))
                .isInstanceOf(TurnCommitRejectedException.class)
                .hasMessage("BRANCH_VERSION_CONFLICT");
        verifyNoInteractions(history);

        branch.setBranchVersion(7L);
        when(attempts.findTailMessageId("child")).thenReturn(21L);
        assertThatThrownBy(() -> composer.compose(attempt, 42L, null))
                .isInstanceOf(TurnCommitRejectedException.class)
                .hasMessage("BRANCH_TAIL_CONFLICT");
        verifyNoInteractions(history);
    }

    @Test
    void ownershipAndBranchStatusAreRecheckedBeforeComposition() {
        InterviewHistoryService history = mock(InterviewHistoryService.class);
        InterviewSessionMapper sessions = mock(InterviewSessionMapper.class);
        BranchSnapshotComposer composer = new BranchSnapshotComposer(
                history,
                sessions,
                mock(ScoreRecordMapper.class),
                mock(TurnAttemptRepository.class));
        InterviewTurnAttempt attempt = attempt(7L, 20L);
        InterviewSession foreign = branch(99L, 7L);
        when(sessions.selectById("child")).thenReturn(foreign);

        assertThatThrownBy(() -> composer.compose(attempt, 42L, null))
                .isInstanceOf(TurnCommitRejectedException.class)
                .hasMessage("OWNERSHIP_CHANGED");

        foreign.setUserId(42L);
        foreign.setStatus(3);
        assertThatThrownBy(() -> composer.compose(attempt, 42L, null))
                .isInstanceOf(TurnCommitRejectedException.class)
                .hasMessage("BRANCH_NOT_ACTIVE");
        verifyNoInteractions(history);
    }

    @Test
    void versionAndTailAreRecheckedAfterTranscriptComposition() {
        InterviewHistoryService versionHistory = mock(InterviewHistoryService.class);
        InterviewSessionMapper versionSessions = mock(InterviewSessionMapper.class);
        TurnAttemptRepository versionAttempts = mock(TurnAttemptRepository.class);
        BranchSnapshotComposer versionComposer = new BranchSnapshotComposer(
                versionHistory,
                versionSessions,
                mock(ScoreRecordMapper.class),
                versionAttempts);
        InterviewTurnAttempt attempt = attempt(7L, 20L);
        InterviewSession stable = branch(42L, 7L);
        InterviewSession drifted = branch(42L, 8L);
        when(versionSessions.selectById("child")).thenReturn(stable, drifted);
        when(versionAttempts.findTailMessageId("child")).thenReturn(20L);
        when(versionHistory.getBranchTranscript("child", 42L)).thenReturn(transcript(List.of(
                message(20L, "child", "ai", "当前问题", "ai_question", "completed", 1))));

        assertThatThrownBy(() -> versionComposer.compose(attempt, 42L, null))
                .isInstanceOf(TurnCommitRejectedException.class)
                .hasMessage("BRANCH_VERSION_CONFLICT");

        InterviewHistoryService tailHistory = mock(InterviewHistoryService.class);
        InterviewSessionMapper tailSessions = mock(InterviewSessionMapper.class);
        TurnAttemptRepository tailAttempts = mock(TurnAttemptRepository.class);
        BranchSnapshotComposer tailComposer = new BranchSnapshotComposer(
                tailHistory,
                tailSessions,
                mock(ScoreRecordMapper.class),
                tailAttempts);
        when(tailSessions.selectById("child")).thenReturn(stable);
        when(tailAttempts.findTailMessageId("child")).thenReturn(20L, 21L);
        when(tailHistory.getBranchTranscript("child", 42L)).thenReturn(transcript(List.of(
                message(20L, "child", "ai", "当前问题", "ai_question", "completed", 1))));

        assertThatThrownBy(() -> tailComposer.compose(attempt, 42L, null))
                .isInstanceOf(TurnCommitRejectedException.class)
                .hasMessage("BRANCH_TAIL_CONFLICT");
    }

    private static InterviewTurnAttempt attempt(Long version, Long tail) {
        InterviewTurnAttempt attempt = new InterviewTurnAttempt();
        attempt.setId("turn-snapshot");
        attempt.setSessionId("child");
        attempt.setLineageId("lineage-1");
        attempt.setExpectedBranchVersion(version);
        attempt.setExpectedTailMessageId(tail);
        attempt.setCandidateAnswer("submitted candidate answer");
        return attempt;
    }

    private static InterviewSession branch(Long userId, Long version) {
        InterviewSession branch = new InterviewSession();
        branch.setId("child");
        branch.setUserId(userId);
        branch.setLineageId("lineage-1");
        branch.setBranchVersion(version);
        branch.setStage("technical_qna");
        branch.setStatus(1);
        branch.setCandidateName("Candidate");
        branch.setResumeContent("Resume");
        branch.setJobRequirements("Job");
        return branch;
    }

    private static BranchTranscriptDTO transcript(List<BranchMessageDTO> messages) {
        BranchTranscriptDTO transcript = new BranchTranscriptDTO();
        transcript.setBranchId("child");
        transcript.setLineageId("lineage-1");
        transcript.setBranchVersion(7L);
        transcript.setStage("technical_qna");
        transcript.setStatus(1);
        transcript.setMessages(messages);
        return transcript;
    }

    private static BranchMessageDTO message(
            Long id,
            String owningBranch,
            String role,
            String content,
            String type,
            String delivery,
            int sequence) {
        BranchMessageDTO message = new BranchMessageDTO();
        message.setId(id);
        message.setOwningBranchId(owningBranch);
        message.setRole(role);
        message.setContent(content);
        message.setStage("technical_qna");
        message.setMessageType(type);
        message.setDeliveryStatus(delivery);
        message.setExpectsResponse("ai_question".equals(type));
        message.setMetadata(Map.of());
        message.setSequence(sequence);
        return message;
    }

    private static BranchMessageDTO structuredMessage(Long id, String owningBranch, int sequence) {
        BranchMessageDTO message = message(
                id,
                owningBranch,
                "ai",
                "选择线程安全集合",
                "ai_question",
                "completed",
                sequence);
        message.setMetadata(Map.of(
                "id", "tech-current",
                "text", "选择线程安全集合",
                "question_type", "MULTIPLE_CHOICE",
                "options", List.of("HashMap", "ConcurrentHashMap")));
        return message;
    }

    private static ScoreRecord score(
            Long id,
            String branchId,
            Long questionId,
            Long answerId,
            String type,
            int value) {
        ScoreRecord score = new ScoreRecord();
        score.setId(id);
        score.setSessionId(branchId);
        score.setTurnId("turn-" + id);
        score.setQuestionMessageId(questionId);
        score.setAnswerMessageId(answerId);
        score.setQuestionType(type);
        score.setQuestion("question-" + id);
        score.setAnswer("answer-" + id);
        score.setScore(value);
        score.setFeedback("feedback-" + id);
        score.setIsFollowup(false);
        return score;
    }
}
