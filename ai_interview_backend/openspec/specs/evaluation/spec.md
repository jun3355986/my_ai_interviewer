# evaluation Specification

## Purpose
评估报告服务，基于面试评分数据生成综合评估报告和统计分析。

## Requirements

### Requirement: Evaluation Report Generation
The system SHALL generate comprehensive evaluation reports based on interview score records.

#### Scenario: Generate Report
- **Given** a completed interview session with score records
- **When** `POST /api/v1/evaluations/{sessionId}` is called
- **Then** the system SHALL aggregate scores, generate an overall assessment
- **And** create an `Evaluation` record with `overall_score` and `summary`.

### Requirement: Evaluation Query
The system SHALL provide APIs to query evaluation results and statistics.

#### Scenario: Get Report
- **Given** a session with an existing evaluation
- **When** `GET /api/v1/evaluations/{sessionId}` is called
- **Then** the system SHALL return the evaluation report.

#### Scenario: Get Score Details
- **Given** a session with score records
- **When** `GET /api/v1/evaluations/{sessionId}/scores` is called
- **Then** the system SHALL return all individual score records for the session.

#### Scenario: Get User Statistics
- **Given** an authenticated user with completed interviews
- **When** `GET /api/v1/evaluations/statistics` is called
- **Then** the system SHALL return aggregate statistics (average score, total interviews, trends).
