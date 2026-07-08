# Wrap single-turn agent runs without changing external contracts

The first LangGraph integration will wrap each Python interview chat or resume request as a Single-Turn Agent Run while preserving the existing Flutter, Java `/interviews/chat`, SSE event, persistence, and replay contracts. This lets us add Agent Checkpoints and graph-level diagnostics without turning the initial integration into a full rewrite of the interview workflow.
