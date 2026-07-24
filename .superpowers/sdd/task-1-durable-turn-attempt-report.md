# Task 1 Implementation Report: Durable Turn Attempt Processing

Status: complete

Date: 2026-07-24

Workspace: `/Users/junjielong/.codex/worktrees/db0e/my_ai_interviewer`

Effective Java runtime: jenv JDK `21.0.11`; no global jenv setting was changed.

## Outcome

The Interview Service now exposes a durable Turn Attempt path in which candidate input and the request concurrency snapshot are committed before server-owned model work is scheduled. Model work is independent of an attached SSE subscriber. Successful results enter canonical interview messages, score linkage, session state, Branch Version, lineage activity, and the attempt terminal state in one Spring transaction. Failure, cancellation, stale state, and forced transaction failure leave no partial canonical rows.

The original V1 schema contained the attempt, message, branch, lineage, and score-linkage columns. Independent review identified that `CANCEL_REQUESTED` also has to retain the lineage processing slot until the worker exits, so V2 replaces the original partial unique index with a guard covering both `PROCESSING` and `CANCEL_REQUESTED`.

## Files changed or created

### Public API and configuration

- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/controller/TurnAttemptController.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/controller/TurnAttemptExceptionHandler.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/config/TurnAttemptConfiguration.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/resources/application.yml`

### Request, response, entity, and model contracts

- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/CreateTurnAttemptRequest.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/RetryTurnAttemptRequest.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/TurnAttemptDTO.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/TurnAttemptEventDTO.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/entity/InterviewTurnAttempt.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/entity/ScoreRecord.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/model/TurnModelClient.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/model/TurnModelCommand.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/model/TurnModelResult.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/model/WebClientTurnModelClient.java`

### Persistence, worker, state machine, and transaction boundary

- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/repository/TurnAttemptRepository.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/TurnAttemptService.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/TurnAttemptWorker.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/TurnCommitService.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/TurnAttemptEventPublisher.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/TurnAttemptConflictException.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/TurnCommitRejectedException.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/resources/db/migration/V2__keep_cancel_requested_in_lineage_processing_slot.sql`

### Tests and test documentation

- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/TurnAttemptLifecycleIntegrationTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/TurnAttemptWorkerSchedulingTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/TurnAttemptEventPublisherTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/controller/TurnAttemptControllerTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/migration/InterviewFlywayMigrationTest.java`
- `tests/docs/test-cases.md`
- `tests/docs/tooling-guide.md`

## API contract decisions

Implemented authenticated endpoints:

- `POST /interviews/branches/{branchId}/turn-attempts`
- `GET /interviews/turn-attempts/{turnId}`
- `GET /interviews/turn-attempts/{turnId}/events`
- `POST /interviews/turn-attempts/{turnId}/retry`
- `POST /interviews/turn-attempts/{turnId}/cancel`
- `POST /interviews/turn-attempts/{turnId}/discard`

Every endpoint requires `X-User-Id`; there is no fallback to user 1. Branch, lineage, and attempt ownership is resolved server-side. Cross-user create/read/events/retry/cancel/discard calls are denied.

The client-supplied `turnId` is the durable primary idempotency key. An exact replay returns the existing attempt without scheduling a second worker. Reusing a `turnId` with a different branch, answer, expected version, expected tail, or retry parent returns HTTP 409 with an explicit conflict reason.

Branch Version and tail message are checked before attempt creation and checked again under row locks immediately before canonical commit. The commit boundary also verifies that the locked branch is still active. Stale or cancelled-branch submissions cannot write canonical rows. The PostgreSQL partial unique index is the final race guard for one unresolved processing slot (`PROCESSING` or `CANCEL_REQUESTED`) per lineage.

`GET` responses expose recoverable candidate input, lifecycle timestamps, state, retry relationship, and a sanitized error code. Internal exception messages, stack traces, transport text, and diagnostic references are not returned. Raw failures are logged server-side against a generated diagnostic reference.

The event endpoint returns an authoritative durable status snapshot followed by an ordered in-memory stream. Events carry a monotonic per-attempt sequence and SSE ids use `turnId:sequence`. Ordinary and terminal emissions are serialized per stream, every Reactor `tryEmitNext`/`tryEmitComplete` result is checked, and the stream is removed only after the terminal event is delivered and completion resolves. Concurrent `cancel_requested`/`cancelled` publication yields exactly one terminal event and completes. Terminal reconnects return one fresh durable snapshot and complete. Disposing the stream subscription removes an unused sink but does not cancel the worker. Explicit cancellation is the only client operation that prevents a late commit.

## State-machine decisions

- Creation persists `PROCESSING`, candidate answer, expected Branch Version, expected tail, request ID, agent run ID, and optional retry parent in a transaction.
- Worker scheduling occurs in `afterCommit`, ensuring model work cannot start before durable attempt creation commits.
- Success transitions `PROCESSING -> COMPLETED` inside the canonical commit transaction.
- Model/provider and commit failures transition `PROCESSING -> FAILED` outside the rolled-back canonical transaction with a generic `MODEL_PROCESSING_FAILED` code. The production Python SSE boundary requires both `result` and `done`, rejects truncated EOF, and verifies that result/done stages and completion semantics agree.
- A stale version/tail or ownership change detected at commit time transitions the unresolved attempt to `INTERRUPTED` and writes no canonical rows.
- Cancel performs `PROCESSING -> CANCEL_REQUESTED`, retains the lineage processing slot, and publishes/interrupts the actual local worker only after the cancellation transaction commits. Per-turn worker control distinguishes queued, running, rejected, and finished tasks. A queued-before-start task is cancelled and finalized immediately; only a genuinely running worker waits until exit to finalize `CANCELLED`. Commit-time status recheck rejects providers that ignore interruption.
- Executor rejection immediately transitions the durable attempt to recoverable `INTERRUPTED` with `WORKER_SCHEDULING_REJECTED`. If cancellation wins the concurrent database race, the same scheduling path finalizes `CANCELLED` instead, so no workerless `CANCEL_REQUESTED` row can retain the lineage slot permanently.
- Retry creates a distinct attempt and stores `retry_of_id`; edited candidate input is required on the retry request.
- Discard changes eligible failed/interrupted/cancelled attempts to `DISCARDED`. It does not delete attempt audit state or canonical transcript data.
- Startup recovery changes unresolved `PROCESSING` or `CANCEL_REQUESTED` attempts older than the configured `interview.turn-attempt.stale-after` duration to `INTERRUPTED`; recently updated attempts are not touched.

## Transaction and concurrency implementation

`TurnCommitService.commit` is a separate Spring bean with a public `@Transactional` method, so transaction advice is not bypassed by self-invocation.

The success transaction:

1. Locks and rechecks the attempt.
2. Locks and rechecks the owned branch and lineage.
3. Rechecks expected Branch Version and canonical tail.
4. Inserts the candidate answer message.
5. Inserts the complete AI message and structured question metadata.
6. Inserts a score linked by `turn_id`, `question_message_id`, and `answer_message_id` when scoring is present.
7. Advances stage/status and applicable project-question count, links the Python session ID, increments Branch Version, and updates branch activity.
8. Updates lineage business activity.
9. Marks the attempt `COMPLETED`.

A PostgreSQL trigger-induced score insert failure was used in the integration test to prove that both messages, score, Branch Version, activity, and completion state roll back together. The worker then records a recoverable `FAILED` attempt in a separate transaction.

`TurnModelClient` is injectable. Production uses `WebClientTurnModelClient`, which consumes the existing Python SSE contract on a server-owned executor thread, requires an explicit consistent terminal `done`, and accumulates complete output outside canonical tables. Tests cover the real WebClient boundary and inject deterministic success, failure, blocking, and interruption-ignoring implementations.

## TDD evidence and verification commands

The initial focused test was written before production classes. With jenv JDK 21 it failed at test compilation because the requested Turn Attempt DTOs, repository, model boundary, services, worker, and controller did not exist. After implementation, behavioral failures were resolved through the focused suite. A later metadata assertion was also observed failing with `expected: "q-next" but was: null` before structured metadata was added to the atomic commit.

Independent review findings were also fixed test-first. The first review RED tests failed because terminal model validation, `CANCEL_REQUESTED` slot retention, scheduler rejection recovery, branch-cancellation recheck, and bounded ordered event streams were absent. The second review added deterministic scheduling barriers and a blocked subscriber callback: the focused run failed three tests because rejection/cancel left no cancellation terminal write, queued cancellation performed no terminal transition, and concurrent ordinary/terminal Reactor emissions lost terminal completion. The production changes were then made until both review regression sets and the original suite passed together.

An initial Testcontainers run failed before test execution because Ryuk could not connect to its Docker Desktop published callback port. The documented focused/full commands set `TESTCONTAINERS_RYUK_DISABLED=true`; all declared PostgreSQL test containers were stopped by the test JVM. No test used the authoritative local PostgreSQL instance.

Focused verification:

```bash
cd ai_interview_backend
TESTCONTAINERS_RYUK_DISABLED=true \
JENV_ROOT="$HOME/.jenv" \
/opt/homebrew/bin/jenv exec mvn \
  -pl ai-interviewer-interview -am \
  -Dtest=TurnAttemptLifecycleIntegrationTest,TurnAttemptControllerTest,TurnAttemptWorkerSchedulingTest,TurnAttemptEventPublisherTest,InterviewFlywayMigrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test -q
```

Result: 27 tests, 0 failures, 0 errors, 0 skipped.

- `TurnAttemptLifecycleIntegrationTest`: 18 tests.
- `TurnAttemptControllerTest`: 2 tests.
- `TurnAttemptWorkerSchedulingTest`: 3 tests.
- `TurnAttemptEventPublisherTest`: 1 test.
- `InterviewFlywayMigrationTest`: 3 tests.

Full Java module verification:

```bash
cd ai_interview_backend
TESTCONTAINERS_RYUK_DISABLED=true \
JENV_ROOT="$HOME/.jenv" \
/opt/homebrew/bin/jenv exec mvn \
  -pl ai-interviewer-interview -am \
  test -q
```

Result: 40 tests across 11 suites, 0 failures, 0 errors, 0 skipped.

Additional final checks:

- `git diff --check`: clean.
- `git diff --cached --name-only`: empty; nothing staged.
- No stopped/running task-created `postgres:16-alpine` container remained. The only matching long-running container was the pre-existing authoritative `ai-interviewer-postgres`, reported healthy and up for 9 days; it was not used or mutated.

## Required regression coverage proved

The focused PostgreSQL/controller suite proves:

1. Candidate input is durable before model completion and absent from canonical history.
2. Model failure preserves input, exposes only a sanitized code, and creates no message or score.
3. Success atomically writes answer/AI messages, rich metadata, linked score, Branch Version/activity, and completion.
4. Forced failure inside the commit transaction rolls back all canonical changes and leaves a recoverable failure.
5. Stale expected version or tail is rejected without model scheduling.
6. Exact duplicate `turnId` schedules once; mismatched reuse conflicts.
7. Service validation and the PostgreSQL partial unique index both reject a second unresolved processing attempt in a lineage, including while cancellation is requested but the worker has not exited.
8. Truncated production SSE and inconsistent `result`/`done` stages fail the attempt without creating canonical rows.
9. Executor rejection immediately leaves a recoverable `INTERRUPTED` attempt instead of a stuck `PROCESSING` row; a concurrent cancellation is finalized as `CANCELLED` without a permanent lineage slot.
10. Cancelling an accepted but not-yet-started queued task finalizes it immediately and prevents later model invocation.
11. Disposing an event subscriber does not cancel server-owned work and cleans up an unused in-memory sink.
12. Active SSE events are monotonic, terminal streams complete, reconnect returns only the authoritative terminal snapshot, and terminal sinks are removed.
13. Concurrent `cancel_requested` and `cancelled` emissions are serialized, produce exactly one terminal event, complete, and do not silently ignore Reactor emission failure results.
14. Cancellation prevents an interruption-ignoring provider from committing late output.
15. Cancelling the interview branch during model work prevents a late canonical commit.
16. Edit-retry linkage and discard transitions do not duplicate canonical data.
17. All create/read/events/retry/cancel/discard cross-user operations are denied.
18. Startup recovery interrupts only unresolved attempts older than the configured stale threshold.

## Intentionally deferred limitations

- Task 2 still owns Java-authoritative branch snapshot construction, Python runtime reconstruction, Python-side `turnId` idempotency, and restart-safe model replay.
- Live progress events are currently in-memory. Durable attempt status and candidate input remain authoritative, and reconnect always receives a fresh status snapshot, but intermediate chunk replay does not survive a Java process restart.
- Worker ownership and stale-attempt recovery currently assume one active Java Interview Service instance. Before horizontal scaling, recovery needs distributed worker ownership/lease semantics so one instance cannot interrupt another instance's live attempt.
- The legacy `/interviews/chat` endpoint remains available as a compatibility path. The new Turn Attempt endpoints are the durable authoritative path for new callers; migrating Flutter submission to them is part of the later replay/processing UI task.
- Full fork/tree semantics and inherited assessment composition remain Tasks 3 and 4.
- Fresh `init.sql` synchronization and cross-service/manual enablement remain Task 5.

## Boundary confirmation

- No commit was created.
- No files were staged.
- No push or pull request was performed.
- No deployment occurred.
- No shared runtime service was restarted.
- No authoritative running PostgreSQL database was queried or mutated by this task.
- All database mutation tests ran only against ephemeral PostgreSQL 16 Testcontainers instances.
- Existing uncommitted workspace changes were preserved.
