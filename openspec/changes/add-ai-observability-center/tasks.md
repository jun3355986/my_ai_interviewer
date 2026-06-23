## 1. Data Model And Configuration

- [ ] 1.1 Add PostgreSQL schema for `t_ai_trace`, `t_ai_trace_step`, `t_ai_llm_call`, and `t_ai_observability_access_log`.
- [ ] 1.2 Add Python AI observability configuration for enablement, PostgreSQL DSN, write timeout, raw payload retention, and raw payload maximum length.
- [ ] 1.3 Add documentation and test registry entries for observability test assets under the root `tests/` directory.

## 2. Python AI Service Instrumentation

- [ ] 2.1 Implement provider usage normalization for DeepSeek, OpenAI-compatible cached tokens, unreported cache fields, and estimated-token fallback.
- [ ] 2.2 Implement trace, step, and LLM call writers using best-effort PostgreSQL persistence.
- [ ] 2.3 Instrument core interview and answer-generation LLM calls before `StrOutputParser` can discard `AIMessage` metadata.
- [ ] 2.4 Record fallback model usage, retrieval/tool-like activity, errors, durations, raw prompt text, and raw response text.

## 3. Java Backend And Admin APIs

- [ ] 3.1 Propagate request, user, session, and business correlation identifiers from Java interview flows to Python AI calls.
- [ ] 3.2 Add Java admin read models and query APIs for observability trace list, trace detail, LLM call detail, and statistics.
- [ ] 3.3 Add admin access logging for full prompt and full response reads.
- [ ] 3.4 Ensure admin APIs enforce existing backend-admin authentication and authorization boundaries.

## 4. Admin Frontend

- [ ] 4.1 Add an AI observability menu entry and route in the admin frontend.
- [ ] 4.2 Build trace and LLM call list filters, pagination, status badges, token columns, latency columns, and provider-cache fields.
- [ ] 4.3 Build trace detail timeline with steps, associated LLM calls, errors, fallback records, and raw prompt/response reveal controls.
- [ ] 4.4 Build statistics panels for token totals, call count, failure rate, average duration, provider cache token hit rate, provider cache call hit ratio, and unreported cache calls.

## 5. Verification

- [ ] 5.1 Add Python tests for DeepSeek usage fields, OpenAI-compatible cached tokens, unreported provider cache fields, and parser-before-metadata capture.
- [ ] 5.2 Add Java API tests or smoke scripts for list, detail, raw payload access audit, and statistics queries.
- [ ] 5.3 Add frontend route/component tests or browser smoke coverage for observability list, detail, and statistics views.
- [ ] 5.4 Run project build/test commands for the touched Python, Java, and admin frontend modules and record evidence in the verification summary.
