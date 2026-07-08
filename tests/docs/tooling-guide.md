# Test Tooling Guide

## Adding Or Changing Tests

When a development task or bug fix requires tests:

1. Put the test in the right location.
2. Add or update the entry in `tests/docs/test-cases.md`.
3. Put reusable input data in `tests/fixtures/`.
4. Add a wrapper under `tests/scripts/` if the test needs a repeatable command.
5. Update this guide when commands, tools, or required environment variables change.
6. Write outputs to `tests/reports/` or a documented evidence directory.

Use project-native locations for isolated unit tests and the root `tests/` tree for cross-project or release-quality tests.

## Python AI Unit Tests

Python AI unit tests live under `ai_interviewer/tests` and use pytest through uv.

```bash
cd ai_interviewer
uv run pytest tests/test_observability_provider_usage.py -q
```

`pytest` is declared in the Python AI project's `dev` dependency group in `ai_interviewer/pyproject.toml`. For a fresh environment, sync that group before running unit tests:

```bash
cd ai_interviewer
uv sync --group dev
```

## Java Unit Tests

Java admin unit and schema tests run with local Maven and JDK 21:

```bash
cd ai_interviewer_admin
env JAVA_HOME=$HOME/.jenv/versions/21 PATH=$HOME/.jenv/versions/21/bin:$PATH mvn -Dtest=AiObservabilityServiceTest,AiObservabilitySchemaMigrationTest test
```

The backend monorepo modules currently inherit an older default Surefire plugin that does not discover JUnit 5 tests reliably. For focused JUnit 5 tests in `ai_interview_backend`, compile first and invoke Surefire 3.2.5 explicitly:

```bash
cd ai_interview_backend
env JAVA_HOME=$HOME/.jenv/versions/21 PATH=$HOME/.jenv/versions/21/bin:$PATH mvn -pl ai-interviewer-interview test-compile org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test -Dtest=InterviewUsernamePropagationTest
```

## Smoke Tests

The smoke suite is a curl-based, non-UI verification for the P0 journey.

### Files

| File | Purpose |
|---|---|
| `tests/smoke/env_check.sh` | Checks local endpoints before deeper smoke tests. |
| `tests/smoke/p0_smoke.sh` | Runs the P0 10-step journey and writes evidence files. |
| `tests/smoke/p012_smoke.sh` | Extends P0 with P1/P2 checks and weighted status. |
| `tests/smoke/passrate.py` | Reads result JSON and emits a markdown report. |

### Run

```bash
bash tests/smoke/env_check.sh
bash tests/smoke/p0_smoke.sh --help
bash tests/smoke/p0_smoke.sh --dry-run
bash tests/smoke/p0_smoke.sh
bash tests/scripts/run-smoke.sh
```

### Environment Variables

| Variable | Default |
|---|---|
| `GATEWAY_BASE_URL` | `http://localhost:9000` |
| `SMOKE_USERNAME` | `test_user` in legacy smoke, `admin` in wrapper scripts |
| `SMOKE_PASSWORD` | `Pass123!` in legacy smoke, `admin123` in wrapper scripts |
| `SMOKE_JOB_KEYWORD` | `java` |
| `SMOKE_MESSAGE` | `I am ready for the interview.` |
| `SMOKE_RESUME_FILE` | Optional resume file path |
| `SMOKE_DRY_RUN` | Skip network calls when truthy |
| `CURL_MAX_TIME` | `45` |
| `SSE_MAX_TIME` | `45` |

## API Tests

System-level API tests live in `tests/api/pytest`.

```bash
bash tests/scripts/run-api.sh
```

Direct pytest runs are skipped unless live API tests are explicitly enabled:

```bash
RUN_LIVE_API_TESTS=1 python3 -m pytest tests/api/pytest
```

SSE tests are opt-in because they can call the real LLM provider:

```bash
RUN_LIVE_API_TESTS=1 RUN_SSE_API_TESTS=1 python3 -m pytest tests/api/pytest/test_interview_api.py
```

## Interview Replay And Stub AI

Use the replay layer when a long interview bug needs to be reproduced quickly without spending real LLM quota.

### Files

| File | Purpose |
|---|---|
| `tests/scripts/interview_replay.py` | Importable replay library for JSONL trace loading, SSE parsing, validation, and report writing. |
| `tests/scripts/replay-interview.py` | Python CLI entrypoint. |
| `tests/scripts/replay-interview.sh` | Root wrapper that loads `tests/config/local.env` through the shared script helpers. |
| `tests/stubs/python-ai/app.py` | FastAPI SSE stub for Python AI `/interview/chat` and `/interview/resume`. |
| `tests/scripts/start-ai-stub.sh` | Starts the stub on `127.0.0.1:18000` by default. |
| `tests/fixtures/interview-traces/` | JSONL replay traces. |
| `tests/fixtures/ai-responses/` | Reusable deterministic AI response fixtures. |
| `tests/reports/replay/` | Replay result JSON output. |

### Unit Check

```bash
cd ai_interviewer
uv run python -m pytest ../tests/api/pytest/test_interview_replay_tooling.py -q
```

Java interview service boundary check:

```bash
cd ai_interview_backend
env JAVA_HOME=$HOME/.jenv/versions/21 PATH=$HOME/.jenv/versions/21/bin:$PATH \
mvn -pl ai-interviewer-interview test-compile \
  org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test \
  -Dtest=SSEProxyServiceStubReplayTest
```

### Start Stub AI

```bash
bash tests/scripts/start-ai-stub.sh
```

The Java interview service must point to the stub for quota-free replay:

```bash
python.ai.base-url=http://127.0.0.1:18000
```

The code also accepts the legacy-compatible key:

```bash
python-ai.base-url=http://127.0.0.1:18000
```

### Replay A Trace

With gateway auth:

```bash
bash tests/scripts/replay-interview.sh tests/fixtures/interview-traces/golden-opening-to-project-qna.jsonl
```

With a pre-issued token:

```bash
REPLAY_ACCESS_TOKEN=<token> \
bash tests/scripts/replay-interview.sh tests/fixtures/interview-traces/golden-opening-to-project-qna.jsonl
```

Stub-only tooling self-check, useful before wiring Java to the stub:

```bash
bash tests/scripts/start-ai-stub.sh

bash tests/scripts/replay-interview.sh \
  tests/fixtures/interview-traces/golden-opening-to-project-qna.jsonl \
  --gateway-base-url http://127.0.0.1:18000 \
  --chat-path /interview/chat \
  --no-login
```

Without auth, for direct local service debugging:

```bash
bash tests/scripts/replay-interview.sh \
  tests/fixtures/interview-traces/golden-opening-to-project-qna.jsonl \
  --gateway-base-url http://127.0.0.1:9003 \
  --chat-path /interviews/chat \
  --no-login
```

Each trace step is one JSON object per line. Use `sessionRef:"previous"` to reuse the latest SSE `session_id` from the prior step.

```json
{"step":1,"action":"chat","sessionId":null,"message":"我准备好了，请开始面试。","expectEvents":["status","chunk","result","done"],"expectStage":"opening"}
{"step":2,"action":"chat","sessionRef":"previous","message":"好的，请开始。","expectEvents":["status","question","chunk","result","done"],"expectStage":"self_introduction"}
```

Replay reports are written to `tests/reports/replay/`. When any step fails, the CLI also prints a session timeline for the failed step so the Java service and Python AI stub can be correlated without opening service logs first. Each timeline entry includes:

| Field | Meaning |
|---|---|
| `javaSessionId` | Java interview session ID propagated to Python as `java_session_id`. |
| `pythonSessionId` | Python AI SSE `session_id`, reused by later trace steps through `sessionRef:"previous"`. |
| `stage` | Stage observed on the SSE event from `stage` or `next_stage`. |
| `event` | SSE event name, such as `status`, `question`, `score`, `result`, or `done`. |
| `durationMs` | HTTP/SSE request duration for that replay step. |

When fixing a bug, save the reproducer as `tests/fixtures/interview-traces/regression-<bug-name>.jsonl` and add it to `tests/docs/test-cases.md` in the same change.

### AI Observability Cross-Service Smoke

The AI observability API smoke is deliberately opt-in because it starts a real interview chat through the Java interview service, can call the Python LLM path, and then checks the admin observability API.

Fixture:

```text
tests/fixtures/payloads/ai-observability-chat.json
```

Contract-only collection and docs check:

```bash
cd ai_interviewer
uv run python -m pytest ../tests/api/pytest/test_ai_observability_api.py::test_ai_observability_fixture_and_docs_contract -q
```

Live cross-service smoke:

```bash
cd ai_interviewer
RUN_LIVE_API_TESTS=1 \
RUN_SSE_API_TESTS=1 \
RUN_AI_OBSERVABILITY_API_TESTS=1 \
uv run python -m pytest ../tests/api/pytest/test_ai_observability_api.py -q
```

The live test calls:

```text
POST /api/v1/interviews/chat
GET  /admin/ai-observability/traces
GET  /admin/ai-observability/traces/{traceId}
GET  /admin/ai-observability/stats
GET  /admin/ai-observability/llm-calls/{callId}/raw?type=PROMPT
GET  /admin/ai-observability/llm-calls/{callId}/raw?type=RESPONSE
```

AI observability runtime variables:

| Variable | Default | Purpose |
|---|---|---|
| `AI_OBSERVABILITY_ENABLED` | `true` in Python config | Enables Python trace, step, and LLM-call writes. |
| `AI_OBSERVABILITY_DB_URL` | Empty | SQLAlchemy PostgreSQL URL for Python writes; the API smoke also reuses it for optional `psql` access-log count verification. Do not commit credentials. |
| `AI_OBSERVABILITY_WRITE_TIMEOUT_MS` | `300` | Best-effort write timeout for observability persistence. |
| `AI_OBSERVABILITY_STORE_RAW_PAYLOAD` | `true` | Controls whether raw prompt and response text are stored for admin audit reads. |
| `AI_OBSERVABILITY_MAX_RAW_CHARS` | `200000` | Maximum raw prompt/response characters retained per LLM call. |

Live smoke variables:

| Variable | Default | Purpose |
|---|---|---|
| `RUN_AI_OBSERVABILITY_API_TESTS` | unset | Additional opt-in gate for the cross-service observability smoke. |
| `GATEWAY_BASE_URL` | `http://localhost:9000` | Gateway base for user login and Java interview chat. |
| `ADMIN_API_BASE_URL` | `GATEWAY_BASE_URL` | Admin API base; set to `http://localhost:9010` when bypassing gateway and calling `ai_interviewer_admin` directly. |
| `ADMIN_SMOKE_USERNAME` | `admin` | Admin account for `/admin/auth/login`; must have `ROLE_ADMIN` and `AI_OBSERVABILITY_RAW_READ`. |
| `ADMIN_SMOKE_PASSWORD` | `admin123` | Admin password for the live smoke account. |
| `AI_OBSERVABILITY_TRACE_WAIT_SECONDS` | `45` | Poll timeout while waiting for a new admin trace after interview chat. |
| `AI_OBSERVABILITY_SSE_MAX_TIME` | `SSE_MAX_TIME` or `60` | Timeout for the interview chat SSE request. |

If `AI_OBSERVABILITY_DB_URL` is unset or `psql` is unavailable, the smoke still opens raw prompt/response through the admin API but skips the direct `t_ai_observability_access_log` count assertion. When DB access is configured, the count must increase by at least 2 after reading both raw payloads.

Postman collection exports, when needed, should be stored under `tests/api/postman`.

## Web E2E

Playwright tests live in `tests/e2e/playwright`.

```bash
bash tests/scripts/run-e2e.sh
```

The Playwright tests use:

| Variable | Purpose |
|---|---|
| `USER_WEB_BASE_URL` | Flutter user app, default `http://localhost:8088` |
| `ADMIN_WEB_BASE_URL` | React admin app, default `http://localhost:8090` |

## Mobile E2E

Maestro flows live in `tests/e2e/mobile/maestro`.

```bash
maestro test tests/e2e/mobile/maestro/login.yaml
maestro test tests/e2e/mobile/maestro/interview-flow.yaml
```

The flows assume the Flutter app is installed on a simulator or device. Update `appId` in each YAML file after the final bundle identifier is confirmed.

## Performance Tests

k6 scripts live in `tests/performance/k6`.

```bash
bash tests/scripts/run-performance.sh
```

The initial scripts are release-smoke checks, not full capacity tests.

SSE performance checks can consume real AI-provider quota and need valid interview preconditions. They are opt-in:

```bash
K6_RUN_SSE=1 bash tests/scripts/run-performance.sh
```

## Security Tests

Security automation is split into three executable tracks:

| Track | Purpose |
|---|---|
| `tests/security/trivy/` | Filesystem and image vulnerability scanning. |
| `tests/security/zap/` | OWASP ZAP baseline scan wrappers. |
| `tests/security/prompt-injection/` | AI-specific prompt injection regression cases. |

```bash
bash tests/scripts/run-security.sh
```

ZAP scans are kept as explicit scripts because they are slower and may need an authenticated setup.
