package com.aiinterviewer.interview.service;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.common.model.PageResult;
import com.aiinterviewer.interview.dto.SessionDTO;
import com.aiinterviewer.interview.entity.InterviewMessage;
import com.aiinterviewer.interview.entity.InterviewLineage;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.mapper.InterviewLineageMapper;
import com.aiinterviewer.interview.mapper.InterviewMessageMapper;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 面试服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewLineageMapper lineageMapper;
    private final CompatibilitySessionWriteGuard writeGuard;

    /**
     * 阶段显示名称映射
     */
    private static final Map<String, String> STAGE_DISPLAY_NAMES = new HashMap<>() {{
        put("resume_submitted", "简历已提交");
        put("opening", "开场阶段");
        put("self_introduction", "自我介绍");
        put("project_qna", "项目提问");
        put("technical_qna", "技术面试");
        put("concluded", "已完成");
    }};

    /**
     * 获取面试列表（分页）
     */
    public PageResult<SessionDTO> listSessions(Long userId, Long current, Long size) {
        Long offset = (current - 1) * size;
        List<InterviewSession> sessions = sessionMapper.selectByUserIdWithPage(userId, size, offset);
        Long total = sessionMapper.countByUserId(userId);

        List<SessionDTO> dtos = sessions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResult.of(current, size, total, dtos);
    }

    /**
     * 获取未完成的面试列表
     */
    public PageResult<SessionDTO> listIncompleteSessions(Long userId) {
        List<InterviewSession> sessions = sessionMapper.selectIncompleteByUserId(userId);

        List<SessionDTO> dtos = sessions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResult.of(1L, (long) dtos.size(), (long) dtos.size(), dtos);
    }

    /**
     * 获取会话详情
     */
    public SessionDTO getSession(String sessionId, Long userId) {
        InterviewSession session = findSessionByAnyId(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        requireCurrentOwnership(session, userId);
        return convertToDTO(session);
    }

    /**
     * 取消面试
     */
    @Transactional
    public void cancelSession(String sessionId, Long userId) {
        InterviewSession session = findSessionByAnyId(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        writeGuard.executeOwnedActive(
                session.getId(),
                session.getLineageId(),
                userId,
                locked -> {
                    InterviewSession current = locked.session();
                    current.setStatus(3); // 已取消
                    current.setFinishedAt(LocalDateTime.now());
                    current.setUpdatedAt(LocalDateTime.now());
                    sessionMapper.updateById(current);
                    return null;
                });

        log.info("Session {} cancelled by user {}", sessionId, userId);
    }

    /**
     * 获取会话历史消息
     */
    public List<InterviewMessage> getSessionHistory(String sessionId, Long userId) {
        InterviewSession session = findSessionByAnyId(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        requireCurrentOwnership(session, userId);
        return messageMapper.selectBySessionId(session.getId());
    }

    private void requireCurrentOwnership(InterviewSession session, Long userId) {
        InterviewLineage lineage = lineageMapper.selectById(session.getLineageId());
        if (!Objects.equals(session.getUserId(), userId)
                || lineage == null
                || !Objects.equals(lineage.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该会话");
        }
    }

    private InterviewSession findSessionByAnyId(String sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            session = sessionMapper.selectByPythonSessionId(sessionId);
        }
        return session;
    }

    /**
     * 计算面试进度（0-100）
     */
    private int calculateProgress(InterviewSession session) {
        String stage = session.getStage();
        if (stage == null) {
            return 0;
        }

        return switch (stage) {
            case "resume_submitted" -> 5;
            case "opening" -> 10;
            case "self_introduction" -> 20;
            case "project_qna" -> {
                // 项目提问阶段根据完成的问题数计算进度 (20-60)
                int projectDone = session.getProjectQuestionsCount() != null ? session.getProjectQuestionsCount() : 0;
                int projectTotal = session.getTargetProjectQuestions() != null ? session.getTargetProjectQuestions() : 5;
                yield 20 + (projectDone * 40 / Math.max(projectTotal, 1));
            }
            case "technical_qna" -> {
                // 技术提问阶段 (60-90)
                int techDone = session.getTechnicalQuestionsCount() != null ? session.getTechnicalQuestionsCount() : 0;
                yield 60 + Math.min(techDone * 6, 30);
            }
            case "concluded" -> 100;
            default -> 0;
        };
    }

    /**
     * 转换为DTO
     */
    private SessionDTO convertToDTO(InterviewSession session) {
        SessionDTO dto = new SessionDTO();
        dto.setSessionId(session.getId());
        dto.setCandidateName(session.getCandidateName());
        dto.setStage(session.getStage());
        dto.setStageDisplay(STAGE_DISPLAY_NAMES.getOrDefault(session.getStage(), session.getStage()));
        dto.setProgress(calculateProgress(session));
        dto.setStatus(session.getStatus());
        dto.setLastQuestion(session.getLastQuestion());
        dto.setProjectQuestionsCount(session.getProjectQuestionsCount());
        dto.setTargetProjectQuestions(session.getTargetProjectQuestions());
        dto.setTechnicalQuestionsCount(session.getTechnicalQuestionsCount());
        dto.setResumeId(session.getResumeId());
        dto.setJobId(session.getJobId());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());
        return dto;
    }
}
