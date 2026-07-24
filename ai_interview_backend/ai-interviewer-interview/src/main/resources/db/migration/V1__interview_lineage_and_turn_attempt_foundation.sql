CREATE TABLE t_interview_lineage (
    id VARCHAR(50) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES t_user(id),
    root_session_id VARCHAR(50),
    last_business_activity_at TIMESTAMP,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE t_interview_session
    ADD COLUMN lineage_id VARCHAR(50),
    ADD COLUMN parent_session_id VARCHAR(50),
    ADD COLUMN fork_point_message_id BIGINT,
    ADD COLUMN fork_trigger_message_id BIGINT,
    ADD COLUMN branch_label VARCHAR(100),
    ADD COLUMN branch_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN last_business_activity_at TIMESTAMP,
    ADD COLUMN legacy_migrated BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE t_interview_turn_attempt (
    id VARCHAR(50) PRIMARY KEY,
    lineage_id VARCHAR(50) NOT NULL REFERENCES t_interview_lineage(id),
    session_id VARCHAR(50) NOT NULL REFERENCES t_interview_session(id),
    expected_branch_version BIGINT NOT NULL,
    expected_tail_message_id BIGINT REFERENCES t_interview_message(id),
    candidate_answer TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    retry_of_id VARCHAR(50) REFERENCES t_interview_turn_attempt(id),
    agent_run_id VARCHAR(100),
    request_id VARCHAR(100),
    error_code VARCHAR(100),
    diagnostic_ref VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMP,
    completed_at TIMESTAMP,
    failed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_interview_turn_attempt_status CHECK (
        status IN (
            'PROCESSING',
            'COMPLETED',
            'FAILED',
            'INTERRUPTED',
            'CANCEL_REQUESTED',
            'CANCELLED',
            'DISCARDED'
        )
    )
);

ALTER TABLE t_interview_message
    ADD COLUMN turn_id VARCHAR(50) REFERENCES t_interview_turn_attempt(id),
    ADD COLUMN message_type VARCHAR(50),
    ADD COLUMN expects_response BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'completed',
    ADD COLUMN metadata JSONB,
    ADD CONSTRAINT chk_interview_message_delivery_status CHECK (
        delivery_status IN ('completed', 'interrupted', 'failed')
    );

ALTER TABLE t_score_record
    ADD COLUMN turn_id VARCHAR(50) REFERENCES t_interview_turn_attempt(id),
    ADD COLUMN question_message_id BIGINT REFERENCES t_interview_message(id),
    ADD COLUMN answer_message_id BIGINT REFERENCES t_interview_message(id);

INSERT INTO t_interview_lineage (
    id,
    user_id,
    root_session_id,
    last_business_activity_at,
    archived,
    created_at,
    updated_at
)
SELECT
    session.id,
    session.user_id,
    session.id,
    COALESCE(MAX(message.created_at), session.updated_at, session.created_at),
    FALSE,
    COALESCE(session.created_at, CURRENT_TIMESTAMP),
    COALESCE(session.updated_at, session.created_at, CURRENT_TIMESTAMP)
FROM t_interview_session session
LEFT JOIN t_interview_message message ON message.session_id = session.id
GROUP BY
    session.id,
    session.user_id,
    session.updated_at,
    session.created_at;

UPDATE t_interview_session session
SET lineage_id = session.id,
    branch_label = '原始分支',
    branch_version = 1,
    last_business_activity_at = COALESCE(
        (
            SELECT MAX(message.created_at)
            FROM t_interview_message message
            WHERE message.session_id = session.id
        ),
        session.updated_at,
        session.created_at
    ),
    legacy_migrated = TRUE;

UPDATE t_interview_message message
SET message_type = CASE
        WHEN message.role = 'human'
             AND message.sequence = 1
             AND BTRIM(message.content) IN ('我准备好了', '好的，请开始。', '开始面试')
            THEN 'system_trigger'
        WHEN message.role = 'human'
            THEN 'candidate_answer'
        WHEN message.role = 'system'
            THEN 'system_trigger'
        WHEN message.role = 'ai'
             AND (
                 EXISTS (
                     SELECT 1
                     FROM t_interview_message next_message
                     WHERE next_message.session_id = message.session_id
                       AND next_message.sequence = message.sequence + 1
                       AND next_message.role = 'human'
                 )
                 OR EXISTS (
                     SELECT 1
                     FROM t_interview_session active_session
                     WHERE active_session.id = message.session_id
                       AND active_session.status = 1
                       AND message.sequence = (
                           SELECT MAX(last_message.sequence)
                           FROM t_interview_message last_message
                           WHERE last_message.session_id = message.session_id
                       )
                 )
             )
            THEN 'ai_question'
        WHEN message.role = 'ai'
             AND EXISTS (
                 SELECT 1
                 FROM t_interview_session completed_session
                 WHERE completed_session.id = message.session_id
                   AND completed_session.status = 2
                   AND message.sequence = (
                       SELECT MAX(last_message.sequence)
                       FROM t_interview_message last_message
                       WHERE last_message.session_id = message.session_id
                   )
             )
            THEN 'final_summary'
        WHEN message.role = 'ai'
            THEN 'ai_feedback'
        ELSE 'system_trigger'
    END,
    expects_response = CASE
        WHEN message.role = 'ai'
             AND (
                 EXISTS (
                     SELECT 1
                     FROM t_interview_message next_message
                     WHERE next_message.session_id = message.session_id
                       AND next_message.sequence = message.sequence + 1
                       AND next_message.role = 'human'
                 )
                 OR EXISTS (
                     SELECT 1
                     FROM t_interview_session active_session
                     WHERE active_session.id = message.session_id
                       AND active_session.status = 1
                       AND message.sequence = (
                           SELECT MAX(last_message.sequence)
                           FROM t_interview_message last_message
                           WHERE last_message.session_id = message.session_id
                       )
                 )
             )
            THEN TRUE
        ELSE FALSE
    END,
    delivery_status = 'completed',
    metadata = COALESCE(message.metadata, '{}'::JSONB)
        || JSONB_BUILD_OBJECT('legacyForkEligible', FALSE);

UPDATE t_interview_session session
SET last_business_activity_at = COALESCE(
        (
            SELECT MAX(message.created_at)
            FROM t_interview_message message
            WHERE message.session_id = session.id
              AND message.message_type <> 'system_trigger'
        ),
        session.last_business_activity_at
    );

UPDATE t_interview_lineage lineage
SET last_business_activity_at = root.last_business_activity_at,
    updated_at = COALESCE(root.updated_at, lineage.updated_at)
FROM t_interview_session root
WHERE root.id = lineage.root_session_id;

ALTER TABLE t_interview_session
    ALTER COLUMN lineage_id SET NOT NULL,
    ALTER COLUMN branch_label SET NOT NULL,
    ADD CONSTRAINT fk_interview_session_lineage
        FOREIGN KEY (lineage_id) REFERENCES t_interview_lineage(id)
        DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_interview_session_parent
        FOREIGN KEY (parent_session_id) REFERENCES t_interview_session(id),
    ADD CONSTRAINT fk_interview_session_fork_point
        FOREIGN KEY (fork_point_message_id) REFERENCES t_interview_message(id),
    ADD CONSTRAINT fk_interview_session_fork_trigger
        FOREIGN KEY (fork_trigger_message_id) REFERENCES t_interview_message(id);

ALTER TABLE t_interview_lineage
    ALTER COLUMN root_session_id SET NOT NULL,
    ADD CONSTRAINT fk_interview_lineage_root_session
        FOREIGN KEY (root_session_id) REFERENCES t_interview_session(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE t_interview_message
    ALTER COLUMN message_type SET NOT NULL,
    ADD CONSTRAINT chk_interview_message_type CHECK (
        message_type IN (
            'candidate_answer',
            'ai_question',
            'ai_feedback',
            'stage_transition',
            'final_summary',
            'system_trigger'
        )
    );

CREATE INDEX idx_interview_lineage_user_activity
    ON t_interview_lineage(user_id, last_business_activity_at DESC);
CREATE INDEX idx_interview_session_lineage_activity
    ON t_interview_session(lineage_id, last_business_activity_at DESC);
CREATE INDEX idx_interview_session_parent
    ON t_interview_session(parent_session_id);
CREATE INDEX idx_interview_message_session_sequence
    ON t_interview_message(session_id, sequence);
CREATE INDEX idx_interview_message_semantics
    ON t_interview_message(session_id, message_type, delivery_status);
CREATE INDEX idx_interview_turn_attempt_branch_status
    ON t_interview_turn_attempt(session_id, status, created_at DESC);
CREATE UNIQUE INDEX ux_interview_turn_attempt_lineage_processing
    ON t_interview_turn_attempt(lineage_id)
    WHERE status = 'PROCESSING';
