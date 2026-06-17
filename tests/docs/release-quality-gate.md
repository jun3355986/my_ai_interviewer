# Release Quality Gate

## Local Gate

Before handing the app to manual testing:

```bash
bash tests/scripts/run-smoke.sh
bash tests/scripts/run-api.sh
bash tests/scripts/run-e2e.sh
```

## Pre-Release Gate

Before packaging or deploying:

```bash
bash tests/scripts/run-smoke.sh
bash tests/scripts/run-api.sh
bash tests/scripts/run-e2e.sh
bash tests/scripts/run-security.sh
bash tests/scripts/run-performance.sh
```

## Blocking Rules

| Failure | Blocks Release |
|---|---|
| P0 smoke failure | Yes |
| Login failure | Yes |
| Resume upload failure | Yes |
| Interview SSE failure | Yes |
| Prompt leakage case | Yes |
| Critical dependency vulnerability | Yes |
| E2E cannot load user web | Yes |
| E2E cannot load admin web | Yes |
| P1/P2 partial failure | Case-by-case |

## Evidence

Generated artifacts should be copied or produced under:

```text
tests/reports/
.sisyphus/evidence/
```
