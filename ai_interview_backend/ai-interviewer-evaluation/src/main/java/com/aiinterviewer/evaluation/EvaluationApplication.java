package com.aiinterviewer.evaluation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 评估服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.aiinterviewer.evaluation", "com.aiinterviewer.common"})
@EnableDiscoveryClient
@MapperScan(basePackages = {
        "com.aiinterviewer.evaluation.mapper",
        "com.aiinterviewer.interview.mapper"
})
public class EvaluationApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvaluationApplication.class, args);
    }
}
