ALTER TABLE t_interview_turn_attempt
    ADD COLUMN fork_source_session_id VARCHAR(50),
    ADD COLUMN fork_trigger_message_id BIGINT,
    ADD COLUMN fork_point_message_id BIGINT,
    ADD COLUMN fork_expected_source_version BIGINT,
    ADD COLUMN fork_expected_source_tail_message_id BIGINT,
    ADD CONSTRAINT fk_interview_turn_attempt_fork_source
        FOREIGN KEY (fork_source_session_id) REFERENCES t_interview_session(id),
    ADD CONSTRAINT fk_interview_turn_attempt_fork_trigger
        FOREIGN KEY (fork_trigger_message_id) REFERENCES t_interview_message(id),
    ADD CONSTRAINT fk_interview_turn_attempt_fork_point
        FOREIGN KEY (fork_point_message_id) REFERENCES t_interview_message(id),
    ADD CONSTRAINT fk_interview_turn_attempt_fork_source_tail
        FOREIGN KEY (fork_expected_source_tail_message_id) REFERENCES t_interview_message(id);

CREATE INDEX idx_interview_turn_attempt_fork_source
    ON t_interview_turn_attempt(fork_source_session_id, created_at DESC)
    WHERE fork_source_session_id IS NOT NULL;
