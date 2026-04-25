# interview-orchestration Specification

## Purpose
面试流程编排服务，基于 LangChain 和 DeepSeek 实现完整的 AI 面试流程管理，包括阶段流转、上下文维护和 SSE 流式响应。

## Requirements

### Requirement: Interview Flow Management
The system SHALL orchestrate the complete interview flow through five sequential stages.

#### Scenario: Stage Progression
- **Given** an active interview session
- **When** the AI determines the current stage is complete
- **Then** the system SHALL transition to the next stage in order: `opening` -> `self_introduction` -> `project_question` -> `technical` -> `concluded`
- **And** emit an SSE `status` event with the new stage.

#### Scenario: Opening Stage
- **Given** a new interview session with resume content
- **When** the first message is received
- **Then** the AI SHALL greet the candidate and introduce the interview process.

#### Scenario: Conclusion Stage
- **Given** all interview stages are complete
- **When** the session transitions to `concluded`
- **Then** the system SHALL generate a summary with overall score and improvement suggestions.

### Requirement: SSE Streaming Response
The system SHALL stream AI responses to the client via Server-Sent Events.

#### Scenario: Streaming Chat
- **Given** a valid chat request at `POST /interview/chat`
- **When** the LLM generates a response
- **Then** the system SHALL stream the response as SSE events: `status`, `chunk` (multiple), `score`, `result`, `done`.

#### Scenario: Session Resume
- **Given** an incomplete interview session
- **When** `POST /interview/resume` is called with the session ID
- **Then** the system SHALL restore the session context and continue from where it left off.

### Requirement: Context Management
The system SHALL maintain conversation context across messages within a session.

#### Scenario: Multi-turn Conversation
- **Given** an ongoing interview session with message history
- **When** a new user message arrives
- **Then** the system SHALL include relevant prior context in the LLM prompt
- **And** maintain coherent conversation flow.
