package com.aiinterviewer.interview.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiinterviewer.interview.entity.InterviewTurnAttempt;
import com.aiinterviewer.interview.model.TurnModelClient;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import com.aiinterviewer.interview.repository.TurnAttemptRepository.BranchState;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;

class TurnAttemptWorkerSnapshotTest {

    @Test
    void snapshotDriftInterruptsAttemptWithoutInvokingPythonModel() {
        TurnAttemptRepository repository = mock(TurnAttemptRepository.class);
        TurnModelClient modelClient = mock(TurnModelClient.class);
        BranchSnapshotComposer composer = mock(BranchSnapshotComposer.class);
        TurnAttemptEventPublisher events = mock(TurnAttemptEventPublisher.class);
        AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return CompletableFuture.completedFuture(null);
        });
        InterviewTurnAttempt attempt = new InterviewTurnAttempt();
        attempt.setId("turn-drift");
        attempt.setSessionId("branch-1");
        attempt.setLineageId("lineage-1");
        attempt.setOwnerUserId(42L);
        attempt.setStatus("PROCESSING");
        when(repository.findById("turn-drift")).thenReturn(Optional.of(attempt));
        BranchState branch = new BranchState(
                "branch-1",
                "lineage-1",
                42L,
                "project_qna",
                1,
                4L,
                null,
                "Candidate",
                "Resume",
                "Job",
                null);
        when(repository.findBranch("branch-1")).thenReturn(Optional.of(branch));
        when(composer.compose(eq(attempt), eq(42L), eq(null)))
                .thenThrow(new TurnCommitRejectedException("BRANCH_TAIL_CONFLICT"));
        when(repository.markInterrupted("turn-drift", "BRANCH_TAIL_CONFLICT"))
                .thenReturn(1);
        TurnAttemptWorker worker = new TurnAttemptWorker(
                repository,
                modelClient,
                mock(TurnCommitService.class),
                composer,
                events,
                executor);

        worker.schedule("turn-drift");

        verifyNoInteractions(modelClient);
        verify(repository).markInterrupted("turn-drift", "BRANCH_TAIL_CONFLICT");
        verify(events).publishTerminal("turn-drift", "interrupted", "INTERRUPTED");
    }

    @Test
    void queuedOwnershipDriftUsesImmutableAttemptOwnerAndNeverInvokesPythonOrCommit() {
        TurnAttemptRepository repository = mock(TurnAttemptRepository.class);
        TurnModelClient modelClient = mock(TurnModelClient.class);
        TurnCommitService commitService = mock(TurnCommitService.class);
        BranchSnapshotComposer composer = mock(BranchSnapshotComposer.class);
        TurnAttemptEventPublisher events = mock(TurnAttemptEventPublisher.class);
        AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return CompletableFuture.completedFuture(null);
        });
        InterviewTurnAttempt attempt = new InterviewTurnAttempt();
        attempt.setId("turn-owner-drift");
        attempt.setSessionId("branch-1");
        attempt.setLineageId("lineage-1");
        attempt.setOwnerUserId(42L);
        attempt.setStatus("PROCESSING");
        when(repository.findById("turn-owner-drift")).thenReturn(Optional.of(attempt));
        BranchState reassignedBranch = new BranchState(
                "branch-1",
                "lineage-1",
                99L,
                "project_qna",
                1,
                4L,
                null,
                "Candidate",
                "Resume",
                "Job",
                null);
        when(repository.findBranch("branch-1")).thenReturn(Optional.of(reassignedBranch));
        when(composer.compose(eq(attempt), eq(42L), eq(null)))
                .thenThrow(new TurnCommitRejectedException("OWNERSHIP_CHANGED"));
        when(repository.markInterrupted("turn-owner-drift", "OWNERSHIP_CHANGED"))
                .thenReturn(1);
        TurnAttemptWorker worker = new TurnAttemptWorker(
                repository,
                modelClient,
                commitService,
                composer,
                events,
                executor);

        worker.schedule("turn-owner-drift");

        verify(composer).compose(attempt, 42L, null);
        verifyNoInteractions(modelClient, commitService);
        verify(repository).markInterrupted("turn-owner-drift", "OWNERSHIP_CHANGED");
        verify(events).publishTerminal("turn-owner-drift", "interrupted", "INTERRUPTED");
    }
}
