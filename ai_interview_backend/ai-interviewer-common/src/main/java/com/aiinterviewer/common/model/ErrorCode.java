package com.aiinterviewer.common.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用错误 1000-1999
    SUCCESS(200, "操作成功"),
    SYSTEM_ERROR(1000, "系统错误"),
    PARAM_ERROR(1001, "参数错误"),
    PARAM_MISSING(1002, "参数缺失"),
    RESOURCE_NOT_FOUND(1003, "资源不存在"),
    OPERATION_FAILED(1004, "操作失败"),
    RATE_LIMIT_EXCEEDED(1005, "请求过于频繁"),
    SERVICE_UNAVAILABLE(1006, "服务不可用"),

    // 认证错误 2000-2999
    UNAUTHORIZED(2000, "未授权"),
    TOKEN_EXPIRED(2001, "Token已过期"),
    TOKEN_INVALID(2002, "Token无效"),
    ACCESS_DENIED(2003, "拒绝访问"),
    LOGIN_FAILED(2004, "登录失败"),
    USER_NOT_FOUND(2005, "用户不存在"),
    PASSWORD_ERROR(2006, "密码错误"),
    USER_DISABLED(2007, "用户已禁用"),
    USER_EXISTS(2008, "用户已存在"),
    EMAIL_EXISTS(2009, "邮箱已被注册"),
    PHONE_EXISTS(2010, "手机号已被注册"),

    // 简历相关 3000-3999
    RESUME_NOT_FOUND(3000, "简历不存在"),
    RESUME_PARSE_FAILED(3001, "简历解析失败"),
    RESUME_UPLOAD_FAILED(3002, "简历上传失败"),
    FILE_TYPE_NOT_SUPPORTED(3003, "文件类型不支持"),
    FILE_TOO_LARGE(3004, "文件大小超出限制"),

    // 面试相关 4000-4999
    SESSION_NOT_FOUND(4000, "面试会话不存在"),
    SESSION_EXPIRED(4001, "面试会话已过期"),
    SESSION_COMPLETED(4002, "面试已结束"),
    SESSION_IN_PROGRESS(4003, "面试进行中"),
    AI_SERVICE_ERROR(4004, "AI服务异常"),
    SSE_CONNECTION_FAILED(4005, "SSE连接失败"),

    // 职位相关 5000-5999
    JOB_NOT_FOUND(5000, "职位不存在"),
    JOB_CLOSED(5001, "职位已关闭"),

    // 评估相关 6000-6999
    EVALUATION_NOT_FOUND(6000, "评估报告不存在"),
    EVALUATION_NOT_READY(6001, "评估报告尚未生成");

    private final Integer code;
    private final String message;
}
