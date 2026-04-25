# api-contracts Specification

## Purpose
跨服务 API 契约定义，确保前端、后端和大模型服务之间的接口一致性。

## Requirements

### Requirement: SSE Event Protocol
All services involved in interview streaming SHALL use a unified SSE event protocol.

#### Scenario: SSE Event Types
- **Given** an active interview SSE stream
- **When** events are emitted
- **Then** the following event types SHALL be used consistently across Python and Java services:
  - `status`: stage change, contains `stage`, `is_processing`, `session_id`
  - `chunk`: partial AI response text, contains `content`
  - `score`: answer evaluation, contains `score`, `feedback`, `question`, `answer`
  - `result`: round result, contains `next_question`, `is_followup`
  - `done`: stream complete, contains `session_id`, `stage`, `is_interview_complete`
  - `error`: error occurred, contains error details

### Requirement: Interview Stage Enum Consistency
All three tiers SHALL use the same interview stage identifiers.

#### Scenario: Stage Identifiers
- **Given** interview stage transitions
- **When** any service references an interview stage
- **Then** the stage identifier SHALL be one of: `opening`, `self_introduction`, `project_question`, `technical`, `concluded`
- **And** the identifiers SHALL be identical across Flutter, Java, and Python codebases.

### Requirement: Authentication Contract
The Gateway-to-downstream authentication contract SHALL use HTTP headers.

#### Scenario: User Identity Propagation
- **Given** an authenticated request passing through the Gateway
- **When** the request is forwarded to a downstream microservice
- **Then** the Gateway SHALL set `X-User-Id`, `X-User-Name`, `X-User-Roles` headers
- **And** downstream services SHALL NOT perform their own JWT validation.

### Requirement: Python-Java API Contract
The Java backend and Python AI service SHALL maintain compatible API contracts.

#### Scenario: Interview Chat API
- **Given** the Java Interview Service needs to proxy a chat request
- **When** calling Python backend `POST /interview/chat`
- **Then** the request body SHALL include `session_id`, `message`, `resume_content`, `candidate_name`
- **And** the Python backend SHALL respond with an SSE stream following the unified event protocol.

#### Scenario: Resume Parse API
- **Given** the Java Resume Service needs to parse a resume
- **When** calling Python backend's resume parsing endpoint
- **Then** the contract SHALL support both file upload and text-based parsing.
