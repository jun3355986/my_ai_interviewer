# Task 2 Brief: Java-Authoritative Python Reconstruction and Turn Idempotency

## Objective

Make Java/PostgreSQL branch state the business authority for every durable Turn Attempt. Java must compose and send a complete, versioned branch snapshot to Python. Python must rebuild its formal interview session from that snapshot whenever local memory/SQLite is missing or stale, process the submitted candidate answer deterministically, and make processing idempotent by `turnId` across process/session-cache restarts.

## Workspace and boundaries

- Workspace: `/Users/junjielong/.codex/worktrees/db0e/my_ai_interviewer`
- Java module: `ai_interview_backend/ai-interviewer-interview`
- Python project: `ai_interviewer`
- Preserve all current uncommitted changes.
- Do not commit, stage, push, deploy, restart shared runtime services, call real paid model providers, or mutate the authoritative running PostgreSQL/SQLite runtime data.
- Use test-driven development and deterministic fake/interviewer boundaries.
- Update `tests/docs/test-cases.md` and `tests/docs/tooling-guide.md`.
- Write `.superpowers/sdd/task-2-python-snapshot-report.md` on completion.

## Existing foundation

- Task 1 provides `TurnAttemptService`, a server-owned `TurnAttemptWorker`, injectable `TurnModelClient`, atomic `TurnCommitService`, and durable `turnId` state.
- `WebClientTurnModelClient` currently calls Python `/interview/chat` with the legacy linear request and depends on `python_session_id` cache/SQLite state.
- `InterviewHistoryService` already composes immutable ancestor prefixes plus focused branch delta for read transcripts.
- Python `InterviewService` and `SessionManager` currently restore only from Python SQLite and mutate the formal session during request processing.
- Python `/interview/chat` already emits `status`, `question`, `chunk`, `score`, `result`, `done`, and `error` events and carries Java/Python observability correlation fields.

## Required branch snapshot contract

Introduce an explicit schema-versioned request object shared by Java serialization and Python Pydantic validation. It must contain enough state to rebuild the formal Python `InterviewSession` without consulting pre-existing Python state:

- `schema_version`
- `turn_id`, `branch_id`, `lineage_id`, `branch_version`, `expected_tail_message_id`
- owner/correlation fields required for observability, without trusting them for Java authorization
- candidate name, resume content, job requirements
- current stage and branch status
- project question count/target, current follow-up count
- project and technical question pools in their structured/legacy-compatible form
- composed canonical transcript through the expected tail, including message ID, owning branch ID, role, content, stage, message type, response expectation, metadata, and sequence/path order
- completed assessments required to reconstruct project/technical Q&A and current scoring context
- the submitted candidate answer remains a separate Turn Attempt input and must not be included as an already-committed transcript message

Use deterministic ordering. Reject unsupported schema versions and malformed snapshots explicitly. Java must compose the snapshot under the same ownership and expected-version/tail assumptions used by the Turn Attempt; never send failed/interrupted messages or diagnostic data.

## Java requirements

1. Add a focused snapshot composer/service rather than coupling reconstruction logic to the HTTP client.
2. Compose inherited ancestry plus branch delta using the canonical business path. Reuse shared transcript path rules to avoid semantic drift.
3. Include completed assessments before the tail with deterministic message linkage.
4. Extend `TurnModelCommand` and `WebClientTurnModelClient` to send `turn_id` and `branch_snapshot` to Python.
5. Preserve existing request/agent/session/user observability correlation, including username where available.
6. Keep `TurnCommitService` as the only Java canonical write boundary. Python responses are candidate results, never direct Java business writes.
7. If snapshot composition detects version/tail drift before the model call, fail/interupt the attempt without calling Python.
8. Retain legacy `/interviews/chat` compatibility, but the durable Turn Attempt path must always send the authoritative snapshot.

## Python reconstruction requirements

1. Extend `UnifiedChatRequest` with required `turn_id` and `branch_snapshot` for the durable entrypoint while preserving legacy requests where explicitly supported.
2. Add a pure reconstruction function that creates a fresh `InterviewSession` from the snapshot. It must restore stage, history, question pools, counters, follow-up state, and Q&A/assessment lists.
3. When a snapshot is present, it always wins over stale in-memory or SQLite session state. Do not append or merge unknown local messages.
4. Use a branch-stable Python session identity. Python local persistence is a replaceable cache and may be rewritten from the Java snapshot.
5. Process the candidate answer against a reconstructed working session. Do not require `/interview/resume` or an earlier Python process to have seen the branch.
6. Preserve structured question/media metadata and current stage-transition behavior.
7. Keep existing SSE event names and observability correlation where compatible. Include `turn_id` in status/result/done metadata where useful for correlation.

## Python `turnId` idempotency

- Persist a Python-side turn ledger in the local SQLite cache (or an equally restart-safe local store) keyed by `turn_id`.
- Store a deterministic hash of the relevant input: snapshot schema/version, branch ID/version/tail, and candidate answer.
- The first invocation records processing ownership before mutating cached session state.
- A completed invocation stores the complete structured result needed to replay the same SSE semantic output without invoking the interviewer or advancing the session twice.
- An exact duplicate after memory or process restart returns the stored result and does not call model/scoring functions again.
- Reusing a `turn_id` with different input returns an explicit idempotency conflict.
- A stale local `PROCESSING` row must be recoverable. It may be safely reprocessed from the immutable Java snapshot if no completed result exists, but must never merge partial local state.
- A Python failure must not mark the turn completed or leave a mutated authoritative cache state that affects a later retry.
- Keep raw internal exception details out of SSE business content.

## Required tests

Add focused Java and Python tests. At minimum prove:

1. Java snapshot composition includes ancestor prefix plus branch delta, deterministic IDs/order, semantics/metadata, counters/pools, and completed assessment linkage.
2. Java excludes incomplete/error messages and assessments beyond the expected tail.
3. Ownership, Branch Version, and tail drift prevent snapshot/model invocation.
4. `WebClientTurnModelClient` sends `turn_id`, schema version, snapshot, and correlation fields, and parses the existing SSE result contract.
5. Python reconstructs a session with no memory and no prior SQLite session row, then processes the next turn successfully.
6. Python snapshot overrides deliberately stale/conflicting memory and SQLite state.
7. Reconstructed project, follow-up, technical, structured-media, and concluded boundaries match equivalent uninterrupted session behavior.
8. Exact duplicate `turn_id` invokes the deterministic interviewer only once and replays the same result.
9. Duplicate behavior still holds after recreating `SessionManager`/service objects against the same temporary SQLite database.
10. Reused `turn_id` with a changed answer, branch version, tail, or snapshot hash is rejected.
11. A simulated Python failure does not persist a completed ledger result or leak partial session mutation into retry.
12. Unsupported/malformed snapshot contracts fail with sanitized explicit SSE errors.
13. Existing Python observability/SSE/rich-question tests remain green.
14. Task 1 Java lifecycle and the full Interview Java module remain green after integration.

Use temporary test database paths or injectable repositories; never read/write the real `ai_interviewer/storage/database/interviews.db` during tests.

## Completion report

The report must list:

- Files changed/created.
- Final snapshot schema and reconstruction rules.
- Python idempotency ledger/state machine decisions.
- Exact focused/full Java and Python commands and results.
- Compatibility behavior and intentionally deferred limitations.
- Confirmation of no commit/push/deploy/shared-runtime or authoritative-data mutation.
