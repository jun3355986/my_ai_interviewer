# Phase observability, recording, and evaluation before the LangGraph wrapper

We will implement the first integration in this order: shared configuration and correlation IDs, LangSmith tracing, Manual Flow Recorder, LangSmith evaluation skeleton, then the LangGraph Single-Turn Agent Run wrapper. This lowers risk by making agent runs observable and replayable before introducing checkpointed graph execution.
