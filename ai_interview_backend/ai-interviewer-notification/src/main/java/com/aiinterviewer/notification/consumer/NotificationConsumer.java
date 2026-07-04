package com.aiinterviewer.notification.consumer;

import com.aiinterviewer.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * RocketMQ消息消费者 (函数式风格)
 * 注意: RocketMQ 暂时禁用，此类仅作为占位
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "notification.rocketmq.enabled", havingValue = "true")
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    /**
     * 监听面试完成事件
     */
    @Bean
    public Consumer<InterviewCompletedMessage> interviewCompleted() {
        return message -> {
            log.info("收到面试完成事件: sessionId={}", message.getSessionId());
            notificationService.notifyInterviewCompleted(
                    message.getUserId(),
                    message.getSessionId(),
                    message.getJobTitle()
            );
        };
    }

    /**
     * 监听报告生成事件
     */
    @Bean
    public Consumer<ReportReadyMessage> reportReady() {
        return message -> {
            log.info("收到报告生成事件: sessionId={}, score={}", message.getSessionId(), message.getScore());
            notificationService.notifyReportReady(
                    message.getUserId(),
                    message.getSessionId(),
                    message.getScore()
            );
        };
    }

    /**
     * 面试完成消息
     */
    @lombok.Data
    public static class InterviewCompletedMessage {
        private Long userId;
        private String sessionId;
        private String jobTitle;
    }

    /**
     * 报告生成消息
     */
    @lombok.Data
    public static class ReportReadyMessage {
        private Long userId;
        private String sessionId;
        private Integer score;
    }
}
