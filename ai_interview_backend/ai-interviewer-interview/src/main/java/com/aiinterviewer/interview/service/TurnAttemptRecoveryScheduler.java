package com.aiinterviewer.interview.service;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TurnAttemptRecoveryScheduler {

    private final TurnAttemptService service;
    private final Duration staleAfter;

    public TurnAttemptRecoveryScheduler(
            TurnAttemptService service,
            @Value("${interview.turn-attempt.stale-after:PT15M}") Duration staleAfter) {
        this.service = service;
        this.staleAfter = staleAfter;
    }

    @Scheduled(
            initialDelayString = "${interview.turn-attempt.recovery-initial-delay:PT0S}",
            fixedDelayString = "${interview.turn-attempt.recovery-interval:PT1M}")
    public void recover() {
        try {
            int recovered = service.recoverStaleProcessing(staleAfter);
            if (recovered > 0) {
                log.warn("Recovered {} stale Turn Attempts", recovered);
            }
        } catch (RuntimeException failure) {
            log.error("Periodic stale Turn Attempt recovery failed", failure);
        }
    }
}
