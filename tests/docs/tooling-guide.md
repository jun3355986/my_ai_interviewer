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

## Flutter Unit Tests

Flutter client unit and widget tests live under `ai_interviewer_front/test`.

```bash
cd ai_interviewer_front
flutter test
```

Run the session-expiry regression independently with:

```bash
cd ai_interviewer_front
flutter test test/api_client_session_expiry_test.dart
```

The real-history, replay, resume-hydration, and early-exit regressions can be run together with:

```bash
cd ai_interviewer_front
flutter test \
  test/interview_resume_hydration_test.dart \
  test/interview_history_page_test.dart \
  test/history_detail_replay_test.dart \
  test/interview_exit_preserves_progress_test.dart
```

The Task 4 durable start, API/service state machine, responsive replay, history filtering, and maintained read/exit regressions can be run together with:

```bash
cd ai_interviewer_front
flutter test --no-pub \
  test/interview_replay_contract_test.dart \
  test/interview_replay_service_test.dart \
  test/interview_replay_ui_test.dart \
  test/interview_durable_start_widget_test.dart \
  test/interview_history_page_test.dart \
  test/interview_resume_hydration_test.dart \
  test/history_detail_replay_test.dart \
  test/interview_exit_preserves_progress_test.dart
flutter analyze --no-pub
```

The durable-start widget fake keeps its processing SSE connection open so the start-page attachment and Chat-page restoration exercise the real single-flight rule. `InterviewService` persists only the non-secret pending start tuple (`turnId`, `resumeId`, `jobId`) through an injectable `PendingStartStore`; production uses SharedPreferences and deterministic tests use an in-memory store. The key is saved before the HTTP request, reused after service reconstruction for an exact retry, replaced when the requested resume/job payload changes, and conditionally cleared only when the successful response still matches that key. Logout, a new successful login, and protected-request session expiry clear the pending tuple so one account cannot inherit another account's opening retry.

An empty or failed Attempt event stream is treated as a disconnect. One business `attachToActiveAttempt()` call owns the bounded reconnect loop with default 250 ms, 500 ms, and 1 second delays. Tests inject the delay function to verify the retry ceiling without sleeping, and verify that a branch switch or replacement Attempt prevents the old delayed attachment from subscribing or refreshing canonical state.

On a workstation without a local Flutter SDK, use the repository's cached packages with the stable Flutter container. `flutter pub get --offline` may rewrite SDK-owned transitive lock entries when the container SDK differs from the repository SDK; do not retain those unrelated lockfile changes.

```bash
docker run --rm \
  -v "$PWD/ai_interviewer_front:/workspace" \
  -v "$HOME/.pub-cache:/root/.pub-cache" \
  -w /workspace \
  ghcr.io/cirruslabs/flutter:stable \
  bash -lc $'set -e\nflutter pub get --offline\nflutter test'
```

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

Java-authoritative snapshot reconstruction and durable `turn_id` idempotency use deterministic fakes and temporary storage only:

```bash
cd ai_interviewer
uv run pytest \
  tests/test_database_injection.py \
  tests/test_branch_snapshot_reconstruction.py \
  tests/test_durable_turn_processor.py \
  tests/test_durable_turn_router.py \
  -q
```

Run the maintained Python unit suite with:

```bash
cd ai_interviewer
uv run pytest tests -q
```

The repository-root `test_interview.py` is a live HTTP dual-agent simulation, not an isolated unit test. A bare `uv run pytest -q` also collects that harness and requires a compatible service at its configured `BASE_URL`; use `uv run pytest tests -q` for the deterministic full unit suite.

Python storage isolation variables:

| Variable | Default | Purpose |
|---|---|---|
| `AI_INTERVIEW_DB_PATH` | `ai_interviewer/storage/database/interviews.db` | Overrides the replaceable session cache and restart-safe turn ledger SQLite file. Tests set this to an OS temporary path before importing service modules. |
| `AI_INTERVIEW_VECTOR_DB_PATH` | `ai_interviewer/storage/vector_db` | Overrides the Chroma persistence directory. Tests set this to an OS temporary directory so collection cannot create or update repository/runtime vector files. |

The durable ledger uses owner-and-status compare-and-swap fencing for stale takeover, completion, and failure cleanup. A successful completion writes the durable result and replaceable SQLite session cache in one transaction; only after that transaction commits may the in-memory cache be published. Storage/acquisition/replay failures cross the API boundary as sanitized durable errors.

The supported Java model call is bounded at 10 minutes. Python's default stale-processing lease is 15 minutes, and configuration at or below the supported call timeout is rejected. This guarantees that a legitimate request still inside the supported model-call duration cannot be taken over as stale; if the Java timeout changes, update and re-verify the Python lease invariant in the same change.

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

Interview Flyway migration tests use Testcontainers with PostgreSQL 16 and require a running Docker daemon. Resolve the effective JDK from jenv rather than macOS `java_home`, which may select an unrelated JDK on this workstation:

```bash
cd ai_interview_backend
JENV_ROOT=$HOME/.jenv \
JAVA_HOME=$(/opt/homebrew/bin/jenv prefix) \
PATH=$JAVA_HOME/bin:$PATH \
mvn -pl ai-interviewer-interview -Dtest=InterviewFlywayMigrationTest test
```

The migration test deliberately keeps Interview Service history in `flyway_interview_schema_history`; the shared database's default `flyway_schema_history` belongs to the Admin service.

`ai_interview_backend/sql/init.sql` intentionally remains the pre-Lineage shared-schema bootstrap. A fresh Docker database runs that file first, then Interview Service baselines the non-empty schema at version 0 and applies V1+. `InterviewFlywayMigrationTest.freshDockerBootstrapRemainsCompatibleWithAllInterviewMigrations` reads the real bootstrap file and prevents duplicate-object drift; do not copy versioned Lineage or Turn Attempt DDL into `init.sql`.

To verify a local PostgreSQL custom backup without connecting to the authoritative Compose database, provide the backup path to the isolated restore wrapper:

```bash
INTERVIEW_BACKUP_PATH=/absolute/path/to/ai_interviewer.dump \
bash tests/scripts/verify-interview-migration.sh
```

The wrapper creates an unexposed temporary PostgreSQL 16 container and a private Docker network, restores with `pg_restore`, and applies the Interview Flyway migrations through the dedicated history table. It requires the repository's latest versioned migration to match `INTERVIEW_EXPECTED_FLYWAY_VERSION` (default `6`), compares both row counts and deterministic legacy-business-content digests, checks one root Lineage per legacy Session and orphan counts, verifies the configured legacy anchor is visible/non-forkable, then reruns Flyway and requires the business digest, Interview schema digest, and Flyway history digest to remain unchanged. It removes both temporary Docker resources on success or failure. Optional overrides are `INTERVIEW_EXPECTED_FLYWAY_VERSION`, `INTERVIEW_LEGACY_ANCHOR_ID`, `INTERVIEW_EXPECTED_ANCHOR_MESSAGE_COUNT`, `INTERVIEW_MIGRATION_POSTGRES_IMAGE`, `INTERVIEW_MIGRATION_FLYWAY_IMAGE`, and `INTERVIEW_MIGRATION_FLYWAY_PLATFORM`.

Compose publishes the Interview and Evaluation services only on the internal `ai-interviewer-net`; authenticated application traffic must use the Gateway on port `9000`. Direct host calls to ports `9003` and `9005` are intentionally unavailable because downstream services trust identity headers injected by the Gateway.

Docker Engine 29 requires API 1.44 or newer. The repository pins the Testcontainers docker-java client through `ai_interviewer-interview/src/test/resources/docker-java.properties`; keep that file when running migration tests on Docker Desktop 29.

Durable Turn Attempt lifecycle tests use the same PostgreSQL 16 Testcontainers fixture and run the real Spring transaction boundary. If a local Docker Desktop setup cannot route Ryuk's published callback port, set `TESTCONTAINERS_RYUK_DISABLED=true` for the focused/full module run. JUnit/Testcontainers still stops its declared PostgreSQL container when the test JVM exits; the setting must not be used as a reason to target the authoritative local PostgreSQL database.

```bash
cd ai_interview_backend
TESTCONTAINERS_RYUK_DISABLED=true \
JENV_ROOT=$HOME/.jenv \
/opt/homebrew/bin/jenv exec mvn \
  -pl ai-interviewer-interview -am \
  -Dtest=TurnAttemptLifecycleIntegrationTest,TurnAttemptControllerTest,TurnAttemptWorkerSchedulingTest,TurnAttemptEventPublisherTest,InterviewFlywayMigrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

The Task 2 snapshot/state-focused Java check is:

```bash
cd ai_interview_backend
JENV_ROOT=$HOME/.jenv \
/opt/homebrew/bin/jenv exec mvn \
  -pl ai-interviewer-interview -am \
  -Dtest=BranchSnapshotComposerTest,WebClientTurnModelClientSnapshotTest,TurnAttemptWorkerSnapshotTest,TurnAttemptControllerTest,TurnAttemptWorkerSchedulingTest,TurnAttemptLifecycleIntegrationTest,InterviewFlywayMigrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

It uses PostgreSQL 16 Testcontainers and verifies that only `TurnCommitService` writes the returned post-turn pools/counters/stage into canonical Java state before the next snapshot is composed. Flyway V4 persists and backfills immutable Turn Attempt `owner_user_id`; the worker and commit boundary continue to use that creation-time owner even if mutable branch ownership drifts. It does not call a real Python or paid model provider.

The Task 3 Lineage/Fork/Evaluation focused check is:

```bash
cd ai_interview_backend
JENV_ROOT=$HOME/.jenv \
/opt/homebrew/bin/jenv exec mvn \
  -pl ai-interviewer-interview,ai-interviewer-evaluation -am \
  -Dtest=InterviewHistoryServiceTest,InterviewHistoryControllerTest,InterviewLineageMapperIntegrationTest,LineageTreeServiceTest,ForkAttemptServiceTest,ComposedAssessmentServiceTest,LineageForkIntegrationTest,LineageCompositionIntegrationTest,InterviewFlywayMigrationTest,SSEProxyServiceStubReplayTest,BranchSnapshotComposerTest,TurnAttemptWorkerSnapshotTest,TurnAttemptControllerTest,TurnAttemptWorkerSchedulingTest,TurnAttemptLifecycleIntegrationTest,EvaluationServiceLineageTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

This command uses only deterministic fakes and ephemeral PostgreSQL 16 Testcontainers. It covers exact state metadata, structured project/technical pool preservation, Forkable Message and Owning Branch resolution, atomic child-plus-first-attempt creation, first-child numbering, inherited-tail fallback, exact `turnId` idempotency and concurrent replay, lineage-first fork/commit locking with a real PostgreSQL deadlock regression, nested canonical transcript/assessment composition, deterministic V6 legacy score linkage, observable ambiguous-score fallback, ownership reassignment for tree/list/evaluation/attempt rows, composed best-score sorting in one recursive SQL query, canonical/legacy QNA category aliases, and independent per-branch reports. It does not call Python, a real model provider, or the authoritative local PostgreSQL database.

Run the complete Interview Service module suite after the focused Turn Attempt tests:

```bash
cd ai_interview_backend
TESTCONTAINERS_RYUK_DISABLED=true \
JENV_ROOT=$HOME/.jenv \
/opt/homebrew/bin/jenv exec mvn \
  -pl ai-interviewer-interview -am \
  test
```

When Evaluation depends on composed Lineage assessments, verify both affected modules together:

```bash
cd ai_interview_backend
JENV_ROOT=$HOME/.jenv \
/opt/homebrew/bin/jenv exec mvn \
  -pl ai-interviewer-interview,ai-interviewer-evaluation -am \
  test
```

This combined suite also verifies deterministic branch report dimension scores, Evaluation runtime wiring, sanitized compatibility SSE failures, and the shared HTTP 409 handler for durable start and normal/fork Turn Attempt idempotency conflicts. Evaluation deliberately excludes `FlywayAutoConfiguration`, sets `spring.flyway.enabled=false`, and excludes the transitive Flyway artifacts; Interview Service is the sole owner of `flyway_interview_schema_history`. Confirm the dependency boundary with `JENV_ROOT=$HOME/.jenv /opt/homebrew/bin/jenv exec mvn -pl ai-interviewer-evaluation dependency:tree -Dincludes=org.flywaydb` (the tree must contain no Flyway dependency rows).

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

Resume replay steps use `action:"resume"` and require `sessionId` or `sessionRef`. The default resume endpoint template is `/api/v1/interviews/{sessionId}/resume`; override it with `--resume-path` or `REPLAY_RESUME_PATH`.

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

### LangSmith, Manual Recorder, And LangGraph Agent Runtime

The LangSmith/LangGraph layer is opt-in and does not replace the project PostgreSQL observability store. The default local behavior is no-op unless the variables below are enabled.

Focused unit check:

```bash
cd ai_interviewer
uv run pytest tests/test_agent_runtime.py -q
```

Prepare LangSmith evaluation examples from an existing Replay Trace:

```bash
tests/scripts/langsmith_eval.py tests/fixtures/interview-traces/golden-opening-to-project-qna.jsonl
```

Add `--report tests/reports/replay/<report>.json` to run deterministic evaluators against a replay report. Set `LANGSMITH_EVALUATION_SYNC=true` only when you intentionally want to create/update examples in LangSmith.

Agent runtime variables:

| Variable | Default | Purpose |
|---|---|---|
| `LANGSMITH_TRACING` | `false` | Enables LangSmith tracing for Python `/interview/chat` and `/interview/resume` observability traces. |
| `LANGSMITH_PROJECT` | `ai-interviewer` | LangSmith project name for traces. |
| `LANGSMITH_CAPTURE_RAW_PAYLOADS` | `false` | Allows raw payload metadata to be sent to LangSmith when explicitly enabled for local/dev debugging. |
| `LANGSMITH_DATASET_NAME` | `ai-interviewer-replay-traces` | Dataset name used by `tests/scripts/langsmith_eval.py`. |
| `LANGSMITH_EVALUATION_SYNC` | unset/false | When truthy, the evaluation script writes examples to LangSmith; otherwise it only prepares local examples. |
| `MANUAL_FLOW_RECORDER_ENABLED` | `false` | Enables local/dev API/SSE manual flow recording. |
| `MANUAL_FLOW_RECORDER_OUTPUT_DIR` | `tests/reports/manual-traces` | Runtime output directory for candidate replay JSONL and companion report JSON. |
| `MANUAL_FLOW_RECORDER_CAPTURE_RAW_PAYLOADS` | `false` | Controls whether manual recorder output includes raw messages, resumes, job requirements, and candidate names. |
| `MANUAL_FLOW_RECORDER_MAX_RAW_CHARS` | `20000` | Maximum raw string length retained by the manual recorder when raw capture is enabled. |
| `LANGGRAPH_AGENT_RUN_ENABLED` | `false` | Enables the LangGraph Single-Turn Agent Run thin wrapper and checkpoint writes. |
| `LANGGRAPH_CHECKPOINT_DB_PATH` | `ai_interviewer/storage/agent_checkpoints.sqlite3` | Runtime-managed SQLite checkpoint store path. Do not commit generated checkpoint databases. |

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
