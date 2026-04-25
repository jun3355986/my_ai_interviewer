# Smoke Tests (Task 8)

This folder provides a curl-based, non-UI smoke verification for P0 journey.

## Files

- `tests/smoke/p0_smoke.sh`: Runs P0 10-step journey and writes evidence files.
- `tests/smoke/passrate.py`: Reads machine-readable result JSON and emits markdown report.

## Prerequisites

- `bash`, `curl`, `python3`
- Gateway and downstream services are reachable
- A valid test account exists for login

## Environment Variables

- `GATEWAY_BASE_URL` (default: `http://localhost:9000`)
- `SMOKE_USERNAME` (default: `test_user`)
- `SMOKE_PASSWORD` (default: `Pass123!`)
- `SMOKE_JOB_KEYWORD` (default: `java`)
- `SMOKE_MESSAGE` (default: `I am ready for the interview.`)
- `SMOKE_RESUME_FILE` (optional; if absent and `fixtures/resume.pdf` missing, script generates a temp `.txt` resume)
- `SMOKE_DRY_RUN` (set `1/true/yes/on` to skip network calls)
- `CURL_MAX_TIME` (default: `45`)
- `SSE_MAX_TIME` (default: `45`)

## Run

```bash
bash tests/smoke/p0_smoke.sh --help
bash tests/smoke/p0_smoke.sh --dry-run
bash tests/smoke/p0_smoke.sh
```

The script writes artifacts to `.sisyphus/evidence/`:

- `task-8-step-01-health.txt`
- `task-8-step-02-login.json`
- `task-8-step-03-users-me.json`
- `task-8-step-04-jobs.json`
- `task-8-step-05-jobs-search.json`
- `task-8-step-06-resume-upload.json`
- `task-8-step-07-resume-parse.json`
- `task-8-step-08-interview-chat.sse.txt`
- `task-8-step-09-interview-resume.sse.txt`
- `task-8-step-10-observability.txt`
- `task-8-p0-results.json`
- `task-8-p0-smoke-report.md`

## Standalone pass-rate command

```bash
python3 tests/smoke/passrate.py \
  --input .sisyphus/evidence/task-8-p0-results.json \
  --output .sisyphus/evidence/task-8-p0-smoke-report.md
```

## Failure behavior

- The smoke script fails fast with actionable error text if a prerequisite is blocked.
- It still emits `task-8-p0-results.json` and (best-effort) report markdown for diagnostics.
