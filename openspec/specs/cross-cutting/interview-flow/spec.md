# interview-flow Specification

## Purpose
端到端面试流程，描述从用户发起面试到面试结束评分的完整跨端数据流。

## Requirements

### Requirement: End-to-End Interview Flow
The system SHALL support a complete interview flow spanning all three tiers.

#### Scenario: Start New Interview
- **Given** an authenticated user with an uploaded resume
- **When** the user initiates an interview from the Flutter app
- **Then** Flutter SHALL send `POST /api/v1/interviews/chat` to Gateway
- **And** Gateway SHALL authenticate and forward to Interview Service
- **And** Interview Service SHALL create a local session, proxy to Python AI
- **And** Python AI SHALL start the `opening` stage and stream SSE events back
- **And** the SSE stream SHALL flow: Python -> Java -> Gateway -> Flutter.

#### Scenario: Multi-Round Conversation
- **Given** an active interview session
- **When** the user sends subsequent messages
- **Then** each message SHALL follow the same SSE proxy chain
- **And** the Java service SHALL persist user messages and AI responses
- **And** score events SHALL be persisted to `t_score_record`.

#### Scenario: Resume Incomplete Interview
- **Given** a previously started but incomplete interview
- **When** the user chooses to resume from the Flutter app
- **Then** the system SHALL restore the session context
- **And** continue from the last active stage.

#### Scenario: Interview Completion
- **Given** all interview stages have been completed
- **When** the Python AI emits the `done` event with `is_interview_complete: true`
- **Then** Java Interview Service SHALL update session status to `concluded`
- **And** publish an `interview.completed` event to RocketMQ
- **And** Flutter SHALL navigate to the evaluation/summary view.

### Requirement: Score Persistence Flow
The system SHALL persist scores at multiple levels throughout the interview.

#### Scenario: Per-Answer Score
- **Given** a candidate answers a question during the interview
- **When** the Python AI evaluates the answer
- **Then** Python SHALL emit an SSE `score` event
- **And** Java Interview Service SHALL intercept and save to `t_score_record`
- **And** the event SHALL also be forwarded to Flutter for real-time display.

#### Scenario: Final Evaluation Report
- **Given** an interview session has concluded
- **When** the evaluation is requested
- **Then** the Java Evaluation Service SHALL aggregate all `t_score_record` entries
- **And** generate a comprehensive `Evaluation` report.
