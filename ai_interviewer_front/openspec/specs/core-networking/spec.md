# core-networking Specification

## Purpose
集中式网络客户端管理，使用 Dio 统一处理所有 HTTP 请求，包括 Token 注入、多服务端口分发。
## Requirements
### Requirement: Centralized Network Client
The system SHALL use a centralized network client (Dio) to manage all HTTP requests to backend services.

#### Scenario: Request Interception
- **Given** an authenticated user
- **When** the user makes a request to any protected endpoint
- **Then** the client SHALL automatically append the `Authorization: Bearer <token>` header.

#### Scenario: Port-based Service Dispatch
- **Given** multiple backend microservices
- **When** calling a user-related endpoint
- **Then** the client SHALL target `http://localhost:9001`.
- **When** calling a job-related endpoint
- **Then** the client SHALL target `http://localhost:9004`.

