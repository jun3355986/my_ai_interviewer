package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.interview.dto.BranchMessageDTO;
import com.aiinterviewer.interview.dto.BranchTranscriptDTO;
import com.aiinterviewer.interview.dto.CreateForkAttemptRequest;
import com.aiinterviewer.interview.dto.CreateTurnAttemptRequest;
import com.aiinterviewer.interview.dto.ForkAttemptDTO;
import com.aiinterviewer.interview.dto.TurnAttemptDTO;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import com.aiinterviewer.interview.repository.ForkBranchRepository;
import com.aiinterviewer.interview.repository.ForkBranchRepository.MessageContext;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import com.aiinterviewer.interview.repository.TurnAttemptRepository.BranchState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ForkAttemptServiceTest {

    private InterviewHistoryService historyService;
    private InterviewSessionMapper sessionMapper;
    private TurnAttemptRepository attemptRepository;
    private ForkBranchRepository branchRepository;
    private TurnAttemptService turnAttemptService;
    private ForkAttemptService service;

    @BeforeEach
    void setUp() {
        historyService = mock(InterviewHistoryService.class);
        sessionMapper = mock(InterviewSessionMapper.class);
        attemptRepository = mock(TurnAttemptRepository.class);
        branchRepository = mock(ForkBranchRepository.class);
        turnAttemptService = mock(TurnAttemptService.class);
        service = new ForkAttemptService(
                historyService,
                sessionMapper,
                attemptRepository,
                branchRepository,
                turnAttemptService);

        when(attemptRepository.findById(any())).thenReturn(Optional.empty());
        BranchState focusedState = new BranchState(
                        "focused",
                        "lineage-1",
                        42L,
                        "project_qna",
                        1,
                        3L,
                        null,
                        "候选人",
                        "简历",
                        "岗位",
                        LocalDateTime.of(2026, 7, 24, 9, 0));
        when(attemptRepository.findBranch("focused")).thenReturn(Optional.of(focusedState));
        when(attemptRepository.lockBranch("focused")).thenReturn(Optional.of(focusedState));
        when(attemptRepository.findTailMessageId("focused")).thenReturn(99L);
        when(attemptRepository.findProcessingByLineage("lineage-1")).thenReturn(Optional.empty());
        when(branchRepository.lockOwnedLineage("lineage-1", 42L)).thenReturn(true);
        when(branchRepository.nextBranchNumber("lineage-1")).thenReturn(3);
        when(branchRepository.insertChild(any())).thenReturn(true);

        InterviewSession owner = new InterviewSession();
        owner.setId("focused");
        owner.setUserId(42L);
        owner.setResumeId(7L);
        owner.setJobId(8L);
        owner.setCandidateName("候选人");
        owner.setResumeContent("简历");
        owner.setJobRequirements("岗位");
        owner.setLineageId("lineage-1");
        when(sessionMapper.selectById("focused")).thenReturn(owner);

        when(turnAttemptService.create(any(), eq(42L), nullable(String.class), any()))
                .thenAnswer(invocation -> {
                    TurnAttemptDTO dto = new TurnAttemptDTO();
                    dto.setTurnId(((CreateTurnAttemptRequest) invocation.getArgument(3)).getTurnId());
                    dto.setBranchId(invocation.getArgument(0));
                    dto.setStatus("PROCESSING");
                    return dto;
                });
    }

    @Test
    void candidateAnswerForkUsesEditedAnswerPrecedingPromptStateAndTriggerOwningBranch() {
        BranchMessageDTO prompt = message(10L, "root", "ai_question", true, true, 10L);
        BranchMessageDTO selectedAnswer = message(
                11L, "focused", "candidate_answer", false, true, 10L);
        when(historyService.getBranchTranscript("focused", 42L))
                .thenReturn(transcript(prompt, selectedAnswer));

        ForkAttemptDTO result = service.create(
                "focused",
                42L,
                "alice",
                request("fork-turn-1", 11L, "编辑后的新回答", 3L, 99L));

        ArgumentCaptor<InterviewSession> childCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(branchRepository).insertChild(childCaptor.capture());
        InterviewSession child = childCaptor.getValue();
        assertThat(child.getId()).isEqualTo(result.getBranchId());
        assertThat(child.getParentSessionId()).isEqualTo("focused");
        assertThat(child.getForkPointMessageId()).isEqualTo(10L);
        assertThat(child.getForkTriggerMessageId()).isEqualTo(11L);
        assertThat(child.getBranchVersion()).isEqualTo(1L);
        assertThat(child.getStatus()).isEqualTo(1);
        assertThat(child.getStage()).isEqualTo("technical_qna");
        assertThat(child.getProjectQuestionsCount()).isEqualTo(2);
        assertThat(child.getTargetProjectQuestions()).isEqualTo(4);
        assertThat(child.getCurrentFollowupCount()).isEqualTo(1);
        assertThat((Object) child.getProjectQuestionsPool().getFirst()).isEqualTo(Map.of(
                "id", "p3",
                "context", Map.of("difficulty", "senior")));
        assertThat(child.getTechnicalQuestionsPool()).containsExactly(Map.of("id", "t1"));

        ArgumentCaptor<CreateTurnAttemptRequest> attemptCaptor =
                ArgumentCaptor.forClass(CreateTurnAttemptRequest.class);
        verify(turnAttemptService).create(
                eq(result.getBranchId()), eq(42L), eq("alice"), attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getTurnId()).isEqualTo("fork-turn-1");
        assertThat(attemptCaptor.getValue().getCandidateAnswer()).isEqualTo("编辑后的新回答");
        assertThat(attemptCaptor.getValue().getExpectedBranchVersion()).isEqualTo(1L);
        assertThat(attemptCaptor.getValue().getExpectedTailMessageId()).isEqualTo(10L);
        verify(attemptRepository).attachForkContext(
                "fork-turn-1", "focused", 11L, 10L, 3L, 99L);
    }

    @Test
    void inheritedAiQuestionForkAttachesToActualOwningBranch() {
        BranchMessageDTO prompt = message(10L, "root", "ai_question", true, true, 10L);
        when(historyService.getBranchTranscript("focused", 42L))
                .thenReturn(transcript(prompt));
        InterviewSession root = new InterviewSession();
        root.setId("root");
        root.setUserId(42L);
        root.setResumeId(7L);
        root.setJobId(8L);
        root.setCandidateName("候选人");
        root.setResumeContent("简历");
        root.setJobRequirements("岗位");
        root.setLineageId("lineage-1");
        when(sessionMapper.selectById("root")).thenReturn(root);
        when(attemptRepository.lockBranch("root")).thenReturn(Optional.of(
                new BranchState(
                        "root", "lineage-1", 42L, "project_qna", 1, 5L,
                        null, "候选人", "简历", "岗位", LocalDateTime.now())));

        ForkAttemptDTO result = service.create(
                "focused",
                42L,
                null,
                request("fork-turn-2", 10L, "新回答", 3L, 99L));

        ArgumentCaptor<InterviewSession> childCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(branchRepository).insertChild(childCaptor.capture());
        assertThat(childCaptor.getValue().getParentSessionId()).isEqualTo("root");
        assertThat(result.getAttempt().getTurnId()).isEqualTo("fork-turn-2");
    }

    @Test
    void rejectsMissingStateOrNonForkableMessagesBeforeCreatingChild() {
        BranchMessageDTO feedback = message(20L, "focused", "ai_feedback", false, false, null);
        when(historyService.getBranchTranscript("focused", 42L))
                .thenReturn(transcript(feedback));

        assertThatThrownBy(() -> service.create(
                        "focused",
                        42L,
                        null,
                        request("fork-turn-invalid", 20L, "回答", 3L, 99L)))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessageContaining("FORK_TRIGGER_NOT_FORKABLE");
        verify(branchRepository, org.mockito.Mockito.never()).insertChild(any());
    }

    @Test
    void distinguishesIncompleteOwnedTriggerFromCompletedMessageOutsideFocusedPath() {
        when(historyService.getBranchTranscript("focused", 42L))
                .thenReturn(transcript());
        when(branchRepository.findMessageContext(30L)).thenReturn(Optional.of(
                new MessageContext(30L, "focused", "lineage-1", 42L, "failed")));
        when(branchRepository.findMessageContext(31L)).thenReturn(Optional.of(
                new MessageContext(31L, "sibling", "lineage-1", 42L, "completed")));

        assertThatThrownBy(() -> service.create(
                        "focused", 42L, null,
                        request("fork-incomplete", 30L, "回答", 3L, 99L)))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessageContaining("FORK_TRIGGER_NOT_FORKABLE");
        assertThatThrownBy(() -> service.create(
                        "focused", 42L, null,
                        request("fork-outside", 31L, "回答", 3L, 99L)))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessageContaining("FORK_TRIGGER_NOT_ON_FOCUSED_PATH");
    }

    private static BranchTranscriptDTO transcript(BranchMessageDTO... messages) {
        BranchTranscriptDTO transcript = new BranchTranscriptDTO();
        transcript.setLineageId("lineage-1");
        transcript.setBranchId("focused");
        transcript.setBranchVersion(3L);
        transcript.setStatus(1);
        transcript.setMessages(List.of(messages));
        return transcript;
    }

    private static BranchMessageDTO message(
            Long id,
            String owner,
            String type,
            boolean expectsResponse,
            boolean forkable,
            Long forkPoint) {
        BranchMessageDTO message = new BranchMessageDTO();
        message.setId(id);
        message.setOwningBranchId(owner);
        message.setRole(type.startsWith("ai_") ? "ai" : "human");
        message.setMessageType(type);
        message.setDeliveryStatus("completed");
        message.setExpectsResponse(expectsResponse);
        message.setForkable(forkable);
        message.setForkPointMessageId(forkPoint);
        message.setCreatedAt(LocalDateTime.of(2026, 7, 24, 8, 0));
        if (forkPoint != null && "ai_question".equals(type)) {
            message.setMetadata(Map.of(
                    "id", "rich-question",
                    "_postTurnStateV1", Map.of(
                            "schemaVersion", 1,
                            "currentStage", "technical_qna",
                            "branchStatus", 1,
                            "projectQuestionsCount", 2,
                            "targetProjectQuestions", 4,
                            "currentFollowupCount", 1,
                            "projectQuestionsPool", List.of(Map.of(
                                    "id", "p3",
                                    "context", Map.of("difficulty", "senior"))),
                            "technicalQuestionsPool", List.of(Map.of("id", "t1")))));
        }
        return message;
    }

    private static CreateForkAttemptRequest request(
            String turnId,
            Long triggerId,
            String answer,
            Long version,
            Long tail) {
        CreateForkAttemptRequest request = new CreateForkAttemptRequest();
        request.setTurnId(turnId);
        request.setTriggerMessageId(triggerId);
        request.setCandidateAnswer(answer);
        request.setExpectedFocusedBranchVersion(version);
        request.setExpectedFocusedTailMessageId(tail);
        return request;
    }
}
