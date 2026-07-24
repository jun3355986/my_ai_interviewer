# Interview History, Resume, and Replay Implementation Plan

Status: implementation in progress; Phase 0 through the Phase 3 Flutter replay client are implemented and verified, while final migration/E2E enablement remains pending

Confirmed: 2026-07-24

Design source: [`INTERVIEW_HISTORY_RESUME_REPLAY_DESIGN.md`](INTERVIEW_HISTORY_RESUME_REPLAY_DESIGN.md)

## Delivery Rule

Implement in dependency order and expose only complete capabilities. Phase 1 may enable real history and reliable tail resume. Fork/replay controls remain hidden until Phases 2 and 3 pass end-to-end verification.

## Phase 0: Baseline and Safety

Implementation status: completed on 2026-07-23. A local PostgreSQL backup and baseline counts were captured without committing live data.

### Tasks

1. Confirm the actual checkout, branch, Compose project, container image provenance, and PostgreSQL volume before changing or validating runtime state.
2. Confirm the effective JDK through jenv before Maven work; do not change the global JDK. The backend requires Java 21.
3. Capture row counts and integrity checks for:
   - `t_interview_session`
   - `t_interview_message`
   - `t_score_record`
   - `t_evaluation`
4. Export a local-only backup before applying migration work to populated data.
5. Capture the current legacy acceptance anchor and create a redacted structural fixture.
6. Add planned tests to `tests/docs/test-cases.md` as their implementations are introduced.

### Gate

- Existing data counts and backup location are recorded.
- Current P0 interview smoke and relevant unit tests have a known baseline.
- The live acceptance row is not copied into git.

## Phase 1: Real History and Reliable Resume

### 1.1 Flyway foundation

Implementation status: foundation and legacy/new-root migration tests completed; fresh `init.sql` synchronization remains part of the final migration enablement pass.

Likely files:

- `ai_interview_backend/ai-interviewer-interview/pom.xml`
- `ai_interview_backend/ai-interviewer-interview/src/main/resources/application.yml`
- `ai_interview_backend/ai-interviewer-interview/src/main/resources/db/migration/`
- `ai_interview_backend/sql/init.sql`

Tasks:

1. Add Flyway to the Interview Service only.
2. Configure baseline for the existing shared PostgreSQL schema.
3. Add nullable lineage, branch-version, message-semantics, and turn-attempt structures.
4. Add versioned, transaction-safe legacy backfill.
5. Produce migration audit queries and failure diagnostics.
6. Keep fresh-environment `init.sql` synchronized.

### 1.2 History contracts

Implementation status: Lineage Summary, composed Branch Transcript, ownership checks, controller contracts, and PostgreSQL mapper integration completed.

Likely Java files:

- `InterviewController.java`
- `InterviewService.java`
- `InterviewSessionMapper.java`
- `InterviewMessageMapper.java`
- new lineage/transcript DTOs, entities, mappers, and services

Tasks:

1. Implement paged Lineage Summary queries.
2. Implement branch detail and composed transcript reads.
3. Implement default Focused Branch selection.
4. Enforce authenticated ownership on every read.
5. Keep old list/detail endpoints compatible while migrating Flutter callers.

### 1.3 Reliable turn processing

Implementation status: completed. The durable Turn Attempt API, server-owned worker, optimistic concurrency, lineage processing slot, atomic canonical commit, cancellation/retry/discard recovery, ordered reconnectable events, and focused/full Java verification are implemented. Final independent review found no Critical or Important issues.

Likely Java files:

- `SSEProxyService.java`
- new `TurnAttemptService`, worker, repository, and event publisher classes
- score/session/message services and mappers

Tasks:

1. Replace pre-call direct user-message insertion with durable Turn Attempt creation.
2. Process the Python call independently of the Flutter SSE connection.
3. Commit messages, score, stage, counters, Branch Version, and Business Activity atomically.
4. Implement idempotency, stale-state detection, explicit cancel, retry, edit, and discard.
5. Enforce one processing Turn Attempt per Lineage with a PostgreSQL guard.
6. Preserve sanitized diagnostic data outside Business Messages.

### 1.4 Java-authoritative Python reconstruction

Implementation status: completed on 2026-07-24. Java now composes a schema-versioned canonical branch snapshot, Python rebuilds a fresh formal session from it, exact `turnId` duplicates replay from an atomic restart-safe owner-fenced SQLite ledger, and Python returns the complete post-turn state for one-transaction Java commit. Focused concurrency tests cover simultaneous first ownership, stale-owner takeover, and late-owner fencing; consecutive-turn tests cover restart and prove the next snapshot uses the prior committed state. Final independent review found no Critical or Important issues.

Likely Python files:

- `ai_interviewer/api/router.py`
- `ai_interviewer/schemas/chat.py`
- `ai_interviewer/services/interview_session.py`
- `ai_interviewer/services/interview_service.py`

Tasks:

1. Add a branch-snapshot request contract.
2. Rebuild the formal Python session from the supplied snapshot when cache/SQLite state is absent or stale.
3. Treat Python persistence as a cache, not business authority.
4. Make `turnId` processing idempotent.
5. Preserve current observability correlation and SSE event semantics where compatible.

Additional completed invariants:

6. Persist optional gateway `X-User-Name` on create/retry so the asynchronous worker retains observability identity.
7. Make SQLite first acquisition an atomic insert and stale takeover a compare-and-swap; losing connections return processing/replay rather than invoking the interviewer twice.
8. Return stage/status, project/technical pools, project count/target, and follow-up count as authoritative post-turn state; only `TurnCommitService` writes them to PostgreSQL.
9. Inject temporary SQLite and Chroma paths during tests so collection and execution never mutate runtime cache files.

### 1.5 Flutter real history and tail resume

Implementation status: completed. Real history, server-side status filtering before pagination, persisted replay, side-effect-free hydration, read-only completed replay, durable tail continuation, processing reattachment, Turn Recovery, and saved-progress exit UX are implemented.

Likely Flutter files:

- `lib/api/interview_api.dart`
- `lib/services/interview_service.dart`
- `lib/interview_history_page.dart`
- `lib/history_detail_page.dart`
- `lib/interview_chat_page.dart`
- new history, branch, message, turn, and recovery models

Tasks:

1. Remove static history/detail data.
2. Load real Lineage Summaries and transcripts.
3. Remove `sendMessage('继续')` resume behavior.
4. Hydrate the focused branch without calling AI.
5. Add loading, empty, auth-expiry, error, processing, and Turn Recovery states.
6. Keep replay/fork controls feature-gated until later phases.

### Phase 1 Gate

- Real history is visible.
- An unfinished branch resumes from the persisted last valid prompt.
- Page open does not create a model request or message.
- Page exit does not cancel submitted processing.
- Failure preserves the candidate answer but keeps partial/error content out of transcript.
- Legacy anchor `ef3d58eb84c74358a4b55dd09ff635b2` passes the confirmed manual checks.
- Equivalent redacted automated regression passes.

## Phase 2: Lineage and Fork Backend

Implementation status: completed and independently reviewed on 2026-07-24. The backend now exposes an owned Lineage Tree, validates exact stateful Fork Points, creates a child branch and its first durable Turn Attempt in one transaction, composes nested inherited transcript/assessment paths without copying rows, and produces branch-independent Evaluation reports. PostgreSQL concurrency tests cover exact `turnId` replay, stale/conflicting submissions, rollback without an empty child, inherited-tail fallback, Lineage-first fork/commit lock ordering, and cross-user or partial-ownership denial. Review remediation added deterministic legacy score linkage/backfill, structured question-pool preservation, production QNA evaluation aliases, composed lineage-summary score sorting, immutable owner filtering, and correct child numbering. The root verification gate passed with 83 Java tests and 88 Python tests, with no unresolved Critical or Important findings.

### Tasks

1. Implement Lineage, parent branch, Fork Point, trigger message, Owning Branch, and Branch Delta rules.
2. Implement Forkable Message validation.
3. Implement atomic fork-and-first-turn submission; never create an empty branch.
4. Compose inherited transcript paths without message copying.
5. Implement Inherited Assessment selection by turn/message boundary.
6. Generate independent child evaluations from inherited plus owned assessments.
7. Implement Branch Version conflict responses.
8. Ensure a fork from inherited content attaches to the actual Owning Branch.
9. Add tree and branch-summary APIs.

### Phase 2 Gate

- Candidate-answer and AI-question forks produce correct context.
- Source branch content, status, and evaluation remain unchanged.
- Multi-level tree ancestry is correct.
- No inherited message or score rows are duplicated.
- Concurrent/stale submission cannot overwrite or silently fork.
- Only one Turn Attempt processes per Lineage.

## Phase 3: Full Flutter Interview Replay

Implementation status: completed and verification-ready on 2026-07-24. Flutter now uses the authenticated durable start/turn/fork contracts for every first-party interview turn, renders responsive lineage replay with deep-tree ancestry, keeps transport/recovery state outside canonical messages, and preserves drafts across conflicts. Full Flutter tests and analyzer are green; final independent review is the remaining Task 4 handoff gate.

### Tasks

1. Replace the old history detail page with Interview Replay.
2. Implement desktop/web split layout: Branch Tree plus Branch Transcript.
3. Implement mobile transcript-first layout with branch drawer or sheet.
4. Highlight inherited prefix, Fork Point, and Branch Delta.
5. Add message-level fork actions only for Forkable Messages.
6. Pre-fill candidate answers; leave AI-prompt drafts empty.
7. Hide the tail composer for completed branches.
8. Add processing reattachment, conflict preservation, and Turn Recovery actions.
9. Display branch status, progress, score, labels, and activity in the tree.
10. Preserve current centralized token-expiry behavior.

Additional completed behavior:

11. Create the root Lineage, Branch, and opening Turn Attempt through the idempotent durable start endpoint before navigating to Chat.
12. Reload the root transcript when opening generation completes, including when no transcript had existed yet.
13. Reattach processing attempts from replay, start, and Chat without cancelling work on navigation or SSE disconnect.
14. Keep opening failures recoverable even before a canonical transcript exists; retry uses the persisted expected version and tail.
15. Filter Lineage history by `all`, `active`, `completed`, or `ended` on the backend before pagination while retaining keyword, sort, and page state.

### Phase 3 Gate

- Web and mobile-width layouts remain usable with deep/large branch trees.
- Completed branches are read-only by default but forkable at eligible messages.
- Active branches continue at the tail without creating a child.
- Fork creation occurs only after explicit answer submission.
- No error or partial-stream bubble appears in canonical replay.

## Phase 4: Migration, End-to-End Verification, and Enablement

### Migration tests

1. Start from the old schema and representative legacy rows.
2. Run Flyway through the latest version.
3. Assert unchanged session/message/score/evaluation content and counts.
4. Assert one root Lineage per old Session.
5. Assert no orphan lineage, parent, fork, turn, message, or assessment references.
6. Assert ambiguous legacy messages remain visible and non-forkable.
7. Re-run migrations to prove safe version behavior.

### Cross-service tests

1. Create, interrupt, reopen, and continue an interview.
2. Restart Python and Java between turns and rebuild from PostgreSQL state.
3. Exit Flutter during generation and reattach.
4. Retry a failed turn without duplicate messages or scores.
5. Submit stale answers from two clients and verify conflict behavior.
6. Create sibling and nested forks from owned and inherited messages.
7. Complete branches and compare independent evaluations.
8. Verify history best-score sorting and latest-active focus.
9. Verify cross-user access is denied for every new endpoint.

### Enablement

1. Enable Phase 1 history/resume after its gate passes.
2. Keep replay/fork UI disabled until Phases 2 and 3 pass.
3. Enable full Interview Replay only after migration and E2E evidence is retained.
4. Update `tests/docs/test-cases.md` and `tests/docs/tooling-guide.md` with actual commands, fixtures, and environment requirements.

## Verification Commands to Establish During Implementation

Use jenv's effective project version; do not change the global JDK. The final commands should be registered in the tooling guide. Expected command families include:

```bash
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv version
cd ai_interview_backend && mvn -pl ai-interviewer-interview test
cd ai_interviewer && uv run pytest
cd ai_interviewer_front && flutter test
bash tests/scripts/run-api.sh
bash tests/scripts/run-smoke.sh
```

If host Flutter is unavailable, use the project's established Flutter container workflow and run dependency installation and tests in the same fresh container.

## Completion Definition

The work is complete only when:

1. Fake history data and fake `继续` resume are gone.
2. Existing and new interviews load from PostgreSQL-backed contracts.
3. Active tail resume, completed replay, fork semantics, scoring, recovery, concurrency, and permissions match the accepted design.
4. Python can rebuild state from a Java/PostgreSQL branch snapshot.
5. Flyway upgrades populated data without loss or duplication.
6. The live legacy anchor and redacted automated fixture both pass.
7. Relevant Java, Python, Flutter, API, smoke, migration, and E2E tests pass.
8. User-visible controls are enabled only for capabilities that passed their release gate.
