package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.interview.dto.TurnAttemptEventDTO;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

class TurnAttemptEventPublisherTest {

    @Test
    void concurrentCancelRequestedAndCancelledDeliversOneTerminalEventAndCompletes()
            throws Exception {
        TurnAttemptEventPublisher publisher = new TurnAttemptEventPublisher();
        TurnAttemptEventPublisher.EventSubscription subscription = publisher.subscribe("turn-events-race");
        List<TurnAttemptEventDTO> observed = new CopyOnWriteArrayList<>();
        CountDownLatch ordinaryEntered = new CountDownLatch(1);
        CountDownLatch releaseOrdinary = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> streamError = new AtomicReference<>();
        Disposable disposable = subscription.events().subscribe(event -> {
            observed.add(event);
            if ("cancel_requested".equals(event.type())) {
                ordinaryEntered.countDown();
                try {
                    if (!releaseOrdinary.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("test did not release ordinary event delivery");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("ordinary event delivery was interrupted", interrupted);
                }
            }
        }, streamError::set, completed::countDown);

        Thread ordinary = Thread.ofPlatform().start(() ->
                publisher.publish("turn-events-race", "cancel_requested", "CANCEL_REQUESTED"));
        assertThat(ordinaryEntered.await(5, TimeUnit.SECONDS)).isTrue();

        CountDownLatch terminalReturned = new CountDownLatch(1);
        Thread terminal = Thread.ofPlatform().start(() -> {
            publisher.publishTerminal("turn-events-race", "cancelled", "CANCELLED");
            terminalReturned.countDown();
        });
        assertThat(terminalReturned.await(200, TimeUnit.MILLISECONDS))
                .as("terminal publication waits for the in-flight ordinary emission")
                .isFalse();
        releaseOrdinary.countDown();

        ordinary.join(TimeUnit.SECONDS.toMillis(5));
        terminal.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(ordinary.isAlive()).isFalse();
        assertThat(terminal.isAlive()).isFalse();
        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(streamError.get()).isNull();
        assertThat(observed).filteredOn(event -> "CANCELLED".equals(event.status())).hasSize(1);
        assertThat(observed.getLast().type()).isEqualTo("cancelled");
        assertThat(observed.getLast().status()).isEqualTo("CANCELLED");
        assertThat(publisher.activeStreamCount()).isZero();
        disposable.dispose();
    }
}
