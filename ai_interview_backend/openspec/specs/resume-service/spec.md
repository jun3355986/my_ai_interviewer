# resume-service Specification

## Purpose
简历管理服务，提供简历文件上传（MinIO 存储）、调用 Python 后端解析、简历列表管理功能。

## Requirements

### Requirement: Resume Upload
The system SHALL allow users to upload PDF resumes which are stored in MinIO object storage.

#### Scenario: Successful Upload
- **Given** an authenticated user with a valid PDF file (max 10MB)
- **When** `POST /api/v1/resumes/upload` is called with the file
- **Then** the system SHALL store the file in MinIO
- **And** create a `Resume` record with file metadata
- **And** return the resume ID and file path.

### Requirement: Resume Parsing
The system SHALL invoke the Python backend to parse resume content from uploaded PDF files.

#### Scenario: Trigger Parse
- **Given** an uploaded resume
- **When** `POST /api/v1/resumes/{id}/parse` is called
- **Then** the system SHALL call Python backend's resume parsing endpoint
- **And** store the parsed content in the `parsed_content` field.

### Requirement: Resume CRUD
The system SHALL provide standard CRUD operations for resume management.

#### Scenario: List Resumes
- **Given** an authenticated user
- **When** `GET /api/v1/resumes` is called
- **Then** the system SHALL return all resumes belonging to the user.

#### Scenario: Set Default Resume
- **Given** a user with multiple resumes
- **When** `PUT /api/v1/resumes/{id}/default` is called
- **Then** the system SHALL set the specified resume as default and unset any previous default.

#### Scenario: Delete Resume
- **Given** an existing resume
- **When** `DELETE /api/v1/resumes/{id}` is called
- **Then** the system SHALL delete the file from MinIO and remove the database record.
