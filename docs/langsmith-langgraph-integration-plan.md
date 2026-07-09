# LangSmith and LangGraph Integration Plan

This plan introduces LangSmith for observability and evaluation, LangGraph for single-turn agent checkpoints, and keeps project Replay Traces as the authoritative business-flow reproduction assets.

## Phase Order

1. Shared configuration and correlation IDs.
2. LangSmith tracing.
3. Manual Flow Recorder.
4. LangSmith evaluation skeleton.
5. LangGraph Single-Turn Agent Run thin wrapper.

## Done Definition

1. Default configuration keeps existing replay, stub, and API tests passing without a LangSmith key.
2. With `LANGSMITH_TRACING=true`, one Python `/interview/chat` request creates a LangSmith trace with `interviewSessionId`, `pythonSessionId`, `agentRunId`, `requestId`, `entrypoint`, and `stage` metadata.
3. With `LANGSMITH_CAPTURE_RAW_PAYLOADS=false`, LangSmith does not receive full resume text, full candidate answers, full prompts, or full model responses.
4. The Project Observability Store keeps writing normally and is not replaced by LangSmith.
5. With Manual Flow Recorder enabled, a real manual interview flow creates a candidate trace report under `tests/reports/manual-traces/`.
6. A curated manual trace can be promoted into a Replay Trace and executed with `bash tests/scripts/replay-interview.sh <trace.jsonl>`.
7. The evaluation skeleton can sync at least one Replay Trace into a LangSmith Evaluation Dataset and run a Deterministic Evaluator.
8. With the LangGraph thin wrapper enabled, existing SSE event types and replay behavior remain stable, while a runtime-managed SQLite Checkpoint Store records Agent Checkpoints keyed by Agent Run ID.

## Runtime Data Boundary

Runtime databases, checkpoint files, manual raw reports, and operational dumps are deployment-managed state and must not be committed. The repository may contain configuration templates, scripts, documentation, and reviewed redacted fixtures.

## Implementation Status

Implemented in the Python AI service:

1. Shared agent runtime configuration and propagated `agent_run_id`.
2. Opt-in LangSmith tracing around project observability traces, with raw payload metadata excluded by default.
3. Opt-in Manual Flow Recorder writing candidate replay JSONL and companion reports under `tests/reports/manual-traces/`.
4. LangSmith evaluation skeleton that converts Replay Traces into dataset examples and runs deterministic replay-report evaluators.
5. Opt-in LangGraph Single-Turn Agent Run thin wrapper that records runtime-managed SQLite checkpoints keyed by Agent Run ID.

Primary code locations:

- `ai_interviewer/services/agent_runtime/`
- `ai_interviewer/services/observability/context.py`
- `tests/scripts/langsmith_eval.py`
- `ai_interviewer/tests/test_agent_runtime.py`
