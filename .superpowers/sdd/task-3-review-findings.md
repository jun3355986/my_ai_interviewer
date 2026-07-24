# Task 3 Independent Review Findings

## Review outcome

- Critical: 0
- Important: 6
- Minor: 1
- Final status after remediation: all findings resolved

## Important findings

### I-1 Legacy and compatibility-chat scores disappear from composed assessment

Status: resolved

`ComposedAssessmentService` only includes score rows whose `question_message_id` and
`answer_message_id` are both present on the composed branch path. The legacy
`/interviews/chat` score writer does not populate either link, and the existing
migrations only add nullable columns without backfilling historical records.

Evidence:

- `ComposedAssessmentService.java:39-53`
- `SSEProxyService.java:451-486`
- `V1__interview_lineage_and_turn_attempt_foundation.sql:63-66`
- `EvaluationService.java:52-58`
- `LineageForkIntegrationTest.java:126-130` manually patches the links and therefore
  does not exercise the real compatibility writer.

Impact:

- Historical root reports can become empty after upgrade.
- New scores written through compatibility `/interviews/chat` are omitted from tree,
  snapshots, and evaluation.
- Existing-root-report compatibility is violated.

Required regression:

- Verify the compatibility chat writer persists the actual question and answer
  message IDs.
- Verify a V6 migration deterministically links historical scores when there is one
  unambiguous question/answer pair.
- Verify ambiguous or unlinked historical scores remain visible through an explicit
  compatibility fallback instead of being silently discarded.

### I-2 Structured project and technical pools are corrupted at fork creation

Status: resolved

`ForkStateSnapshot` accepts `List<Object>`, while `InterviewSession` stores pools as
`List<String>`. `ForkAttemptService.childSession` converts each pool member through
`String.valueOf`, turning JSON objects into Java map strings and losing exact state.

Evidence:

- `ForkStateSnapshot.java:9-17`
- `InterviewSession.java:85-95`
- `ForkAttemptService.java:208-213`

Impact:

- A structured pool item such as `{\"id\":\"q1\",\"text\":\"...\"}` becomes
  `{id=q1, text=...}`.
- Child snapshots and later Python turn processing no longer receive the exact
  post-turn state.

Required regression:

- Fork a branch whose project and technical pools contain nested objects and verify
  equality after child persistence, snapshot composition, and Python request mapping.

### I-3 Inconsistent fork lock order can deadlock concurrent ancestor/nested forks

Status: resolved

`ForkAttemptService.create` locks the focused branch first, then the lineage, then the
actual owning/parent branch. Two requests focused on different branches in the same
lineage can acquire branch and lineage locks in opposite order.

Evidence:

- `ForkAttemptService.java:51`
- `ForkAttemptService.java:82`
- `ForkAttemptService.java:94-95`
- Existing concurrency coverage at `LineageForkIntegrationTest.java:268-296` only
  exercises duplicate requests on the same focused branch.

Reproducible interleaving:

1. A locks nested branch C, then lineage L, then waits for ancestor R.
2. B locks root R, then waits for lineage L.
3. PostgreSQL detects A waiting for R while B waits for L and aborts one transaction.

Required regression:

- Concurrently fork an ancestor-owned trigger while one request is focused on a nested
  branch and another is focused on the ancestor branch. Verify no deadlock and stable
  lineage-processing semantics.

### I-4 Tree and lineage list bypass immutable evaluation/attempt owner authorization

Status: resolved

Tree evaluation queries only by session ID, recoverable attempt queries only by
session/status, and the lineage list joins evaluation by session. They do not match
the stored immutable `evaluation.user_id` or `turn_attempt.owner_user_id` to the
current user.

Evidence:

- `LineageTreeRepository.java:54-86`
- `LineageTreeService.java:64-68`
- `InterviewLineageMapper.xml:59-70`

Impact:

- After a session/lineage ownership reassignment, the new owner can see evaluation
  summaries/scores and attempt metadata still owned by the previous user.

Required regression:

- Reassign a lineage while leaving old evaluation and attempt rows unchanged. Verify
  tree and list do not expose the old owner's rows, and verify current-user rows still
  render normally.

### I-5 Evaluation category matching does not recognize production QNA stage values

Status: resolved

Turn persistence writes `branch.stage()` values such as `technical_qna` and
`project_qna`. Evaluation only matches exact `technical` and `project` strings.

Evidence:

- `TurnCommitService.java:101-127`
- `SSEProxyService.java:475-486`
- `EvaluationService.java:199-224,242-251`
- `EvaluationServiceLineageTest.java:132` uses the non-production value `technical`.

Impact:

- Technical and experience scores can be zero for real rows.
- Strength classification is inconsistent with persisted production data.

Required regression:

- Evaluate score records carrying `technical_qna`, `project_qna`, and legacy
  `technical`/`project`; all aliases must map to the same canonical categories.

### I-6 Lineage best-score fallback is not composed and can sort incorrectly

Status: resolved

When there is no stored evaluation, the lineage list SQL averages score rows belonging
directly to the child session. Tree and evaluation use the composed ancestor-prefix +
child-delta path.

Evidence:

- `InterviewLineageMapper.xml:51-70`

Impact:

- With an inherited score of 80 and a child-owned score of 100, the composed score is
  90 but the list fallback displays and sorts by 100.
- Lineage best-score sorting can disagree with tree/evaluation semantics.

Required regression:

- Create a completed child with inherited and child-owned score records, no evaluation,
  and verify displayed average and descending best-score order use the composed path.
- Verify the implementation remains owner-filtered and avoids an obvious per-lineage
  N+1 query loop.

## Minor finding

### M-1 First child branch is labelled `branch 2`

Status: resolved

`ForkBranchRepository.nextBranchNumber` counts every session in the lineage, including
the root, and adds one. A root-only lineage therefore creates its first child as
`branch 2`.

Evidence:

- `ForkBranchRepository.java:28-33`

Required regression:

- A root-only lineage must create `branch 1`; subsequent children must remain unique
  and monotonic.

## Remediation evidence

- I-1: compatibility `/interviews/chat` captures the persisted prompt/answer IDs;
  `V6__backfill_deterministic_score_message_links.sql` backfills only a single unique
  adjacent pair. Focused-branch unlinked scores remain visible, while ancestor rows
  require every matching completed adjacent pair to be inside the visible prefix.
  Excluded ambiguous/partial rows emit sanitized `LEGACY_SCORE_LINK_*` warnings.
- I-2: both Session pools are `List<Object>` and fork creation copies the structured
  values without string conversion. Unit and PostgreSQL JSONB read-back assertions
  preserve nested maps.
- I-3: fork creation and Turn Commit now acquire the owned Lineage row before any
  branch row. The ancestor/nested regression first reproduced PostgreSQL
  `deadlock detected`/`PessimisticLockingFailureException`; it is green after the
  common lock order. Fork versus an in-flight commit also completes without a 500.
- I-4: tree branches, evaluations, recoverable attempts, summary roots, summary
  evaluations, page results, and counts are filtered by the current user and their
  immutable stored owners. Full and partial ownership-reassignment tests are green.
- I-5: question types are trimmed, lower-cased with `Locale.ROOT`, and match both the
  canonical category and its `_qna` form. Production, legacy, uppercase, and padded
  inputs share the same results.
- I-6: the summary mapper uses one recursive CTE to construct every branch's composed
  message-ID path and batch-compute completed scores. An inherited 80 plus owned 100
  produces 90 and sorts below an independent 95 without a Java per-branch query loop.
- M-1: branch numbering counts only derived sessions; the first child persists as
  `分支 1`.
- Affected Java modules: 82 tests, 0 failures, 0 errors, 0 skipped.
- Python deterministic compatibility: 88 passed.

Root follow-up verification added a partial-reassignment Tree regression after the
review remediation: changing only `t_interview_lineage.user_id` must not expose the
old owner's `root_session_id`. The test failed against the first remediation, then
passed after `LineageTreeRepository.findOwnedRootSessionId` joined and verified the
root Session owner. The final affected Java suite is 83 tests with 0 failures,
0 errors, and 0 skipped; unresolved Critical and Important findings remain zero.

## Verification context at review time

- Effective JDK: 21, verified through jenv.
- Focused Java suite: 63 tests, 0 failures, 0 errors, 0 skipped.
- Maven packaging contained the Interview mapper/resources and the Evaluation runtime
  dependency wiring.
- The green suite did not cover the six Important failure modes above.
