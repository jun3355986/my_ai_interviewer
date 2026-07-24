package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiinterviewer.interview.dto.BranchMessageDTO;
import com.aiinterviewer.interview.dto.BranchTranscriptDTO;
import com.aiinterviewer.interview.dto.ComposedAssessmentDTO;
import com.aiinterviewer.interview.entity.ScoreRecord;
import com.aiinterviewer.interview.mapper.ScoreRecordMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComposedAssessmentServiceTest {

    @Test
    void composesOnlyCanonicalPrefixAndOwnedScoresInPathOrderWithoutCopying() {
        InterviewHistoryService historyService = mock(InterviewHistoryService.class);
        ScoreRecordMapper scoreMapper = mock(ScoreRecordMapper.class);
        ComposedAssessmentService service = new ComposedAssessmentService(
                historyService,
                scoreMapper);

        BranchTranscriptDTO transcript = new BranchTranscriptDTO();
        transcript.setBranchId("child");
        transcript.setMessages(List.of(
                message(1L, "root"),
                message(2L, "root"),
                message(3L, "root"),
                message(6L, "child"),
                message(7L, "child")));
        when(historyService.getBranchTranscript("child", 42L)).thenReturn(transcript);

        ScoreRecord inherited = score(10L, "root", 1L, 2L, 1, 80);
        ScoreRecord excludedAfterBoundary = score(11L, "root", 3L, 4L, 2, 60);
        ScoreRecord owned = score(12L, "child", 3L, 6L, 1, 90);
        when(scoreMapper.selectBySessionId("root"))
                .thenReturn(List.of(inherited, excludedAfterBoundary));
        when(scoreMapper.selectBySessionId("child"))
                .thenReturn(List.of(owned));

        List<ComposedAssessmentDTO> result = service.compose("child", 42L);

        assertThat(result).extracting(ComposedAssessmentDTO::getId)
                .containsExactly(10L, 12L);
        assertThat(result).extracting(ComposedAssessmentDTO::getOwningBranchId)
                .containsExactly("root", "child");
        assertThat(result).extracting(ComposedAssessmentDTO::getInherited)
                .containsExactly(true, false);
        assertThat(result).extracting(ComposedAssessmentDTO::getDisplayOrder)
                .containsExactly(1, 2);
        assertThat(result).extracting(ComposedAssessmentDTO::getQuestionIndex)
                .containsExactly(1, 2);
    }

    private static BranchMessageDTO message(Long id, String owner) {
        BranchMessageDTO message = new BranchMessageDTO();
        message.setId(id);
        message.setOwningBranchId(owner);
        return message;
    }

    private static ScoreRecord score(
            Long id,
            String owner,
            Long questionMessageId,
            Long answerMessageId,
            int storedIndex,
            int value) {
        ScoreRecord score = new ScoreRecord();
        score.setId(id);
        score.setSessionId(owner);
        score.setQuestionMessageId(questionMessageId);
        score.setAnswerMessageId(answerMessageId);
        score.setQuestionIndex(storedIndex);
        score.setQuestionType("project");
        score.setQuestion("问题" + id);
        score.setAnswer("回答" + id);
        score.setScore(value);
        return score;
    }
}
