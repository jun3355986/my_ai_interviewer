# job-service Specification

## Purpose
职位管理服务，提供职位 CRUD 和简历-职位匹配度分析功能。

## Requirements

### Requirement: Job CRUD
The system SHALL provide CRUD operations for job position management.

#### Scenario: Create Job
- **Given** a valid job creation request with title, description, and requirements
- **When** `POST /api/v1/jobs` is called
- **Then** the system SHALL create the job record and return the job details.

#### Scenario: List Jobs
- **Given** available job positions
- **When** `GET /api/v1/jobs` is called
- **Then** the system SHALL return a paginated list of jobs.

### Requirement: Resume-Job Match Analysis
The system SHALL analyze the match between a user's resume and a job description.

#### Scenario: Trigger Match Analysis
- **Given** a valid resume ID and job ID
- **When** `POST /api/v1/jobs/{id}/match` is called
- **Then** the system SHALL compute a match score and return `matchScore` and `matchDetails`.
