package com.aiinterviewer.common.constant;

/**
 * 公共常量
 */
public interface CommonConstants {

    /**
     * 请求头中的Token前缀
     */
    String TOKEN_PREFIX = "Bearer ";

    /**
     * 请求头中的Authorization
     */
    String AUTHORIZATION_HEADER = "Authorization";

    /**
     * 用户ID请求头(网关传递)
     */
    String USER_ID_HEADER = "X-User-Id";

    /**
     * 用户名请求头(网关传递)
     */
    String USERNAME_HEADER = "X-User-Name";

    /**
     * 用户角色请求头(网关传递)
     */
    String USER_ROLES_HEADER = "X-User-Roles";

    /**
     * 请求ID(链路追踪)
     */
    String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 默认分页大小
     */
    int DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大分页大小
     */
    int MAX_PAGE_SIZE = 100;
}
