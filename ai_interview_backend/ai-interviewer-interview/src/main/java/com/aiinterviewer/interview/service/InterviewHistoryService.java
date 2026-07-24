package com.aiinterviewer.interview.service;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.common.model.PageResult;
import com.aiinterviewer.interview.dto.BranchMessageDTO;
import com.aiinterviewer.interview.dto.BranchTranscriptDTO;
import com.aiinterviewer.interview.dto.LineageSummaryDTO;
import com.aiinterviewer.interview.entity.InterviewLineage;
import com.aiinterviewer.interview.entity.InterviewMessage;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.entity.ScoreRecord;
import com.aiinterviewer.interview.mapper.InterviewLineageMapper;
import com.aiinterviewer.interview.mapper.InterviewMessageMapper;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import com.aiinterviewer.interview.model.ForkStateSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.OptionalInt;

@Service
@RequiredArgsConstructor
public class InterviewHistoryService {

    private static final Map<String, String> STAGE_DISPLAY_NAMES = Map.of(
            "resume_submitted", "简历已提交",
            "opening", "开场阶段",
            "self_introduction", "自我介绍",
            "project_qna", "项目提问",
            "technical_qna", "技术面试",
            "concluded", "已完成");

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewLineageMapper lineageMapper;

    public PageResult<LineageSummaryDTO> listLineages(
            Long userId,
            Long current,
            Long size,
            String keyword,
            String sortBy,
            String status) {
        long normalizedCurrent = current == null || current < 1 ? 1 : current;
        long normalizedSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedSortBy = "score".equals(sortBy) ? "score" : "time";
        String normalizedStatus = normalizeStatus(status);
        long offset = (normalizedCurrent - 1) * normalizedSize;

        List<LineageSummaryDTO> summaries = lineageMapper.selectSummaryPage(
                        userId,
                        normalizedKeyword,
                        normalizedSortBy,
                        normalizedStatus,
                        normalizedSize,
                        offset)
                .stream()
                .map(this::toLineageSummaryDTO)
                .toList();
        Long total = lineageMapper.countSummaries(userId, normalizedKeyword, normalizedStatus);

        return PageResult.of(
                normalizedCurrent,
                normalizedSize,
                total == null ? 0L : total,
                summaries);
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "all";
        }
        String normalized = status.trim().toLowerCase(java.util.Locale.ROOT);
        return List.of("active", "completed", "ended").contains(normalized)
                ? normalized
                : "all";
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private LineageSummaryDTO toLineageSummaryDTO(
            com.aiinterviewer.interview.projection.LineageSummaryRow row) {
        LineageSummaryDTO dto = new LineageSummaryDTO();
        dto.setLineageId(row.getLineageId());
        dto.setRootSessionId(row.getRootSessionId());
        dto.setCandidateName(row.getCandidateName());
        dto.setResumeId(row.getResumeId());
        dto.setJobId(row.getJobId());
        dto.setJobTitle(row.getJobTitle());
        dto.setBranchCount(row.getBranchCount());
        dto.setActiveBranchCount(row.getActiveBranchCount());
        dto.setCompletedBranchCount(row.getCompletedBranchCount());
        dto.setBestCompletedScore(row.getBestCompletedScore());
        dto.setLatestActivityAt(row.getLatestActivityAt());
        dto.setFocusedBranchId(row.getFocusedBranchId());
        dto.setFocusedBranchStage(row.getFocusedBranchStage());
        dto.setFocusedBranchStageDisplay(STAGE_DISPLAY_NAMES.getOrDefault(
                row.getFocusedBranchStage(),
                row.getFocusedBranchStage()));
        dto.setFocusedBranchStatus(row.getFocusedBranchStatus());
        dto.setFocusedBranchProgress(calculateProgress(row));
        return dto;
    }

    private int calculateProgress(
            com.aiinterviewer.interview.projection.LineageSummaryRow row) {
        String stage = row.getFocusedBranchStage();
        if (stage == null) {
            return 0;
        }
        return switch (stage) {
            case "resume_submitted" -> 5;
            case "opening" -> 10;
            case "self_introduction" -> 20;
            case "project_qna" -> {
                int done = row.getProjectQuestionsCount() == null
                        ? 0
                        : row.getProjectQuestionsCount();
                int total = row.getTargetProjectQuestions() == null
                        ? 5
                        : row.getTargetProjectQuestions();
                yield 20 + (done * 40 / Math.max(total, 1));
            }
            case "technical_qna" -> 60;
            case "concluded" -> 100;
            default -> 0;
        };
    }

    public BranchTranscriptDTO getBranchTranscript(String branchId, Long userId) {
        InterviewSession focusedBranch = requireOwnedBranch(branchId, userId);
        List<BranchMessageDTO> messages = composeMessages(
                focusedBranch,
                focusedBranch.getId(),
                userId,
                new HashSet<>());
        markForkability(messages);

        BranchTranscriptDTO transcript = new BranchTranscriptDTO();
        transcript.setLineageId(focusedBranch.getLineageId());
        transcript.setBranchId(focusedBranch.getId());
        transcript.setBranchLabel(focusedBranch.getBranchLabel());
        transcript.setParentBranchId(focusedBranch.getParentSessionId());
        transcript.setForkPointMessageId(focusedBranch.getForkPointMessageId());
        transcript.setStage(focusedBranch.getStage());
        transcript.setStatus(focusedBranch.getStatus());
        transcript.setBranchVersion(focusedBranch.getBranchVersion());
        transcript.setMessages(messages);
        return transcript;
    }

    public OptionalInt findVisibleLegacyScoreAnswerOrder(
            ScoreRecord score,
            BranchTranscriptDTO transcript) {
        List<InterviewMessage> allOwningBranchMessages = messageMapper.selectBySessionId(
                score.getSessionId());
        int allMatchingPairs = countMatchingPairs(allOwningBranchMessages, score);
        if (allMatchingPairs == 0) {
            return OptionalInt.empty();
        }

        int visibleMatchingPairs = 0;
        int firstVisibleAnswerOrder = -1;
        List<BranchMessageDTO> visibleMessages = transcript.getMessages();
        for (int index = 0; index + 1 < visibleMessages.size(); index++) {
            BranchMessageDTO question = visibleMessages.get(index);
            BranchMessageDTO answer = visibleMessages.get(index + 1);
            if (matchesLegacyPair(score, question, answer)) {
                visibleMatchingPairs++;
                if (firstVisibleAnswerOrder < 0) {
                    firstVisibleAnswerOrder = index + 2;
                }
            }
        }
        if (visibleMatchingPairs > 0 && visibleMatchingPairs == allMatchingPairs) {
            return OptionalInt.of(firstVisibleAnswerOrder);
        }
        return OptionalInt.empty();
    }

    private int countMatchingPairs(List<InterviewMessage> messages, ScoreRecord score) {
        int matches = 0;
        for (int index = 0; index + 1 < messages.size(); index++) {
            InterviewMessage question = messages.get(index);
            InterviewMessage answer = messages.get(index + 1);
            if (Objects.equals(question.getSessionId(), score.getSessionId())
                    && Objects.equals(answer.getSessionId(), score.getSessionId())
                    && "ai".equals(question.getRole())
                    && "human".equals(answer.getRole())
                    && "completed".equals(question.getDeliveryStatus())
                    && "completed".equals(answer.getDeliveryStatus())
                    && Objects.equals(question.getSequence() + 1, answer.getSequence())
                    && Objects.equals(question.getContent(), score.getQuestion())
                    && Objects.equals(answer.getContent(), score.getAnswer())) {
                matches++;
            }
        }
        return matches;
    }

    private boolean matchesLegacyPair(
            ScoreRecord score,
            BranchMessageDTO question,
            BranchMessageDTO answer) {
        return Objects.equals(question.getOwningBranchId(), score.getSessionId())
                && Objects.equals(answer.getOwningBranchId(), score.getSessionId())
                && "ai".equals(question.getRole())
                && "human".equals(answer.getRole())
                && "completed".equals(question.getDeliveryStatus())
                && "completed".equals(answer.getDeliveryStatus())
                && Objects.equals(question.getSequence() + 1, answer.getSequence())
                && Objects.equals(question.getContent(), score.getQuestion())
                && Objects.equals(answer.getContent(), score.getAnswer());
    }

    private InterviewSession requireOwnedBranch(String branchId, Long userId) {
        InterviewSession branch = sessionMapper.selectById(branchId);
        if (branch == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (!Objects.equals(branch.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该面试分支");
        }
        InterviewLineage lineage = lineageMapper.selectById(branch.getLineageId());
        if (lineage == null || !Objects.equals(lineage.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该面试谱系");
        }
        return branch;
    }

    private List<BranchMessageDTO> composeMessages(
            InterviewSession branch,
            String focusedBranchId,
            Long userId,
            Set<String> visiting) {
        if (!visiting.add(branch.getId())) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "面试分支关系存在循环");
        }
        List<BranchMessageDTO> result = new ArrayList<>();
        if (branch.getParentSessionId() != null) {
            InterviewSession parent = requireOwnedBranch(branch.getParentSessionId(), userId);
            if (!Objects.equals(branch.getLineageId(), parent.getLineageId())) {
                throw new BusinessException(ErrorCode.OPERATION_FAILED, "面试分支不属于同一谱系");
            }
            List<BranchMessageDTO> parentTranscript = composeMessages(
                    parent,
                    focusedBranchId,
                    userId,
                    visiting);
            int forkPointIndex = -1;
            for (int index = 0; index < parentTranscript.size(); index++) {
                if (Objects.equals(
                        parentTranscript.get(index).getId(),
                        branch.getForkPointMessageId())) {
                    forkPointIndex = index;
                    break;
                }
            }
            if (forkPointIndex < 0) {
                throw new BusinessException(ErrorCode.OPERATION_FAILED, "找不到分支对应的分叉消息");
            }
            result.addAll(parentTranscript.subList(0, forkPointIndex + 1));
        }

        for (InterviewMessage message : messageMapper.selectBySessionId(branch.getId())) {
            if ("completed".equals(message.getDeliveryStatus())) {
                result.add(toMessageDTO(message, !Objects.equals(branch.getId(), focusedBranchId)));
            }
        }
        visiting.remove(branch.getId());
        return result;
    }

    private BranchMessageDTO toMessageDTO(InterviewMessage message, boolean inherited) {
        BranchMessageDTO dto = new BranchMessageDTO();
        dto.setId(message.getId());
        dto.setOwningBranchId(message.getSessionId());
        dto.setRole(message.getRole());
        dto.setMessageType(message.getMessageType());
        dto.setContent(message.getContent());
        dto.setStage(message.getStage());
        dto.setSequence(message.getSequence());
        dto.setExpectsResponse(Boolean.TRUE.equals(message.getExpectsResponse()));
        dto.setDeliveryStatus(message.getDeliveryStatus());
        dto.setInherited(inherited);
        dto.setForkable(false);
        dto.setMetadata(message.getMetadata());
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }

    private void markForkability(List<BranchMessageDTO> messages) {
        for (int index = 0; index < messages.size(); index++) {
            BranchMessageDTO message = messages.get(index);
            if (!isCompletedNonLegacy(message)) {
                continue;
            }
            if (isStatefulQuestion(message)) {
                message.setForkable(true);
                message.setForkPointMessageId(message.getId());
                continue;
            }
            if (!"candidate_answer".equals(message.getMessageType()) || index == 0) {
                continue;
            }
            BranchMessageDTO previous = messages.get(index - 1);
            if (isStatefulQuestion(previous)) {
                message.setForkable(true);
                message.setForkPointMessageId(previous.getId());
            }
        }
    }

    private boolean isCompletedNonLegacy(BranchMessageDTO message) {
        return "completed".equals(message.getDeliveryStatus())
                && (message.getMetadata() == null
                        || !Boolean.FALSE.equals(message.getMetadata().get("legacyForkEligible")));
    }

    private boolean isStatefulQuestion(BranchMessageDTO message) {
        return isCompletedNonLegacy(message)
                && "ai_question".equals(message.getMessageType())
                && Boolean.TRUE.equals(message.getExpectsResponse())
                && ForkStateSnapshot.fromMetadata(message.getMetadata()).isPresent();
    }
}
