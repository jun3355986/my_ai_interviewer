# Task 2 Independent Review Findings

Status: resolved; final independent re-review found no Critical or Important issues

## Critical

1. Python ledger `complete`/`fail` performs a Python-side owner/status check and then an ORM update/delete keyed only by the primary key. An old owner can read its row, lose ownership to a stale takeover, and still complete or delete the new owner's row. Use one atomic SQL compare-and-swap statement whose `WHERE` includes `turn_id`, `PROCESSING`, and `owner_token`; only update/delete when exactly one row matches. Do not publish cache/session state before fenced completion succeeds. Add deterministic two-owner takeover tests proving the old owner cannot complete, delete, or overwrite the winner.

## Important

2. Java worker passes the branch's current `userId` into snapshot composition rather than the authenticated owner captured at Turn Attempt creation. Persist the original authenticated user ID on the attempt (or an equivalent immutable request snapshot) and use it for later ownership drift checks. If branch/lineage ownership changes while queued/running, snapshot composition and commit must reject before Python/canonical commit. Add worker-level ownership-drift coverage.
3. Exceptions thrown during Python ledger acquisition occur before the processor's sanitized processing `try` boundary and can reach the router's generic exception handler, which returns `str(exc)` in SSE. Wrap storage/SQLAlchemy errors in sanitized durable error types and ensure the durable route never exposes SQLite paths, SQL, constraint text, or stack details. Add a forced acquisition failure regression.

## Additional concurrency check

- Verify the stale-processing lease cannot be taken over while a legitimate model call is still within the supported Java/Python processing timeout. Use a heartbeat/lease renewal or a stale threshold safely beyond the maximum call lifetime, and document/test the fencing rule.

## Re-verification required

- Focused ledger fencing/storage-error/ownership-drift tests.
- Python maintained full suite.
- Java Task 1+2 focused and full Interview module.
- Independent re-review with no Critical/Important findings.

## Final resolution

- Ledger completion and failure cleanup now use single owner/status-fenced SQL statements.
- Ledger result and SQLite cache commit atomically; memory publishes only after commit.
- Immutable Java attempt ownership is persisted by V4 and checked before snapshot/model work and again before canonical commit.
- Durable storage/acquisition/replay errors are sanitized at the processor and router boundaries.
- The supported 10-minute model window is protected by a 15-minute default stale lease, and unsafe lease configuration is rejected.
- Final independent re-review found no remaining Critical or Important issues.
- A dedicated forced cache-upsert rollback test remains a non-blocking enhancement for the final cross-service verification pass.
