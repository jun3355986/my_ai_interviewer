package com.aiinterviewer.interview.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TurnAttemptConfiguration {

    @Bean(name = "turnAttemptExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor turnAttemptExecutor(
            @Value("${interview.turn-attempt.executor.core-size:2}") int coreSize,
            @Value("${interview.turn-attempt.executor.max-size:4}") int maxSize,
            @Value("${interview.turn-attempt.executor.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("turn-attempt-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
