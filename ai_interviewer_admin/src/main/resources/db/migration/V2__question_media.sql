CREATE TABLE IF NOT EXISTS t_question_media (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    media_type VARCHAR(30) NOT NULL,
    media_url TEXT NOT NULL,
    caption TEXT,
    alt_text TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_question_media_question
        FOREIGN KEY (question_id) REFERENCES t_question_bank (id)
);

CREATE INDEX IF NOT EXISTS idx_question_media_question_id
    ON t_question_media (question_id);

CREATE INDEX IF NOT EXISTS idx_question_media_type
    ON t_question_media (media_type);
