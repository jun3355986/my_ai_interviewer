# auth-flow Specification

## Purpose
端到端认证流程，描述从用户登录到 API 鉴权的完整跨端认证链路。

## Requirements

### Requirement: End-to-End Authentication
The system SHALL provide a complete authentication chain from Flutter login to downstream service authorization.

#### Scenario: Login Flow
- **Given** a user on the Flutter login page
- **When** the user submits valid credentials
- **Then** Flutter SHALL call `POST /api/v1/auth/login` through Gateway
- **And** Gateway SHALL forward to User Service (whitelist bypass)
- **And** User Service SHALL validate credentials and return JWT tokens
- **And** Flutter SHALL securely store `accessToken` and `refreshToken`.

#### Scenario: Authenticated API Call
- **Given** a logged-in user with a valid access token
- **When** the user performs any API action (e.g., start interview)
- **Then** Flutter SHALL attach `Authorization: Bearer <token>` header
- **And** Gateway SHALL validate the JWT and extract user claims
- **And** Gateway SHALL set `X-User-Id`, `X-User-Name`, `X-User-Roles` headers
- **And** the downstream service SHALL use these headers for user identification.

#### Scenario: Token Expiry and Refresh
- **Given** an expired access token
- **When** Flutter receives HTTP 401 from Gateway
- **Then** Flutter SHALL attempt token refresh via `POST /api/v1/auth/refresh`
- **And** on success, retry the original request with the new token
- **And** on failure, redirect to the login page.
