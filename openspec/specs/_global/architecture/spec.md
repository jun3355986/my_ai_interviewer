# architecture Specification

## Purpose
系统整体架构约束和基础设施要求，适用于所有子项目。

## Requirements

### Requirement: Three-Tier Architecture
The system SHALL maintain a three-tier architecture: Flutter frontend, Spring Cloud backend, and Python AI service.

#### Scenario: Service Communication
- **Given** the three-tier architecture
- **When** the Flutter frontend needs AI interview capability
- **Then** all requests SHALL route through the Spring Cloud Gateway
- **And** the Gateway SHALL forward AI-related requests to the Python service via Java microservices
- **And** the frontend SHALL NOT directly call the Python AI service.

### Requirement: Infrastructure Dependencies
The system SHALL depend on the following infrastructure components for the Java backend.

#### Scenario: Required Infrastructure
- **Given** a deployment environment
- **When** the Java backend is started
- **Then** the following services MUST be available:
  - Nacos (:8848) for service discovery and configuration
  - PostgreSQL (:5432) for data persistence
  - Redis (:6379) for caching and token blacklist
  - MinIO (:9000) for file/resume storage

### Requirement: Unified Response Format
All Java backend APIs SHALL use the `Result<T>` response wrapper.

#### Scenario: API Response Structure
- **Given** any API request to a Java microservice
- **When** the response is returned
- **Then** it SHALL contain `code`, `message`, `data`, and `timestamp` fields.

### Requirement: Environment Variable Management
All sensitive configurations SHALL be managed via environment variables, never hardcoded.

#### Scenario: API Keys
- **Given** external service dependencies (DeepSeek, DashScope)
- **When** the services are configured
- **Then** API keys MUST be provided via environment variables (`DEEPSEEK_API_KEY`, `DASHSCOPE_API_KEY`)
- **And** SHALL NOT be committed to the code repository.
