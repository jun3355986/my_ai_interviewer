# Test Strategy

## Goal

Build a production-style automated test system for the AI Interviewer monorepo without breaking each sub-project's native test conventions.

## Tool Stack

| Layer | Tool | Scope |
|---|---|---|
| Python unit tests | pytest | Python AI service internals |
| Java unit tests | JUnit 5 | Java service internals |
| Web E2E | Playwright | User web and admin web |
| Performance | k6 | Gateway, job search, and opt-in SSE checks |
| Security | Trivy, ZAP, prompt cases | Dependencies, images, web baseline, AI safety |
| Reports | Allure-compatible output directories | Centralized test artifacts |
| CI | GitHub Actions example | Thin wrapper over `tests/scripts/*` |

## Directory Boundary

Cross-project tests live under `tests/`.

Project-native tests stay in their native locations:

- Python: `ai_interviewer/tests`
- Flutter: `ai_interviewer_front/test`
- Java: `*/src/test/java`
- React admin: `src/**/*.test.*`

Use this split deliberately:

| Test asset | Location |
|---|---|
| Cross-service API tests | `tests/api/` |
| Smoke tests | `tests/smoke/` |
| Web or mobile E2E tests | `tests/e2e/` |
| Performance tests | `tests/performance/` |
| Security and AI safety tests | `tests/security/` |
| Shared payloads, users, resumes, and files | `tests/fixtures/` |
| Command wrappers | `tests/scripts/` |
| Generated reports | `tests/reports/` |
| Test case catalog and process docs | `tests/docs/` |

When a project-native test protects a release-critical behavior, also register it in `tests/docs/test-cases.md` so the root test catalog remains the long-term source of truth.

## Quality Gates

| Gate | Required For | Command |
|---|---|---|
| Smoke | Local release readiness | `bash tests/scripts/run-smoke.sh` |
| API | Backend contract confidence | `bash tests/scripts/run-api.sh` |
| E2E | UI main-flow confidence | `bash tests/scripts/run-e2e.sh` |
| Security | Dependency and prompt-safety baseline | `bash tests/scripts/run-security.sh` |
| Performance | Basic latency and stability check | `bash tests/scripts/run-performance.sh` |

## AI-Specific Testing Rules

- Do not assert full LLM text.
- Assert structure, state transition, non-empty assistant output, and safety constraints.
- Keep real-provider calls opt-in when they cost quota or are slow.
- Convert useful AI exploration paths into deterministic Playwright, pytest, or shell tests.
