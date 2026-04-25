package com.aiinterviewer.interview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 面试服务启动类
 *
 * 核心功能：
 * - 面试会话管理
 * - SSE流式响应代理
 * - 历史记录持久化
 */
@SpringBootApplication(scanBasePackages = "com.aiinterviewer")
@EnableDiscoveryClient
@EnableAsync
@MapperScan("com.aiinterviewer.interview.mapper")
public class InterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewApplication.class, args);
    }
}
