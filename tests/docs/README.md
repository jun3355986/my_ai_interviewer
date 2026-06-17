# AI Interviewer Test Documentation

This is the single entrypoint for test documentation.

## Documentation Placement Rule

All test documentation should live in `tests/docs/` unless a tool or platform has a strict location requirement.

If a document must live somewhere else, record the reason in this file.

## Root Test Directory Rule

The root `tests/` directory is the project-wide test asset center.

- Cross-project, cross-service, smoke, API, E2E, performance, security, and AI safety tests live under `tests/`.
- Project-native unit tests stay in their native framework locations:
  - Python: `ai_interviewer/tests/`
  - Flutter: `ai_interviewer_front/test/`
  - Java: `*/src/test/java/`
- Shared fixtures live in `tests/fixtures/`.
- Environment templates live in `tests/config/`.
- Generated reports live in `tests/reports/` or a documented evidence directory.
- Repeatable command wrappers live in `tests/scripts/`.

## Test Case Registration Rule

Every new or changed automated test must be registered in `tests/docs/test-cases.md`.

Each entry should include:

- Stable test ID.
- Module or flow.
- Scenario.
- Test type.
- Priority.
- Automation status.
- Code location.
- Run command.
- Release blocking status.

When a bug fix requires a regression test, add a regression scenario entry before considering the fix complete.

## Current Documentation

| File | Purpose |
|---|---|
| `tests/docs/test-strategy.md` | Test layers, tool choices, and quality gates. |
| `tests/docs/test-cases.md` | P0/P1/P2 and AI safety test case catalog. |
| `tests/docs/release-quality-gate.md` | Local and pre-release blocking rules. |
| `tests/docs/tooling-guide.md` | How to run smoke, API, E2E, mobile, performance, and security checks. |
| `tests/docs/troubleshooting.md` | Common failure causes and next checks. |

## Non-Docs Kept Outside This Directory

| Path | Reason |
|---|---|
| `tests/scripts/` | Executable wrappers must stay near the test framework. |
| `tests/config/` | Environment templates are consumed by scripts and should remain versioned config. |
| `tests/fixtures/` | Test data is loaded by tests and scripts. |
| `tests/e2e/playwright/package.json` | Node tooling requires package metadata in the Playwright project directory. |
| `tests/e2e/playwright/playwright.config.ts` | Playwright expects config near its test project. |
| `tests/e2e/mobile/maestro/*.yaml` | Maestro flows are executable test definitions, not documentation. |
| `tests/performance/k6/*.js` | k6 scripts are executable tests, not documentation. |
| `tests/security/trivy/*.sh` and `tests/security/zap/*.sh` | Security scan wrappers are executable tests. |
| `tests/smoke/*.sh` and `tests/smoke/*.py` | Existing smoke implementation files. |
| `tests/ci/github-actions-test.yml.example` | CI example is a copy-ready workflow artifact. |

## Common Commands

```bash
bash tests/scripts/run-smoke.sh
bash tests/scripts/run-api.sh
bash tests/scripts/run-e2e.sh
bash tests/scripts/run-performance.sh
bash tests/scripts/run-security.sh
bash tests/scripts/run-all.sh
```

## Local Environment

Copy the example config when you want local overrides:

```bash
cp tests/config/local.env.example tests/config/local.env
```

`tests/config/local.env` is ignored by the root `.gitignore`. Keep secrets out of committed files.
