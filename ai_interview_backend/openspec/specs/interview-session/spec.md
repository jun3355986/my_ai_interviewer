# interview-session Specification

## Purpose
面试会话管理，提供会话的 CRUD 操作、状态流转和消息历史查询。

## Requirements

### Requirement: Interview Session Lifecycle
The system SHALL manage the complete lifecycle of interview sessions from creation to conclusion.

#### Scenario: List User Sessions
- **Given** an authenticated user
- **When** `GET /api/v1/interviews` is called
- **Then** the system SHALL return a paginated list of the user's interview sessions
- **And** each session SHALL include `sessionId`, `stage`, `status`, `progress`, `lastQuestion`, timestamps.

#### Scenario: List Incomplete Sessions
- **Given** an authenticated user with active interview sessions
- **When** `GET /api/v1/interviews/incomplete` is called
- **Then** the system SHALL return only sessions with status not equal to `concluded` or `cancelled`.

#### Scenario: Get Session Detail
- **Given** a valid session ID belonging to the authenticated user
- **When** `GET /api/v1/interviews/{id}` is called
- **Then** the system SHALL return the full session details.

#### Scenario: Cancel Session
- **Given** an active interview session
- **When** `DELETE /api/v1/interviews/{id}` is called
- **Then** the system SHALL set the session status to `cancelled`.

### Requirement: Interview Stage Progression
The system SHALL track interview stages in order: opening -> self_introduction -> project_qna -> technical_qna -> conclusion.

#### Scenario: Stage Transition
- **Given** an interview session in stage `opening`
- **When** the Python backend signals a stage change via SSE `status` event
- **Then** the system SHALL update the session's `stage` field to the new stage.

### Requirement: Message History
The system SHALL persist all user messages and AI responses for each session.

#### Scenario: Save User Message
- **Given** an active interview session
- **When** the user sends a chat message
- **Then** the system SHALL save the message to `t_interview_message` with role `user` and incrementing sequence.

#### Scenario: Save AI Response
- **Given** the SSE stream completes for a round
- **When** all chunk events have been accumulated
- **Then** the system SHALL save the full AI response to `t_interview_message` with role `assistant`.
