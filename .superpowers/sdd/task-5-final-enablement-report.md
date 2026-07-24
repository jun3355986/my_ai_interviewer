# Task 5 Report: Migration, Security, End-to-End Gates, and Final Enablement

Status: complete; final independent review reports 0 Critical / 0 Important findings

Date: 2026-07-24

Workspace: `/Users/junjielong/.codex/worktrees/db0e/my_ai_interviewer`

Effective Java runtime: jenv JDK 21. The global jenv version was not changed.

## Outcome

The accepted Interview History, reliable resume, Interview Replay, message-anchored fork, and durable Turn Attempt design is implemented across Java, Python, and Flutter. Full replay controls are enabled in the client because the backend lineage/fork contracts, responsive client behavior, populated-data migration, fresh bootstrap, deterministic cross-service boundaries, recovery/concurrency guards, and ownership checks passed their release gates.

No commit, stage, push, deployment, shared Compose restart, authoritative database write, or real model-provider call was performed.

## Fresh schema and populated migration

`ai_interview_backend/sql/init.sql` is intentionally the pre-Lineage shared-schema bootstrap. A fresh PostgreSQL container executes it, then Interview Service Flyway baselines the non-empty schema at version 0 and applies V1 through V6 into the dedicated `flyway_interview_schema_history` table. Duplicating versioned Lineage/Turn Attempt DDL in `init.sql` would make that first Flyway boot fail, so the bootstrap contract is documented and tested rather than copied.

`InterviewFlywayMigrationTest` now reads the real Docker `init.sql` and verifies it upgrades to the current Interview schema with the expected Turn Attempt and message-semantics columns.

The reusable `tests/scripts/verify-interview-migration.sh` release gate restores a caller-supplied PostgreSQL custom backup into an unexposed temporary PostgreSQL 16 container on a private Docker network, runs Flyway, audits the data, reruns Flyway, and removes the temporary resources.

The local acceptance backup produced the following evidence:

- Business rows before and after: Sessions `65`, Messages `256`, Scores `42`, Evaluations `0`.
- Root Lineages after migration: `65`, exactly one for each legacy Session.
- Orphan counts for Session-to-Lineage, Lineage-to-root, parent Branch, Message, Score, and Turn Attempt references: `0|0|0|0|0|0`.
- Legacy acceptance anchor: `6` persisted messages and all `6` explicitly non-forkable.
- Flyway version: `6`; the second migration run reported no migration necessary.

The authoritative Compose PostgreSQL instance and its volume were never connected to or modified by this gate.

## Deterministic cross-service evidence

The cross-service scenarios are covered without a paid or real model call:

- Java sends the schema-versioned branch snapshot and correlation fields over the real WebClient/SSE parser boundary; Python router tests emit the same `status`, score/question/result, and `done` protocol.
- Java lifecycle integration persists Turn Attempts before work, commits canonical messages/score/state atomically, rejects stale version/tail ownership, and prevents late commits after cancellation or ownership drift.
- Python reconstructs from the Java snapshot after cache loss/restart, replays exact `turn_id` duplicates, fences concurrent/stale owners, and returns the authoritative post-turn state for the next Java snapshot.
- A forced Python cache-upsert failure rolls back the ledger completion and cache row in the same SQLite transaction; an exact retry then succeeds without publishing partial cache state.
- Flutter creates the durable opening before navigation, can exit while generation continues, reattaches after navigation/disconnect, reloads canonical history exactly once on terminal completion, and never inserts optimistic/partial/error bubbles.
- Stale two-client state, exact idempotency replay, failed retry/discard, sibling and nested forks, inherited Fork Points, lineage lock ordering, independent branch evaluation, best-score sorting, and full/partial ownership reassignment are covered by the PostgreSQL integration suites.

## Security and correctness hardening

- All new history/tree/transcript/start/turn/fork/status/events/retry/cancel/discard paths require the authenticated user header and enforce owned Lineage, Branch, immutable Turn Attempt owner, Resume, or Evaluation state.
- Durable-start idempotency conflicts now use the same sanitized HTTP `409` handler as normal and fork Turn Attempts instead of falling through as an internal error.
- Compatibility chat/resume SSE failures return stable public codes and messages; provider, filesystem, SQLite, and database-constraint details remain only in server logs.
- Legacy list/incomplete/get/cancel/history/chat/resume paths require both current Session and current Lineage ownership. Compatibility mutations run inside a transaction that locks Lineage before Branch, so ownership cannot change between authorization and message, score, Session, or Lineage writes; non-writing stream events also reauthorize and close on drift.
- Equivalent composed assessment paths now produce the same deterministic communication score; report dimensions no longer depend on `Math.random()`.
- Evaluation imports the required Interview composition and lock guard services while explicitly excluding Flyway auto-configuration and transitive Flyway dependencies, leaving Interview Service as the only owner of `flyway_interview_schema_history`.
- Pending durable-start identity is cleared on logout, new login, and protected-request session expiry. Conditional deletion prevents a late response for an older request from deleting a newer pending key; completed exact start/tail replays always refresh canonical transcript state, with bounded retry and an explicit UI retry after exhaustion.
- A fork draft is bound to its originating Branch. Selecting another Branch preserves the draft but hides its editor/submit action and prevents overwrite until the user returns or explicitly discards it.
- The Chat top action is now “退出面试”: it uses the persisted-progress exit path and no longer fabricates a completed interview or a local-only report. Completion remains a canonical server state reached by the interview flow.

## Verification

- Java Interview + Evaluation under jenv JDK 21: Interview `99` plus Evaluation `10`, for `109` tests with `0` failures and `0` errors.
- Python maintained suite: `89` passed.
- Flutter full suite: `45` passed.
- Flutter analyzer: no issues found.
- Isolated populated-backup migration: passed with the counts and integrity evidence above.
- Evaluation Flyway dependency tree: no `org.flywaydb` dependency rows; Maven build succeeded.
- Independent whole-change review after remediation: `0 Critical / 0 Important` remaining.
- `git diff --check`: clean.
- Staged files: `0`.
- Flutter `pubspec.lock`: no task-unrelated container SDK rewrite remains.

## Release boundary

This work enables the complete feature in source code and verifies the accepted release gates locally. Runtime deployment and live smoke remain intentionally outside this task because the user did not authorize commit, push, deployment, service restart, authoritative database migration, or real provider use. A future authorized release should run the same backup gate first, deploy Java/Python/Flutter together, read back Flyway version and service revisions, and perform a token-authenticated smoke through the Gateway.
