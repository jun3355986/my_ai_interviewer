# Capability: User Authentication

## ADDED Requirements

### Requirement: User Login
The system SHALL allow users to log in using their username and password.

#### Scenario: Successful Login
- **Given** valid credentials
- **When** the user submits the login form
- **Then** the system SHALL call `POST /auth/login` at port 9001
- **And** store the returned `accessToken` and `refreshToken` securely
- **And** navigate to the Home Page.

### Requirement: Continuous Authentication
The system SHALL maintain the user session across app restarts.

#### Scenario: Auto-Login
- **Given** a stored valid `accessToken`
- **When** the app starts
- **Then** the system SHALL skip the login page and navigate directly to the Home Page.
