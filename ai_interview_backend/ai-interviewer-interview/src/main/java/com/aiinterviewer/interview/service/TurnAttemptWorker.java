package com.aiinterviewer.interview.service;

import com.aiinterviewer.interview.entity.InterviewTurnAttempt;
import com.aiinterviewer.interview.model.BranchSnapshot;
import com.aiinterviewer.interview.model.TurnModelClient;
import com.aiinterviewer.interview.model.TurnModelCommand;
import com.aiinterviewer.interview.model.TurnModelResult;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import com.aiinterviewer.interview.repository.TurnAttemptRepository.BranchState;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TurnAttemptWorker {

    private final TurnAttemptRepository repository;
    private final TurnModelClient modelClient;
    private final TurnCommitService commitService;
    private final BranchSnapshotComposer snapshotComposer;
    private final TurnAttemptEventPublisher eventPublisher;

    private final AsyncTaskExecutor executor;

    private final ConcurrentMap<String, TaskControl> tasks = new ConcurrentHashMap<>();

    public TurnAttemptWorker(
            TurnAttemptRepository repository,
            TurnModelClient modelClient,
            TurnCommitService commitService,
            BranchSnapshotComposer snapshotComposer,
            TurnAttemptEventPublisher eventPublisher,
            @Qualifier("turnAttemptExecutor") AsyncTaskExecutor executor) {
        this.repository = repository;
        this.modelClient = modelClient;
        this.commitService = commitService;
        this.snapshotComposer = snapshotComposer;
        this.eventPublisher = eventPublisher;
        this.executor = executor;
    }

    public void schedule(String turnId) {
        TaskControl control = new TaskControl();
        if (tasks.putIfAbsent(turnId, control) != null) {
            return;
        }
        try {
            Future<?> future = executor.submit(() -> runControlled(turnId, control));
            control.attach(future);
        } catch (TaskRejectedException rejected) {
            boolean cancellationWillFinalize = control.reject();
            tasks.remove(turnId, control);
            log.warn("Turn attempt executor rejected {}; marking it recoverable", turnId);
            if (cancellationWillFinalize) {
                return;
            }
            if (repository.markInterrupted(turnId, "WORKER_SCHEDULING_REJECTED") == 1) {
                eventPublisher.publishTerminal(turnId, "interrupted", "INTERRUPTED");
            } else if (repository.markCancelled(turnId) == 1) {
                eventPublisher.publishTerminal(turnId, "cancelled", "CANCELLED");
            }
        }
    }

    public void cancel(String turnId) {
        TaskControl control = tasks.get(turnId);
        if (control == null) {
            finalizeQueuedCancellation(turnId);
            return;
        }
        CancelAction action = control.cancel();
        if (action == CancelAction.QUEUED) {
            tasks.remove(turnId, control);
            finalizeQueuedCancellation(turnId);
        }
    }

    private void runControlled(String turnId, TaskControl control) {
        if (!control.start(Thread.currentThread())) {
            tasks.remove(turnId, control);
            return;
        }
        try {
            process(turnId);
        } finally {
            control.finish();
            tasks.remove(turnId, control);
        }
    }

    private void process(String turnId) {
        try {
            InterviewTurnAttempt attempt = repository.findById(turnId).orElse(null);
            if (attempt == null) {
                return;
            }
            if ("CANCEL_REQUESTED".equals(attempt.getStatus())) {
                if (repository.markCancelled(turnId) == 1) {
                    eventPublisher.publishTerminal(turnId, "cancelled", "CANCELLED");
                }
                return;
            }
            if (!"PROCESSING".equals(attempt.getStatus())) {
                return;
            }
            BranchState branch = repository.findBranch(attempt.getSessionId())
                    .orElseThrow(() -> new IllegalStateException("Branch disappeared"));
            eventPublisher.publish(turnId, "processing", "PROCESSING");

            Long ownerUserId = attempt.getOwnerUserId();
            if (ownerUserId == null) {
                throw new TurnCommitRejectedException("OWNERSHIP_CHANGED");
            }

            BranchSnapshot snapshot = snapshotComposer.compose(
                    attempt, ownerUserId, attempt.getUsername());

            TurnModelResult result = modelClient.process(new TurnModelCommand(
                    attempt.getId(),
                    attempt.getRequestId(),
                    attempt.getAgentRunId(),
                    branch.id(),
                    branch.lineageId(),
                    ownerUserId,
                    attempt.getUsername(),
                    branch.pythonSessionId(),
                    attempt.getCandidateAnswer(),
                    branch.candidateName(),
                    branch.resumeContent(),
                    branch.jobRequirements(),
                    branch.stage(),
                    snapshot));
            commitService.commit(turnId, result);
            eventPublisher.publishTerminal(turnId, "completed", "COMPLETED");
        } catch (TurnCommitRejectedException rejected) {
            handleRejectedCommit(turnId, rejected);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            finishCancellationOrFailure(turnId, "WORKER_INTERRUPTED", interrupted);
        } catch (Exception failure) {
            finishCancellationOrFailure(turnId, "MODEL_PROCESSING_FAILED", failure);
        }
    }

    private void handleRejectedCommit(String turnId, TurnCommitRejectedException rejected) {
        InterviewTurnAttempt current = repository.findById(turnId).orElse(null);
        if (current != null
                && ("CANCEL_REQUESTED".equals(current.getStatus())
                        || "CANCELLED".equals(current.getStatus()))) {
            if (repository.markCancelled(turnId) == 1) {
                eventPublisher.publishTerminal(turnId, "cancelled", "CANCELLED");
            }
            return;
        }
        if (repository.markInterrupted(turnId, rejected.getMessage()) == 1) {
            eventPublisher.publishTerminal(turnId, "interrupted", "INTERRUPTED");
        }
    }

    private void finishCancellationOrFailure(String turnId, String errorCode, Exception failure) {
        InterviewTurnAttempt current = repository.findById(turnId).orElse(null);
        if (current != null
                && ("CANCEL_REQUESTED".equals(current.getStatus())
                        || "CANCELLED".equals(current.getStatus()))) {
            if (repository.markCancelled(turnId) == 1) {
                eventPublisher.publishTerminal(turnId, "cancelled", "CANCELLED");
            }
            return;
        }
        String diagnosticRef = UUID.randomUUID().toString();
        log.error("Turn attempt {} failed; diagnosticRef={}", turnId, diagnosticRef, failure);
        if (repository.markFailed(turnId, errorCode, diagnosticRef) == 1) {
            eventPublisher.publishTerminal(turnId, "failed", "FAILED");
        }
    }

    private void finalizeQueuedCancellation(String turnId) {
        if (repository.markCancelled(turnId) == 1) {
            eventPublisher.publishTerminal(turnId, "cancelled", "CANCELLED");
        }
    }

    private enum CancelAction {
        QUEUED,
        RUNNING,
        NONE
    }

    private enum TaskState {
        QUEUED,
        RUNNING,
        CANCELLED_BEFORE_START,
        REJECTED,
        FINISHED
    }

    private static final class TaskControl {

        private TaskState state = TaskState.QUEUED;
        private Future<?> future;
        private Thread thread;

        private synchronized void attach(Future<?> submittedFuture) {
            future = submittedFuture;
            if (state == TaskState.CANCELLED_BEFORE_START) {
                submittedFuture.cancel(false);
            }
        }

        private synchronized boolean start(Thread workerThread) {
            if (state != TaskState.QUEUED) {
                return false;
            }
            state = TaskState.RUNNING;
            thread = workerThread;
            return true;
        }

        private synchronized CancelAction cancel() {
            if (state == TaskState.QUEUED) {
                state = TaskState.CANCELLED_BEFORE_START;
                if (future != null) {
                    future.cancel(false);
                }
                return CancelAction.QUEUED;
            }
            if (state == TaskState.RUNNING) {
                thread.interrupt();
                return CancelAction.RUNNING;
            }
            return CancelAction.NONE;
        }

        private synchronized boolean reject() {
            boolean cancellationWillFinalize = state == TaskState.CANCELLED_BEFORE_START;
            if (state != TaskState.FINISHED) {
                state = TaskState.REJECTED;
            }
            return cancellationWillFinalize;
        }

        private synchronized void finish() {
            state = TaskState.FINISHED;
            thread = null;
        }
    }
}
