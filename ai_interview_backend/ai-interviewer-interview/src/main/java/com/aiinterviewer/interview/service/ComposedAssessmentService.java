package com.aiinterviewer.interview.service;

import com.aiinterviewer.interview.dto.BranchMessageDTO;
import com.aiinterviewer.interview.dto.BranchTranscriptDTO;
import com.aiinterviewer.interview.dto.ComposedAssessmentDTO;
import com.aiinterviewer.interview.entity.ScoreRecord;
import com.aiinterviewer.interview.mapper.ScoreRecordMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComposedAssessmentService {

    private final InterviewHistoryService historyService;
    private final ScoreRecordMapper scoreRecordMapper;

    public void verifyOwnership(String branchId, Long userId) {
        historyService.getBranchTranscript(branchId, userId);
    }

    public List<ComposedAssessmentDTO> compose(String branchId, Long userId) {
        BranchTranscriptDTO transcript = historyService.getBranchTranscript(branchId, userId);
        Map<Long, Integer> messageOrder = new LinkedHashMap<>();
        Set<String> owningBranches = new LinkedHashSet<>();
        int pathOrder = 0;
        for (BranchMessageDTO message : transcript.getMessages()) {
            messageOrder.put(message.getId(), ++pathOrder);
            owningBranches.add(message.getOwningBranchId());
        }

        List<VisibleScore> visible = new ArrayList<>();
        for (String owningBranch : owningBranches) {
            List<ScoreRecord> records = scoreRecordMapper.selectBySessionId(owningBranch);
            if (records == null) {
                continue;
            }
            for (ScoreRecord record : records) {
                if (record.getQuestionMessageId() != null
                        && record.getAnswerMessageId() != null) {
                    if (messageOrder.containsKey(record.getQuestionMessageId())
                            && messageOrder.containsKey(record.getAnswerMessageId())) {
                        visible.add(new VisibleScore(
                                record,
                                messageOrder.get(record.getAnswerMessageId())));
                    }
                } else if (record.getQuestionMessageId() == null
                        && record.getAnswerMessageId() == null) {
                    if (branchId.equals(record.getSessionId())) {
                        visible.add(new VisibleScore(
                                record,
                                pathOrder + Math.max(value(record.getQuestionIndex()), 1)));
                    } else {
                        var answerOrder = historyService.findVisibleLegacyScoreAnswerOrder(
                                record,
                                transcript);
                        if (answerOrder.isPresent()) {
                            visible.add(new VisibleScore(record, answerOrder.getAsInt()));
                        } else {
                            log.warn(
                                    "LEGACY_SCORE_LINK_AMBIGUOUS scoreId={} owningBranchId={} focusedBranchId={}",
                                    record.getId(),
                                    record.getSessionId(),
                                    branchId);
                        }
                    }
                } else {
                    log.warn(
                            "LEGACY_SCORE_LINK_PARTIAL scoreId={} owningBranchId={} focusedBranchId={}",
                            record.getId(),
                            record.getSessionId(),
                            branchId);
                }
            }
        }
        visible.sort(Comparator
                .comparingInt(VisibleScore::answerOrder)
                .thenComparing(
                        visibleScore -> visibleScore.record().getId(),
                        Comparator.nullsLast(Long::compareTo)));

        List<ComposedAssessmentDTO> result = new ArrayList<>();
        for (VisibleScore visibleScore : visible) {
            ScoreRecord record = visibleScore.record();
            int displayOrder = result.size() + 1;
            ComposedAssessmentDTO dto = new ComposedAssessmentDTO();
            dto.setId(record.getId());
            dto.setOwningBranchId(record.getSessionId());
            dto.setInherited(!branchId.equals(record.getSessionId()));
            dto.setDisplayOrder(displayOrder);
            dto.setQuestionIndex(displayOrder);
            dto.setTurnId(record.getTurnId());
            dto.setQuestionMessageId(record.getQuestionMessageId());
            dto.setAnswerMessageId(record.getAnswerMessageId());
            dto.setQuestionType(record.getQuestionType());
            dto.setQuestion(record.getQuestion());
            dto.setAnswer(record.getAnswer());
            dto.setScore(record.getScore());
            dto.setFeedback(record.getFeedback());
            dto.setIsFollowup(record.getIsFollowup());
            dto.setCreatedAt(record.getCreatedAt());
            result.add(dto);
        }
        return List.copyOf(result);
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private record VisibleScore(ScoreRecord record, int answerOrder) {
    }
}
