package com.aiinterviewer.admin.audit;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.audit.entity.AdminOperationLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(adminAudit)")
    public Object audit(ProceedingJoinPoint joinPoint, AdminAudit adminAudit) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            writeLog(joinPoint, adminAudit, AuditLogService.RESULT_SUCCESS, null, elapsed(start));
            return result;
        } catch (Throwable ex) {
            try {
                writeLog(joinPoint, adminAudit, AuditLogService.RESULT_FAILED, ex, elapsed(start));
            } catch (RuntimeException auditException) {
                ex.addSuppressed(auditException);
            }
            throw ex;
        }
    }

    private void writeLog(
            ProceedingJoinPoint joinPoint,
            AdminAudit adminAudit,
            String result,
            Throwable error,
            long durationMs) {
        HttpServletRequest request = currentRequest();
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminUserId(currentAdminUserId());
        log.setModule(adminAudit.module());
        log.setOperation(adminAudit.operation());
        log.setTargetType(adminAudit.targetType());
        log.setTargetId(resolveTargetId(joinPoint, adminAudit));
        log.setRequestUri(request == null ? null : request.getRequestURI());
        log.setRequestMethod(request == null ? null : request.getMethod());
        log.setRequestParams(toRequestParamsJson(request));
        log.setIpAddress(resolveIpAddress(request));
        log.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        log.setResult(result);
        log.setErrorMessage(error == null ? null : abbreviate(error.getMessage()));
        log.setDurationMs(durationMs);
        auditLogService.write(log);
    }

    private Long currentAdminUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long adminUserId) {
            return adminUserId;
        }
        if (principal instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String resolveTargetId(ProceedingJoinPoint joinPoint, AdminAudit adminAudit) {
        if (hasText(adminAudit.targetId())) {
            return adminAudit.targetId();
        }
        if (!hasText(adminAudit.targetIdParam())) {
            return null;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                if (adminAudit.targetIdParam().equals(parameterNames[i])) {
                    return stringify(args[i]);
                }
            }
        }
        if (adminAudit.targetIdParam().startsWith("arg")) {
            return resolveByArgIndex(adminAudit.targetIdParam(), args);
        }
        return args.length == 1 ? stringify(args[0]) : null;
    }

    private String resolveByArgIndex(String targetIdParam, Object[] args) {
        try {
            int index = Integer.parseInt(targetIdParam.substring(3));
            if (index >= 0 && index < args.length) {
                return stringify(args[index]);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private String toRequestParamsJson(HttpServletRequest request) {
        if (request == null || request.getParameterMap().isEmpty()) {
            return null;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            Object value = values == null || values.length != 1 ? values : values[0];
            params.put(key, value);
        });
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String resolveIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (hasText(forwardedFor)) {
            return Arrays.stream(forwardedFor.split(","))
                    .map(String::trim)
                    .filter(this::hasText)
                    .findFirst()
                    .orElse(request.getRemoteAddr());
        }
        String realIp = request.getHeader("X-Real-IP");
        return hasText(realIp) ? realIp : request.getRemoteAddr();
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long elapsed(long start) {
        return Math.max(0L, System.currentTimeMillis() - start);
    }

    private String abbreviate(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
