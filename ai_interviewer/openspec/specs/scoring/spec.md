# scoring Specification

## Purpose
答案评分服务，使用 DeepSeek LLM 对候选人每个回答进行多维度评分并生成反馈。

## Requirements

### Requirement: Answer Scoring
The system SHALL evaluate each candidate answer and produce a score with feedback.

#### Scenario: Score Individual Answer
- **Given** a question and the candidate's answer
- **When** the scoring logic is invoked
- **Then** the system SHALL use the LLM to evaluate the answer
- **And** produce a numerical score and textual feedback
- **And** emit an SSE `score` event with `score`, `feedback`, `question`, `answer` fields.

### Requirement: Multi-dimensional Evaluation
The system SHALL evaluate answers across multiple dimensions.

#### Scenario: Evaluate Dimensions
- **Given** a candidate's answer to an interview question
- **When** the evaluation is performed
- **Then** the system SHALL assess: completeness, technical accuracy, logical expression, depth and breadth
- **And** weight these dimensions into the final score.

### Requirement: Overall Assessment
The system SHALL generate a comprehensive assessment when the interview concludes.

#### Scenario: Generate Final Summary
- **Given** a completed interview session with all score records
- **When** the session transitions to `concluded` stage
- **Then** the system SHALL generate an overall summary with total score, per-stage scores, strengths, weaknesses, and improvement suggestions.
