package com.aiinterviewer.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 邮件配置
 */
@Configuration
@ConfigurationProperties(prefix = "spring.mail")
public class MailConfig {
    private String host;
    private int port;
    private String username;
    private String password;
    private String propertiesMailSmtpAuth;
    private String propertiesMailSmtpStarttlsEnable;

    // getters and setters
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPropertiesMailSmtpAuth() { return propertiesMailSmtpAuth; }
    public void setPropertiesMailSmtpAuth(String propertiesMailSmtpAuth) { this.propertiesMailSmtpAuth = propertiesMailSmtpAuth; }
    public String getPropertiesMailSmtpStarttlsEnable() { return propertiesMailSmtpStarttlsEnable; }
    public void setPropertiesMailSmtpStarttlsEnable(String propertiesMailSmtpStarttlsEnable) { this.propertiesMailSmtpStarttlsEnable = propertiesMailSmtpStarttlsEnable; }
}
