# Task 1 Independent Review Findings

Status: resolved; final independent re-review found no Critical or Important issues

## Critical

1. `WebClientTurnModelClient` accepts a clean EOF without a `done` event, so truncated chunks can become canonical AI messages. Require an explicit successful terminal event and validate result/done consistency. Add a production-boundary truncated-SSE regression proving `FAILED` with no canonical rows.
2. `cancel()` immediately marks `CANCELLED`, releasing the lineage slot even when an interruption-ignoring model invocation is still running. Keep `CANCEL_REQUESTED` as a live processing state until the worker actually exits; the worker alone finalizes `CANCELLED`. Extend both service lookup and PostgreSQL uniqueness guard to cover `PROCESSING` plus `CANCEL_REQUESTED`. Add cancel-then-immediate-resubmit coverage.

## Important

3. Executor rejection after the attempt transaction commits can leave an orphan `PROCESSING` row indefinitely. Catch scheduling rejection and transition immediately to a sanitized recoverable state; add a rejecting-executor test.
4. A session cancelled while model work is in flight can still receive a late canonical turn because commit does not recheck active branch status. Reject non-active branches under the commit lock and add a delayed-model/session-cancel race test.
5. SSE reconnect emits a terminal snapshot followed by old replayed processing events, never completes terminal streams, and leaks per-turn sinks. Use an authoritative terminal snapshot that closes, active snapshot plus bounded latest/live events with race-safe terminal delivery, unique/ordered event IDs where needed, and terminal sink completion/removal. Add reconnect ordering/completion/cleanup tests.

## Minor follow-up

- Document the current single-Java-instance assumption for stale recovery; multi-instance leasing/heartbeat belongs to later operational hardening.
- Add the missing production-boundary tests rather than relying only on an injected assembled `TurnModelResult`.

## Re-verification required

- Focused new regression tests.
- Task 1 focused suite.
- Full Interview Java module.
- `git diff --check` and boundary checks.

## Second review findings

The first fix pass removed all Critical issues, but two Important races remain:

1. Executor rejection can race a concurrent cancellation. If the row has already become `CANCEL_REQUESTED`, `markInterrupted` updates zero rows and no worker exists to finalize it, permanently holding the lineage slot. Queued-before-start cancellation also needs an explicit terminal path rather than waiting for the queue to drain. Add race-safe worker scheduling/cancellation state and tests.
2. Ordinary and terminal event publication can run concurrently. Unchecked Reactor `tryEmitNext`/`tryEmitComplete` may return `FAIL_NON_SERIALIZED`, and terminal state is currently removed before successful delivery/completion. Serialize emissions per stream, check emission results, remove only after terminal resolution, and add a concurrent `cancel_requested` versus `cancelled` test requiring one terminal event plus completion.

## Final resolution

- The first review's two Critical and three Important findings were fixed test-first.
- The second review's scheduling/cancellation and concurrent event-emission races were fixed test-first.
- Final focused verification passed 27/27 tests.
- Final Interview Java module verification passed 40/40 tests across 11 suites.
- Final independent re-review found no remaining Critical or Important issues.
