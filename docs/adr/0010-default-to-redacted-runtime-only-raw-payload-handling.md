# Default to redacted runtime-only raw payload handling

Raw Payload capture will be disabled by default for LangSmith, Manual Flow Recorder, LangGraph checkpoints, and git-versioned Replay Traces. Raw data may be captured only through explicit local or dev debug flags into runtime-managed reports or stores, and curated repository test assets must be reviewed and redacted before commit.
