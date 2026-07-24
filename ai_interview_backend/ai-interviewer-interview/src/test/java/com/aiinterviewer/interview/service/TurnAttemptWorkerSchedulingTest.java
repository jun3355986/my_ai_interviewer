package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiinterviewer.interview.model.TurnModelClient;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class TurnAttemptWorkerSchedulingTest {

    @Test
    void executorRejectionMakesCommittedAttemptImmediatelyRecoverable() {
        TurnAttemptRepository repository = mock(TurnAttemptRepository.class);
        TurnAttemptEventPublisher events = mock(TurnAttemptEventPublisher.class);
        AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
        when(executor.submit(any(Runnable.class)))
                .thenThrow(new TaskRejectedException("queue full"));
        when(repository.markInterrupted("turn-rejected", "WORKER_SCHEDULING_REJECTED"))
                .thenReturn(1);
        TurnAttemptWorker worker = new TurnAttemptWorker(
                repository,
                mock(TurnModelClient.class),
                mock(TurnCommitService.class),
                mock(BranchSnapshotComposer.class),
                events,
                executor);

        assertThatCode(() -> worker.schedule("turn-rejected")).doesNotThrowAnyException();

        verify(repository).markInterrupted("turn-rejected", "WORKER_SCHEDULING_REJECTED");
        verify(events).publishTerminal("turn-rejected", "interrupted", "INTERRUPTED");
    }

    @Test
    void cancellationRacingExecutorRejectionFinalizesCancelledAttempt() throws Exception {
        TurnAttemptRepository repository = mock(TurnAttemptRepository.class);
        TurnAttemptEventPublisher events = mock(TurnAttemptEventPublisher.class);
        AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
        CountDownLatch submitEntered = new CountDownLatch(1);
        CountDownLatch rejectSubmission = new CountDownLatch(1);
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            submitEntered.countDown();
            if (!rejectSubmission.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("test did not release executor rejection");
            }
            throw new TaskRejectedException("queue full");
        });
        when(repository.markCancelled("turn-rejection-cancel-race")).thenReturn(1, 0);
        TurnAttemptWorker worker = new TurnAttemptWorker(
                repository,
                mock(TurnModelClient.class),
                mock(TurnCommitService.class),
                mock(BranchSnapshotComposer.class),
                events,
                executor);

        Thread scheduling = Thread.ofPlatform().start(() -> worker.schedule("turn-rejection-cancel-race"));
        assertThat(submitEntered.await(5, TimeUnit.SECONDS)).isTrue();

        worker.cancel("turn-rejection-cancel-race");
        rejectSubmission.countDown();
        scheduling.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(scheduling.isAlive()).isFalse();
        verify(repository).markCancelled("turn-rejection-cancel-race");
        verify(events, times(1)).publishTerminal(
                "turn-rejection-cancel-race", "cancelled", "CANCELLED");
    }

    @Test
    void cancellationBeforeQueuedTaskStartsFinalizesWithoutWaitingForQueueDrain() {
        TurnAttemptRepository repository = mock(TurnAttemptRepository.class);
        TurnAttemptEventPublisher events = mock(TurnAttemptEventPublisher.class);
        TurnModelClient modelClient = mock(TurnModelClient.class);
        AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
        Future<?> future = mock(Future.class);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            queued.set(invocation.getArgument(0));
            return future;
        });
        when(future.cancel(false)).thenReturn(true);
        when(repository.markCancelled("turn-queued-cancel")).thenReturn(1);
        TurnAttemptWorker worker = new TurnAttemptWorker(
                repository,
                modelClient,
                mock(TurnCommitService.class),
                mock(BranchSnapshotComposer.class),
                events,
                executor);

        worker.schedule("turn-queued-cancel");
        worker.cancel("turn-queued-cancel");

        verify(repository).markCancelled("turn-queued-cancel");
        verify(events).publishTerminal("turn-queued-cancel", "cancelled", "CANCELLED");
        queued.get().run();
        verifyNoInteractions(modelClient);
    }
}
