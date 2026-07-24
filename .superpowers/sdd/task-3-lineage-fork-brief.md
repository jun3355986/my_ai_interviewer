# Task 3 Brief: Lineage Tree, Atomic Historical Forks, and Inherited Assessments

## Objective

Implement the backend domain for branch trees and message-anchored historical continuation. A user can submit an answer from an eligible historical message and atomically create a child Interview Branch plus its first durable Turn Attempt. The source branch remains immutable. Child transcripts and assessments are composed through ancestry without copying inherited business rows, and each completed child can produce an independent evaluation from inherited plus branch-owned assessments.

## Workspace and boundaries

- Workspace: `/Users/junjielong/.codex/worktrees/db0e/my_ai_interviewer`
- Primary modules: `ai_interview_backend/ai-interviewer-interview` and `ai_interview_backend/ai-interviewer-evaluation`
- Python changes are permitted only where a deterministic snapshot/state contract adjustment is genuinely required; Task 2 reconstruction remains the base.
- Preserve all current uncommitted changes.
- Do not commit, stage, push, deploy, restart shared services, call real providers, or mutate authoritative runtime data.
- Use TDD with PostgreSQL Testcontainers for transactional/tree/ancestry behavior.
- Update `tests/docs/test-cases.md` and `tests/docs/tooling-guide.md`.
- Write `.superpowers/sdd/task-3-lineage-fork-report.md` on completion.

## Existing foundation

- Lineages, parent/fork fields, message semantics, score linkage, composed transcripts, durable Turn Attempts, Java-authoritative Python snapshots, and atomic turn commits already exist.
- Legacy migrated messages carry `legacyForkEligible=false` and must remain visible but non-forkable.
- `InterviewHistoryService` composes ancestor prefixes through each child `fork_point_message_id` plus the focused branch delta.
- Task 2 snapshots already compose canonical messages and assessments, and Python returns authoritative post-turn branch state.
- The Evaluation Service currently reads only direct `t_score_record.session_id` rows and therefore must be adapted for inherited assessment composition.

## Public contracts

Implement authenticated/user-owned contracts equivalent to:

1. `GET /interviews/lineages/{lineageId}/tree`
   - Returns every branch in the lineage as a stable tree/flat-node contract suitable for desktop and mobile clients.
   - Each node includes branch ID, parent ID, label, fork point/trigger, stage, status, Branch Version, latest business activity, progress, owned/inherited/total assessment counts, completed score/evaluation summary where available, and active/recoverable Turn Attempt state.
   - Default focused branch follows the accepted rule: latest active by Business Activity, otherwise latest completed.
2. Existing branch transcript/detail remains the linear focused-path contract and exposes fork eligibility, inherited markers, owning branch, and fork point consistently.
3. `POST /interviews/branches/{focusedBranchId}/fork-attempts`
   - Request includes `turnId`, `triggerMessageId`, candidate answer, and the expected version/tail of the focused view needed to detect a stale UI context.
   - Returns the newly created child branch ID and durable Turn Attempt.
   - Merely selecting a message never calls this endpoint and never creates a branch.

All endpoints require `X-User-Id`; optional `X-User-Name` is persisted through the same durable attempt correlation path. Cross-user lineage, branch, message, fork, tree, score, and evaluation access is denied.

## Fork semantics

### Candidate-answer trigger

- The trigger must be a completed, non-legacy `candidate_answer` visible in the focused composed transcript.
- The Fork Point is the complete AI prompt immediately preceding that answer on the canonical path.
- The request candidate answer is explicit and editable; do not reuse the historical answer implicitly.
- The historical answer and its assessment are not inherited into the child.

### AI-message trigger

- The trigger must be a completed, non-legacy `ai_question` with `expects_response=true` visible in the focused composed transcript.
- The trigger itself is the Fork Point.
- The submitted candidate answer starts the new child delta.

### Owning branch and ancestry

- A fork from inherited content attaches to the trigger message's actual Owning Branch, not necessarily the currently focused branch.
- The child `parent_session_id` is that owning branch.
- `fork_point_message_id` records the inherited context boundary; `fork_trigger_message_id` records the UI-selected message.
- Source/ancestor branch rows, messages, scores, statuses, activity, and evaluations are never modified by child creation or child progress.

## Exact branch-state-at-fork requirement

Historical fork processing must start from the interview state that existed at the Fork Point, not the source branch's current tail state.

- Persist a deterministic post-turn state snapshot with each newly committed AI prompt (for example in message metadata under a versioned reserved key) containing stage/status, counters, follow-up count, and both remaining question pools.
- Forkable new messages must have a usable state snapshot for their resolved Fork Point.
- Legacy/ambiguous messages without exact state remain visible and non-forkable.
- Build the new child session fields from the Fork Point state snapshot.
- Ensure compatibility-created current interviews either persist equivalent exact state for eligible prompts or are conservatively marked non-forkable until they do.
- Keep structured question/media metadata separate and intact when adding the reserved state metadata.

## Atomic fork-and-first-turn

- Child session creation and durable Turn Attempt insertion occur in one database transaction.
- No empty child branch may remain if attempt validation/insertion fails.
- The child begins active with Branch Version `1`; its canonical inherited tail is the Fork Point even though that message is owned by an ancestor.
- Update canonical tail logic so a branch with no owned messages uses `fork_point_message_id`, and after the first commit uses its own latest completed message.
- The first child turn uses expected child version `1` and expected tail equal to the Fork Point.
- Schedule model work only after the transaction commits.
- Reusing an exact `turnId` returns the same child/attempt and does not create a sibling duplicate. Reusing it with different fork trigger/context/answer conflicts.
- A failed/interrupted/cancelled first attempt leaves the explicitly created child available for Turn Recovery because the branch and attempt were intentionally created atomically. A transaction failure before durable attempt creation leaves neither.
- The one-unresolved-processing-attempt-per-lineage guard applies to fork creation as well as tail continuation.

## Fork validation and concurrency

- Validate lineage/branch/message ownership and that the trigger appears on the focused composed path.
- Validate message type, delivery status, response expectation, legacy eligibility, and exact state snapshot availability.
- Reject stale focused Branch Version/tail without creating a branch or model request.
- Recheck the actual Owning Branch/lineage and Lineage processing slot under transaction/race guards.
- Never silently fork on ordinary stale tail continuation; this endpoint is explicit only.
- Error responses preserve candidate text client-side and expose stable reason codes without internal SQL/stack details.

## Inherited assessments

- Assessments whose question and answer messages are strictly within the inherited canonical prefix are inherited by reference/query, not copied into child rows.
- The selected historical candidate answer's assessment is excluded because the child path stops at the preceding AI prompt.
- The first new child answer and all downstream answers create branch-owned `t_score_record` rows.
- Provide a shared composed-assessment query ordered by canonical path. It should return inherited and owned records with owning branch/inherited metadata or an equivalent DTO.
- Child-owned `question_index`/display order must follow composed path order rather than restarting ambiguously at 1.
- Snapshot composition, replay detail, tree counts/scores, and Evaluation Service must use the same path/boundary semantics.

## Independent child evaluations

- Update Evaluation Service generation and score reads to use composed assessments for a branch while retaining the child session ID as the independent report key.
- Existing root reports remain unchanged.
- A child report combines inherited prefix assessments with child-owned assessments and never incorporates source-branch assessments after the Fork Point.
- Ownership must be validated against the interview branch before generating or reading a report/scores.
- Sibling and nested branches produce independent reports and best-score lineage sorting continues to use completed branch results only.

## Required tests

At minimum prove:

1. Tree API returns root, sibling, and nested nodes with correct parent/fork/activity/status/progress and default focus.
2. Cross-user tree, transcript, trigger lookup, fork submission, composed score, and evaluation access is denied.
3. AI-question fork creates the child+attempt atomically only on answer submission and inherits through the selected prompt.
4. Candidate-answer fork resolves the preceding AI prompt, excludes the selected old answer/assessment, and uses the edited submitted answer.
5. Forking an inherited message attaches the child to the message's actual Owning Branch.
6. Non-completed, feedback, summary, system, non-response AI, missing-state, and legacy messages are rejected as non-forkable.
7. Transaction failure, invalid trigger, stale focused version/tail, and lineage processing conflict leave no empty child.
8. Exact duplicate `turnId` returns one child/attempt; mismatched duplicate conflicts; concurrent duplicates cannot create sibling duplicates.
9. Child with no owned messages reports Fork Point as canonical tail; first successful turn produces only two child-owned messages and no copied inherited rows.
10. Source branch messages, scores, status, Branch Version, activity, and evaluation remain unchanged after child failure/success/completion.
11. Multi-level transcript and snapshot ancestry stop at every correct fork boundary.
12. Composed assessments include only prefix-before-boundary plus child-owned scores, in path order, without duplicated rows.
13. Child evaluation uses composed assessments; siblings/nested branches remain independent.
14. New AI messages persist exact fork-state snapshots without losing rich question/media metadata.
15. Existing Task 1/2 Java and Python tests remain green; no real model call occurs.

## Completion report

The report must list:

- Files and migrations changed.
- Tree and fork API contracts/reason codes.
- Fork Point/Owning Branch/state-boundary decisions.
- Transaction/idempotency/concurrency implementation.
- Composed assessment and Evaluation Service behavior.
- Exact focused/full commands/results across affected Java modules and Python if changed.
- Deferred Flutter interaction work for Task 4.
- Confirmation of no commit/push/deploy/shared-runtime or authoritative-data mutation.
