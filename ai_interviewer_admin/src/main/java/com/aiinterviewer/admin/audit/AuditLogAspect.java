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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AuditLogAspect {

    private static final int MAX_TARGET_ID_LENGTH = 100;
    private static final int MAX_PARAM_KEY_LENGTH = 256;
    private static final int MAX_PARAM_VALUE_LENGTH = 256;
    private static final int MAX_PARAM_ARRAY_VALUES = 5;
    private static final int MAX_REQUEST_PARAMS_JSON_LENGTH = 16_000;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;
    private static final String MASKED_VALUE = "******";
    private static final String TRUNCATED_MARKER = "_truncated";
    private static final String[] SENSITIVE_PARAM_KEYWORDS = {
        "password", "passwd", "token", "secret", "authorization", "credential"
    };

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(adminAudit)")
    public Object audit(ProceedingJoinPoint joinPoint, AdminAudit adminAudit) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            try {
                writeLog(joinPoint, adminAudit, AuditLogService.RESULT_SUCCESS, null, elapsed(start), result);
            } catch (RuntimeException auditException) {
                log.warn("Admin audit write failed after successful operation", auditException);
            }
            return result;
        } catch (Throwable ex) {
            try {
                writeLog(joinPoint, adminAudit, AuditLogService.RESULT_FAILED, ex, elapsed(start), null);
            } catch (RuntimeException auditException) {
                log.warn("Admin audit write failed after failed operation", auditException);
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
            long durationMs,
            Object methodResult) {
        HttpServletRequest request = currentRequest();
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminUserId(currentAdminUserId());
        log.setModule(adminAudit.module());
        log.setOperation(adminAudit.operation());
        log.setTargetType(adminAudit.targetType());
        log.setTargetId(resolveTargetId(joinPoint, adminAudit, methodResult));
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

    private String resolveTargetId(ProceedingJoinPoint joinPoint, AdminAudit adminAudit, Object methodResult) {
        if (adminAudit.targetIdFromResult()) {
            return truncate(stringify(methodResult), MAX_TARGET_ID_LENGTH);
        }
        if (hasText(adminAudit.targetId())) {
            return truncate(adminAudit.targetId(), MAX_TARGET_ID_LENGTH);
        }
        if (!hasText(adminAudit.targetIdParam())) {
            return null;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (adminAudit.targetIdParam().startsWith("arg")) {
            return truncate(resolveByArgIndex(adminAudit.targetIdParam(), args), MAX_TARGET_ID_LENGTH);
        }
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                if (adminAudit.targetIdParam().equals(parameterNames[i])) {
                    return truncate(stringify(args[i]), MAX_TARGET_ID_LENGTH);
                }
            }
        }
        log.debug("Admin audit targetIdParam '{}' did not match method arguments", adminAudit.targetIdParam());
        return null;
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
        boolean truncated = false;
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String key = truncate(entry.getKey(), MAX_PARAM_KEY_LENGTH);
            Object value = sanitizeParamValue(entry.getKey(), entry.getValue());
            Map<String, Object> candidate = new LinkedHashMap<>(params);
            candidate.put(key, value);
            if (toJson(candidate).length() > MAX_REQUEST_PARAMS_JSON_LENGTH) {
                truncated = true;
                break;
            }
            params.put(key, value);
        }
        if (truncated) {
            params.put(TRUNCATED_MARKER, true);
        }
        String json = toJson(params);
        if (json.length() <= MAX_REQUEST_PARAMS_JSON_LENGTH) {
            return json;
        }
        return "{\"" + TRUNCATED_MARKER + "\":true}";
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

    private Object sanitizeParamValue(String key, String[] values) {
        if (isSensitiveKey(key)) {
            return MASKED_VALUE;
        }
        if (values == null) {
            return null;
        }
        if (values.length == 1) {
            return truncate(values[0], MAX_PARAM_VALUE_LENGTH);
        }
        int valueCount = Math.min(values.length, MAX_PARAM_ARRAY_VALUES);
        String[] sanitized = new String[values.length > MAX_PARAM_ARRAY_VALUES ? valueCount + 1 : valueCount];
        for (int i = 0; i < valueCount; i++) {
            sanitized[i] = truncate(values[i], MAX_PARAM_VALUE_LENGTH);
        }
        if (values.length > MAX_PARAM_ARRAY_VALUES) {
            sanitized[valueCount] = "...";
        }
        return sanitized;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lowerKey = key.toLowerCase();
        return Arrays.stream(SENSITIVE_PARAM_KEYWORDS).anyMatch(lowerKey::contains);
    }

    private String toJson(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
