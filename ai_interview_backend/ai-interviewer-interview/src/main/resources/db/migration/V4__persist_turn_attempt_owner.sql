ALTER TABLE t_interview_turn_attempt
    ADD COLUMN owner_user_id BIGINT;

UPDATE t_interview_turn_attempt attempt
SET owner_user_id = session.user_id
FROM t_interview_session session
WHERE session.id = attempt.session_id
  AND attempt.owner_user_id IS NULL;

ALTER TABLE t_interview_turn_attempt
    ALTER COLUMN owner_user_id SET NOT NULL,
    ADD CONSTRAINT fk_interview_turn_attempt_owner
        FOREIGN KEY (owner_user_id) REFERENCES t_user(id);
