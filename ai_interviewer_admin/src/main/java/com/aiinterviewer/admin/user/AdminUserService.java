package com.aiinterviewer.admin.user;

import com.aiinterviewer.admin.audit.annotation.AdminAudit;
import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.user.mapper.AdminUserMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int DISABLED_STATUS = 0;
    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 20L;
    private static final long MAX_SIZE = 100L;
    private static final long MAX_CURRENT = 1_000_000L;

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<AdminUserListItem> listUsers(AdminUserQuery query) {
        AdminUserQuery safeQuery = query == null ? new AdminUserQuery() : query;
        long current = safeQuery.normalizedCurrent();
        long size = safeQuery.normalizedSize();
        Long total = adminUserMapper.countUsers(safeQuery);
        List<AdminUserListItem> records = adminUserMapper.selectUsers(safeQuery, size, safeOffset(current, size));
        return PageResult.of(current, size, total == null ? 0L : total, records);
    }

    @Transactional
    @AdminAudit(module = "USER", operation = "DISABLE", targetType = "USER", targetIdParam = "userId")
    public void disableUser(Long userId) {
        ensureUserExists(userId);
        int updated = adminUserMapper.disableUser(userId);
        if (updated == 0) {
            throw new AdminBusinessException(500, "用户禁用失败");
        }
    }

    @Transactional
    @AdminAudit(module = "USER", operation = "RESET_PASSWORD", targetType = "USER", targetIdParam = "userId")
    public void resetPassword(Long userId, String newPassword) {
        ensureUserExists(userId);
        if (!StringUtils.hasText(newPassword)) {
            throw new AdminBusinessException(400, "新密码不能为空");
        }
        int updated = adminUserMapper.resetPassword(userId, passwordEncoder.encode(newPassword));
        if (updated == 0) {
            throw new AdminBusinessException(500, "密码重置失败");
        }
    }

    private void ensureUserExists(Long userId) {
        if (userId == null) {
            throw new AdminBusinessException(400, "用户ID不能为空");
        }
        Integer count = adminUserMapper.countExistingUser(userId);
        if (count == null || count == 0) {
            throw new AdminBusinessException(404, "用户不存在或已删除");
        }
    }

    private long safeOffset(long current, long size) {
        try {
            return Math.multiplyExact(current - 1, size);
        } catch (ArithmeticException ex) {
            return (MAX_CURRENT - 1) * MAX_SIZE;
        }
    }

    @Data
    public static class AdminUserQuery {

        private String username;
        private String email;
        private String phone;
        private Integer status;
        private Long current = DEFAULT_CURRENT;
        private Long size = DEFAULT_SIZE;

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

    @Data
    public static class AdminUserListItem {

        private Long id;
        private String username;
        private String email;
        private String phone;
        private String nickname;
        private String avatarUrl;
        private Integer status;
        private LocalDateTime lastLoginTime;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
