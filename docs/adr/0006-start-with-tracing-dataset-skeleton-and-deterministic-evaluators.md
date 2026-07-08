# Start with tracing, dataset skeleton, and deterministic evaluators

The first LangSmith testing integration will include tracing, a path to convert project replay examples into Evaluation Dataset examples, and Deterministic Evaluators for explicit checks such as event presence, stage, errors, token limits, and cost limits. We will defer LLM-as-judge, production-trace auto-promotion, and A/B routing until the replay and observability foundations are stable.
