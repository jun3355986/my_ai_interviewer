-- ============================================
-- AI面试官 PostgreSQL 数据库初始化脚本
-- ============================================

-- 创建数据库(如果不存在)
-- CREATE DATABASE ai_interviewer;

-- ============================================
-- 用户服务相关表
-- ============================================

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    avatar_url VARCHAR(500),
    status SMALLINT DEFAULT 1,          -- 1:正常 0:禁用 2:待验证
    last_login_time TIMESTAMP,
    last_login_ip VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP                -- 软删除
);

-- 角色表
CREATE TABLE IF NOT EXISTS t_role (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS t_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES t_user(id),
    role_id BIGINT NOT NULL REFERENCES t_role(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, role_id)
);

-- OAuth2绑定表
CREATE TABLE IF NOT EXISTS t_oauth_binding (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES t_user(id),
    provider VARCHAR(50) NOT NULL,      -- github, wechat, google
    provider_user_id VARCHAR(255) NOT NULL,
    access_token VARCHAR(500),
    refresh_token VARCHAR(500),
    token_expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider, provider_user_id)
);

-- ============================================
-- 简历服务相关表
-- ============================================

-- 简历表
CREATE TABLE IF NOT EXISTS t_resume (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES t_user(id),
    file_name VARCHAR(255) NOT NULL,        -- 存储文件名(UUID)
    original_file_name VARCHAR(255),        -- 原始文件名
    file_path VARCHAR(500),                 -- MinIO存储路径
    file_size BIGINT,                       -- 文件大小(bytes)
    content_type VARCHAR(100),              -- 文件MIME类型
    parsed_content JSONB,                   -- 解析后的结构化内容
    raw_text TEXT,                          -- 原始文本内容(用于搜索)
    parse_status SMALLINT DEFAULT 0,        -- 0:未解析 1:解析中 2:解析成功 3:解析失败
    parse_error TEXT,                       -- 解析失败原因
    is_default BOOLEAN DEFAULT FALSE,       -- 是否默认简历
    version_count INT DEFAULT 1,            -- 版本数量
    parsed_at TIMESTAMP,                    -- 解析完成时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 简历版本历史表
CREATE TABLE IF NOT EXISTS t_resume_version (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL REFERENCES t_resume(id) ON DELETE CASCADE,
    version INT NOT NULL,                   -- 版本号
    file_path VARCHAR(500),                 -- 文件路径
    file_name VARCHAR(255),                 -- 文件名
    file_size BIGINT,                       -- 文件大小
    parsed_content JSONB,                   -- 解析内容快照
    operation_type VARCHAR(20),             -- UPLOAD, UPDATE, REPARSE
    operator_id BIGINT,                     -- 操作人ID
    remark TEXT,                            -- 备注
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 职位服务相关表
-- ============================================

-- 职位表
CREATE TABLE IF NOT EXISTS t_job (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(100) NOT NULL,        -- 职位名称
    company VARCHAR(200),               -- 公司名称
    department VARCHAR(100),            -- 部门
    location VARCHAR(100),              -- 工作地点
    job_type VARCHAR(50),               -- full-time, part-time, contract
    experience_required VARCHAR(50),    -- 经验要求
    education_required VARCHAR(50),     -- 学历要求
    salary_min DECIMAL(10,2),           -- 薪资范围(最低)
    salary_max DECIMAL(10,2),           -- 薪资范围(最高)
    description TEXT,                   -- 职位描述(JD)
    requirements TEXT,                  -- 岗位要求
    skills JSONB,                       -- 技能标签 ["Java", "Spring"]
    status SMALLINT DEFAULT 1,          -- 1:招聘中 0:已关闭
    created_by BIGINT REFERENCES t_user(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

-- 面试问题库关联表
CREATE TABLE IF NOT EXISTS t_job_question (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES t_job(id),
    question_type VARCHAR(50),          -- 问题类型
    question_count INT DEFAULT 5,       -- 该类型问题数量
    priority INT DEFAULT 0,             -- 优先级
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 面试服务相关表
-- ============================================

-- 面试会话表
CREATE TABLE IF NOT EXISTS t_interview_session (
    id VARCHAR(50) PRIMARY KEY,         -- UUID
    user_id BIGINT NOT NULL REFERENCES t_user(id),
    resume_id BIGINT REFERENCES t_resume(id),
    job_id BIGINT REFERENCES t_job(id),
    candidate_name VARCHAR(50),
    stage VARCHAR(50) NOT NULL,         -- 面试阶段
    status SMALLINT DEFAULT 1,          -- 1:进行中 2:已完成 3:已取消
    resume_content TEXT,                -- 简历内容(冗余)
    job_requirements TEXT,              -- 职位要求(冗余)
    project_questions_count INT DEFAULT 0,
    target_project_questions INT DEFAULT 5,
    project_questions_pool JSONB,       -- 项目问题池
    technical_questions_pool JSONB,     -- 技术问题池
    current_followup_count INT DEFAULT 0,
    python_session_id VARCHAR(100),     -- Python后端会话ID
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 面试消息历史表
CREATE TABLE IF NOT EXISTS t_interview_message (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL REFERENCES t_interview_session(id),
    role VARCHAR(20) NOT NULL,          -- human, ai, system
    content TEXT NOT NULL,
    stage VARCHAR(50),                  -- 消息所属阶段
    sequence INT NOT NULL,              -- 消息序号
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 评估服务相关表
-- ============================================

-- 评分记录表
CREATE TABLE IF NOT EXISTS t_score_record (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL REFERENCES t_interview_session(id),
    question_index INT NOT NULL,        -- 问题序号
    question_type VARCHAR(50),          -- project, technical
    question TEXT NOT NULL,             -- 问题内容
    answer TEXT,                        -- 回答内容
    score INT,                          -- 分数 0-100
    feedback TEXT,                      -- AI反馈
    is_followup BOOLEAN DEFAULT FALSE,  -- 是否是追问
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 面试评估报告表
CREATE TABLE IF NOT EXISTS t_evaluation (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL UNIQUE REFERENCES t_interview_session(id),
    user_id BIGINT NOT NULL REFERENCES t_user(id),
    job_id BIGINT REFERENCES t_job(id),

    -- 评分维度
    overall_score INT,                  -- 总体评分 0-100
    technical_score INT,                -- 技术能力评分
    communication_score INT,            -- 沟通能力评分
    logic_score INT,                    -- 逻辑思维评分
    experience_score INT,               -- 经验匹配度评分

    -- 评估内容
    summary TEXT,                       -- 面试总结
    strengths TEXT,                     -- 优势
    weaknesses TEXT,                    -- 待改进
    recommendation VARCHAR(50),         -- 推荐结果: recommend, consider, reject
    detailed_feedback JSONB,            -- 详细反馈(JSON)

    -- 统计数据
    total_questions INT,
    answered_questions INT,
    average_score DECIMAL(5,2),
    duration_minutes INT,               -- 面试时长(分钟)

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 通知服务相关表
-- ============================================

-- 通知记录表
CREATE TABLE IF NOT EXISTS t_notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES t_user(id),
    type VARCHAR(50) NOT NULL,          -- email, sms, in_app
    template_code VARCHAR(100),         -- 通知模板编码
    title VARCHAR(200),
    content TEXT,
    related_type VARCHAR(50),           -- interview, evaluation
    related_id VARCHAR(100),
    status SMALLINT DEFAULT 0,          -- 0:待发送 1:已发送 2:发送失败
    send_time TIMESTAMP,
    read_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 索引
-- ============================================

CREATE INDEX IF NOT EXISTS idx_user_email ON t_user(email);
CREATE INDEX IF NOT EXISTS idx_user_phone ON t_user(phone);
CREATE INDEX IF NOT EXISTS idx_user_status ON t_user(status);
CREATE INDEX IF NOT EXISTS idx_resume_user ON t_resume(user_id);
CREATE INDEX IF NOT EXISTS idx_resume_status ON t_resume(parse_status);
CREATE INDEX IF NOT EXISTS idx_job_status ON t_job(status);
CREATE INDEX IF NOT EXISTS idx_session_user ON t_interview_session(user_id);
CREATE INDEX IF NOT EXISTS idx_session_status ON t_interview_session(status);
CREATE INDEX IF NOT EXISTS idx_message_session ON t_interview_message(session_id);
CREATE INDEX IF NOT EXISTS idx_score_session ON t_score_record(session_id);
CREATE INDEX IF NOT EXISTS idx_evaluation_user ON t_evaluation(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_user ON t_notification(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_status ON t_notification(status);

-- ============================================
-- 初始数据
-- ============================================

-- 初始化角色
INSERT INTO t_role (role_code, role_name, description) VALUES
('ROLE_USER', '普通用户', '普通用户角色'),
('ROLE_ADMIN', '管理员', '系统管理员角色'),
('ROLE_INTERVIEWER', '面试官', '面试官角色')
ON CONFLICT (role_code) DO NOTHING;

-- 创建管理员账户 (密码: admin123)
INSERT INTO t_user (username, email, password_hash, nickname, status) VALUES
('admin', 'admin@aiinterviewer.com', '$2a$10$7VAPi29XtFii2ZxQ8WdyEeqxqzePiwkyz3amLje.n6lFaxrpYhV6e', '管理员', 1)
ON CONFLICT (username) DO NOTHING;

-- 关联管理员角色
INSERT INTO t_user_role (user_id, role_id)
SELECT u.id, r.id FROM t_user u, t_role r
WHERE u.username = 'admin' AND r.role_code = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO t_user_role (user_id, role_id)
SELECT u.id, r.id FROM t_user u, t_role r
WHERE u.username = 'admin' AND r.role_code = 'ROLE_USER'
ON CONFLICT DO NOTHING;
