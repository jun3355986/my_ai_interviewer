# Task 3 Report: Lineage Tree, Atomic Historical Forks, and Inherited Assessments

Status: **DONE — independent review findings resolved**

Date: 2026-07-24

Workspace: `/Users/junjielong/.codex/worktrees/db0e/my_ai_interviewer`

## Outcome

The Interview and Evaluation backends now implement the complete Task 3 historical-fork domain:

1. An authenticated user can read every branch in an owned Lineage through a stable flat tree contract containing ancestry, fork anchors, status, activity, progress, assessment counts, completed Evaluation summary, and recoverable Turn Attempt state.
2. A completed candidate answer or response-expecting AI question is forkable only when its resolved Fork Point contains an exact versioned post-turn state snapshot.
3. Submitting a fork creates the child branch and its first durable Turn Attempt in one PostgreSQL transaction. Merely selecting a message creates nothing.
4. A fork from inherited content attaches to the selected trigger message's actual Owning Branch. The Fork Point can still be owned by an earlier ancestor.
5. Exact `turnId` replay returns the same child and Turn Attempt; changed reuse conflicts; concurrent exact requests cannot create sibling duplicates.
6. Nested transcripts, snapshots, and assessments are composed through every ancestry boundary without copying inherited message or score rows.
7. Evaluation generation and score reads use the same composed assessment path. Each child keeps its own report key while exposing inherited and Owning Branch metadata for individual scores.
8. Compatibility `/interviews/chat` scores now persist their actual question/answer message links; deterministic historical rows are backfilled, and ambiguous legacy rows use an explicit path-safety fallback instead of silently disappearing.
9. Structured project and technical pools remain JSON values across fork creation and child persistence rather than being converted to Java map strings.
10. Fork creation and Turn Commit acquire the owned Lineage before any Branch row, eliminating the reproduced ancestor/nested PostgreSQL deadlock cycle.
11. Tree and summary reads require both current session ownership and the immutable owner stored on Evaluation/Turn Attempt rows, including after partial or full ownership reassignment.
12. Evaluation treats production `technical_qna`/`project_qna` and legacy `technical`/`project` as the same normalized categories.
13. Lineage summary fallback scores and descending sort order use the same recursively composed ancestry path as tree and Evaluation reads.

No Python production code was changed for Task 3, and no real model/provider was called.

## Files changed or created for Task 3

### Interview API, DTOs, and exact state

- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/controller/InterviewController.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/controller/TurnAttemptController.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/BranchMessageDTO.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/CreateForkAttemptRequest.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/ForkAttemptDTO.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/ComposedAssessmentDTO.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/LineageTreeDTO.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/LineageTreeNodeDTO.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/model/ForkStateSnapshot.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/entity/InterviewSession.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/entity/ScoreRecord.java`

### Interview lineage, fork, and assessment services

- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/ForkAttemptService.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/LineageTreeService.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/ComposedAssessmentService.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/InterviewHistoryService.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/BranchSnapshotComposer.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/TurnCommitService.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/SSEProxyService.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/repository/ForkBranchRepository.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/repository/LineageTreeRepository.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/repository/TurnAttemptRepository.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/entity/InterviewTurnAttempt.java`
- `ai_interview_backend/ai-interviewer-interview/src/main/resources/mapper/InterviewLineageMapper.xml`

### Evaluation integration

- `ai_interview_backend/ai-interviewer-evaluation/src/main/java/com/aiinterviewer/evaluation/EvaluationApplication.java`
- `ai_interview_backend/ai-interviewer-evaluation/src/main/java/com/aiinterviewer/evaluation/dto/ScoreDTO.java`
- `ai_interview_backend/ai-interviewer-evaluation/src/main/java/com/aiinterviewer/evaluation/service/EvaluationService.java`

### Migrations

- `ai_interview_backend/ai-interviewer-interview/src/main/resources/db/migration/V5__persist_historical_fork_context.sql`
- `ai_interview_backend/ai-interviewer-interview/src/main/resources/db/migration/V6__backfill_deterministic_score_message_links.sql`

V5 adds nullable fork-request context to `t_interview_turn_attempt`:

- `fork_source_session_id`
- `fork_trigger_message_id`
- `fork_point_message_id`
- `fork_expected_source_version`
- `fork_expected_source_tail_message_id`

The foreign keys retain an auditable identity/context link for exact idempotency replay. Upgrade tests cover populated V4 to V5 and fresh V0 to V5 migration.

V6 links a historical score only when one completed AI-question/human-answer pair in the same session is adjacent by sequence and exactly matches the stored question and answer text. Multiple candidates remain unlinked so migration cannot silently pick the wrong transcript turn. Runtime composition keeps focused-branch unlinked rows visible and accepts ancestor unlinked rows only when all matching pairs are on the composed visible path.

### Tests

- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/ForkAttemptServiceTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/LineageTreeServiceTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/ComposedAssessmentServiceTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/LineageForkIntegrationTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/LineageCompositionIntegrationTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/InterviewHistoryServiceTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/TurnAttemptLifecycleIntegrationTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/controller/InterviewHistoryControllerTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/controller/TurnAttemptControllerTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/migration/InterviewFlywayMigrationTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/mapper/InterviewLineageMapperIntegrationTest.java`
- `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/SSEProxyServiceStubReplayTest.java`
- `ai_interview_backend/ai-interviewer-evaluation/src/test/java/com/aiinterviewer/evaluation/service/EvaluationServiceLineageTest.java`

### Documentation

- `docs/INTERVIEW_HISTORY_RESUME_REPLAY_IMPLEMENTATION_PLAN.md`
- `tests/docs/test-cases.md`
- `tests/docs/tooling-guide.md`
- `.superpowers/sdd/task-3-lineage-fork-report.md`

## Public contracts

### Lineage Tree

`GET /interviews/lineages/{lineageId}/tree`

Required header: `X-User-Id`.

The response contains `lineageId`, `rootBranchId`, the server-selected `focusedBranchId`, and stable flat `nodes`. Each node contains:

- branch, parent, label, Fork Point, and trigger identity;
- stage, status, Branch Version, latest business activity, and progress;
- owned, inherited, and total assessment counts;
- completed score/Evaluation summary where available;
- latest recoverable Turn Attempt ID, status, and sanitized error code.

Focus selection is the latest active branch by Business Activity, otherwise the latest completed branch, with a stable branch-ID tie break.

### Historical fork plus first attempt

`POST /interviews/branches/{focusedBranchId}/fork-attempts`

Required header: `X-User-Id`; optional correlation header: `X-User-Name`.

Request fields:

- `turnId`
- `triggerMessageId`
- `candidateAnswer`
- `expectedFocusedBranchVersion`
- `expectedFocusedTailMessageId`

The response contains the deterministic child `branchId` and the normal durable `TurnAttemptDTO`.

Stable HTTP 409 reasons include:

- `BRANCH_VERSION_CONFLICT`
- `BRANCH_TAIL_CONFLICT`
- `FORK_TRIGGER_NOT_FORKABLE`
- `FORK_TRIGGER_NOT_ON_FOCUSED_PATH`
- `FORK_STATE_UNAVAILABLE`
- `LINEAGE_PROCESSING_CONFLICT:<turnId>`
- `IDEMPOTENCY_CONFLICT`
- `IDEMPOTENCY_PAYLOAD_MISMATCH`
- `FORK_CREATION_FAILED`

Authorization failures use the existing `ACCESS_DENIED` business result. Raw SQL and stack details do not cross the fork API boundary.

## Fork Point, Owning Branch, and exact-state decisions

### Candidate-answer trigger

- The selected message must be a completed, non-legacy `candidate_answer` on the focused canonical path.
- Its immediate previous canonical message must be a completed, response-expecting, stateful `ai_question`.
- That AI question becomes the Fork Point.
- The old answer and its score are outside the child path; only the explicitly submitted edited answer enters the first child Turn Attempt.
- The child parent is the selected answer's Owning Branch, even when the Fork Point belongs to an earlier ancestor.

### AI-question trigger

- The selected message itself must be a completed, non-legacy `ai_question` with `expects_response=true` and exact state.
- It is both the trigger and Fork Point.
- The submitted candidate answer begins the child delta.

### Exact post-turn state

Every newly committed AI response merges a reserved `_postTurnStateV1` metadata object while preserving existing rich question/media metadata. The snapshot contains:

- schema version;
- current stage and active branch status;
- project-question count and target;
- follow-up count;
- remaining project and technical question pools.

Only a usable schema-v1 active state makes the prompt forkable. Legacy, ambiguous, missing-state, non-response, feedback, system, summary, and incomplete messages remain readable but non-forkable.

## Transaction, idempotency, and concurrency

- An unlocked focused-branch hint identifies the Lineage without making an authorization or mutation decision.
- The owned Lineage is locked first; only then are the focused Branch and actual trigger Owning/parent Branch locked and revalidated.
- Turn Commit uses the same Lineage-before-Branch order, so a fork cannot form an opposite lock cycle with an in-flight commit.
- The focused Branch Version and canonical tail are checked after the locks and before mutation.
- The one-unresolved-processing-attempt-per-Lineage guard is reused for historical forks.
- The child ID is deterministically derived from `turnId`.
- Child insertion, first Turn Attempt insertion, and fork-context attachment share one Spring transaction.
- The child starts active at Branch Version `1`; before it owns a completed message, its canonical tail is the ancestor-owned Fork Point.
- The first child attempt expects version `1` and that Fork Point tail.
- Worker scheduling occurs only after transaction commit through the existing Turn Attempt boundary.
- A transaction/validation/storage failure before durable attempt creation rolls back the child.
- An exact duplicate returns the same child/attempt. A changed trigger, focused context, version, tail, or candidate answer conflicts.
- PostgreSQL concurrency coverage proves concurrent exact duplicates create one child, one attempt, and one scheduled worker.
- PostgreSQL coverage also reproduces the former ancestor/nested deadlock interleaving and verifies stable serialization after the lock-order fix.
- A fork racing an in-flight Turn Commit completes through the same lock order without a database deadlock or HTTP 500 equivalent.
- After the intentional child+attempt transaction commits, a later failed/interrupted/cancelled first attempt leaves that child available for normal Turn Recovery.

## Canonical ancestry and inherited assessments

Nested transcript composition is recursive: compose the complete parent canonical path, cut that whole path at the child's Fork Point, then append only the child's completed delta. This supports the important case where the child parent owns the selected answer but the Fork Point is owned by an earlier ancestor.

`ComposedAssessmentService` uses that canonical message path as the single assessment boundary:

- a score is visible only when both linked question and answer message IDs are on the path;
- a focused Branch keeps its owned unlinked compatibility scores visible because the entire owned delta is in scope;
- an ancestor unlinked score is accepted only when a matching adjacent completed question/answer pair exists on the visible prefix and no same-content pair exists outside it;
- ambiguous or partially linked legacy rows are excluded with sanitized `LEGACY_SCORE_LINK_*` warnings rather than guessed or silently discarded;
- source scores after any Fork Point are excluded;
- the selected historical answer's score is excluded from an answer-trigger fork;
- inherited records are returned by reference/query, not inserted into the child branch;
- records are ordered by canonical answer path, then stable score ID;
- public `questionIndex`/`displayOrder` is recomputed from the composed path;
- `owningBranchId` and `inherited` remain explicit.

`BranchSnapshotComposer`, tree counts/scores, Turn Commit question indexing, and Evaluation use this shared service/boundary.

The lineage summary mapper builds every owned Branch's composed message-ID path in one recursive SQL CTE and batch-computes fallback scores from that path. It therefore avoids a Java per-Lineage query loop while keeping list values and sorting consistent with tree and Evaluation behavior.

## Evaluation behavior

- `generateReport`, `getReport`, and `getScores` first verify ownership of the requested Interview Branch.
- Existing reports also verify their stored `userId` before being returned.
- A child report is stored under the requested child `sessionId`; sibling and nested report keys remain independent.
- Report generation calculates from inherited-prefix plus branch-owned composed scores only.
- Public score DTOs keep `sessionId` equal to the requested/focused branch while `owningBranchId` identifies the score row's actual source.
- Public `questionIndex` follows composed display order and does not restart at the child-local row index.
- Root behavior remains the same because its composed path contains only root-owned assessments.
- Question categories are trimmed and lower-cased with `Locale.ROOT`; canonical and `_qna` persisted forms map to the same technical/project calculations.
- Tree/list Evaluation and recoverable-attempt projections require current session ownership plus the immutable row owner, preventing previous-owner metadata from leaking after reassignment.

## TDD evidence

Meaningful RED observations before implementation/fixes included:

- missing Lineage Tree, fork request/response, exact state, and composed-assessment contracts at compilation;
- nested composition failing because it searched the immediate parent's owned delta for an ancestor-owned Fork Point;
- a first child score restarting at question index `1` instead of continuing composed order;
- concurrent exact fork requests returning a Lineage processing conflict instead of replaying the winner;
- raw database exception details escaping the fork boundary;
- inherited Evaluation score DTOs replacing the requested child `sessionId` with the score's Owning Branch;
- `generateReport` returning an existing report without checking its stored `userId`.
- a structured project-pool object persisting as the invalid Java string `{id=p3, context={difficulty=senior}}`;
- the first derived Branch being labelled `分支 2`;
- compatibility-chat score rows persisting null question/answer message IDs and disappearing from composed assessment;
- a unique legacy score remaining unlinked before V6, while an ambiguous duplicate correctly remained null after the fix;
- production `technical_qna` producing a technical score of `100` instead of the expected category calculation `90`;
- a reassigned Lineage exposing the previous owner's Evaluation summary and count;
- summary fallback sorting a child by its owned `100` instead of its composed inherited-80/owned-100 average of `90`;
- a real PostgreSQL `deadlock detected` / `PessimisticLockingFailureException` when ancestor and nested forks acquired Branch and Lineage rows in opposite order.

Each failure was followed by the smallest implementation change and a focused GREEN rerun.

## Verification

Effective JDK:

```text
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv version
21 (set by /Users/junjielong/.jenv/version)

JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv global
21
```

No global JDK switch was performed.

### Task 3 focused Java regression

```bash
cd ai_interview_backend
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv exec mvn \
  -pl ai-interviewer-interview,ai-interviewer-evaluation -am \
  -Dtest=InterviewHistoryServiceTest,InterviewHistoryControllerTest,InterviewLineageMapperIntegrationTest,LineageTreeServiceTest,ForkAttemptServiceTest,ComposedAssessmentServiceTest,LineageForkIntegrationTest,LineageCompositionIntegrationTest,InterviewFlywayMigrationTest,SSEProxyServiceStubReplayTest,BranchSnapshotComposerTest,TurnAttemptWorkerSnapshotTest,TurnAttemptControllerTest,TurnAttemptWorkerSchedulingTest,TurnAttemptLifecycleIntegrationTest,EvaluationServiceLineageTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test -q
```

Result: **75 tests, 0 failures, 0 errors, 0 skipped**.

This includes real Spring transactions and ephemeral PostgreSQL 16 Testcontainers for migration, deterministic score-link backfill, atomicity, rollback, idempotency, lock ordering, ownership reassignment, composed summary sorting, tail fallback, and nested composition.

### Full affected Java modules

```bash
cd ai_interview_backend
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv exec mvn \
  -pl ai-interviewer-interview,ai-interviewer-evaluation -am \
  test -q
```

Result: **82 tests, 0 failures, 0 errors, 0 skipped**.

After the remediation review, the root agent added one final partial-ownership
regression requiring the Lineage Tree root session to be owned by the current
Lineage owner. The regression failed before the repository join was tightened and
passed after the fix. The independently rerun affected-module suite then reported:

**83 tests, 0 failures, 0 errors, 0 skipped**.

### Affected Java packaging and artifact inspection

```bash
cd ai_interview_backend
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv exec mvn \
  -pl ai-interviewer-interview,ai-interviewer-evaluation -am \
  package -DskipTests
```

Result: **BUILD SUCCESS** for the parent, Common, API, Interview, and Evaluation reactor projects.

Artifact inspection verified that the Interview JAR contains `InterviewLineageMapper.xml`, `V6__backfill_deterministic_score_message_links.sql`, `ForkAttemptService`, `TurnCommitService`, `SSEProxyService`, and `ComposedAssessmentService`. The executable Evaluation JAR contains `EvaluationService` and the nested `ai-interviewer-interview-1.0.0-SNAPSHOT.jar` runtime dependency.

### Task 1/2 Python compatibility

```bash
cd ai_interviewer
uv run pytest tests -q
```

Result: **88 passed in 1.61s**.

The Python suite used temporary SQLite/Chroma paths. No real provider or live HTTP dual-agent simulation was run.

## Independent review closure

The independent Task 3 review recorded **0 Critical, 6 Important, and 1 Minor** findings. All seven are resolved with focused RED-to-GREEN regressions and affected-module verification. The finding-by-finding evidence and original review context are preserved in `.superpowers/sdd/task-3-review-findings.md`.

Resolved scope:

- compatibility score linkage, deterministic V6 backfill, and ambiguity-safe runtime fallback;
- structured question-pool preservation;
- Lineage-first fork/commit lock ordering;
- immutable owner filtering in tree and summary reads;
- production QNA evaluation aliases;
- recursively composed lineage-summary fallback and sorting;
- first-child `分支 1` numbering.
- partial Lineage reassignment cannot expose or return an old-owner root branch ID
  through the tree entry contract.

## Deferred to Task 4

Task 3 intentionally does not implement the Flutter replay/fork interaction layer. Task 4 still owns:

- web split-view and mobile transcript-first branch navigation;
- tree rendering, branch focus, inherited-prefix/Fork Point/branch-delta presentation;
- message-level fork controls and candidate-answer prefill;
- explicit submit-only branch creation UX;
- processing reattachment, stale-context preservation, and Turn Recovery UI;
- client-side use of the new tree/fork and composed score metadata.

Task 4 was not started.

## Boundary confirmation

- No commit was created.
- No files were staged.
- No push or pull request was performed.
- No deployment occurred.
- No shared runtime service was restarted.
- No real model/provider call occurred.
- No authoritative PostgreSQL, SQLite, or vector-store data was queried or mutated by Task 3.
- Database mutation tests used only disposable PostgreSQL 16 Testcontainers instances.
- Existing uncommitted workspace changes were preserved.
