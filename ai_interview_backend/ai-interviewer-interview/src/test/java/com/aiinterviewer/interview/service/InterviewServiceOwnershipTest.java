package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.interview.entity.InterviewMessage;
import com.aiinterviewer.interview.entity.InterviewLineage;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.mapper.InterviewLineageMapper;
import com.aiinterviewer.interview.mapper.InterviewMessageMapper;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class InterviewServiceOwnershipTest {

    @Test
    void legacySessionReadsAndCancellationRejectLineageOnlyOwnershipDrift() {
        InterviewSessionMapper sessions = mock(InterviewSessionMapper.class);
        InterviewMessageMapper messages = mock(InterviewMessageMapper.class);
        InterviewLineageMapper lineages = mock(InterviewLineageMapper.class);
        InterviewSession branch = new InterviewSession();
        branch.setId("branch-1");
        branch.setLineageId("lineage-1");
        branch.setUserId(1L);
        branch.setStatus(1);
        when(sessions.selectById("branch-1")).thenReturn(branch);
        InterviewLineage reassigned = new InterviewLineage();
        reassigned.setId("lineage-1");
        reassigned.setUserId(2L);
        when(lineages.selectById("lineage-1")).thenReturn(reassigned);
        InterviewService service = new InterviewService(
                sessions,
                messages,
                lineages,
                new CompatibilitySessionWriteGuard(sessions, lineages));

        assertDenied(() -> service.getSession("branch-1", 1L));
        assertDenied(() -> service.cancelSession("branch-1", 1L));
        assertDenied(() -> service.getSessionHistory("branch-1", 1L));

        verify(sessions, never()).updateById(branch);
        verify(messages, never()).selectBySessionId("branch-1");
    }

    @Test
    void legacySessionListsJoinCurrentLineageOwnership() throws Exception {
        for (String methodName : List.of(
                "selectIncompleteByUserId",
                "selectByUserIdWithPage",
                "countByUserId")) {
            Method method = switch (methodName) {
                case "selectByUserIdWithPage" -> InterviewSessionMapper.class.getMethod(
                        methodName, Long.class, Long.class, Long.class);
                default -> InterviewSessionMapper.class.getMethod(methodName, Long.class);
            };
            String sql = String.join(" ", method.getAnnotation(Select.class).value())
                    .replaceAll("\\s+", " ")
                    .toLowerCase();

            assertThat(sql)
                    .contains("join t_interview_lineage")
                    .contains("session.user_id = #{userid}")
                    .contains("lineage.user_id = #{userid}");
        }
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(2003);
    }
}
