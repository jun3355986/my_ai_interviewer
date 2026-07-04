package com.aiinterviewer.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 通知服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.aiinterviewer.notification", "com.aiinterviewer.common"})
@EnableDiscoveryClient
@MapperScan(basePackages = {
        "com.aiinterviewer.notification.mapper",
        "com.aiinterviewer.user.mapper"
})
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
