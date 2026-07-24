DROP INDEX ux_interview_turn_attempt_lineage_processing;

CREATE UNIQUE INDEX ux_interview_turn_attempt_lineage_processing
    ON t_interview_turn_attempt(lineage_id)
    WHERE status IN ('PROCESSING', 'CANCEL_REQUESTED');
