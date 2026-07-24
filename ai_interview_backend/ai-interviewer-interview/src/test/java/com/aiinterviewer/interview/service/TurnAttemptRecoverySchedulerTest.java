package com.aiinterviewer.interview.service;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

class TurnAttemptRecoverySchedulerTest {

    @Test
    void periodicallyRecoversAttemptsThatBecomeStaleAfterApplicationStartup() {
        TurnAttemptService service = mock(TurnAttemptService.class);
        when(service.recoverStaleProcessing(any(Duration.class))).thenReturn(0);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "turn-recovery-test",
                    Map.of(
                            "interview.turn-attempt.stale-after", "PT0.01S",
                            "interview.turn-attempt.recovery-initial-delay", "PT0S",
                            "interview.turn-attempt.recovery-interval", "PT0.05S")));
            context.getBeanFactory().registerSingleton("turnAttemptService", service);
            context.register(SchedulingTestConfiguration.class);
            context.refresh();

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                    verify(service, atLeast(2)).recoverStaleProcessing(Duration.ofMillis(10)));
        }
    }

    @Configuration
    @EnableScheduling
    static class SchedulingTestConfiguration {

        @Bean
        TurnAttemptRecoveryScheduler turnAttemptRecoveryScheduler(TurnAttemptService service) {
            return new TurnAttemptRecoveryScheduler(service, Duration.ofMillis(10));
        }
    }
}
