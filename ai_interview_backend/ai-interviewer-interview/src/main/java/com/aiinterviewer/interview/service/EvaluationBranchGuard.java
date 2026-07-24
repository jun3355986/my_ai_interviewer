package com.aiinterviewer.interview.service;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EvaluationBranchGuard {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockCompletedOwnedBranch(String branchId, Long userId) {
        String lineageId = jdbcTemplate.query(
                        "SELECT lineage_id FROM t_interview_session WHERE id = ?",
                        (rs, rowNum) -> rs.getString(1),
                        branchId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        boolean lineageLocked = !jdbcTemplate.query(
                        "SELECT id FROM t_interview_lineage WHERE id = ? AND user_id = ? FOR UPDATE",
                        (rs, rowNum) -> rs.getString(1),
                        lineageId,
                        userId)
                .isEmpty();
        if (!lineageLocked) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权生成该面试报告");
        }

        BranchEvaluationState branch = jdbcTemplate.query("""
                        SELECT lineage_id, user_id, status
                        FROM t_interview_session
                        WHERE id = ?
                        FOR UPDATE
                        """,
                        (rs, rowNum) -> new BranchEvaluationState(
                                rs.getString("lineage_id"),
                                rs.getLong("user_id"),
                                rs.getInt("status")),
                        branchId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!Objects.equals(branch.lineageId(), lineageId)
                || !Objects.equals(branch.userId(), userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权生成该面试报告");
        }
        if (!Integer.valueOf(2).equals(branch.status())) {
            throw new BusinessException(ErrorCode.EVALUATION_NOT_READY, "面试分支尚未完成");
        }
    }

    private record BranchEvaluationState(String lineageId, Long userId, Integer status) {
    }
}
