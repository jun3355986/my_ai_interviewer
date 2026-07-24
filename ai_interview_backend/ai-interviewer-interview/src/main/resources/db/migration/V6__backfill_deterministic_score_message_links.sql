WITH score_message_candidates AS (
    SELECT
        score.id AS score_id,
        question.id AS question_message_id,
        answer.id AS answer_message_id,
        COUNT(*) OVER (PARTITION BY score.id) AS candidate_count
    FROM t_score_record score
    JOIN t_interview_message question
      ON question.session_id = score.session_id
     AND question.role = 'ai'
     AND question.message_type = 'ai_question'
     AND question.delivery_status = 'completed'
     AND question.content = score.question
    JOIN t_interview_message answer
      ON answer.session_id = question.session_id
     AND answer.sequence = question.sequence + 1
     AND answer.role = 'human'
     AND answer.message_type = 'candidate_answer'
     AND answer.delivery_status = 'completed'
     AND answer.content = score.answer
    WHERE score.question_message_id IS NULL
      AND score.answer_message_id IS NULL
), deterministic_links AS (
    SELECT score_id, question_message_id, answer_message_id
    FROM score_message_candidates
    WHERE candidate_count = 1
)
UPDATE t_score_record score
SET question_message_id = deterministic.question_message_id,
    answer_message_id = deterministic.answer_message_id
FROM deterministic_links deterministic
WHERE score.id = deterministic.score_id;
