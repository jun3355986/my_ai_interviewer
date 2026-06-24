## ADDED Requirements

### Requirement: AI trace persistence
The system SHALL persist one AI observability trace for each observed user conversation turn or interview progression request.

#### Scenario: Trace created for observed answer generation
- **WHEN** the Python AI service starts an observed answer-generation or interview-progression flow
- **THEN** the system persists a trace containing request identity, user/session context, business type, start time, end time, status, duration, and correlation identifiers.

#### Scenario: Observability write failure does not block interview
- **WHEN** persisting a trace, step, or LLM call fails
- **THEN** the candidate-facing interview flow continues and the failure is recorded as an observability write failure for later diagnosis.

### Requirement: Execution step timeline
The system SHALL persist a timeline of business execution steps associated with each AI observability trace.

#### Scenario: Step timeline records process details
- **WHEN** an observed flow performs prompt building, retrieval, LLM generation, fallback, evaluation, tool-like lookup, or response assembly
- **THEN** the system records ordered step entries with step type, name, status, timestamps, duration, metadata, and error details when applicable.

#### Scenario: Tool and retrieval activity represented as steps
- **WHEN** the flow performs a retrieval, helper invocation, or tool-like operation that is not itself an LLM call
- **THEN** the system records that activity as a trace step so the admin detail page can reconstruct the answer process.

### Requirement: LLM call usage capture
The system SHALL persist each LLM call made during an observed trace with provider, model, call type, latency, status, token usage, and raw provider usage metadata.

#### Scenario: Provider usage captured before parsing
- **WHEN** a LangChain LLM response is received
- **THEN** the system captures `AIMessage` metadata and usage before string parsing removes provider metadata.

#### Scenario: Token source is explicit
- **WHEN** token counts come from provider usage
- **THEN** the system marks the token source as provider-reported.

#### Scenario: Token fallback is explicit
- **WHEN** provider usage is unavailable and the system estimates token counts
- **THEN** the system marks the token source as estimated and excludes estimated cache fields from provider-cache metrics.

### Requirement: Raw prompt and response retention
The system SHALL support retaining the full prompt and full LLM response text for each observed LLM call.

#### Scenario: Raw payloads retained when enabled
- **WHEN** raw payload retention is enabled
- **THEN** the system persists complete prompt and response text subject to configured maximum size limits.

#### Scenario: Raw payload access is audited
- **WHEN** an administrator opens full prompt or response text in the admin system
- **THEN** the system records an access log entry containing administrator identity, target trace or call, access type, timestamp, and request metadata.

### Requirement: Provider prompt cache metrics
The system SHALL calculate provider prompt cache metrics only from cache fields reported by the LLM provider.

#### Scenario: DeepSeek cache usage normalized
- **WHEN** DeepSeek returns `prompt_cache_hit_tokens` and `prompt_cache_miss_tokens`
- **THEN** the system stores hit tokens, miss tokens, provider-cache reported status, and per-call cache token hit rate.

#### Scenario: OpenAI-compatible cached tokens normalized
- **WHEN** an OpenAI-compatible provider returns `prompt_tokens_details.cached_tokens`
- **THEN** the system stores cached prompt tokens as provider cache hit tokens and derives miss tokens from prompt tokens minus cached tokens when prompt tokens are available.

#### Scenario: Cache token hit rate calculated
- **WHEN** the admin system aggregates calls with provider-reported cache fields
- **THEN** it calculates provider prompt cache token hit rate as `sum(prompt_cache_hit_tokens) / sum(prompt_cache_hit_tokens + prompt_cache_miss_tokens)`.

#### Scenario: Cache call hit ratio calculated
- **WHEN** the admin system aggregates calls with provider-reported cache fields
- **THEN** it calculates provider prompt cache call hit ratio as `count(prompt_cache_hit_tokens > 0) / count(cache_reported_by_provider = true)`.

#### Scenario: Unreported cache calls are visible
- **WHEN** calls do not include provider cache fields
- **THEN** the admin system excludes them from provider-cache denominators and shows their count as provider-cache unreported calls.

### Requirement: Admin observability queries
The system SHALL expose admin-only APIs and UI views for querying AI observability traces, LLM calls, metrics, and raw payload detail.

#### Scenario: Admin list filters traces
- **WHEN** an administrator filters by time range, user, session, model, provider, call type, status, or trace id
- **THEN** the admin system returns paginated trace and call summaries with token usage, duration, status, and cache summary fields.

#### Scenario: Admin detail reconstructs a response
- **WHEN** an administrator opens a trace detail page
- **THEN** the page shows the ordered execution timeline, associated LLM calls, prompt/response detail access controls, errors, fallback records, token usage, and provider-cache fields.

#### Scenario: Admin statistics summarize usage
- **WHEN** an administrator opens the observability statistics view
- **THEN** the system shows aggregate token usage, LLM calls, failure rate, average duration, provider cache token hit rate, provider cache call hit ratio, and high-consumption call types for the selected time range.

### Requirement: No application-level LLM cache in first release
The system SHALL NOT introduce an application-level LLM prompt-response cache as part of the first AI observability release.

#### Scenario: Cache architecture remains absent
- **WHEN** the first release is implemented
- **THEN** Redis or other storage is not used to short-circuit LLM calls by prompt/response cache key, and cache metrics remain provider-cache metrics only.
