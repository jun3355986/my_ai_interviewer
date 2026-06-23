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
