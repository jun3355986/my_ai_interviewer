package com.aiinterviewer.resume.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /**
     * MinIO endpoint
     */
    private String endpoint;

    /**
     * Access Key
     */
    private String accessKey;

    /**
     * Secret Key
     */
    private String secretKey;

    /**
     * Bucket名称
     */
    private String bucket;

    /**
     * 连接超时时间 (毫秒)
     */
    private long connectTimeout;

    /**
     * 读取超时时间 (毫秒)
     */
    private long readTimeout;

    /**
     * 默认bucket
     */
    private static final String DEFAULT_BUCKET = "ai-interviewer";

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 获取bucket名称
     */
    public String getBucket() {
        return bucket != null && !bucket.isEmpty() ? bucket : DEFAULT_BUCKET;
    }
}
