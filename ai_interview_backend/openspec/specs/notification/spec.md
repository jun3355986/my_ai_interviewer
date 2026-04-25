# notification Specification

## Purpose
通知服务，通过 RocketMQ 消费面试事件，发送邮件和站内通知。

## Requirements

### Requirement: Interview Completion Notification
The system SHALL send notifications when an interview session is completed.

#### Scenario: Interview Done Notification
- **Given** an interview session that has concluded
- **When** the `interview.completed` event is published to RocketMQ
- **Then** the Notification Service SHALL consume the event
- **And** create a notification record for the user.

### Requirement: Report Generation Notification
The system SHALL notify users when their evaluation report is ready.

#### Scenario: Report Ready Notification
- **Given** an evaluation report that has been generated
- **When** the `report.generated` event is published to RocketMQ
- **Then** the Notification Service SHALL send an email notification (if configured)
- **And** create an in-app notification record.
