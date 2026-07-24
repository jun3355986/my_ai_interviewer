# Task 1 Brief: Durable Turn Attempt Processing

## Objective

Implement the accepted durable Turn Attempt lifecycle in the Java Interview Service. A submitted candidate answer must be durably recoverable before model processing, while canonical interview messages, scores, branch progress, and activity become visible only through one atomic successful commit. Model processing must continue independently of an attached Flutter SSE client.

## Workspace and boundaries

- Workspace: `/Users/junjielong/.codex/worktrees/db0e/my_ai_interviewer`
- Primary module: `ai_interview_backend/ai-interviewer-interview`
- Preserve all existing uncommitted changes; they are part of the larger accepted implementation.
- Do not commit, stage, push, deploy, restart shared runtime services, or mutate the authoritative running PostgreSQL database.
- Use JDK 21 through the current jenv effective version. Do not change the global jenv setting.
- Follow test-driven development: add failing focused tests before implementation, then make them pass.
- Update `tests/docs/test-cases.md` and `tests/docs/tooling-guide.md` for all new or changed regression coverage and commands.
- Write an implementation report to `.superpowers/sdd/task-1-durable-turn-attempt-report.md` when done.

## Existing foundation

- `V1__interview_lineage_and_turn_attempt_foundation.sql` already creates `t_interview_lineage`, branch/message extensions, and `t_interview_turn_attempt`.
- Allowed attempt states are `PROCESSING`, `COMPLETED`, `FAILED`, `INTERRUPTED`, `CANCEL_REQUESTED`, `CANCELLED`, and `DISCARDED`.
- A PostgreSQL partial unique guard already restricts one `PROCESSING` attempt per lineage.
- Existing `SSEProxyService` creates schema-compatible messages and lineages, but still binds processing to the original `/interviews/chat` SSE request and inserts the user message before the Python call. Replace this behavior for the new API without regressing legacy tests.
- Existing Lineage Summary and Branch Transcript read APIs are user-scoped and canonical transcripts already filter to completed delivery status.

## Required public contracts

Implement authenticated, user-owned endpoints equivalent to:

1. `POST /interviews/branches/{branchId}/turn-attempts`
   - Request includes a client-supplied `turnId` used as the idempotency key, candidate answer, `expectedBranchVersion`, and `expectedTailMessageId` (nullable only when the branch has no tail).
   - Creates or returns the same attempt for an exact idempotent replay.
   - Rejects reused `turnId` with a different payload.
   - Rejects stale branch version or tail with an explicit conflict response.
   - Rejects a second processing attempt anywhere in the same lineage.
2. `GET /interviews/turn-attempts/{turnId}` returns durable status and recoverable candidate input, but never exposes unsanitized internal errors.
3. `GET /interviews/turn-attempts/{turnId}/events` lets a client attach or reattach to live events. The worker lifetime must not depend on this subscription.
4. `POST /interviews/turn-attempts/{turnId}/retry` creates a new attempt linked by `retry_of_turn_id`, with explicit candidate input so edit-and-retry is supported. It uses fresh expected version/tail values and normal concurrency validation.
5. `POST /interviews/turn-attempts/{turnId}/cancel` makes a best effort to stop work and, critically, prevents a late canonical commit.
6. `POST /interviews/turn-attempts/{turnId}/discard` removes the attempt from normal recovery while preserving auditable durable state; it must not delete canonical transcript data.

All endpoints must require `X-User-Id` and enforce branch/lineage/attempt ownership. Cross-user access is denied.

## Processing and persistence rules

- Creating the attempt durably stores the candidate answer and request snapshot before scheduling work.
- The worker is server-owned (for example a Spring task executor); closing or never opening the events stream must not cancel it.
- Keep Python/model invocation behind an injectable boundary so deterministic tests can simulate success, failure, slow work, and cancellation. It may adapt the existing WebClient/SSE logic, but persistence/transactions must not be performed in client-stream callbacks.
- Accumulate model output as candidate result state outside canonical transcript rows while processing.
- On success, one Spring transaction must insert the candidate answer message and AI message, insert/update the score with `turn_id`, `question_message_id`, and `answer_message_id`, advance session stage/counters/status as applicable, update branch version and business activity, and change the attempt to `COMPLETED`.
- If processing fails, no candidate answer, AI message, or score from that attempt may appear in the canonical transcript. The attempt remains recoverable with sanitized diagnostics and status `FAILED` or `INTERRUPTED`.
- Before the success transaction writes anything canonical, re-check ownership, cancellation state, expected branch version, and expected tail. A cancel or stale state must prevent late commit.
- Business messages contain no stack traces, transport errors, partial AI output, or recovery diagnostics.
- The old `/interviews/chat` contract may remain as a compatibility adapter, but new attempt processing must be the authoritative implementation and must not reintroduce pre-call message insertion.
- On service startup, stale `PROCESSING` attempts left from a prior process should become explicitly recoverable (for example `INTERRUPTED`) rather than remaining permanently locked. Keep the policy bounded and testable.

## Data model guidance

- Reuse the client-supplied `turnId` as the primary idempotency key if practical; otherwise add a versioned `V2` migration for a unique idempotency key. Do not silently mutate the already-tested V1 semantics when an additive migration is clearer.
- Add Java entity/mapper fields for turn attempts and the existing score linkage columns.
- Prefer focused mapper/repository methods and a transaction service over expanding `SSEProxyService` into a second monolith.
- Transactional methods must be invoked across Spring bean boundaries; do not rely on private or self-invoked `@Transactional` methods.
- In-memory live event delivery is acceptable for this task only if durable attempt status remains authoritative and a reconnect can always recover final state. Python snapshot reconstruction and restart-safe replay are Task 2.

## Required tests

Add focused tests using the PostgreSQL migration fixture/Testcontainers where transaction or database guards matter, plus unit/controller tests where appropriate. At minimum prove:

1. Candidate answer is durable in the attempt but absent from canonical transcript before completion.
2. Python/model failure preserves candidate input, stores only sanitized diagnostics, and creates no partial canonical message or score.
3. Success atomically creates answer/question messages and linked score, increments branch version/activity, and completes the attempt.
4. A forced failure inside the commit transaction rolls back all canonical changes and leaves the attempt recoverable.
5. Stale expected branch version or tail is rejected without scheduling work.
6. Exact duplicate `turnId` submission returns the same attempt and schedules once; mismatched duplicate payload is rejected.
7. The database and service reject a second processing attempt in the same lineage.
8. Disposing an event subscriber does not cancel the server-owned worker.
9. Cancel prevents a delayed worker from performing a late commit.
10. Retry/edit/discard state transitions follow the contract and do not duplicate canonical data.
11. Cross-user create/read/events/retry/cancel/discard access is denied.
12. Startup recovery transitions stale processing attempts without touching recently active work.

Run the module's full Java test suite after focused tests pass. Use the established jenv/JDK 21 environment. Do not claim unrelated integration systems were tested.

## Completion report

The report must list:

- Files changed/created.
- API contract and state-machine decisions.
- Transaction and concurrency implementation.
- Exact tests and commands run with results.
- Any residual limitations intentionally deferred to Task 2 or later.
- Confirmation that no commit/push/deploy/live DB mutation occurred.
