# sse-proxy Specification

## Purpose
核心 SSE 代理服务，负责将 Python FastAPI 后端的 SSE 流式响应透传给前端，同时拦截关键事件（评分、状态变更）进行持久化。

## Requirements

### Requirement: SSE Stream Proxy
The SSEProxyService SHALL transparently proxy SSE streams from the Python FastAPI backend to the Flutter frontend.

#### Scenario: Chat Proxy
- **Given** a valid chat request with message and user context
- **When** the Interview Service receives `POST /interviews/chat`
- **Then** the service SHALL forward the request to Python backend `POST /interview/chat`
- **And** stream all SSE events back to the client in real-time.

#### Scenario: Resume Session Proxy
- **Given** an existing incomplete interview session
- **When** the user resumes with `POST /interviews/{id}/resume`
- **Then** the service SHALL forward to Python backend `POST /interview/resume`
- **And** stream the SSE response back to the client.

### Requirement: Event Interception
The SSEProxyService SHALL intercept specific SSE event types for persistence while forwarding all events to the client.

#### Scenario: Status Event Handling
- **Given** an SSE event with type `status`
- **When** the event is received from Python backend
- **Then** the service SHALL extract and store `python_session_id`
- **And** update the local session stage
- **And** forward the event to the client.

#### Scenario: Score Event Persistence
- **Given** an SSE event with type `score`
- **When** the event contains score data (`score`, `feedback`, `question`, `answer`)
- **Then** the service SHALL persist the score to `t_score_record` table
- **And** forward the event to the client.

#### Scenario: Chunk Event Accumulation
- **Given** multiple SSE events with type `chunk`
- **When** the events arrive sequentially
- **Then** the service SHALL accumulate the content for later persistence as the full AI response
- **And** forward each chunk to the client immediately.

#### Scenario: Done Event Finalization
- **Given** an SSE event with type `done`
- **When** the event is received
- **Then** the service SHALL update the session status
- **And** save the accumulated AI response as a message record
- **And** forward the event to the client.

### Requirement: Session Management
The SSEProxyService SHALL manage the mapping between local Java sessions and Python backend sessions.

#### Scenario: Create New Session
- **Given** a chat request without a session ID
- **When** the first message is sent
- **Then** the service SHALL create a new `InterviewSession` record
- **And** associate it with the user, resume, and optional job.

#### Scenario: Reuse Existing Session
- **Given** a chat request with a valid session ID
- **When** the message is sent
- **Then** the service SHALL load the existing session
- **And** use the stored `python_session_id` for the Python backend request.
