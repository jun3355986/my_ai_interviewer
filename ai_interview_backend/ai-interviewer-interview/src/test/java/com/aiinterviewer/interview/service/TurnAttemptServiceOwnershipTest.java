package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.interview.entity.InterviewTurnAttempt;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import com.aiinterviewer.interview.repository.TurnAttemptRepository.BranchState;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

class TurnAttemptServiceOwnershipTest {

    @Test
    void liveEventSubscriptionRechecksCurrentLineageOwnershipBeforeEachEvent()
            throws InterruptedException {
        TurnAttemptRepository repository = mock(TurnAttemptRepository.class);
        TurnAttemptEventPublisher events = new TurnAttemptEventPublisher();
        TurnAttemptService service = new TurnAttemptService(
                repository,
                mock(TurnAttemptWorker.class),
                events);
        InterviewTurnAttempt attempt = new InterviewTurnAttempt();
        attempt.setId("turn-live-owner");
        attempt.setLineageId("lineage-1");
        attempt.setSessionId("branch-1");
        attempt.setOwnerUserId(1L);
        attempt.setStatus("PROCESSING");
        BranchState branch = new BranchState(
                "branch-1",
                "lineage-1",
                1L,
                "project_qna",
                1,
                1L,
                null,
                null,
                null,
                null,
                LocalDateTime.now());
        AtomicBoolean lineageOwned = new AtomicBoolean(true);
        when(repository.findById("turn-live-owner")).thenReturn(Optional.of(attempt));
        when(repository.findBranch("branch-1")).thenReturn(Optional.of(branch));
        when(repository.lineageOwnedBy("lineage-1", 1L))
                .thenAnswer(ignored -> lineageOwned.get());

        CountDownLatch snapshotSeen = new CountDownLatch(1);
        CountDownLatch accessDenied = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Disposable subscription = service.events("turn-live-owner", 1L).subscribe(
                event -> snapshotSeen.countDown(),
                error -> {
                    failure.set(error);
                    accessDenied.countDown();
                });
        try {
            assertThat(snapshotSeen.await(1, TimeUnit.SECONDS)).isTrue();
            lineageOwned.set(false);
            events.publish("turn-live-owner", "progress", "PROCESSING");

            assertThat(accessDenied.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isInstanceOf(BusinessException.class);
            assertThat(events.activeStreamCount()).isZero();
        } finally {
            subscription.dispose();
        }
    }
}
