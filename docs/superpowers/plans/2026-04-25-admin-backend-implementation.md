# Admin Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an independent Spring Boot admin API project for AI Interviewer covering phase 1 and phase 2 admin capabilities.

**Architecture:** Create a new `ai_interviewer_admin` Spring Boot service that registers with Nacos, is routed by Gateway under `/admin/**`, reads existing PostgreSQL business tables for management queries, and owns admin-only tables for RBAC, audit logs, structured question bank, notification templates, and strategy configuration. The service does not replace the candidate interview flow or the Python AI service; it integrates with them through database reads and explicit HTTP sync APIs.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Security, JWT, MyBatis-Plus, PostgreSQL, Redis, Flyway, Nacos, Knife4j/OpenAPI, WebClient.

---

## Implementation Rules

- Before running Java or Maven commands, confirm the active JDK with `JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv version`. Do not change global JDK.
- Keep `ai_interviewer_admin` as an independent project at `/Users/junjielong/workspace/my_ai_interviewer/ai_interviewer_admin`.
- Prefer tests around service behavior and mapper SQL before adding controllers.
- Use frequent commits after each task.
- Do not implement a web UI in this plan.

## File Structure Map

```text
ai_interviewer_admin/
├── pom.xml
├── README.md
├── src/main/java/com/aiinterviewer/admin/
│   ├── AdminApplication.java
│   ├── common/
│   │   ├── config/
│   │   ├── exception/
│   │   ├── model/
│   │   ├── security/
│   │   └── util/
│   ├── auth/
│   ├── rbac/
│   ├── audit/
│   ├── dashboard/
│   ├── user/
│   ├── resume/
│   ├── job/
│   ├── interview/
│   ├── evaluation/
│   ├── questionbank/
│   ├── notification/
│   └── systemconfig/
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/V1__admin_schema.sql
│   └── mapper/
└── src/test/java/com/aiinterviewer/admin/
```

## Task 1: Bootstrap Independent Spring Boot Project

**Files:**
- Create: `/Users/junjielong/workspace/my_ai_interviewer/ai_interviewer_admin/pom.xml`
- Create: `/Users/junjielong/workspace/my_ai_interviewer/ai_interviewer_admin/README.md`
- Create: `/Users/junjielong/workspace/my_ai_interviewer/ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/AdminApplication.java`
- Create: `/Users/junjielong/workspace/my_ai_interviewer/ai_interviewer_admin/src/main/resources/application.yml`
- Create: `/Users/junjielong/workspace/my_ai_interviewer/ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/AdminApplicationTests.java`

- [ ] **Step 1: Confirm JDK**

Run:

```bash
cd /Users/junjielong/workspace/my_ai_interviewer/ai_interviewer_admin
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv version
```

Expected: JDK 21 is active, or project-local `.java-version` points to 21.

- [ ] **Step 2: Create Maven project skeleton**

Create a Spring Boot 3.3.x Maven project with dependencies for web, security, validation, actuator, PostgreSQL, Redis, MyBatis-Plus, Flyway, Nacos discovery/config, Knife4j/OpenAPI, Lombok, and test.

- [ ] **Step 3: Add startup test**

Create `AdminApplicationTests` with a single `contextLoads()` test.

- [ ] **Step 4: Run test**

Run:

```bash
./mvnw test
```

Expected: build succeeds and `contextLoads()` passes.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin
git commit -m "feat(admin): bootstrap admin service"
```

## Task 2: Add Admin Database Schema Migrations

**Files:**
- Create: `/Users/junjielong/workspace/my_ai_interviewer/ai_interviewer_admin/src/main/resources/db/migration/V1__admin_schema.sql`
- Create: `/Users/junjielong/workspace/my_ai_interviewer/ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/schema/AdminSchemaMigrationTest.java`

- [ ] **Step 1: Write migration test**

Create a test using Testcontainers PostgreSQL or an integration profile that verifies Flyway creates these tables:

```text
t_admin_menu
t_admin_permission
t_admin_role_permission
t_admin_user_role
t_question_bank
t_question_tag
t_question_tag_relation
t_question_import_batch
t_question_vector_sync_record
t_notification_template
t_system_config
t_interview_strategy_config
t_admin_operation_log
```

- [ ] **Step 2: Run migration test and verify failure**

Run:

```bash
./mvnw -Dtest=AdminSchemaMigrationTest test
```

Expected: fail because migration file or tables do not exist yet.

- [ ] **Step 3: Implement SQL migration**

Add explicit `CREATE TABLE IF NOT EXISTS` statements for every admin-owned table. Use `BIGSERIAL` primary keys, `created_at`, `updated_at`, and `deleted_at` on mutable business tables such as question bank, tags, notification templates, and system config records.

- [ ] **Step 4: Run migration test**

Run:

```bash
./mvnw -Dtest=AdminSchemaMigrationTest test
```

Expected: pass and confirm every table exists.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/main/resources/db/migration/V1__admin_schema.sql ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/schema/AdminSchemaMigrationTest.java
git commit -m "feat(admin): add admin schema migrations"
```

## Task 3: Add Common API, Error, and Security Foundation

**Files:**
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/common/model/Result.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/common/model/PageResult.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/common/exception/AdminBusinessException.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/common/exception/GlobalExceptionHandler.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/common/security/JwtService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/common/security/AdminSecurityConfig.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/common/GlobalExceptionHandlerTest.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/common/security/JwtServiceTest.java`

- [ ] **Step 1: Write tests**

Add tests for:

```text
JwtService creates token with user id and roles.
JwtService rejects expired token.
GlobalExceptionHandler maps AdminBusinessException to Result failure.
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dtest=JwtServiceTest,GlobalExceptionHandlerTest test
```

Expected: fail because common classes are not implemented.

- [ ] **Step 3: Implement common foundation**

Implement `Result<T>` compatible with existing Java backend response style, JWT helpers, global exception handling, CORS, stateless sessions, and `/admin/auth/login` permit-all while all other `/admin/**` routes require authentication.

- [ ] **Step 4: Run tests**

Run:

```bash
./mvnw -Dtest=JwtServiceTest,GlobalExceptionHandlerTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/common ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/common
git commit -m "feat(admin): add common security foundation"
```

## Task 4: Implement Admin Auth and RBAC

**Files:**
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/auth/AuthController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/auth/AuthService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/auth/dto/AdminLoginRequest.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/auth/dto/AdminLoginResponse.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/rbac/RbacController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/rbac/RbacService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/rbac/entity/AdminMenu.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/rbac/entity/AdminPermission.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/auth/AuthServiceTest.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/rbac/RbacServiceTest.java`

- [ ] **Step 1: Write tests**

Cover these cases:

```text
admin user with ROLE_ADMIN can login.
normal user without ROLE_ADMIN cannot login.
disabled user cannot login.
admin can list menus and permissions.
admin can bind role permissions.
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dtest=AuthServiceTest,RbacServiceTest test
```

Expected: fail because auth and RBAC services do not exist.

- [ ] **Step 3: Implement auth and RBAC**

Reuse existing `t_user`, `t_role`, and `t_user_role` for identity. Use admin-owned tables for menu and permission authorization.

- [ ] **Step 4: Run tests**

Run:

```bash
./mvnw -Dtest=AuthServiceTest,RbacServiceTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/auth ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/rbac ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/auth ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/rbac
git commit -m "feat(admin): implement auth and rbac"
```

## Task 5: Implement Audit Logging

**Files:**
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/audit/AuditLogAspect.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/audit/AuditLogService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/audit/AuditLogController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/audit/entity/AdminOperationLog.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/audit/annotation/AdminAudit.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/audit/AuditLogServiceTest.java`

- [ ] **Step 1: Write audit tests**

Cover:

```text
successful audited operation writes t_admin_operation_log.
failed audited operation writes result=FAILED and error message.
audit list can filter by admin user, module, operation, and time range.
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dtest=AuditLogServiceTest test
```

Expected: fail because audit service is missing.

- [ ] **Step 3: Implement audit**

Add `@AdminAudit(module = "USER", operation = "DISABLE")` style annotations and an aspect that captures request URI, method, target id, admin id, IP, user agent, result, and error message.

- [ ] **Step 4: Run tests**

Run:

```bash
./mvnw -Dtest=AuditLogServiceTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/audit ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/audit
git commit -m "feat(admin): add operation audit logs"
```

## Task 6: Implement Dashboard Aggregations

**Files:**
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/dashboard/DashboardController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/dashboard/DashboardService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/dashboard/dto/DashboardOverviewResponse.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/dashboard/mapper/DashboardMapper.java`
- Create: `ai_interviewer_admin/src/main/resources/mapper/DashboardMapper.xml`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/dashboard/DashboardServiceTest.java`

- [ ] **Step 1: Write aggregation tests**

Cover:

```text
overview returns counts for users, jobs, resumes, interviews, evaluations.
score distribution groups by score ranges.
interview trend groups by day for the last 30 days.
recent errors include sessions with concluded too early or missing score records.
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dtest=DashboardServiceTest test
```

Expected: fail because dashboard queries are missing.

- [ ] **Step 3: Implement dashboard queries**

Read from existing `t_user`, `t_job`, `t_resume`, `t_interview_session`, `t_score_record`, and `t_evaluation`.

- [ ] **Step 4: Run tests**

Run:

```bash
./mvnw -Dtest=DashboardServiceTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/dashboard ai_interviewer_admin/src/main/resources/mapper/DashboardMapper.xml ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/dashboard
git commit -m "feat(admin): add dashboard aggregations"
```

## Task 7: Implement User and Resume Management

**Files:**
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/user/AdminUserController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/user/AdminUserService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/resume/AdminResumeController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/resume/AdminResumeService.java`
- Create: `ai_interviewer_admin/src/main/resources/mapper/AdminUserMapper.xml`
- Create: `ai_interviewer_admin/src/main/resources/mapper/AdminResumeMapper.xml`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/user/AdminUserServiceTest.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/resume/AdminResumeServiceTest.java`

- [ ] **Step 1: Write service tests**

Cover:

```text
user list supports username, email, phone, status filters.
disable user changes t_user.status and writes audit log.
reset password updates password_hash and writes audit log.
resume list supports user id and parse status filters.
resume detail includes parsed content, raw text, and versions.
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dtest=AdminUserServiceTest,AdminResumeServiceTest test
```

Expected: fail because services are missing.

- [ ] **Step 3: Implement services and controllers**

Implement read APIs and guarded write APIs. Apply `@AdminAudit` to status changes and password reset.

- [ ] **Step 4: Run tests**

Run:

```bash
./mvnw -Dtest=AdminUserServiceTest,AdminResumeServiceTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/user ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/resume ai_interviewer_admin/src/main/resources/mapper/AdminUserMapper.xml ai_interviewer_admin/src/main/resources/mapper/AdminResumeMapper.xml ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/user ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/resume
git commit -m "feat(admin): add user and resume management"
```

## Task 8: Implement Job Management and Interview Strategy Config

**Files:**
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/job/AdminJobController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/job/AdminJobService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/systemconfig/InterviewStrategyController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/systemconfig/InterviewStrategyService.java`
- Create: `ai_interviewer_admin/src/main/resources/mapper/AdminJobMapper.xml`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/job/AdminJobServiceTest.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/systemconfig/InterviewStrategyServiceTest.java`

- [ ] **Step 1: Write tests**

Cover:

```text
job list filters by title, company, status, skill.
admin can create and update job records.
admin can close or reopen job.
admin can configure question types and counts for a job.
strategy config stores default technical question types, counts, and difficulty ratio.
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dtest=AdminJobServiceTest,InterviewStrategyServiceTest test
```

Expected: fail because job and strategy services are missing.

- [ ] **Step 3: Implement job and strategy modules**

Use existing `t_job` and `t_job_question`; use `t_interview_strategy_config` for global defaults.

- [ ] **Step 4: Run tests**

Run:

```bash
./mvnw -Dtest=AdminJobServiceTest,InterviewStrategyServiceTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/job ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/systemconfig ai_interviewer_admin/src/main/resources/mapper/AdminJobMapper.xml ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/job ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/systemconfig
git commit -m "feat(admin): add job and interview strategy management"
```

## Task 9: Implement Interview and Evaluation Management

**Files:**
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/interview/AdminInterviewController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/interview/AdminInterviewService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/interview/dto/InterviewDiagnosisResponse.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/evaluation/AdminEvaluationController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/evaluation/AdminEvaluationService.java`
- Create: `ai_interviewer_admin/src/main/resources/mapper/AdminInterviewMapper.xml`
- Create: `ai_interviewer_admin/src/main/resources/mapper/AdminEvaluationMapper.xml`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/interview/AdminInterviewServiceTest.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/evaluation/AdminEvaluationServiceTest.java`

- [ ] **Step 1: Write tests**

Cover:

```text
interview list filters by user, job, stage, status, time range.
interview detail includes session, messages, score records, and evaluation summary.
diagnose reports missing technical questions, empty technical pool, missing scores, and early concluded stage.
cancel session changes status to canceled and writes audit log.
evaluation list filters by recommendation and score range.
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dtest=AdminInterviewServiceTest,AdminEvaluationServiceTest test
```

Expected: fail because modules are missing.

- [ ] **Step 3: Implement services and diagnosis**

Read from `t_interview_session`, `t_interview_message`, `t_score_record`, and `t_evaluation`. Diagnosis must be read-only.

- [ ] **Step 4: Run tests**

Run:

```bash
./mvnw -Dtest=AdminInterviewServiceTest,AdminEvaluationServiceTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/interview ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/evaluation ai_interviewer_admin/src/main/resources/mapper/AdminInterviewMapper.xml ai_interviewer_admin/src/main/resources/mapper/AdminEvaluationMapper.xml ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/interview ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/evaluation
git commit -m "feat(admin): add interview and evaluation management"
```

## Task 10: Implement Structured Question Bank CRUD

**Files:**
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/entity/QuestionBankItem.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/entity/QuestionTag.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionCreateRequest.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionUpdateRequest.java`
- Create: `ai_interviewer_admin/src/main/resources/mapper/QuestionBankMapper.xml`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionServiceTest.java`

- [ ] **Step 1: Write CRUD tests**

Cover:

```text
create question requires question text, type, difficulty, and status.
update question changes answer reference, tags, difficulty, and status.
list question filters by type, difficulty, tag, status, job id, and keyword.
disabled question is not eligible for vector sync.
soft delete hides question from default list.
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dtest=QuestionServiceTest test
```

Expected: fail because question bank service is missing.

- [ ] **Step 3: Implement CRUD**

Persist structured questions in `t_question_bank`; persist tags in `t_question_tag` and `t_question_tag_relation`.

- [ ] **Step 4: Run tests**

Run:

```bash
./mvnw -Dtest=QuestionServiceTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank ai_interviewer_admin/src/main/resources/mapper/QuestionBankMapper.xml ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank
git commit -m "feat(admin): add structured question bank"
```

## Task 11: Implement Question Import

**Files:**
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionImportController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionImportService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionImportRow.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/entity/QuestionImportBatch.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionImportServiceTest.java`
- Test resource: `ai_interviewer_admin/src/test/resources/questionbank/sample_questions.csv`

- [ ] **Step 1: Write import tests**

Cover:

```text
valid CSV creates import batch and question records.
row missing question text is rejected with row number.
duplicate question text in same batch is rejected.
partial failure records failed row count and does not create invalid rows.
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dtest=QuestionImportServiceTest test
```

Expected: fail because import service is missing.

- [ ] **Step 3: Implement CSV import**

Use a fixed CSV schema:

```text
question_text,answer_reference,question_type,difficulty,tags,skill_area,job_id,status
```

- [ ] **Step 4: Run tests**

Run:

```bash
./mvnw -Dtest=QuestionImportServiceTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank ai_interviewer_admin/src/test/resources/questionbank/sample_questions.csv
git commit -m "feat(admin): add question import"
```

## Task 12: Implement Question Vector Sync

**Files:**
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionVectorSyncController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionVectorSyncService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/entity/QuestionVectorSyncRecord.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/client/PythonQuestionBankClient.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionVectorSyncServiceTest.java`

- [ ] **Step 1: Write sync tests**

Cover:

```text
sync only sends enabled questions.
sync excludes deleted questions.
successful sync marks vector_sync_status as SYNCED.
failed sync marks vector_sync_status as FAILED and writes failure reason.
sync creates t_question_vector_sync_record with counts and status.
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dtest=QuestionVectorSyncServiceTest test
```

Expected: fail because vector sync service is missing.

- [ ] **Step 3: Implement sync client and service**

Call Python AI through a configurable endpoint. Use this request contract:

```json
{
  "questions": [
    {
      "id": 1,
      "question_text": "请介绍 HashMap 的实现原理",
      "answer_reference": "从数组、链表、红黑树、扩容、扰动函数说明",
      "question_type": "Java基础",
      "difficulty": "medium",
      "tags": ["Java", "集合"],
      "skill_area": "Java"
    }
  ]
}
```

- [ ] **Step 4: Run tests**

Run:

```bash
./mvnw -Dtest=QuestionVectorSyncServiceTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank
git commit -m "feat(admin): add question vector sync"
```

## Task 13: Implement System Config and Notification Templates

**Files:**
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/systemconfig/SystemConfigController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/systemconfig/SystemConfigService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/notification/AdminNotificationController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/notification/AdminNotificationService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/notification/entity/NotificationTemplate.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/systemconfig/SystemConfigServiceTest.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/notification/AdminNotificationServiceTest.java`

- [ ] **Step 1: Write tests**

Cover:

```text
system config returns masked value for secret-like keys.
system config update writes audit log.
notification template create and update works.
send notification writes t_notification and references selected template.
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dtest=SystemConfigServiceTest,AdminNotificationServiceTest test
```

Expected: fail because services are missing.

- [ ] **Step 3: Implement config and notification modules**

Use `t_system_config`, `t_interview_strategy_config`, `t_notification_template`, and existing `t_notification`.

- [ ] **Step 4: Run tests**

Run:

```bash
./mvnw -Dtest=SystemConfigServiceTest,AdminNotificationServiceTest test
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/systemconfig ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/notification ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/systemconfig ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/notification
git commit -m "feat(admin): add config and notification management"
```

## Task 14: Add Gateway, Docker Compose, and Documentation Integration

**Files:**
- Modify: `/Users/junjielong/workspace/my_ai_interviewer/ai_interview_backend/docker-compose.yml`
- Modify: `/Users/junjielong/workspace/my_ai_interviewer/ai_interview_backend/ai-interviewer-gateway/src/main/resources/application.yml`
- Modify: `/Users/junjielong/workspace/my_ai_interviewer/README.md`
- Create: `/Users/junjielong/workspace/my_ai_interviewer/ai_interviewer_admin/Dockerfile`
- Create: `/Users/junjielong/workspace/my_ai_interviewer/ai_interviewer_admin/.env.example`

- [ ] **Step 1: Confirm gateway route test target**

Run:

```bash
rg -n "ai-interviewer-interview|/interviews|routes:" /Users/junjielong/workspace/my_ai_interviewer/ai_interview_backend/ai-interviewer-gateway/src/main/resources/application.yml
```

Expected: existing interview route is visible and can be mirrored for admin.

- [ ] **Step 2: Add admin service container and route**

Add `admin` service to Docker Compose, expose port `9010`, and add Gateway route `/admin/** -> lb://ai-interviewer-admin`.

- [ ] **Step 3: Build containers**

Run:

```bash
cd /Users/junjielong/workspace/my_ai_interviewer/ai_interview_backend
docker compose config
```

Expected: compose configuration is valid.

- [ ] **Step 4: Update README**

Document admin service, port, route prefix, and local startup command.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/Dockerfile ai_interviewer_admin/.env.example ai_interview_backend/docker-compose.yml ai_interview_backend/ai-interviewer-gateway/src/main/resources/application.yml README.md
git commit -m "chore(admin): wire admin service into gateway and compose"
```

## Task 15: Add API Documentation and Smoke Tests

**Files:**
- Create: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/smoke/AdminApiSmokeTest.java`
- Create: `ai_interviewer_admin/docs/admin-api-smoke.md`
- Modify: `ai_interviewer_admin/README.md`

- [ ] **Step 1: Write smoke tests**

Cover these requests with authenticated admin token:

```text
GET /admin/auth/me
GET /admin/dashboard/overview
GET /admin/users
GET /admin/jobs
GET /admin/interviews
GET /admin/questions
GET /admin/audit/logs
```

- [ ] **Step 2: Run smoke tests and verify failure if endpoints are missing**

Run:

```bash
./mvnw -Dtest=AdminApiSmokeTest test
```

Expected: pass after all endpoint tasks are complete.

- [ ] **Step 3: Add API smoke document**

Document login request, token usage, and the smoke-test curl sequence.

- [ ] **Step 4: Run full admin test suite**

Run:

```bash
./mvnw test
```

Expected: all admin service tests pass.

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/smoke ai_interviewer_admin/docs/admin-api-smoke.md ai_interviewer_admin/README.md
git commit -m "test(admin): add api smoke coverage"
```

## Final Verification

- [ ] Confirm JDK:

```bash
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv version
```

- [ ] Run admin tests:

```bash
cd /Users/junjielong/workspace/my_ai_interviewer/ai_interviewer_admin
./mvnw test
```

- [ ] Validate Docker Compose:

```bash
cd /Users/junjielong/workspace/my_ai_interviewer/ai_interview_backend
docker compose config
```

- [ ] Start core stack and admin service:

```bash
cd /Users/junjielong/workspace/my_ai_interviewer/ai_interview_backend
docker compose up -d --build admin gateway postgres redis nacos
```

- [ ] Verify health:

```bash
curl -i http://localhost:9000/admin/actuator/health
```

Expected: HTTP 200 or gateway-routed health response from admin service.

## Coverage Check

This plan covers every section in `/Users/junjielong/workspace/my_ai_interviewer/docs/ADMIN_BACKEND_DESIGN.md`:

- Auth and RBAC: Tasks 3 and 4.
- Dashboard: Task 6.
- User and resume management: Task 7.
- Job and strategy management: Task 8.
- Interview and evaluation management: Task 9.
- Structured question bank: Tasks 10, 11, and 12.
- Notification and system config: Task 13.
- Audit logging: Task 5.
- Gateway and deployment integration: Task 14.
- Documentation and smoke verification: Task 15.
