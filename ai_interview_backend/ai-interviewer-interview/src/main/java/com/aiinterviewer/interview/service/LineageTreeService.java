package com.aiinterviewer.interview.service;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.interview.dto.ComposedAssessmentDTO;
import com.aiinterviewer.interview.dto.LineageTreeDTO;
import com.aiinterviewer.interview.dto.LineageTreeNodeDTO;
import com.aiinterviewer.interview.repository.LineageTreeRepository;
import com.aiinterviewer.interview.repository.LineageTreeRepository.BranchNodeRow;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LineageTreeService {

    private final LineageTreeRepository repository;
    private final ComposedAssessmentService assessmentService;

    public LineageTreeDTO getTree(String lineageId, Long userId) {
        String rootBranchId = repository.findOwnedRootSessionId(lineageId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCESS_DENIED,
                        "无权访问该面试谱系"));
        List<BranchNodeRow> rows = repository.findBranches(lineageId, userId);
        List<LineageTreeNodeDTO> nodes = rows.stream()
                .map(row -> toNode(row, userId))
                .toList();

        LineageTreeDTO tree = new LineageTreeDTO();
        tree.setLineageId(lineageId);
        tree.setRootBranchId(rootBranchId);
        tree.setFocusedBranchId(selectFocus(rows));
        tree.setNodes(nodes);
        return tree;
    }

    private LineageTreeNodeDTO toNode(BranchNodeRow row, Long userId) {
        List<ComposedAssessmentDTO> assessments = assessmentService.compose(
                row.branchId(),
                userId);
        int owned = (int) assessments.stream()
                .filter(assessment -> !Boolean.TRUE.equals(assessment.getInherited()))
                .count();
        int inherited = assessments.size() - owned;

        LineageTreeNodeDTO node = new LineageTreeNodeDTO();
        node.setBranchId(row.branchId());
        node.setParentBranchId(row.parentBranchId());
        node.setBranchLabel(row.branchLabel());
        node.setForkPointMessageId(row.forkPointMessageId());
        node.setForkTriggerMessageId(row.forkTriggerMessageId());
        node.setStage(row.stage());
        node.setStatus(row.status());
        node.setBranchVersion(row.branchVersion());
        node.setLatestBusinessActivityAt(row.latestBusinessActivityAt());
        node.setProgress(progress(row));
        node.setOwnedAssessmentCount(owned);
        node.setInheritedAssessmentCount(inherited);
        node.setTotalAssessmentCount(assessments.size());
        if (Integer.valueOf(2).equals(row.status())) {
            repository.findEvaluation(row.branchId(), userId).ifPresentOrElse(evaluation -> {
                node.setCompletedScore(evaluation.overallScore());
                node.setEvaluationSummary(evaluation.summary());
            }, () -> node.setCompletedScore(averageScore(assessments)));
        }
        repository.findLatestRecoverableTurn(row.branchId(), userId).ifPresent(turn -> {
            node.setRecoverableTurnId(turn.turnId());
            node.setRecoverableTurnStatus(turn.status());
            node.setRecoverableTurnErrorCode(turn.errorCode());
        });
        return node;
    }

    private String selectFocus(List<BranchNodeRow> rows) {
        Comparator<BranchNodeRow> byActivity = Comparator
                .comparing(
                        BranchNodeRow::latestBusinessActivityAt,
                        Comparator.nullsFirst(LocalDateTime::compareTo))
                .thenComparing(BranchNodeRow::branchId);
        return rows.stream()
                .filter(row -> Integer.valueOf(1).equals(row.status()))
                .max(byActivity)
                .or(() -> rows.stream()
                        .filter(row -> Integer.valueOf(2).equals(row.status()))
                        .max(byActivity))
                .or(() -> rows.stream().max(byActivity))
                .map(BranchNodeRow::branchId)
                .orElse(null);
    }

    private int progress(BranchNodeRow row) {
        if (row.stage() == null) {
            return 0;
        }
        return switch (row.stage()) {
            case "resume_submitted" -> 5;
            case "opening" -> 10;
            case "self_introduction" -> 20;
            case "project_qna" -> 20 + (value(row.projectQuestionsCount(), 0) * 40
                    / Math.max(value(row.targetProjectQuestions(), 5), 1));
            case "technical_qna" -> 60;
            case "concluded" -> 100;
            default -> 0;
        };
    }

    private Integer averageScore(List<ComposedAssessmentDTO> assessments) {
        return (int) assessments.stream()
                .map(ComposedAssessmentDTO::getScore)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
