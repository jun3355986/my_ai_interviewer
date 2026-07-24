CREATE TABLE t_user (
    id BIGINT PRIMARY KEY
);

CREATE TABLE t_job (
    id BIGINT PRIMARY KEY,
    title VARCHAR(100) NOT NULL
);

CREATE TABLE t_interview_session (
    id VARCHAR(50) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES t_user(id),
    resume_id BIGINT,
    job_id BIGINT,
    candidate_name VARCHAR(50),
    stage VARCHAR(50) NOT NULL,
    status SMALLINT DEFAULT 1,
    resume_content TEXT,
    job_requirements TEXT,
    project_questions_count INT DEFAULT 0,
    target_project_questions INT DEFAULT 5,
    project_questions_pool JSONB,
    technical_questions_pool JSONB,
    current_followup_count INT DEFAULT 0,
    python_session_id VARCHAR(100),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_interview_message (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL REFERENCES t_interview_session(id),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    stage VARCHAR(50),
    sequence INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_score_record (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL REFERENCES t_interview_session(id),
    question_index INT NOT NULL,
    question_type VARCHAR(50),
    question TEXT NOT NULL,
    answer TEXT,
    score INT,
    feedback TEXT,
    is_followup BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_evaluation (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(50) NOT NULL UNIQUE REFERENCES t_interview_session(id),
    user_id BIGINT NOT NULL REFERENCES t_user(id),
    overall_score INT,
    summary TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- The shared database already has Admin-owned Flyway history. Interview migrations must not reuse it.
CREATE TABLE flyway_schema_history (
    marker VARCHAR(100) PRIMARY KEY
);
INSERT INTO flyway_schema_history(marker) VALUES ('admin-history');

INSERT INTO t_user(id) VALUES (1);
INSERT INTO t_job(id, title) VALUES (10, 'Java 后端工程师');

INSERT INTO t_interview_session(
    id,
    user_id,
    candidate_name,
    job_id,
    stage,
    status,
    project_questions_count,
    target_project_questions,
    python_session_id,
    started_at,
    created_at,
    updated_at
) VALUES (
    'ef3d58eb84c74358a4b55dd09ff635b2',
    1,
    'Legacy Candidate',
    10,
    'project_qna',
    1,
    1,
    5,
    'stub-ef3d58eb84c74358a4b55dd09ff635b2',
    TIMESTAMP '2026-07-17 03:37:50',
    TIMESTAMP '2026-07-17 03:37:50',
    TIMESTAMP '2026-07-17 03:38:43'
);

INSERT INTO t_interview_message(session_id, role, content, stage, sequence, created_at) VALUES
    ('ef3d58eb84c74358a4b55dd09ff635b2', 'human', '我准备好了', 'opening', 1, TIMESTAMP '2026-07-17 03:37:50'),
    ('ef3d58eb84c74358a4b55dd09ff635b2', 'ai', '欢迎参加本次面试，请先做自我介绍。', 'self_introduction', 2, TIMESTAMP '2026-07-17 03:37:55'),
    ('ef3d58eb84c74358a4b55dd09ff635b2', 'human', '我有五年 Java 开发经验。', 'self_introduction', 3, TIMESTAMP '2026-07-17 03:38:05'),
    ('ef3d58eb84c74358a4b55dd09ff635b2', 'ai', '请介绍一个有挑战性的项目。', 'project_qna', 4, TIMESTAMP '2026-07-17 03:38:12'),
    ('ef3d58eb84c74358a4b55dd09ff635b2', 'human', '我负责过高并发订单系统。', 'project_qna', 5, TIMESTAMP '2026-07-17 03:38:30'),
    ('ef3d58eb84c74358a4b55dd09ff635b2', 'ai', '你如何设计 Redis 缓存和数据库一致性策略？', 'project_qna', 6, TIMESTAMP '2026-07-17 03:38:43');

INSERT INTO t_score_record(
    session_id,
    question_index,
    question_type,
    question,
    answer,
    score,
    feedback,
    is_followup,
    created_at
) VALUES (
    'ef3d58eb84c74358a4b55dd09ff635b2',
    1,
    'project_qna',
    '请介绍一个有挑战性的项目。',
    '我负责过高并发订单系统。',
    80,
    '回答清晰。',
    FALSE,
    TIMESTAMP '2026-07-17 03:38:31'
);

INSERT INTO t_evaluation(
    session_id,
    user_id,
    overall_score,
    summary,
    created_at,
    updated_at
) VALUES (
    'ef3d58eb84c74358a4b55dd09ff635b2',
    1,
    80,
    'Legacy evaluation summary.',
    TIMESTAMP '2026-07-17 03:39:00',
    TIMESTAMP '2026-07-17 03:39:00'
);
