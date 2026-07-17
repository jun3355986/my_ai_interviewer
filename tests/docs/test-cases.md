# Test Cases

This file is the long-term catalog for test cases that matter to release quality.

## Maintenance Rules

- Keep stable IDs after a test case is created.
- Add new automated tests to the catalog in the same change that introduces them.
- Update an entry when the scenario, assertion, code location, or run command changes.
- Mark bug-fix regression tests explicitly with `Regression` in the `Tags` column.
- Keep detailed setup data in `tests/fixtures/`; avoid embedding large payloads in this file.
- If a test is intentionally manual, set `Automated` to `No` and explain the manual evidence location.

## ID Format

Use this pattern:

```text
<PRIORITY>-<AREA>-<NUMBER>
```

Examples:

```text
P0-AUTH-001
P0-INTERVIEW-001
P1-RESUME-001
SEC-PROMPT-001
E2E-INTERVIEW-001
PERF-GATEWAY-001
```

## Priority Definitions

| Priority | Meaning | Release Impact |
|---|---|---|
| P0 | Core path or security gate | Blocks release when failing |
| P1 | Important business behavior | Requires owner decision when failing |
| P2 | Useful coverage or edge case | Does not block release by default |

## Test Type Definitions

| Type | Default Location | Purpose |
|---|---|---|
| Unit | Project-native test directory | Internal logic and isolated behavior |
| API | `tests/api/` | Cross-service HTTP contracts |
| Smoke | `tests/smoke/` | Fast release-readiness checks |
| E2E | `tests/e2e/` | User-visible flows across UI and backend |
| Performance | `tests/performance/` | Latency and stability checks |
| Security | `tests/security/` | Dependency, web baseline, and AI safety checks |

## Catalog

| ID | Area | Scenario | Type | Priority | Automated | Code Location | Run Command | Blocks Release | Tags |
|---|---|---|---|---|---|---|---|---|---|
| P0-GATEWAY-001 | Gateway | Gateway health endpoint returns 200 | Smoke | P0 | Yes | `tests/smoke/p0_smoke.sh` | `bash tests/scripts/run-smoke.sh` | Yes | P0 journey |
| P0-AUTH-001 | Auth | Login returns successful wrapper and non-empty `accessToken` | Smoke/API | P0 | Yes | `tests/smoke/p0_smoke.sh`, `tests/api/pytest/test_auth_api.py` | `bash tests/scripts/run-smoke.sh` or `bash tests/scripts/run-api.sh` | Yes | P0 journey |
| P0-AUTH-002 | Auth | Authenticated current-user endpoint succeeds | Smoke/API | P0 | Yes | `tests/smoke/p0_smoke.sh`, `tests/api/pytest/test_auth_api.py` | `bash tests/scripts/run-smoke.sh` or `bash tests/scripts/run-api.sh` | Yes | P0 journey |
| P0-AUTH-003 | Auth | A protected Flutter request that cannot refresh an expired session clears both locally persisted tokens and invokes the app-level session-expiry callback; a login 401 does not clear credentials | Unit | P0 | Yes | `ai_interviewer_front/test/api_client_session_expiry_test.dart` | `cd ai_interviewer_front && flutter test test/api_client_session_expiry_test.dart` | Yes | Regression, Flutter, Session expiry |
| P0-JOB-001 | Job | Job list endpoint returns successful wrapper | Smoke/API | P0 | Yes | `tests/smoke/p0_smoke.sh`, `tests/api/pytest/test_job_api.py` | `bash tests/scripts/run-smoke.sh` or `bash tests/scripts/run-api.sh` | Yes | P0 journey |
| P0-JOB-002 | Job | Job search endpoint returns successful wrapper | Smoke/API | P0 | Yes | `tests/smoke/p0_smoke.sh`, `tests/api/pytest/test_job_api.py` | `bash tests/scripts/run-smoke.sh` or `bash tests/scripts/run-api.sh` | Yes | P0 journey |
| P0-RESUME-001 | Resume | Resume upload returns a resume ID | Smoke | P0 | Yes | `tests/smoke/p0_smoke.sh` | `bash tests/scripts/run-smoke.sh` | Yes | P0 journey |
| P0-RESUME-002 | Resume | Resume parse endpoint returns successful wrapper | Smoke | P0 | Yes | `tests/smoke/p0_smoke.sh` | `bash tests/scripts/run-smoke.sh` | Yes | P0 journey |
| P0-INTERVIEW-001 | Interview | Interview chat SSE returns event-stream data | Smoke/API | P0 | Yes | `tests/smoke/p0_smoke.sh`, `tests/api/pytest/test_interview_api.py` | `bash tests/scripts/run-smoke.sh`; SSE API checks require `RUN_SSE_API_TESTS=1` | Yes | P0 journey, SSE |
| P0-INTERVIEW-002 | Interview | Resume-based interview SSE returns events | Smoke/API | P0 | Yes | `tests/smoke/p0_smoke.sh`, `tests/api/pytest/test_interview_api.py` | `bash tests/scripts/run-smoke.sh`; SSE API checks require `RUN_SSE_API_TESTS=1` | Yes | P0 journey, SSE |
| P0-OBS-001 | Observability | Known invalid request fails in a controlled way | Smoke | P0 | Yes | `tests/smoke/p0_smoke.sh` | `bash tests/scripts/run-smoke.sh` | Yes | P0 journey |
| P1-RESUME-001 | Resume | Authenticated resume list succeeds | Smoke/API | P1 | Yes | `tests/smoke/p012_smoke.sh` | `bash tests/scripts/run-smoke.sh` | Conditional | P1 journey |
| P1-INTERVIEW-001 | Interview | Session detail succeeds after session creation | Smoke/API | P1 | Yes | `tests/smoke/p012_smoke.sh` | `bash tests/scripts/run-smoke.sh` | Conditional | P1 journey |
| P1-INTERVIEW-002 | Interview | Interview replay tooling parses trace JSONL, validates SSE event order, reports missing expected events or stages, and prints failed-step session timeline with Java session ID, Python session ID, stage, event, and duration | API/Tooling | P1 | Yes | `tests/api/pytest/test_interview_replay_tooling.py` | `cd ai_interviewer && uv run python -m pytest ../tests/api/pytest/test_interview_replay_tooling.py -q` | Conditional | Regression, Replay, SSE, Session timeline |
| P1-INTERVIEW-003 | Interview | Python AI SSE stub returns deterministic `status/chunk/result/done` events for the first chat turn, echoes Java/Python session correlation fields, and avoids real LLM provider calls | API/Stub | P1 | Yes | `tests/stubs/python-ai/app.py`, `tests/api/pytest/test_interview_replay_tooling.py` | `cd ai_interviewer && uv run python -m pytest ../tests/api/pytest/test_interview_replay_tooling.py -q` | Conditional | Regression, Stub AI, SSE, Session timeline |
| P1-INTERVIEW-004 | Interview | Golden replay trace covers opening, self-introduction, and project-question stage progression using reusable JSONL fixtures | API/Replay | P1 | Yes | `tests/fixtures/interview-traces/golden-opening-to-project-qna.jsonl`, `tests/scripts/replay-interview.sh` | `bash tests/scripts/replay-interview.sh tests/fixtures/interview-traces/golden-opening-to-project-qna.jsonl` | Conditional | Regression, Replay, Stage persistence |
| P1-INTERVIEW-005 | Interview | Java `SSEProxyService` consumes deterministic Python SSE stub events and persists linked Python session ID, AI message, and final stage | Unit/Integration | P1 | Yes | `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/SSEProxyServiceStubReplayTest.java` | `cd ai_interview_backend && env JAVA_HOME=$HOME/.jenv/versions/21 PATH=$HOME/.jenv/versions/21/bin:$PATH mvn -pl ai-interviewer-interview test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test -Dtest=SSEProxyServiceStubReplayTest` | Conditional | Regression, Stub AI, SSE, Stage persistence |
| P1-JOB-001 | Job | Repeated job search is idempotent | Smoke/API | P1 | Yes | `tests/smoke/p012_smoke.sh`, `tests/api/pytest/test_job_api.py` | `bash tests/scripts/run-smoke.sh` or `bash tests/scripts/run-api.sh` | Conditional | P1 journey |
| P1-EVALUATION-001 | Evaluation | Evaluation detail returns success or a controlled not-ready state | Smoke | P1 | Yes | `tests/smoke/p012_smoke.sh` | `bash tests/scripts/run-smoke.sh` | Conditional | P1 journey |
| P2-NOTIFICATION-001 | Notification | Unread count endpoint succeeds for authenticated user | Smoke | P2 | Yes | `tests/smoke/p012_smoke.sh` | `bash tests/scripts/run-smoke.sh` | No | P2 journey |
| P2-RESUME-001 | Resume | Set default resume succeeds or returns a controlled business guard | Smoke | P2 | Yes | `tests/smoke/p012_smoke.sh` | `bash tests/scripts/run-smoke.sh` | No | P2 journey |
| P1-OBS-001 | Observability | Admin Flyway migration creates AI observability trace, step, LLM call, raw-access audit schema, indexes, menu, and permission resource/method/enabled contracts | Unit/Schema | P1 | Yes | `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/schema/AiObservabilitySchemaMigrationTest.java` | `cd ai_interviewer_admin && env JAVA_HOME=/Users/junjielong/.jenv/versions/21 PATH=/Users/junjielong/.jenv/versions/21/bin:$PATH mvn -Dtest=AiObservabilitySchemaMigrationTest test` | Conditional | Regression, Schema |
| P1-OBS-002 | Observability | Python provider usage normalization ignores invalid token shapes, normalizes provider prompt-cache hit/miss tokens, caps hit rates at 1, and does not count unreported provider cache calls in provider-cache denominators | Unit | P1 | Yes | `ai_interviewer/tests/test_observability_provider_usage.py` | `cd ai_interviewer && uv run pytest tests/test_observability_provider_usage.py -q` | Conditional | Regression, Provider usage, Provider cache |
| P1-OBS-003 | Observability | Python AI observability captures provider usage and response metadata before text/parser conversion, persists ERROR calls when response text conversion fails, keeps repository writes best-effort across serialization failures, marks wrapped fallback models, and records technical-question retrieval steps | Unit | P1 | Yes | `ai_interviewer/tests/test_observable_langchain.py` | `cd ai_interviewer && uv run pytest tests/test_observable_langchain.py -q` | Conditional | Regression, AI observability, Metadata capture |
| P1-OBS-004 | Observability | Python repository builds a SQLAlchemy PostgreSQL repository for `postgresql+psycopg` URLs without requiring a live database connection | Unit | P1 | Yes | `ai_interviewer/tests/test_observability_repository.py` | `cd ai_interviewer && uv run pytest tests/test_observability_repository.py -q` | Conditional | Regression, Persistence |
| P1-OBS-005 | Observability | Python interview chat and resume streams create trace contexts with the generated or propagated Python/Java session correlation while preserving SSE event ordering | Unit | P1 | Yes | `ai_interviewer/tests/test_router_observability.py` | `cd ai_interviewer && uv run pytest tests/test_router_observability.py -q` | Conditional | Regression, Trace correlation |
| P1-OBS-006 | Observability | Java admin observability service verifies trace list/detail filtering, raw prompt/response permission and audit logging, statistics queries, provider cache token hit rate, provider cache call hit ratio, and unreported provider cache calls from filtered real SQL rows | Unit/Mapper | P1 | Yes | `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/observability/AiObservabilityServiceTest.java` | `cd ai_interviewer_admin && env JAVA_HOME=$HOME/.jenv/versions/21 PATH=$HOME/.jenv/versions/21/bin:$PATH mvn -Dtest=AiObservabilityServiceTest test` | Conditional | Regression, Admin observability, Security, Provider cache |
| P1-OBS-007 | Observability | Java interview chat and resume endpoints propagate gateway `X-User-Name` and trace correlation fields into Python chat/resume requests | Unit | P1 | Yes | `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/InterviewUsernamePropagationTest.java` | `cd ai_interview_backend && env JAVA_HOME=$HOME/.jenv/versions/21 PATH=$HOME/.jenv/versions/21/bin:$PATH mvn -pl ai-interviewer-interview test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test -Dtest=InterviewUsernamePropagationTest` | Conditional | Regression, Trace correlation |
| P1-OBS-008 | Observability | Root AI observability smoke fixture and documentation contract keeps the Java interview chat payload, registry IDs, and required env-var documentation synchronized | API/Docs | P1 | Yes | `tests/api/pytest/test_ai_observability_api.py`, `tests/fixtures/payloads/ai-observability-chat.json` | `cd ai_interviewer && uv run python -m pytest ../tests/api/pytest/test_ai_observability_api.py::test_ai_observability_fixture_and_docs_contract -q` | Conditional | Regression, Documentation, Fixture |
| P1-OBS-009 | Observability | Opt-in cross-service API smoke starts interview chat through Java, waits for a Python AI trace, opens admin trace list/detail/statistics, reads raw prompt/response, and verifies raw access-log count increases when DB access is configured | API/Smoke | P1 | Yes | `tests/api/pytest/test_ai_observability_api.py` | `cd ai_interviewer && RUN_LIVE_API_TESTS=1 RUN_SSE_API_TESTS=1 RUN_AI_OBSERVABILITY_API_TESTS=1 uv run python -m pytest ../tests/api/pytest/test_ai_observability_api.py -q` | Conditional | Regression, Cross-service, Raw audit, Provider cache |
| P1-OBS-010 | Observability | Admin MyBatis Plus registers PostgreSQL UUID type handler so AI observability mapper XML parses in the real Spring context and UUID parameters/results bind safely | Unit/Context | P1 | Yes | `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/common/mybatis/PostgresUuidTypeHandlerTest.java`, `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/smoke/AdminApiSmokeTest.java` | `cd ai_interviewer_admin && env JAVA_HOME=$HOME/.jenv/versions/21 PATH=$HOME/.jenv/versions/21/bin:$PATH mvn -Dtest=PostgresUuidTypeHandlerTest test` or full `mvn test` | Conditional | Regression, Admin observability, MyBatis, PostgreSQL |
| P1-OBS-011 | Observability | Admin web AI observability filters submit backend/Python exact-match values, including trace status `ERROR` and LLM call type `generate_opening`, while still rendering mocked filtered trace/detail/raw payload results | E2E | P1 | Yes | `tests/e2e/playwright/tests/admin-web-smoke.spec.ts` | `cd tests/e2e/playwright && ADMIN_WEB_BASE_URL=http://127.0.0.1:8091 npm run test -- --project=admin-web-chromium tests/admin-web-smoke.spec.ts` | Conditional | Regression, Admin observability, Exact-match filters |
| P1-OBS-012 | Observability | Agent runtime integration keeps LangSmith and LangGraph disabled by default, redacts LangSmith raw payload metadata, writes manual flow candidate replay traces, converts Replay Traces into LangSmith dataset examples, runs deterministic replay evaluators, and stores LangGraph SQLite checkpoints keyed by Agent Run ID | Unit/Tooling | P1 | Yes | `ai_interviewer/tests/test_agent_runtime.py`, `tests/scripts/langsmith_eval.py` | `cd ai_interviewer && uv run pytest tests/test_agent_runtime.py -q` | Conditional | Regression, LangSmith, LangGraph, Replay, Manual recorder |
| SEC-PROMPT-001 | AI Safety | User asks for a full score and model does not blindly comply | Security | P0 | Yes | `tests/security/prompt-injection/cases.json`, `tests/security/prompt-injection/run_prompt_security_tests.py` | `bash tests/scripts/run-security.sh` | Yes | Prompt injection |
| SEC-PROMPT-002 | AI Safety | User asks for hidden instructions and model does not leak system prompt | Security | P0 | Yes | `tests/security/prompt-injection/cases.json`, `tests/security/prompt-injection/run_prompt_security_tests.py` | `bash tests/scripts/run-security.sh` | Yes | Prompt injection |
| SEC-PROMPT-003 | AI Safety | User attempts role override and model remains interviewer | Security | P0 | Yes | `tests/security/prompt-injection/cases.json`, `tests/security/prompt-injection/run_prompt_security_tests.py` | `bash tests/scripts/run-security.sh` | Yes | Prompt injection |

## New Test Case Template

Use this table row for simple cases:

```markdown
| P1-AREA-001 | Area | Scenario | Type | P1 | Yes | `path/to/test` | `command` | Conditional | Regression |
```

Use this section template for complex cases:

```markdown
### P1-AREA-001 Scenario Name

- Area:
- Type:
- Priority:
- Automated:
- Blocks Release:
- Tags:
- Preconditions:
- Test Data:
- Steps:
  1. 
- Expected Result:
- Code Location:
- Run Command:
- Notes:
```
