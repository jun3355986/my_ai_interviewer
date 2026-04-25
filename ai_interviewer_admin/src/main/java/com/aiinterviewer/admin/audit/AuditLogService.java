package com.aiinterviewer.admin.audit;

import com.aiinterviewer.admin.audit.entity.AdminOperationLog;
import com.aiinterviewer.admin.common.model.PageResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";

    private final JdbcTemplate jdbcTemplate;

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
                log.getModule(),
                log.getOperation(),
                blankToNull(log.getTargetType()),
                blankToNull(log.getTargetId()),
                log.getRequestUri(),
                log.getRequestMethod(),
                log.getRequestParams(),
                log.getBeforeSnapshot(),
                log.getAfterSnapshot(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getResult(),
                log.getErrorMessage(),
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
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(size);
        listArgs.add((current - 1) * size);
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
            return current == null || current < 1 ? 1L : current;
        }

        long normalizedSize() {
            if (size == null || size < 1) {
                return 20L;
            }
            return Math.min(size, 100L);
        }
    }
}
