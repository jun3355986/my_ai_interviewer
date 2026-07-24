# AI Interviewer Context

This context defines the language for the AI interviewer's monitoring, replay, and agent execution model.

Implementation milestones are defined in
[`docs/langsmith-langgraph-integration-plan.md`](docs/langsmith-langgraph-integration-plan.md).

## Language

**Interview Session**:
A business interview conversation owned by the Java Interview Service and persisted in PostgreSQL.
_Avoid_: LangGraph session, Python session, agent session

**Interview Branch**:
A single linear interview path with its own progress, outcome, and evaluation. The original path and every forked path are separate Interview Sessions.
_Avoid_: Chat version, overwritten history

**Branch Delta**:
The Business Messages, assessments, and state produced specifically on one Interview Branch after its Fork Point, excluding the inherited ancestor prefix.
_Avoid_: Full transcript copy, lineage snapshot, inherited history

**Legacy Branch**:
A root Interview Branch migrated from an interview session that predates lineage and message-semantics support, preserving its original conversation and assessments while treating uncertain messages as non-forkable.
_Avoid_: Imported branch, reconstructed interview, new branch

**Active Branch**:
An Interview Branch that has not been completed or cancelled and can be continued from its current tail. An Interview Lineage may contain more than one Active Branch.
_Avoid_: Current branch, selected branch, running request

**Branch Version**:
A monotonically advancing identity for the committed tail of an Interview Branch, used to detect submissions based on stale conversation state.
_Avoid_: App version, schema version, message sequence

**Focused Branch**:
The Interview Branch currently displayed in Interview Replay. Initial focus prefers the Active Branch with the latest Business Activity, or the most recently completed branch when none are active.
_Avoid_: Active branch, source branch, default session

**Interview Lineage**:
An original Interview Branch together with every descendant branch created from it. It is the single interview-history item through which users enter Interview Replay.
_Avoid_: Interview Session, branch, transcript

**Lineage Processing Slot**:
The single permission within an Interview Lineage for one Turn Attempt to perform AI processing at a time, independent of how many Active Branches the lineage contains.
_Avoid_: Active branch, queued request, global model capacity

**Lineage Summary**:
The interview-history projection of an Interview Lineage, showing branch counts, latest Business Activity, and the best completed branch score or the latest active progress when no branch is complete.
_Avoid_: Average branch score, branch evaluation, focused branch

**Interview Fork**:
The creation of a new Interview Branch from an earlier point in an existing branch without changing the source branch.
_Avoid_: Resume, rewind, overwrite

**Fork Point**:
The persisted interview message that anchors the inherited conversation context of an Interview Fork.
_Avoid_: Checkpoint, cursor, selected text

**Owning Branch**:
The Interview Branch on which a Business Message was originally created. A fork from inherited content is attached to that message's Owning Branch rather than the branch currently being viewed.
_Avoid_: Focused branch, displaying branch, root branch

**Branch Draft**:
A proposed first candidate answer for an Interview Fork. It has no branch identity and does not change interview history until the candidate submits it.
_Avoid_: Pending session, empty branch, autosaved fork

**Interview Replay**:
A tree-shaped view of an Interview Branch and its descendants. A completed branch is read-only in replay, although one of its historical messages may still be used to prepare an Interview Fork.
_Avoid_: Chat screen, transcript, resume session

**Branch Tree**:
The branch-level navigation of an Interview Lineage whose edges identify where each child branch forked. Individual messages are viewed in the Branch Transcript rather than represented as tree nodes.
_Avoid_: Message tree, transcript, history list

**Branch Transcript**:
The linear conversation shown for the Focused Branch, including its inherited prefix, Fork Point, and branch-specific continuation.
_Avoid_: Branch tree, flat lineage history, raw event log

**Business Message**:
A successfully persisted candidate or AI message that forms part of the interview conversation. Transport errors, UI-only system notices, and incomplete stream fragments are not Business Messages.
_Avoid_: Log entry, error banner, SSE chunk

**Forkable Message**:
A completed Business Message from which a Branch Draft may be prepared: either a candidate answer or an AI prompt that expects a candidate response.
_Avoid_: Any AI message, system message, partial response

**Interview Turn**:
An atomically completed unit consisting of a candidate answer and the resulting AI response, assessment, and interview-state transition.
_Avoid_: Message, SSE request, model call

**Turn Attempt**:
A durable attempt to produce an Interview Turn from a candidate answer. A failed or interrupted attempt preserves the answer for recovery but does not add partial AI output to the branch history.
_Avoid_: Interview Turn, error message, duplicate answer

**Turn Recovery**:
The user-facing resolution state for an unfinished Turn Attempt, presented outside the Branch Transcript so its preserved answer can be retried, edited, or discarded without becoming interview history.
_Avoid_: Error message, chat bubble, Business Message

**Turn Processing**:
The server-owned lifecycle that produces an Interview Turn from a Turn Attempt independently of any client page, network connection, or progress subscription.
_Avoid_: SSE connection, open chat page, foreground request

**Business Activity**:
The creation time of the latest Business Message on an Interview Branch. It represents actual interview progress rather than a generic record update.
_Avoid_: Updated time, last viewed time, error time

**Inherited Assessment**:
A completed question-and-answer assessment from before a Fork Point that contributes unchanged to a child branch's independent final evaluation.
_Avoid_: Re-scored history, shared final report, copied result

**Agent Run**:
A single Python AI execution that handles one interview request and may include question selection, scoring, follow-up generation, or summary work.
_Avoid_: Interview session, request trace

**Single-Turn Agent Run**:
An Agent Run scoped to one interview chat or resume request, not to the entire Interview Session.
_Avoid_: Full interview graph, business session

**Agent Run ID**:
The identifier for one Single-Turn Agent Run and the first-stage LangGraph thread key.
_Avoid_: Interview Session ID, request ID

**Agent Checkpoint**:
A LangGraph-saved intermediate state of an Agent Run used for resume, fork, time travel, or skipping already-completed agent nodes.
_Avoid_: Business session state, database checkpoint

**Checkpoint Store**:
A runtime-managed store for Agent Checkpoints, owned by deployment and operations rather than git version control.
_Avoid_: Git fixture, replay trace, schema migration

**Replay Trace**:
A project-owned JSONL artifact that can replay interview API and SSE behavior through the Java interview boundary.
_Avoid_: LangSmith trace, recording, log

**Manual Flow Recorder**:
A local or dev-only API/SSE recorder that captures real manual interview requests and stream outcomes as candidate replay material.
_Avoid_: UI recorder, screen recording, LangSmith trace

**Authoritative Test Asset**:
A repository-owned test artifact that can be executed locally without relying on external observability or evaluation platforms.
_Avoid_: LangSmith dataset, experiment result, report

**Evaluation Dataset**:
A curated set of examples used to run repeatable LangSmith experiments against interview AI behavior.
_Avoid_: Replay trace, production log

**Deterministic Evaluator**:
A non-LLM evaluator that checks explicit facts such as event presence, stage, errors, token limits, cost limits, or required fields.
_Avoid_: LLM judge, subjective review

**Observability Trace**:
A run record used to inspect prompts, model calls, tool calls, token usage, cost, latency, errors, and correlation metadata.
_Avoid_: Replay trace, test case

**Project Observability Store**:
The project's own database-backed observability records used for local diagnostics, admin views, SQL correlation, and project-controlled raw payload access.
_Avoid_: LangSmith, replay report

**Raw Payload**:
The full prompt, model request, model response, resume text, job requirements, or candidate answer captured for debugging.
_Avoid_: Metadata, summary, metric
