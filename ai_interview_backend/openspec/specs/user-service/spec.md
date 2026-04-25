# user-service Specification

## Purpose
用户管理服务，提供用户注册、登录、JWT 认证和用户信息 CRUD 功能。

## Requirements

### Requirement: User Registration
The system SHALL allow new users to register with username, email, and password.

#### Scenario: Successful Registration
- **Given** a valid registration request with unique username and email
- **When** the user submits `POST /api/v1/auth/register`
- **Then** the system SHALL create the user with hashed password
- **And** return user profile data with HTTP 200.

#### Scenario: Duplicate Username
- **Given** a registration request with an already existing username
- **When** the request is processed
- **Then** the system SHALL return HTTP 400 with an appropriate error message.

### Requirement: User Login
The system SHALL authenticate users via username/password and issue JWT tokens.

#### Scenario: Successful Login
- **Given** valid credentials (username and password)
- **When** the user submits `POST /api/v1/auth/login`
- **Then** the system SHALL return `accessToken`, `refreshToken`, `expiresIn`, and `tokenType`.

#### Scenario: Invalid Credentials
- **Given** incorrect username or password
- **When** the login request is processed
- **Then** the system SHALL return HTTP 401 with error code `2000`.

### Requirement: Token Refresh
The system SHALL support refreshing expired access tokens using a valid refresh token.

#### Scenario: Token Refresh
- **Given** a valid refresh token
- **When** `POST /api/v1/auth/refresh` is called
- **Then** the system SHALL issue a new access token and refresh token pair.

### Requirement: User Profile Management
The system SHALL provide CRUD operations for user profile information.

#### Scenario: Get User Profile
- **Given** an authenticated user
- **When** `GET /api/v1/users/{id}` is called
- **Then** the system SHALL return the user's profile information.
