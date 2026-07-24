# Task 2 Report: Java-Authoritative Python Reconstruction and Turn Idempotency

Status: **DONE**

Date: 2026-07-24

Workspace: `/Users/junjielong/.codex/worktrees/db0e/my_ai_interviewer`

## Outcome

The durable Turn Attempt path now treats Java/PostgreSQL as the interview business authority on every turn:

1. Java persists the authenticated Turn Attempt creator as immutable `owner_user_id` and composes a schema-versioned canonical branch snapshot under that owner, Branch Version, and expected-tail assumptions.
2. The asynchronous Java worker sends `turn_id`, the complete snapshot, request/agent/session/user correlation, and the persisted optional username to Python.
3. Python validates the contract and reconstructs a fresh formal `InterviewSession`; snapshot state replaces, rather than merges with, stale memory or SQLite state.
4. Python acquires restart-safe `turn_id` processing ownership in a local SQLite ledger before model/session mutation, then owner/status-fences completion and failure cleanup. The complete result and SQLite session cache commit atomically before the in-memory cache is published.
5. Python returns a complete `post_turn_state` candidate. `TurnCommitService` remains the only Java canonical write boundary and atomically commits that state with the candidate answer, AI message, score, branch activity, and Branch Version increment.
6. Consecutive-turn tests prove that the next snapshot uses the prior Java commit. Python restart tests prove that a later turn still reconstructs from the supplied snapshot rather than depending on prior process memory.

No real model provider was invoked.

## Files changed or created

### Java snapshot and model boundary

- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/model/BranchSnapshot.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/model/BranchSnapshotMessage.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/model/BranchSnapshotAssessment.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/model/AuthoritativeTurnState.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/model/TurnModelCommand.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/model/TurnModelResult.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/model/WebClientTurnModelClient.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/PythonChatRequest.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/BranchSnapshotComposer.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/TurnAttemptWorker.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/TurnCommitService.java`

### Java username persistence and structured pool support

- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/controller/TurnAttemptController.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/TurnAttemptService.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/repository/TurnAttemptRepository.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/entity/InterviewTurnAttempt.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/entity/InterviewSession.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/resources/db/migration/V3__persist_turn_attempt_username.sql`
- `ai_interview_backend/ai-interviewer-interview/src/main/resources/db/migration/V4__persist_turn_attempt_owner.sql`

### Python reconstruction, cache injection, and ledger

- `ai_interviewer/schemas/branch_snapshot.py`
- `ai_interviewer/schemas/chat.py`
- `ai_interviewer/services/branch_reconstruction.py`
- `ai_interviewer/services/durable_turn.py`
- `ai_interviewer/services/database.py`
- `ai_interviewer/services/interview_session.py`
- `ai_interviewer/services/interview_service.py`
- `ai_interviewer/services/question_bank.py`
- `ai_interviewer/api/router.py`
- `ai_interviewer/tests/conftest.py`

### Tests

- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/BranchSnapshotComposerTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/model/WebClientTurnModelClientSnapshotTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/TurnAttemptWorkerSnapshotTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/TurnAttemptWorkerSchedulingTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/TurnAttemptLifecycleIntegrationTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/controller/TurnAttemptControllerTest.java`
- `ai_interviewer/tests/test_database_injection.py`
- `ai_interviewer/tests/test_branch_snapshot_reconstruction.py`
- `ai_interviewer/tests/test_durable_turn_processor.py`
- `ai_interviewer/tests/test_durable_turn_router.py`

### Documentation

- `docs/INTERVIEW_HISTORY_RESUME_REPLAY_DESIGN.md`
- `docs/INTERVIEW_HISTORY_RESUME_REPLAY_IMPLEMENTATION_PLAN.md`
- `tests/docs/test-cases.md`
- `tests/docs/tooling-guide.md`
- `.superpowers/sdd/task-2-python-snapshot-report.md`

## Final snapshot schema

Schema version: `1`.

Top-level fields:

- identity and concurrency: `schema_version`, `turn_id`, `branch_id`, `lineage_id`, `branch_version`, `expected_tail_message_id`;
- ownership/observability: `owner_user_id`, optional `username`;
- interview inputs: `candidate_name`, `resume_content`, `job_requirements`;
- formal state: `current_stage`, `branch_status`, `project_questions_count`, `target_project_questions`, `current_followup_count`;
- remaining work: `project_questions_pool`, `technical_questions_pool`, retaining legacy strings and structured question objects;
- canonical history: ordered `messages` containing Java message ID, owning branch, role, content, stage, message type, response expectation, metadata, sequence, and deterministic path order;
- scoring context: ordered `assessments` linked to question and answer message IDs that both exist on the canonical path.

The submitted candidate answer is not placed in the snapshot. It remains a separate immutable Turn Attempt input, preventing Python from treating uncommitted input as an existing Business Message.

Java filters non-completed messages and assessments whose linked answer/question is outside the snapshot path. Python rejects malformed message ordering, duplicate IDs, a tail mismatch, an unsupported schema version, or invalid stage through sanitized contract errors.

## Reconstruction rules

- The Java branch ID is the branch-stable Python session ID.
- Reconstruction is pure: it creates a new `InterviewSession` from the snapshot without consulting existing Python memory or SQLite.
- Canonical messages are restored in Java path order, including structured question/media metadata.
- Linked assessments rebuild project and technical Q&A lists.
- Stage, project count/target, follow-up count, and both question pools are copied from the snapshot.
- The candidate answer is processed against the fresh working session.
- Only after successful deterministic processing and fenced ledger completion are the durable result and replaceable SQLite cache committed in the same transaction; the in-memory cache is updated afterward.
- A deliberately stale/conflicting in-memory and SQLite session is overwritten by snapshot-derived state; unknown local messages are never merged.

## Authoritative post-turn state

The Python `result` SSE event now includes `post_turn_state` with:

- `current_stage`;
- `branch_status`;
- `project_questions_count`;
- `target_project_questions`;
- `current_followup_count`;
- `project_questions_pool`;
- `technical_questions_pool`.

`WebClientTurnModelClient` requires this state on the durable snapshot path. `TurnCommitService` validates stage/status/completion consistency and non-negative counters, serializes both pools, and writes the complete state in the existing atomic PostgreSQL transaction. Python never writes Java/PostgreSQL business rows directly.

## Python turn ledger decisions

Table: local SQLite `turn_ledger`, keyed by `turn_id`.

Stored fields include the deterministic full-input hash, state, owner token, complete structured result, and timestamps.

State machine:

1. Missing row: atomically `INSERT ... ON CONFLICT DO NOTHING` a `PROCESSING` owner before session/model mutation.
2. Same hash + fresh `PROCESSING`: return `TURN_PROCESSING_IN_PROGRESS` without invoking the interviewer.
3. Same hash + stale `PROCESSING`: perform a compare-and-swap using the observed owner token and timestamp. Exactly one connection becomes the new owner.
4. Same hash + `COMPLETED`: deserialize and replay the complete durable result without model/scoring/session advancement.
5. Different hash: return `TURN_IDEMPOTENCY_CONFLICT`.
6. Processing success: one SQL compare-and-swap updates only the row still in `PROCESSING` and owned by that processor. The result and SQLite session cache commit together; a late old owner fails the fence and publishes no memory cache.
7. Processing failure before completion: one owner/status-fenced delete removes only the row still owned by that processor, does not publish a completed result, and permits a safe retry from the immutable Java snapshot. A late old owner cannot delete a takeover winner.

The input hash covers the normalized full snapshot plus candidate answer, so changed answer, Branch Version, tail, candidate fields, pools, history, or other snapshot content conflicts with reuse of the same `turn_id`.

Concurrent tests use two independent SQLAlchemy engines/connections against the same temporary SQLite file. They cover simultaneous first acquisition, simultaneous stale-owner takeover, and deterministic late old-owner success/failure after a takeover. In each acquisition race only one fake interviewer call occurs; the loser receives `PROCESSING`, and a later exact call replays the winner's completed result. In late-owner races, the old worker neither publishes cache state nor overwrites/deletes the winner.

The Java WebClient model call is bounded at 10 minutes. Python defaults the stale-processing lease to 15 minutes and rejects lease configuration at or below the supported call timeout, so a legitimate call within the supported maximum duration cannot be declared stale. Ledger acquisition/replay/storage failures are wrapped as sanitized durable exceptions, and the durable router never returns raw database details.

## Username and observability correlation

- Turn Attempt create and retry accept optional gateway `X-User-Name`.
- The value is persisted on `t_interview_turn_attempt` by Flyway migration V3.
- A retry without a new username inherits the original attempt username.
- The asynchronous worker passes the persisted username into the snapshot and Python request together with `turn_id`, `request_id`, `agent_run_id`, Java branch/session ID, lineage ID, and user ID.
- The username is correlation metadata only; Java authorization continues to use the authenticated user ID and owned branch/lineage checks.

## Immutable attempt ownership

- Flyway V4 adds required `t_interview_turn_attempt.owner_user_id` and backfills pre-V4 attempts from their owning interview session.
- Create persists the authenticated user ID once; retry creates a new attempt with the authenticated owner.
- The asynchronous worker uses the attempt owner for snapshot composition and the Python model command instead of trusting mutable branch ownership.
- `TurnCommitService` rechecks the immutable owner against the locked branch and lineage before canonical writes.
- Tests cover ownership drift while queued and while model work is in flight; both paths prevent Python/canonical writes or reject the late commit.

## TDD evidence

Observed RED failures before implementation included:

- missing Java `BranchSnapshot` contract at test compilation;
- missing Python snapshot/reconstruction and durable processor modules;
- missing durable router boundary;
- missing `post_turn_state` on successful durable results;
- raw SQLite `UNIQUE constraint failed: turn_ledger.turn_id` escaping during simultaneous first acquisition;
- both stale processors taking ownership and blocking in the interviewer;
- missing Java authoritative-state model and create/retry username boundary;
- second-turn integration remaining `PROCESSING` while the test snapshot composer still returned stale hard-coded state.
- late old-owner success publishing memory/SQLite cache before losing its completion fence;
- read-then-delete failure cleanup without a single owner/status-fenced statement;
- raw SQLite acquisition details escaping the durable route;
- an unsafe stale lease equal to the supported Java model-call timeout;
- Java compilation missing immutable attempt owner accessors and queued ownership drift trusting the current branch owner.

Each was followed by the focused GREEN commands below. No real provider was used; fake interviewer/model boundaries were deterministic.

## Verification

Effective JDK confirmation:

```bash
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv version
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv global
```

Result:

```text
21 (set by /Users/junjielong/.jenv/version)
21
```

### Python focused durable snapshot/ledger/router

```bash
cd ai_interviewer
uv run pytest tests/test_durable_turn_processor.py tests/test_durable_turn_router.py -q
```

Result: `22 passed in 1.19s`.

The broader snapshot/rich-SSE compatibility focused run was also executed:

```bash
cd ai_interviewer
uv run pytest \
  tests/test_database_injection.py \
  tests/test_branch_snapshot_reconstruction.py \
  tests/test_durable_turn_processor.py \
  tests/test_durable_turn_router.py \
  tests/test_interview_technical_transition.py \
  tests/test_rich_question_interview_flow.py \
  tests/test_rich_question_sse.py \
  tests/test_router_observability.py \
  -q
```

Result: `39 passed in 1.87s`.

### Python maintained full unit suite

```bash
cd ai_interviewer
uv run pytest tests -q
```

Result: `88 passed in 1.76s`.

The top-level `test_interview.py` live HTTP dual-agent simulation is intentionally not part of this maintained unit-suite command.

### Java focused snapshot/state/username and Task 1 lifecycle

```bash
cd ai_interview_backend
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv exec mvn \
  -pl ai-interviewer-interview -am \
  -Dtest=BranchSnapshotComposerTest,WebClientTurnModelClientSnapshotTest,TurnAttemptWorkerSnapshotTest,TurnAttemptControllerTest,TurnAttemptWorkerSchedulingTest,TurnAttemptLifecycleIntegrationTest,InterviewFlywayMigrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test -q
```

Result: `36 tests`, `0 failures`, `0 errors`, `0 skipped`.

### Java full Interview module

```bash
cd ai_interview_backend
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv exec mvn \
  -pl ai-interviewer-interview -am \
  test -q
```

Result: `50 tests`, `0 failures`, `0 errors`, `0 skipped`.

All PostgreSQL integration/migration checks used ephemeral PostgreSQL 16 Testcontainers databases.

## Storage and mutation boundary read-back

- Python tests set `AI_INTERVIEW_DB_PATH` and `AI_INTERVIEW_VECTOR_DB_PATH` to OS temporary locations before module imports.
- Repository runtime SQLite remained unchanged during the final focused/full runs: `ai_interviewer/storage/database/interviews.db`, size `12288`, mtime `2026-07-23 21:13:03`.
- No worktree `ai_interviewer/storage/vector_db` file exists after verification.
- The separate main-workspace Chroma database remained unchanged: inode `120842488`, size `11902976`, mtime `2026-07-14 17:54:49`.

During the first test-collection attempt, eager module-level `QuestionBank()` initialization created an empty worktree `storage/vector_db/chroma.sqlite3`. Investigation proved it was a new worktree inode with zero embeddings/queue rows and that the separate main-workspace Chroma database was untouched. After explicit cleanup authorization, only that task-created worktree file was removed. The test-path injection and lazy service initialization changes prevent recurrence; subsequent focused and full tests created no repository vector file.

## Compatibility and intentionally deferred work

- Legacy non-durable `/interview/chat` requests remain supported and do not require a branch snapshot.
- Existing SSE event names and ordering remain `status`, optional `score`, optional `question`, `chunk`, `result`, and `done`; durable events add `turn_id` correlation and `post_turn_state` on `result`.
- Existing Task 1 Java lifecycle, cancellation, recovery, transaction, and migration behavior remains green.
- The independent Task 2 review's Critical owner-fencing issue and two Important immutable-owner/error-sanitization issues are remediated with deterministic regression coverage; the bounded lease concern is also closed by the 10-minute/15-minute invariant.
- Python SQLite and Chroma remain disposable caches, not business authority.
- No live Java-to-Python deployment smoke, real provider call, Flutter integration change, fork creation, branch switching, or production data migration was performed in this task.

## Boundary confirmation

- No commit created.
- No files staged.
- No push or pull request.
- No deployment or Jenkins action.
- No shared runtime service restart.
- No paid model/API call.
- No authoritative running PostgreSQL mutation.
- No authoritative runtime SQLite/Chroma mutation.
