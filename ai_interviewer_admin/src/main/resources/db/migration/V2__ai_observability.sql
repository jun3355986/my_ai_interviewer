CREATE TABLE IF NOT EXISTS t_ai_trace (
    id UUID PRIMARY KEY,
    request_id VARCHAR(100),
    user_id BIGINT,
    username VARCHAR(120),
    session_id VARCHAR(100),
    python_session_id VARCHAR(100),
    business_type VARCHAR(80) NOT NULL,
    entrypoint VARCHAR(120),
    status VARCHAR(30) NOT NULL,
    error_code VARCHAR(100),
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    duration_ms BIGINT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_ai_trace_step (
    id UUID PRIMARY KEY,
    trace_id UUID NOT NULL REFERENCES t_ai_trace (id),
    step_order INTEGER NOT NULL,
    step_type VARCHAR(80) NOT NULL,
    step_name VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    duration_ms BIGINT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_ai_llm_call (
    id UUID PRIMARY KEY,
    trace_id UUID NOT NULL REFERENCES t_ai_trace (id),
    step_id UUID REFERENCES t_ai_trace_step (id),
    call_type VARCHAR(100) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    model VARCHAR(160) NOT NULL,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    fallback_from_model VARCHAR(160),
    status VARCHAR(30) NOT NULL,
    prompt_tokens BIGINT,
    completion_tokens BIGINT,
    total_tokens BIGINT,
    token_source VARCHAR(30) NOT NULL,
    prompt_cache_hit_tokens BIGINT,
    prompt_cache_miss_tokens BIGINT,
    prompt_cache_hit_rate NUMERIC(8,6),
    cache_reported_by_provider BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms BIGINT,
    prompt_text TEXT,
    response_text TEXT,
    raw_usage_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_ai_observability_access_log (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT,
    trace_id UUID,
    llm_call_id UUID,
    access_type VARCHAR(40) NOT NULL,
    request_uri VARCHAR(500),
    ip_address VARCHAR(100),
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_trace_started_at ON t_ai_trace (started_at);
CREATE INDEX IF NOT EXISTS idx_ai_trace_session_id ON t_ai_trace (session_id);
CREATE INDEX IF NOT EXISTS idx_ai_trace_user_id ON t_ai_trace (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_trace_status ON t_ai_trace (status);

CREATE INDEX IF NOT EXISTS idx_ai_trace_step_trace_id ON t_ai_trace_step (trace_id);
CREATE INDEX IF NOT EXISTS idx_ai_trace_step_started_at ON t_ai_trace_step (started_at);
CREATE INDEX IF NOT EXISTS idx_ai_trace_step_status ON t_ai_trace_step (status);

CREATE INDEX IF NOT EXISTS idx_ai_llm_call_trace_id ON t_ai_llm_call (trace_id);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_step_id ON t_ai_llm_call (step_id);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_started_at ON t_ai_llm_call (started_at);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_status ON t_ai_llm_call (status);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_provider ON t_ai_llm_call (provider);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_model ON t_ai_llm_call (model);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_call_type ON t_ai_llm_call (call_type);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_cache_reported_by_provider ON t_ai_llm_call (cache_reported_by_provider);

CREATE INDEX IF NOT EXISTS idx_ai_observability_access_log_trace_id
    ON t_ai_observability_access_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_ai_observability_access_log_llm_call_id
    ON t_ai_observability_access_log (llm_call_id);
CREATE INDEX IF NOT EXISTS idx_ai_observability_access_log_created_at
    ON t_ai_observability_access_log (created_at);

INSERT INTO t_admin_menu (menu_code, menu_name, path, component, icon, sort_order, visible, enabled)
VALUES ('ai_observability', 'AI 观测中心', '/ai-observability', 'AiObservability', 'activity', 60, TRUE, TRUE)
ON CONFLICT (menu_code) WHERE deleted_at IS NULL
DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO t_admin_permission
    (menu_id, permission_code, permission_name, resource_type, resource_path, http_method, enabled, description)
SELECT id, 'AI_OBSERVABILITY_VIEW', 'AI 观测查看', 'API', '/admin/ai-observability/**', 'GET', TRUE,
       '查看 AI 调用观测列表和详情'
FROM t_admin_menu
WHERE menu_code = 'ai_observability' AND deleted_at IS NULL
ON CONFLICT (permission_code) WHERE deleted_at IS NULL
DO UPDATE SET
    menu_id = EXCLUDED.menu_id,
    permission_name = EXCLUDED.permission_name,
    resource_type = EXCLUDED.resource_type,
    resource_path = EXCLUDED.resource_path,
    http_method = EXCLUDED.http_method,
    enabled = EXCLUDED.enabled,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO t_admin_permission
    (menu_id, permission_code, permission_name, resource_type, resource_path, http_method, enabled, description)
SELECT id, 'AI_OBSERVABILITY_RAW_READ', 'AI 观测原文读取', 'API', '/admin/ai-observability/**/raw', 'GET', TRUE,
       '读取 AI 调用完整 prompt 和 response 原文'
FROM t_admin_menu
WHERE menu_code = 'ai_observability' AND deleted_at IS NULL
ON CONFLICT (permission_code) WHERE deleted_at IS NULL
DO UPDATE SET
    menu_id = EXCLUDED.menu_id,
    permission_name = EXCLUDED.permission_name,
    resource_type = EXCLUDED.resource_type,
    resource_path = EXCLUDED.resource_path,
    http_method = EXCLUDED.http_method,
    enabled = EXCLUDED.enabled,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO t_admin_permission
    (menu_id, permission_code, permission_name, resource_type, resource_path, http_method, enabled, description)
SELECT id, 'AI_OBSERVABILITY_STATS', 'AI 观测统计', 'API', '/admin/ai-observability/stats', 'GET', TRUE,
       '查看 AI 调用 token、缓存、耗时和失败率统计'
FROM t_admin_menu
WHERE menu_code = 'ai_observability' AND deleted_at IS NULL
ON CONFLICT (permission_code) WHERE deleted_at IS NULL
DO UPDATE SET
    menu_id = EXCLUDED.menu_id,
    permission_name = EXCLUDED.permission_name,
    resource_type = EXCLUDED.resource_type,
    resource_path = EXCLUDED.resource_path,
    http_method = EXCLUDED.http_method,
    enabled = EXCLUDED.enabled,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;
