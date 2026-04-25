# Capability: Core Networking

## ADDED Requirements

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
