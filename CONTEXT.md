# AI Interviewer Context

This context defines the language for the AI interviewer's monitoring, replay, and agent execution model.

## Language

**Interview Session**:
A business interview conversation owned by the Java Interview Service and persisted in PostgreSQL.
_Avoid_: LangGraph session, Python session, agent session

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
