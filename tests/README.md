# Test Assets

The root `tests/` directory is the unified test asset center for AI Interviewer.

## Directory Layout

| Path | Purpose |
|---|---|
| `tests/docs/` | Test strategy, catalog, quality gates, tooling, and troubleshooting docs. |
| `tests/smoke/` | Fast release-readiness smoke checks. |
| `tests/api/` | Cross-service API contract and integration tests. |
| `tests/e2e/` | Web and mobile end-to-end tests. |
| `tests/performance/` | k6 and other performance checks. |
| `tests/security/` | Dependency, web baseline, and AI safety checks. |
| `tests/fixtures/` | Shared users, payloads, resumes, and other reusable test data. |
| `tests/config/` | Committed environment templates and endpoint config. |
| `tests/scripts/` | Repeatable local and CI wrapper commands. |
| `tests/reports/` | Generated test reports and machine-readable outputs. |

## Placement Rules

- Put cross-project, cross-service, smoke, API, E2E, performance, security, and AI safety tests under `tests/`.
- Keep project-native unit tests in each framework's default location:
  - Python: `ai_interviewer/tests/`
  - Flutter: `ai_interviewer_front/test/`
  - Java: `*/src/test/java/`
- Register every new or changed test case in `tests/docs/test-cases.md`.
- Update `tests/docs/tooling-guide.md` when commands, tools, or required environment variables change.
- Store shared test data in `tests/fixtures/`.
- Store generated outputs in `tests/reports/` or a documented evidence directory.
- Keep secrets out of committed config files.

## Common Commands

```bash
bash tests/scripts/run-smoke.sh
bash tests/scripts/run-api.sh
bash tests/scripts/run-e2e.sh
bash tests/scripts/run-performance.sh
bash tests/scripts/run-security.sh
bash tests/scripts/run-all.sh
```

## Documentation

Start with `tests/docs/README.md`.
