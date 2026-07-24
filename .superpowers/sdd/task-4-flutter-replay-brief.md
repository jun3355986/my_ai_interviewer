# Task 4: Flutter Interview Replay, Forking, and Turn Recovery

## Goal

Replace the checkpoint replay detail with the accepted full Interview Replay experience. Flutter must render the persisted lineage tree and composed transcript, continue an active branch through durable Turn Attempts, create message-anchored forks only after explicit submission, and recover from processing failures without placing transport state in the canonical transcript.

## Scope

### Models and API

- Add Flutter models for `LineageTreeDTO`, `LineageTreeNodeDTO`, `TurnAttemptDTO`, `TurnAttemptEventDTO`, and `ForkAttemptDTO`.
- Parse `BranchMessageDTO.forkPointMessageId`; do not infer the server's Fork Point in Flutter.
- Add authenticated API calls for:
  - `POST /interviews/start-attempts`
  - `GET /interviews/lineages/{lineageId}/tree`
  - `POST /interviews/branches/{branchId}/turn-attempts`
  - `POST /interviews/branches/{focusedBranchId}/fork-attempts`
  - `GET /interviews/turn-attempts/{turnId}`
  - `GET /interviews/turn-attempts/{turnId}/events`
  - `POST /interviews/turn-attempts/{turnId}/retry`
  - `POST /interviews/turn-attempts/{turnId}/cancel`
  - `POST /interviews/turn-attempts/{turnId}/discard`
- Preserve the existing centralized 401/session-expiry behavior by using `ApiClient` service Dio instances; do not create an unauthenticated standalone client.
- Generate a stable client `turnId` once per explicit submission and reuse it for an exact retry of the same HTTP submission. A recovery retry uses a new `turnId` and retains `retryOfTurnId` server-side.

### Durable initial interview start

- The accepted "exit at any time" guarantee includes the opening generation of a newly started interview. Do not leave the first model call owned by the legacy `/interviews/chat` connection.
- Add an authenticated, idempotent `POST /interviews/start-attempts` contract that creates the root Lineage, root Branch, and opening Turn Attempt in one transaction. Merely opening the upload/chat route must not create duplicates.
- Resolve a supplied resume only when `t_resume.user_id` matches the authenticated user; load its persisted `raw_text` and parsed candidate name when available. Resolve the selected active job's requirements from persisted job data. Keep both IDs optional so the existing skip-resume flow remains usable.
- Use a stable deterministic root ID/idempotency boundary for concurrent exact `turnId` retries; changed payload reuse must conflict and a failed transaction must not leave an empty Lineage or Branch.
- The opening attempt uses branch stage `opening`, expected Branch Version `1`, a `null` canonical tail, and the system trigger `我准备好了`. The durable Python processor already handles the opening stage; the canonical commit must persist this first human entry as `system_trigger`, not `candidate_answer`.
- Starting the interview returns as soon as the durable attempt exists. Flutter navigates to chat, attaches to processing, and may exit immediately without cancelling it.
- After the opening completes, Flutter reloads the canonical transcript. Every later live-chat answer uses the normal durable branch Turn Attempt API; the legacy `/interviews/chat` endpoint remains compatibility-only.

### Replay state

- Load the lineage tree and focused transcript without calling the model or appending messages.
- Selecting a tree node loads that branch's composed transcript and updates focus locally.
- Track one active/recoverable attempt separately from Business Messages.
- Reattach to `PROCESSING` or `CANCEL_REQUESTED` attempts by status/event stream; navigation or stream loss must not cancel processing.
- On terminal `COMPLETED`, reload both tree and selected branch transcript.
- On `FAILED`, `INTERRUPTED`, or `CANCELLED`, retain the candidate answer and show a Turn Recovery card outside the transcript.
- `DISCARDED` removes the recovery card after a refresh but never deletes canonical Business Messages.

### Tail continuation

- Show a tail composer only when the selected branch is Active, its completed canonical tail is an AI message with `expectsResponse=true`, and no attempt is processing.
- Submit to `POST /branches/{branchId}/turn-attempts` with the selected branch version and exact canonical tail message ID.
- Do not optimistically insert candidate or AI Business Message bubbles. The persisted transcript is reloaded after completion.
- If a stale version/tail or Lineage Processing Slot conflict occurs, keep the draft text and provide refresh. Never silently overwrite history, queue work, or auto-create a branch.
- Completed branches are read-only by default and show no tail composer.

### Message-anchored fork draft

- Show a fork action only when `message.forkable == true`.
- Selecting a candidate answer creates a local Branch Draft pre-filled with that answer.
- Selecting an AI prompt creates a local empty Branch Draft.
- Creating or editing a Branch Draft performs no server write.
- Explicit submission calls `POST /branches/{focusedBranchId}/fork-attempts` with the selected trigger message, answer, focused branch version, focused canonical tail, and stable `turnId`.
- On success, select the returned child branch and attach to its first Turn Attempt.
- On conflict/error, preserve the draft and selected trigger. The user may refresh or retry explicitly.
- Trust the server for Owning Branch, Fork Point, ancestry, and forkability; inherited messages may create a child under a branch other than the currently focused branch.

### Responsive experience

- Desktop/web: branch tree and transcript split view.
- Mobile/narrow width: transcript first, with the branch tree available in a drawer or modal sheet.
- Tree nodes show label, status, progress, score/evaluation summary when present, activity, owned/inherited assessment counts, and recoverable attempt status.
- Transcript distinguishes inherited prefix, Fork Point, and current-branch delta. A message whose ID equals the transcript `forkPointMessageId` is visibly marked.
- Processing and recovery state is rendered outside the canonical message list; no error, partial chunk, or transport status becomes a message bubble.
- Deep and large trees remain scrollable and selectable.

### History filters

- Preserve real paged history, keyword search, and time/score sort.
- Add a user-scoped backend status parameter and Flutter status filtering. Filtering must happen before pagination; do not filter only the currently loaded page.
- Define status from the server-selected focused branch so mixed lineages remain in the Active category while any Active Branch exists. Support `all`, `active`, `completed`, and `ended` (`status` values other than Active/Completed), rejecting or normalizing unknown values consistently.

## Required Tests (write RED first)

1. Model parsing for tree, branch message Fork Point, attempt, event, and fork response.
2. API request paths and payloads for normal turn, fork turn, get/events, retry, cancel, and discard.
3. Opening replay loads tree plus persisted transcript and performs no chat/model call.
4. Desktop split layout and narrow transcript-first layout with branch-tree affordance.
5. Selecting another node loads that branch and preserves canonical ordering.
6. Completed branch has no tail composer but retains fork actions on eligible messages.
7. Active AI tail submits a durable Turn Attempt using exact version/tail and does not add optimistic Business Messages.
8. Candidate-answer fork pre-fills; AI-question fork is empty; neither creates a branch before explicit submit.
9. Fork success selects the child and reattaches to its first attempt.
10. Processing reattachment survives page rebuild/stream reconnect and blocks a second submission.
11. Completed attempt reloads tree/transcript exactly once per terminal transition.
12. Failed/interrupted/cancelled attempt shows recovery outside transcript; retry/cancel/discard call the correct APIs.
13. Stale/conflict response preserves the user's draft and offers refresh.
14. Inherited message fork sends the viewed focused branch state while leaving branch ownership resolution to the server.
15. Session-expiry behavior remains centralized; no new raw 401 error bubble is inserted.
16. History status filtering and existing keyword/sort/paging regressions.
17. Existing read-path, hydration, media, and exit-navigation tests remain green.
18. New interview start atomically creates one root Lineage/Branch/opening attempt, replays exact concurrent `turnId`, rejects changed payload reuse, resolves only the caller's resume, and leaves no empty root on failure.
19. Flutter upload/start navigation attaches to the opening attempt, permits immediate exit, and uses durable Turn Attempts for every later answer without calling legacy chat.

Register every new or changed case in `tests/docs/test-cases.md`; update `tests/docs/tooling-guide.md` if commands or prerequisites change.

## Boundaries

- Do not use the legacy `/interviews/chat` endpoint for a resumed branch answer or a replay fork.
- Do not use the legacy `/interviews/chat` endpoint for a newly started interview opening or any later first-party Flutter turn.
- Do not create empty child branches.
- Do not append optimistic, partial, failed, or transport messages to canonical replay.
- Do not cancel processing merely because the screen is closed.
- Do not modify the authoritative running database, deploy services, or restart shared Compose services.
- Do not commit, stage, or push.

## Verification Gate

- Focused Flutter model/API/service tests pass.
- Full Flutter widget/unit suite passes in the established Flutter container.
- `flutter analyze --no-pub` is clean.
- Existing Java and Python checkpoint suites remain green after the client contract is finalized.
- Independent review reports no unresolved Critical or Important findings.
