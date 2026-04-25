package com.aiinterviewer.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway")
public class AuthProperties {

    /**
     * 白名单路径列表(无需认证)
     */
    private List<String> whiteList = new ArrayList<>();
}
