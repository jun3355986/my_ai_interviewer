package com.aiinterviewer.interview.service;

import com.aiinterviewer.interview.dto.TurnAttemptEventDTO;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class TurnAttemptEventPublisher {

    private final ConcurrentMap<String, StreamState> streams = new ConcurrentHashMap<>();

    public EventSubscription subscribe(String turnId) {
        StreamState state = streams.compute(turnId, (ignored, existing) -> {
            StreamState selected = existing == null ? new StreamState() : existing;
            selected.subscribers.incrementAndGet();
            return selected;
        });
        return new EventSubscription(turnId, state);
    }

    public void publish(String turnId, String type, String status) {
        StreamState state = streams.get(turnId);
        if (state != null) {
            state.emitOrdinary(turnId, type, status);
        }
    }

    public void publishTerminal(String turnId, String type, String status) {
        StreamState state = streams.get(turnId);
        if (state == null) {
            return;
        }
        if (state.emitTerminal(turnId, type, status)) {
            streams.remove(turnId, state);
        }
    }

    public int activeStreamCount() {
        return streams.size();
    }

    private void release(String turnId, StreamState state) {
        if (state.releaseSubscriber()) {
            streams.remove(turnId, state);
        }
    }

    public final class EventSubscription implements AutoCloseable {

        private final String turnId;
        private final StreamState state;
        private final AtomicBoolean released = new AtomicBoolean();

        private EventSubscription(String turnId, StreamState state) {
            this.turnId = turnId;
            this.state = state;
        }

        public Flux<TurnAttemptEventDTO> events() {
            return state.sink.asFlux().doFinally(ignored -> releaseOnce());
        }

        @Override
        public void close() {
            releaseOnce();
        }

        private void releaseOnce() {
            if (released.compareAndSet(false, true)) {
                release(turnId, state);
            }
        }
    }

    private static final class StreamState {

        private final Sinks.Many<TurnAttemptEventDTO> sink = Sinks.many().replay().limit(1);
        private final AtomicLong sequence = new AtomicLong();
        private final AtomicInteger subscribers = new AtomicInteger();
        private StreamLifecycle lifecycle = StreamLifecycle.ACTIVE;

        private synchronized void emitOrdinary(String turnId, String type, String status) {
            if (lifecycle != StreamLifecycle.ACTIVE) {
                return;
            }
            requireSuccess(sink.tryEmitNext(event(turnId, type, status)), "ordinary event");
        }

        private synchronized boolean emitTerminal(String turnId, String type, String status) {
            if (lifecycle == StreamLifecycle.TERMINATED
                    || lifecycle == StreamLifecycle.DETACHED) {
                return true;
            }
            if (lifecycle == StreamLifecycle.TERMINATING) {
                return false;
            }
            lifecycle = StreamLifecycle.TERMINATING;
            try {
                requireSuccess(sink.tryEmitNext(event(turnId, type, status)), "terminal event");
                requireSuccess(sink.tryEmitComplete(), "terminal completion");
                lifecycle = StreamLifecycle.TERMINATED;
                return true;
            } catch (RuntimeException emissionFailure) {
                lifecycle = StreamLifecycle.ACTIVE;
                throw emissionFailure;
            }
        }

        private synchronized boolean releaseSubscriber() {
            int remaining = subscribers.decrementAndGet();
            if (remaining > 0 || lifecycle == StreamLifecycle.TERMINATING) {
                return false;
            }
            if (lifecycle == StreamLifecycle.ACTIVE) {
                lifecycle = StreamLifecycle.DETACHED;
            }
            return true;
        }

        private TurnAttemptEventDTO event(String turnId, String type, String status) {
            return new TurnAttemptEventDTO(
                    turnId,
                    sequence.incrementAndGet(),
                    type,
                    status,
                    LocalDateTime.now());
        }

        private void requireSuccess(Sinks.EmitResult result, String operation) {
            if (result.isFailure()) {
                throw new IllegalStateException(
                        "Turn Attempt stream " + operation + " failed: " + result);
            }
        }
    }

    private enum StreamLifecycle {
        ACTIVE,
        TERMINATING,
        TERMINATED,
        DETACHED
    }
}
