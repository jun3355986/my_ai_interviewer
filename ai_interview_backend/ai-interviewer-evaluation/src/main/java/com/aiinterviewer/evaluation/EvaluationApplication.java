package com.aiinterviewer.evaluation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Import;
import com.aiinterviewer.interview.service.ComposedAssessmentService;
import com.aiinterviewer.interview.service.EvaluationBranchGuard;
import com.aiinterviewer.interview.service.InterviewHistoryService;

/**
 * 评估服务启动类
 */
@SpringBootApplication(
        scanBasePackages = {"com.aiinterviewer.evaluation", "com.aiinterviewer.common"},
        exclude = FlywayAutoConfiguration.class)
@EnableDiscoveryClient
@MapperScan(basePackages = {
        "com.aiinterviewer.evaluation.mapper",
        "com.aiinterviewer.interview.mapper"
})
@Import({
        InterviewHistoryService.class,
        ComposedAssessmentService.class,
        EvaluationBranchGuard.class
})
public class EvaluationApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvaluationApplication.class, args);
    }
}
