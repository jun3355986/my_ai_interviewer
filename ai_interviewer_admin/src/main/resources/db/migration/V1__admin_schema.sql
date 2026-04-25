CREATE TABLE IF NOT EXISTS t_admin_menu (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT,
    menu_code VARCHAR(100) NOT NULL,
    menu_name VARCHAR(100) NOT NULL,
    path VARCHAR(255),
    component VARCHAR(255),
    icon VARCHAR(100),
    sort_order INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_admin_menu_code UNIQUE (menu_code)
);

CREATE TABLE IF NOT EXISTS t_admin_permission (
    id BIGSERIAL PRIMARY KEY,
    menu_id BIGINT,
    permission_code VARCHAR(120) NOT NULL,
    permission_name VARCHAR(120) NOT NULL,
    resource_type VARCHAR(50) NOT NULL DEFAULT 'API',
    resource_path VARCHAR(255),
    http_method VARCHAR(20),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_admin_permission_code UNIQUE (permission_code),
    CONSTRAINT fk_admin_permission_menu FOREIGN KEY (menu_id) REFERENCES t_admin_menu (id)
);

CREATE TABLE IF NOT EXISTS t_admin_role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(100) NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_admin_role_permission UNIQUE (role_code, permission_id),
    CONSTRAINT fk_admin_role_permission_permission FOREIGN KEY (permission_id) REFERENCES t_admin_permission (id)
);

CREATE TABLE IF NOT EXISTS t_admin_user_role (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    role_code VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_admin_user_role UNIQUE (admin_user_id, role_code)
);

CREATE TABLE IF NOT EXISTS t_question_bank (
    id BIGSERIAL PRIMARY KEY,
    question_code VARCHAR(100) NOT NULL,
    question_type VARCHAR(50) NOT NULL,
    difficulty VARCHAR(50),
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    reference_answer TEXT,
    analysis TEXT,
    source VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_question_bank_code UNIQUE (question_code)
);

CREATE TABLE IF NOT EXISTS t_question_tag (
    id BIGSERIAL PRIMARY KEY,
    tag_code VARCHAR(100) NOT NULL,
    tag_name VARCHAR(100) NOT NULL,
    tag_type VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    color VARCHAR(30),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_question_tag_code UNIQUE (tag_code)
);

CREATE TABLE IF NOT EXISTS t_question_tag_relation (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_question_tag_relation UNIQUE (question_id, tag_id),
    CONSTRAINT fk_question_tag_relation_question FOREIGN KEY (question_id) REFERENCES t_question_bank (id),
    CONSTRAINT fk_question_tag_relation_tag FOREIGN KEY (tag_id) REFERENCES t_question_tag (id)
);

CREATE TABLE IF NOT EXISTS t_question_import_batch (
    id BIGSERIAL PRIMARY KEY,
    batch_no VARCHAR(100) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_url TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    imported_by BIGINT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_question_import_batch_no UNIQUE (batch_no)
);

CREATE TABLE IF NOT EXISTS t_question_vector_sync_record (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    sync_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    vector_store_id VARCHAR(255),
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_question_vector_sync_question UNIQUE (question_id),
    CONSTRAINT fk_question_vector_sync_question FOREIGN KEY (question_id) REFERENCES t_question_bank (id)
);

CREATE TABLE IF NOT EXISTS t_notification_template (
    id BIGSERIAL PRIMARY KEY,
    template_code VARCHAR(100) NOT NULL,
    template_name VARCHAR(120) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    subject VARCHAR(255),
    content TEXT NOT NULL,
    variables JSONB NOT NULL DEFAULT '[]'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_notification_template_code UNIQUE (template_code)
);

CREATE TABLE IF NOT EXISTS t_system_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(150) NOT NULL,
    config_value TEXT,
    config_type VARCHAR(50) NOT NULL DEFAULT 'STRING',
    config_group VARCHAR(100) NOT NULL DEFAULT 'DEFAULT',
    description TEXT,
    encrypted BOOLEAN NOT NULL DEFAULT FALSE,
    editable BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_system_config_key UNIQUE (config_key)
);

CREATE TABLE IF NOT EXISTS t_interview_strategy_config (
    id BIGSERIAL PRIMARY KEY,
    strategy_code VARCHAR(100) NOT NULL,
    strategy_name VARCHAR(120) NOT NULL,
    job_type VARCHAR(100),
    difficulty VARCHAR(50),
    question_count INTEGER NOT NULL DEFAULT 0,
    duration_minutes INTEGER NOT NULL DEFAULT 0,
    prompt_template TEXT,
    scoring_rule JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_interview_strategy_config_code UNIQUE (strategy_code)
);

CREATE TABLE IF NOT EXISTS t_admin_operation_log (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT,
    admin_username VARCHAR(100),
    operation_type VARCHAR(80) NOT NULL,
    module VARCHAR(100) NOT NULL,
    request_method VARCHAR(20),
    request_path VARCHAR(255),
    request_params JSONB,
    response_status INTEGER,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    client_ip VARCHAR(64),
    user_agent TEXT,
    duration_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_menu_parent_id ON t_admin_menu (parent_id);
CREATE INDEX IF NOT EXISTS idx_admin_permission_menu_id ON t_admin_permission (menu_id);
CREATE INDEX IF NOT EXISTS idx_admin_role_permission_role_code ON t_admin_role_permission (role_code);
CREATE INDEX IF NOT EXISTS idx_admin_user_role_admin_user_id ON t_admin_user_role (admin_user_id);
CREATE INDEX IF NOT EXISTS idx_question_bank_status ON t_question_bank (status);
CREATE INDEX IF NOT EXISTS idx_question_tag_type ON t_question_tag (tag_type);
CREATE INDEX IF NOT EXISTS idx_question_import_batch_status ON t_question_import_batch (status);
CREATE INDEX IF NOT EXISTS idx_question_vector_sync_status ON t_question_vector_sync_record (sync_status);
CREATE INDEX IF NOT EXISTS idx_notification_template_channel ON t_notification_template (channel);
CREATE INDEX IF NOT EXISTS idx_system_config_group ON t_system_config (config_group);
CREATE INDEX IF NOT EXISTS idx_interview_strategy_enabled ON t_interview_strategy_config (enabled);
CREATE INDEX IF NOT EXISTS idx_admin_operation_log_user_id ON t_admin_operation_log (admin_user_id);
CREATE INDEX IF NOT EXISTS idx_admin_operation_log_created_at ON t_admin_operation_log (created_at);
