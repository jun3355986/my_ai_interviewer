# Capability: Job Management and Match

## ADDED Requirements

### Requirement: Live Job Listing
The system SHALL fetch and display a list of available jobs from the backend.

#### Scenario: Load Home Page Jobs
- **Given** an authenticated user on the Home Page
- **When** the page loads
- **Then** the system SHALL call `GET /api/v1/jobs` at port 9004
- **And** populate the "Recent Activity" or job list section with real data.

### Requirement: Resume-Job Match Analysis
The system SHALL perform a match analysis between a user's uploaded resume and a selected job.

#### Scenario: Trigger Match Analysis
- **Given** a selected job and an uploaded PDF resume
- **When** the user initiates the match process
- **Then** the system SHALL call `POST /api/v1/jobs/{id}/match` at port 9004
- **And** navigate to the Results Page to display the `matchScore` and `matchDetails`.
