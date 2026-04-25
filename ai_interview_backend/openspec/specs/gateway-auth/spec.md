# gateway-auth Specification

## Purpose
API 网关认证与路由管理，负责统一入口、JWT 认证、限流熔断和 SSE 长连接透传。

## Requirements

### Requirement: JWT Authentication Filter
The Gateway SHALL intercept all non-whitelisted requests and validate the JWT token before forwarding to downstream services.

#### Scenario: Valid Token Forwarding
- **Given** a request with a valid `Authorization: Bearer <token>` header
- **When** the request arrives at the Gateway
- **Then** the Gateway SHALL extract user claims from the JWT
- **And** set `X-User-Id`, `X-User-Name`, `X-User-Roles` headers on the downstream request
- **And** forward the request to the target service.

#### Scenario: Whitelist Bypass
- **Given** a request to `/auth/login`, `/auth/register`, or `/auth/refresh`
- **When** the request arrives at the Gateway
- **Then** the Gateway SHALL forward it without JWT validation.

#### Scenario: Invalid or Missing Token
- **Given** a request without a valid JWT token to a protected endpoint
- **When** the request arrives at the Gateway
- **Then** the Gateway SHALL return HTTP 401 with error code `2000`.

### Requirement: Service Routing
The Gateway SHALL route requests to downstream microservices via Nacos service discovery.

#### Scenario: Route to Microservices
- **Given** a registered microservice in Nacos
- **When** a request matches the route prefix (e.g., `/api/v1/users/**`)
- **Then** the Gateway SHALL route to `lb://ai-interviewer-{service}`.

### Requirement: SSE Passthrough
The Gateway SHALL support Server-Sent Events long connections for interview streaming.

#### Scenario: SSE Stream Forwarding
- **Given** a POST request to `/api/v1/interviews/chat` with `Accept: text/event-stream`
- **When** the downstream Interview Service returns an SSE stream
- **Then** the Gateway SHALL transparently proxy the SSE stream to the client without buffering.

### Requirement: Rate Limiting
The Gateway SHALL enforce rate limiting via Sentinel to protect downstream services.

#### Scenario: Rate Limit Exceeded
- **Given** a client exceeding the configured request rate
- **When** additional requests arrive
- **Then** the Gateway SHALL return HTTP 429 with a descriptive error message.
