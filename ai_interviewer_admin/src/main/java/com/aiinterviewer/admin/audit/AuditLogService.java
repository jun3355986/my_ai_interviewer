package com.aiinterviewer.admin.audit;

import com.aiinterviewer.admin.audit.entity.AdminOperationLog;
import com.aiinterviewer.admin.common.model.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";

    private static final int MAX_TARGET_TYPE_LENGTH = 100;
    private static final int MAX_TARGET_ID_LENGTH = 100;
    private static final int MAX_REQUEST_URI_LENGTH = 500;
    private static final int MAX_REQUEST_METHOD_LENGTH = 20;
    private static final int MAX_IP_ADDRESS_LENGTH = 100;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final int MAX_MODULE_LENGTH = 100;
    private static final int MAX_OPERATION_LENGTH = 100;
    private static final int MAX_RESULT_LENGTH = 30;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;
    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 20L;
    private static final long MAX_SIZE = 100L;
    private static final long MAX_CURRENT = 1_000_000L;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AdminOperationLog log) {
        jdbcTemplate.update(
                """
                INSERT INTO t_admin_operation_log
                    (admin_user_id, module, operation, target_type, target_id, request_uri, request_method,
                     request_params, before_snapshot, after_snapshot, ip_address, user_agent, result,
                     error_message, duration_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb),
                        ?, ?, ?, ?, ?)
                """,
                log.getAdminUserId(),
                truncate(log.getModule(), MAX_MODULE_LENGTH),
                truncate(log.getOperation(), MAX_OPERATION_LENGTH),
                blankToNull(truncate(log.getTargetType(), MAX_TARGET_TYPE_LENGTH)),
                blankToNull(truncate(log.getTargetId(), MAX_TARGET_ID_LENGTH)),
                truncate(log.getRequestUri(), MAX_REQUEST_URI_LENGTH),
                truncate(log.getRequestMethod(), MAX_REQUEST_METHOD_LENGTH),
                sanitizeJson(log.getRequestParams()),
                sanitizeJson(log.getBeforeSnapshot()),
                sanitizeJson(log.getAfterSnapshot()),
                truncate(log.getIpAddress(), MAX_IP_ADDRESS_LENGTH),
                truncate(log.getUserAgent(), MAX_USER_AGENT_LENGTH),
                truncate(log.getResult(), MAX_RESULT_LENGTH),
                truncate(log.getErrorMessage(), MAX_ERROR_MESSAGE_LENGTH),
                log.getDurationMs());
    }

    public PageResult<AdminOperationLog> listLogs(AuditLogQuery query) {
        AuditLogQuery safeQuery = query == null ? new AuditLogQuery() : query;
        long current = safeQuery.normalizedCurrent();
        long size = safeQuery.normalizedSize();
        List<Object> args = new ArrayList<>();
        String whereClause = buildWhereClause(safeQuery, args);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_admin_operation_log" + whereClause,
                Long.class,
                args.toArray());
        long offset = safeOffset(current, size);
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(size);
        listArgs.add(offset);
        List<AdminOperationLog> records = jdbcTemplate.query(
                """
                SELECT id, admin_user_id, module, operation, target_type, target_id, request_uri,
                       request_method, request_params::text, before_snapshot::text, after_snapshot::text,
                       ip_address, user_agent, result, error_message, duration_ms, created_at
                FROM t_admin_operation_log
                """
                        + whereClause
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                this::mapLog,
                listArgs.toArray());

        return PageResult.of(current, size, total == null ? 0L : total, records);
    }

    private String buildWhereClause(AuditLogQuery query, List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (query.getAdminUserId() != null) {
            where.append(" AND admin_user_id = ?");
            args.add(query.getAdminUserId());
        }
        if (hasText(query.getModule())) {
            where.append(" AND module = ?");
            args.add(query.getModule());
        }
        if (hasText(query.getOperation())) {
            where.append(" AND operation = ?");
            args.add(query.getOperation());
        }
        if (query.getStartTime() != null) {
            where.append(" AND created_at >= ?");
            args.add(query.getStartTime());
        }
        if (query.getEndTime() != null) {
            where.append(" AND created_at <= ?");
            args.add(query.getEndTime());
        }
        return where.toString();
    }

    private AdminOperationLog mapLog(ResultSet rs, int rowNum) throws SQLException {
        AdminOperationLog log = new AdminOperationLog();
        log.setId(rs.getLong("id"));
        log.setAdminUserId(readNullableLong(rs, "admin_user_id"));
        log.setModule(rs.getString("module"));
        log.setOperation(rs.getString("operation"));
        log.setTargetType(rs.getString("target_type"));
        log.setTargetId(rs.getString("target_id"));
        log.setRequestUri(rs.getString("request_uri"));
        log.setRequestMethod(rs.getString("request_method"));
        log.setRequestParams(rs.getString("request_params"));
        log.setBeforeSnapshot(rs.getString("before_snapshot"));
        log.setAfterSnapshot(rs.getString("after_snapshot"));
        log.setIpAddress(rs.getString("ip_address"));
        log.setUserAgent(rs.getString("user_agent"));
        log.setResult(rs.getString("result"));
        log.setErrorMessage(rs.getString("error_message"));
        log.setDurationMs(readNullableLong(rs, "duration_ms"));
        log.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        return log;
    }

    private Long readNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String sanitizeJson(String value) {
        if (value == null) {
            return null;
        }
        try {
            objectMapper.readTree(value);
            return value;
        } catch (JsonProcessingException ex) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException ignored) {
                return null;
            }
        }
    }

    private long safeOffset(long current, long size) {
        try {
            return Math.multiplyExact(current - 1, size);
        } catch (ArithmeticException ex) {
            return (MAX_CURRENT - 1) * MAX_SIZE;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String blankToNull(String value) {
        return hasText(value) ? value : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Data
    public static class AuditLogQuery {

        private Long adminUserId;
        private String module;
        private String operation;
        private OffsetDateTime startTime;
        private OffsetDateTime endTime;
        private Long current = 1L;
        private Long size = 20L;

        long normalizedCurrent() {
            if (current == null || current < 1) {
                return DEFAULT_CURRENT;
            }
            return Math.min(current, MAX_CURRENT);
        }

        long normalizedSize() {
            if (size == null || size < 1) {
                return DEFAULT_SIZE;
            }
            return Math.min(size, MAX_SIZE);
        }
    }
}
