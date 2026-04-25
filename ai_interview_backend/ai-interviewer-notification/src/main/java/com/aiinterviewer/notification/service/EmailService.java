package com.aiinterviewer.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@aiinterviewer.com}")
    private String fromEmail;

    /**
     * 发送简单邮件
     */
    public void sendSimpleEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("邮件发送成功: {}", to);
        } catch (Exception e) {
            log.error("邮件发送失败: {}", to, e);
        }
    }

    /**
     * 发送面试完成通知
     */
    public void sendInterviewCompletedEmail(String to, String candidateName, String jobTitle) {
        String subject = "面试已完成 - " + jobTitle;
        String content = String.format("""
            您好 %s,

            您的面试已经完成。

            职位: %s

            评估报告已生成,请登录系统查看详情。

            祝您求职顺利!

            AI面试官
            """, candidateName, jobTitle);
        sendSimpleEmail(to, subject, content);
    }

    /**
     * 发送报告生成通知
     */
    public void sendReportReadyEmail(String to, String candidateName, int score) {
        String subject = "评估报告已生成";
        String content = String.format("""
            您好 %s,

            您的面试评估报告已生成。

            综合评分: %d 分

            请登录系统查看详细报告。

            AI面试官
            """, candidateName, score);
        sendSimpleEmail(to, subject, content);
    }
}
