# Task 4 Report: Flutter Durable Start, Interview Replay, and Turn Recovery

Status: implementation, independent review, and verification complete

Date: 2026-07-24

Workspace: `/Users/junjielong/.codex/worktrees/db0e/my_ai_interviewer`

Effective Java runtime: jenv JDK 21; no global jenv setting was changed.

## Outcome

Flutter now treats persisted Java/PostgreSQL interview state as authoritative from the opening turn onward. Upload or skip creates an idempotent durable opening attempt before Chat navigation. Chat and Replay attach to processing attempts without owning or cancelling server work, reload canonical history on completion, and keep failures/recovery outside Business Messages. Every later answer and historical fork uses the authenticated durable Turn Attempt APIs; the legacy chat endpoint remains compatibility-only.

The history detail page is now a responsive Interview Replay experience: desktop uses a Branch Tree/transcript split, narrow layouts open the tree in a sheet, deep parent chains receive cycle-guarded depth indentation, nodes expose status/progress/activity/assessment/evaluation/recovery metadata, and transcripts distinguish inherited prefix, server-supplied Fork Point, and current-branch delta. Structured media remains visible.

History status filtering is server-side before pagination and composes with keyword search, time/score sorting, and page navigation.

## Client behavior delivered

- Stable client `turnId` reuse for exact HTTP retries and new IDs for recovery retries.
- Authenticated service-Dio calls for durable start, tree, normal turn, fork, status/events, retry, cancel, and discard.
- No optimistic candidate/AI bubbles, partial chunks, transport errors, or processing labels in canonical transcript data.
- Tail composer only for an active branch whose canonical AI tail expects a response and has no processing attempt.
- Candidate-answer fork drafts are prefilled; AI-question drafts are empty; no branch exists before explicit submit.
- Fork success selects the returned child and attaches its first attempt.
- Processing reattachment is single-flight while connected and permits bounded reconnect after a stream disconnect.
- Opening completion reloads the root transcript even though no transcript existed when start returned.
- Opening failure can retry from persisted expected version/tail or be discarded without presenting a no-op ordinary composer.
- Navigation away from processing does not call cancel; explicit Cancel remains available.
- Centralized API-client 401/session-expiry behavior is preserved.
- A fork draft remains bound to its originating Branch: selecting another Branch preserves the draft but does not expose, submit, or overwrite it until the user switches back or explicitly discards it.

## Backend additions required by durable opening and filtering

- `POST /interviews/start-attempts` creates root Lineage, root Branch, and opening Turn Attempt in one transaction.
- Exact/concurrent `turnId` replay returns the same root; changed payload reuse conflicts.
- Resume ownership and active Job resolution are enforced before using persisted inputs.
- Opening uses stage `opening`, Branch Version 1, null canonical tail, and persists `我准备好了` as `system_trigger` on commit.
- Failure during root/attempt creation rolls back the entire root transaction.
- Lineage history accepts normalized `all`, `active`, `completed`, and `ended` status before LIMIT/OFFSET and count.

## Primary Flutter files

- `ai_interviewer_front/lib/models/interview_history.dart`
- `ai_interviewer_front/lib/api/interview_api.dart`
- `ai_interviewer_front/lib/services/interview_service.dart`
- `ai_interviewer_front/lib/interview_history_page.dart`
- `ai_interviewer_front/lib/history_detail_page.dart`
- `ai_interviewer_front/lib/upload_resume_page.dart`
- `ai_interviewer_front/lib/interview_chat_page.dart`

## New/updated Flutter tests

- `test/interview_replay_contract_test.dart`
- `test/interview_replay_service_test.dart`
- `test/interview_replay_ui_test.dart`
- `test/interview_durable_start_widget_test.dart`
- `test/interview_history_page_test.dart`
- `test/interview_resume_hydration_test.dart`
- `test/history_detail_replay_test.dart`
- `test/interview_exit_preserves_progress_test.dart`

## TDD evidence

Meaningful RED observations included missing durable models/endpoints; replay widgets absent; fixed one-level tree indentation; missing activity/evaluation/media; no automatic processing reattachment; opening completion returning without loading a transcript; status filter controls absent; upload navigating before durable start; opening failure exposing an unusable ordinary input; and legacy exit tests attempting to model processing through the removed first-party chat path.

Each was followed by the smallest contract/state/UI change and a focused rerun. The opening-completion regression initially failed with `Expected treeCalls 1, Actual 0`; the final refresh path now falls back through current transcript, current session, focused Branch, and root Branch.

## Verification

- Flutter full suite at the Task 4 handoff: 34 tests, 0 failures.
- Flutter analyzer: no issues found.
- Java Interview + Evaluation affected modules: 92 tests across 21 suites, 0 failures, 0 errors, 0 skipped, using ephemeral PostgreSQL 16 Testcontainers.
- Python maintained suite: 89 passed.
- No real model provider, deployment, shared Compose restart, or authoritative database mutation was performed.

## Independent review

The Task 4 review found and fixed one Important interaction boundary: a fork draft could remain visible and submit against its original Branch after the user selected another Branch. The final UI now renders an explicit off-Branch draft notice and blocks wrong-Branch overwrite/submission. The added service and widget regressions brought the Task 4 handoff suite from 32 to 34 tests. No Critical or Important finding remained after the focused 19-test replay rerun and the full Flutter/analyzer gate.

## Remaining boundary

Fresh-schema synchronization, isolated backup migration, cross-service/security enablement, and final whole-change review remain Task 5.

No files were staged or committed, and nothing was pushed or deployed.
