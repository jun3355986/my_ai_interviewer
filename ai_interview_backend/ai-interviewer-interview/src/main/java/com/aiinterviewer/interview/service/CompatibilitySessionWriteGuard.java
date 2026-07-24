package com.aiinterviewer.interview.service;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.interview.entity.InterviewLineage;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.mapper.InterviewLineageMapper;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction boundary for the legacy compatibility endpoints.
 *
 * <p>The lineage row is locked before the branch row, matching the durable-turn and fork paths.
 * Ownership is therefore checked in the same transaction as the caller's persistence callback.
 */
@Service
@RequiredArgsConstructor
public class CompatibilitySessionWriteGuard {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewLineageMapper lineageMapper;

    @Transactional
    public void createSession(InterviewLineage lineage, InterviewSession session) {
        lineageMapper.insert(lineage);
        sessionMapper.insert(session);
    }

    @Transactional
    public <T> T executeOwnedActive(
            String sessionId,
            String lineageId,
            Long userId,
            Function<LockedSession, T> persistence) {
        InterviewLineage lineage = lineageMapper.selectOwnedForUpdate(lineageId, userId);
        if (lineage == null) {
            throw denied();
        }

        InterviewSession session = sessionMapper.selectByIdForUpdate(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (!Objects.equals(session.getLineageId(), lineageId)
                || !Objects.equals(session.getUserId(), userId)) {
            throw denied();
        }
        if (!Integer.valueOf(1).equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.SESSION_COMPLETED);
        }

        return persistence.apply(new LockedSession(session, lineage));
    }

    private BusinessException denied() {
        return new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该会话");
    }

    public record LockedSession(InterviewSession session, InterviewLineage lineage) {
    }
}
