package com.aiinterviewer.admin.portal;

import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理端门户的身份解析。
 *
 * 管理员账号本身就落在共享库的 t_user 表（由角色体系区分），
 * 因此发起/查询面试时直接以 adminUserId 作为面试体系的 userId 注入，
 * interview/evaluation 服务的所有权校验天然成立，无需额外绑定账号。
 */
@Service
@RequiredArgsConstructor
public class PortalIdentityResolver {

    private final JdbcTemplate jdbcTemplate;

    public PortalIdentity requireIdentity(Long adminUserId) {
        if (adminUserId == null) {
            throw new AdminBusinessException(401, "未认证的管理员身份");
        }
        List<PortalIdentity> identities = jdbcTemplate.query(
                """
                SELECT id, username, nickname
                FROM t_user
                WHERE id = ? AND deleted_at IS NULL
                LIMIT 1
                """,
                (rs, rowNum) -> new PortalIdentity(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("nickname")),
                adminUserId);
        if (identities.isEmpty()) {
            throw new AdminBusinessException(401, "管理员账号不存在");
        }
        PortalIdentity identity = identities.getFirst();
        return new PortalIdentity(
                identity.id(),
                identity.username(),
                StringUtils.hasText(identity.displayName()) ? identity.displayName() : identity.username());
    }

    public record PortalIdentity(Long id, String username, String displayName) {
    }
}
