---
change: add-ai-observability-center
design-doc: docs/superpowers/specs/2026-06-23-ai-observability-design.md
base-ref: fc53e43a0f607f5ff0ce183ed1d8c9a40d281c51
---

# AI Observability Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an admin-facing AI/LLM observability center that records conversation traces, execution steps, LLM token usage, provider prompt-cache metrics, tool/retrieval activity, full prompts, full responses, and raw-payload access audit.

**Architecture:** Python AI service owns capture because it has the complete prompt, response, `AIMessage` metadata, provider usage, fallback behavior, and retrieval context. PostgreSQL stores observability tables shared with the admin service; Java admin exposes read-only APIs and raw-payload audit; React admin renders list/detail/statistics views. Java interview service only propagates correlation context to Python.

**Tech Stack:** FastAPI, LangChain, `langchain_openai.ChatOpenAI`, PostgreSQL, Spring Boot 3.3.5, MyBatis, Flyway-style SQL migration, React, TypeScript, Vite, Maven, uv, pytest.

---

## File Structure

- Create: `ai_interviewer/services/observability/__init__.py`
  Exposes the Python observability API.
- Create: `ai_interviewer/services/observability/config.py`
  Reads `AI_OBSERVABILITY_*` environment variables.
- Create: `ai_interviewer/services/observability/models.py`
  Contains dataclasses/enums for trace, step, LLM call, and normalized usage.
- Create: `ai_interviewer/services/observability/provider_usage.py`
  Normalizes DeepSeek and OpenAI-compatible provider usage into cache/token fields.
- Create: `ai_interviewer/services/observability/repository.py`
  Best-effort PostgreSQL writer using short timeouts.
- Create: `ai_interviewer/services/observability/context.py`
  Provides trace context helpers and no-op behavior when disabled.
- Create: `ai_interviewer/services/observability/langchain.py`
  Invokes prompt + LLM without losing `AIMessage` metadata before parsing.
- Modify: `ai_interviewer/api/interviewer.py`
  Replace direct `prompt | self.llm | StrOutputParser()` paths with observable calls.
- Modify: `ai_interviewer/api/router.py`
  Create/request trace context for SSE and REST interview endpoints.
- Modify: `ai_interviewer/schemas/chat.py`
  Add optional correlation fields accepted from Java `PythonChatRequest`.
- Test: `ai_interviewer/tests/test_observability_provider_usage.py`
- Test: `ai_interviewer/tests/test_observable_langchain.py`

- Create: `ai_interviewer_admin/src/main/resources/db/migration/V2__ai_observability.sql`
  Adds observability tables, indexes, and admin menu/permission seed data.
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/observability/AiObservabilityController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/observability/AiObservabilityService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/observability/mapper/AiObservabilityMapper.java`
- Create: `ai_interviewer_admin/src/main/resources/mapper/AiObservabilityMapper.xml`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/observability/dto/*.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/observability/AiObservabilityServiceTest.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/schema/AiObservabilitySchemaMigrationTest.java`

- Modify: `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/PythonChatRequest.java`
- Modify: `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/SSEProxyService.java`

- Modify: `ai_interviewer_admin_front/src/types.ts`
- Modify: `ai_interviewer_admin_front/src/api.ts`
- Modify: `ai_interviewer_admin_front/src/App.tsx`
- Modify: `ai_interviewer_admin_front/src/styles.css`
- Test: `tests/e2e/playwright/tests/admin-web-smoke.spec.ts`
- Modify: `tests/docs/test-cases.md`
- Modify: `tests/docs/tooling-guide.md`

## Task 1: Database Schema And Admin Contract

**Files:**
- Create: `ai_interviewer_admin/src/main/resources/db/migration/V2__ai_observability.sql`
- Create: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/schema/AiObservabilitySchemaMigrationTest.java`
- Modify: `tests/docs/test-cases.md`

- [x] **Step 1: Write the schema migration test**

Add a test that boots the admin schema migration and asserts these tables exist:

```java
@Test
void aiObservabilityTablesAreCreated() throws Exception {
    assertTableExists("t_ai_trace");
    assertTableExists("t_ai_trace_step");
    assertTableExists("t_ai_llm_call");
    assertTableExists("t_ai_observability_access_log");
}
```

Run: `JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv version` and then `cd ai_interviewer_admin && ./mvnw -Dtest=AiObservabilitySchemaMigrationTest test`

Expected before implementation: FAIL because `V2__ai_observability.sql` does not exist.

- [x] **Step 2: Add the observability migration**

Create `V2__ai_observability.sql` with these table responsibilities:

```sql
CREATE TABLE IF NOT EXISTS t_ai_trace (
    id UUID PRIMARY KEY,
    request_id VARCHAR(100),
    user_id BIGINT,
    username VARCHAR(120),
    session_id VARCHAR(100),
    python_session_id VARCHAR(100),
    business_type VARCHAR(80) NOT NULL,
    entrypoint VARCHAR(120),
    status VARCHAR(30) NOT NULL,
    error_code VARCHAR(100),
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    duration_ms BIGINT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_ai_trace_step (
    id UUID PRIMARY KEY,
    trace_id UUID NOT NULL REFERENCES t_ai_trace (id),
    step_order INTEGER NOT NULL,
    step_type VARCHAR(80) NOT NULL,
    step_name VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    duration_ms BIGINT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_ai_llm_call (
    id UUID PRIMARY KEY,
    trace_id UUID NOT NULL REFERENCES t_ai_trace (id),
    step_id UUID REFERENCES t_ai_trace_step (id),
    call_type VARCHAR(100) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    model VARCHAR(160) NOT NULL,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    fallback_from_model VARCHAR(160),
    status VARCHAR(30) NOT NULL,
    prompt_tokens BIGINT,
    completion_tokens BIGINT,
    total_tokens BIGINT,
    token_source VARCHAR(30) NOT NULL,
    prompt_cache_hit_tokens BIGINT,
    prompt_cache_miss_tokens BIGINT,
    prompt_cache_hit_rate NUMERIC(8,6),
    cache_reported_by_provider BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms BIGINT,
    prompt_text TEXT,
    response_text TEXT,
    raw_usage_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_ai_observability_access_log (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT,
    trace_id UUID,
    llm_call_id UUID,
    access_type VARCHAR(40) NOT NULL,
    request_uri VARCHAR(500),
    ip_address VARCHAR(100),
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Add indexes for `started_at`, `session_id`, `user_id`, `status`, `provider`, `model`, `call_type`, `cache_reported_by_provider`, and `trace_id`.

- [x] **Step 3: Seed admin menu and permissions**

In the same migration, insert menu code `ai_observability` and permissions for:

```text
AI_OBSERVABILITY_VIEW
AI_OBSERVABILITY_RAW_READ
AI_OBSERVABILITY_STATS
```

Use `ON CONFLICT`-safe inserts consistent with existing seed style in `V1__admin_schema.sql`.

- [x] **Step 4: Run schema tests**

Run: `cd ai_interviewer_admin && ./mvnw -Dtest=AiObservabilitySchemaMigrationTest test`

Expected: PASS.

- [x] **Step 5: Commit schema task**

```bash
git add ai_interviewer_admin/src/main/resources/db/migration/V2__ai_observability.sql ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/schema/AiObservabilitySchemaMigrationTest.java tests/docs/test-cases.md
git commit -m "feat: add ai observability schema"
```

## Task 2: Python Provider Usage Normalization

**Files:**
- Create: `ai_interviewer/services/observability/models.py`
- Create: `ai_interviewer/services/observability/provider_usage.py`
- Test: `ai_interviewer/tests/test_observability_provider_usage.py`

- [x] **Step 1: Write failing provider usage tests**

Create tests covering DeepSeek, OpenAI/Azure, unreported cache fields, and estimated fallback:

```python
from services.observability.provider_usage import normalize_provider_usage


def test_deepseek_prompt_cache_tokens_are_normalized():
    usage = {
        "prompt_tokens": 100,
        "completion_tokens": 25,
        "total_tokens": 125,
        "prompt_cache_hit_tokens": 60,
        "prompt_cache_miss_tokens": 40,
    }

    result = normalize_provider_usage("deepseek", usage)

    assert result.prompt_tokens == 100
    assert result.completion_tokens == 25
    assert result.total_tokens == 125
    assert result.prompt_cache_hit_tokens == 60
    assert result.prompt_cache_miss_tokens == 40
    assert result.prompt_cache_hit_rate == 0.6
    assert result.cache_reported_by_provider is True
    assert result.token_source == "provider"


def test_openai_cached_tokens_are_normalized():
    usage = {
        "prompt_tokens": 100,
        "completion_tokens": 15,
        "total_tokens": 115,
        "prompt_tokens_details": {"cached_tokens": 35},
    }

    result = normalize_provider_usage("openai", usage)

    assert result.prompt_cache_hit_tokens == 35
    assert result.prompt_cache_miss_tokens == 65
    assert result.prompt_cache_hit_rate == 0.35
    assert result.cache_reported_by_provider is True


def test_unreported_cache_fields_are_excluded_from_cache_metrics():
    result = normalize_provider_usage(
        "azure_openai",
        {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15},
    )

    assert result.cache_reported_by_provider is False
    assert result.prompt_cache_hit_tokens is None
    assert result.prompt_cache_miss_tokens is None
    assert result.prompt_cache_hit_rate is None


def test_missing_usage_becomes_estimated_source_without_cache_metrics():
    result = normalize_provider_usage("unknown", None, estimated_prompt_tokens=12, estimated_completion_tokens=8)

    assert result.prompt_tokens == 12
    assert result.completion_tokens == 8
    assert result.total_tokens == 20
    assert result.token_source == "estimated"
    assert result.cache_reported_by_provider is False
```

Run: `cd ai_interviewer && uv run pytest tests/test_observability_provider_usage.py -q`

Expected before implementation: FAIL because the module does not exist.

- [x] **Step 2: Implement normalized usage dataclass**

Create `models.py` with:

```python
from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class NormalizedUsage:
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    total_tokens: int | None = None
    token_source: str = "provider"
    prompt_cache_hit_tokens: int | None = None
    prompt_cache_miss_tokens: int | None = None
    prompt_cache_hit_rate: float | None = None
    cache_reported_by_provider: bool = False
    raw_usage: dict[str, Any] = field(default_factory=dict)
```

- [x] **Step 3: Implement provider normalizer**

Create `provider_usage.py` with a pure function that:

- Reads `prompt_tokens`, `completion_tokens`, and `total_tokens`.
- Reads DeepSeek `prompt_cache_hit_tokens` and `prompt_cache_miss_tokens`.
- Reads OpenAI-compatible `prompt_tokens_details.cached_tokens`.
- Calculates miss tokens as `prompt_tokens - cached_tokens` when possible.
- Calculates hit rate only when hit + miss is greater than zero.
- Uses `token_source="estimated"` only when provider usage is missing.

- [x] **Step 4: Run provider usage tests**

Run: `cd ai_interviewer && uv run pytest tests/test_observability_provider_usage.py -q`

Expected: PASS.

- [x] **Step 5: Commit provider usage normalization**

```bash
git add ai_interviewer/services/observability/models.py ai_interviewer/services/observability/provider_usage.py ai_interviewer/tests/test_observability_provider_usage.py
git commit -m "feat: normalize ai provider usage"
```

## Task 3: Python Observability Writer And LangChain Capture

**Files:**
- Create: `ai_interviewer/services/observability/config.py`
- Create: `ai_interviewer/services/observability/repository.py`
- Create: `ai_interviewer/services/observability/context.py`
- Create: `ai_interviewer/services/observability/langchain.py`
- Modify: `ai_interviewer/api/interviewer.py`
- Modify: `ai_interviewer/api/router.py`
- Modify: `ai_interviewer/schemas/chat.py`
- Test: `ai_interviewer/tests/test_observable_langchain.py`

- [x] **Step 1: Write failing metadata preservation test**

Create a fake LLM response object with `content`, `usage_metadata`, and `response_metadata`, then assert the observable helper captures usage before returning text:

```python
def test_observable_invoke_captures_usage_before_text_parser(fake_trace_writer):
    response = observable_invoke_for_test(
        content="hello",
        usage_metadata={"input_tokens": 10, "output_tokens": 3, "total_tokens": 13},
        response_metadata={"token_usage": {"prompt_tokens": 10, "completion_tokens": 3, "total_tokens": 13}},
    )

    assert response.text == "hello"
    assert fake_trace_writer.calls[0].prompt_tokens == 10
    assert fake_trace_writer.calls[0].completion_tokens == 3
    assert fake_trace_writer.calls[0].total_tokens == 13
```

Run: `cd ai_interviewer && uv run pytest tests/test_observable_langchain.py -q`

Expected before implementation: FAIL because the helper does not exist.

- [x] **Step 2: Add observability configuration**

Implement these environment variables in `config.py`:

```text
AI_OBSERVABILITY_ENABLED=true
AI_OBSERVABILITY_DB_URL=
AI_OBSERVABILITY_WRITE_TIMEOUT_MS=300
AI_OBSERVABILITY_STORE_RAW_PAYLOAD=true
AI_OBSERVABILITY_MAX_RAW_CHARS=200000
```

If `AI_OBSERVABILITY_DB_URL` is empty, the writer returns a no-op repository and logs one startup warning.

- [x] **Step 3: Add best-effort repository**

Implement repository methods:

```python
create_trace(...)
finish_trace(...)
create_step(...)
finish_step(...)
record_llm_call(...)
```

Each method catches exceptions, logs `observability write failed`, and never raises into business code.

- [x] **Step 4: Add observable LangChain helper**

Implement helper behavior:

```python
prompt_value = prompt.invoke(input_values)
ai_message = llm.invoke(prompt_value)
usage = extract_usage(ai_message)
text = ai_message.content if isinstance(ai_message.content, str) else str(ai_message.content)
repository.record_llm_call(..., prompt_text=prompt_value.to_string(), response_text=text, raw_usage_json=usage)
return text
```

Do not pipe through `StrOutputParser` before usage extraction.

- [x] **Step 5: Replace direct LLM calls in `Interviewer`**

For each method currently using `prompt | self.llm | StrOutputParser()`:

```text
generate_opening
ask_self_introduction
generate_project_questions
evaluate_answer
generate_followup_question
conclude_interview
ask
```

Call the observable helper with a stable `call_type`, such as `generate_opening`, `ask_self_introduction`, `generate_project_questions`, `evaluate_answer`, `generate_followup_question`, `conclude_interview`, and `ask`.

- [x] **Step 6: Add trace context to routers**

Extend `UnifiedChatRequest` and `PythonChatRequest`-compatible schema with:

```text
request_id
java_session_id
user_id
username
business_type
entrypoint
```

Create a trace around `/interview/chat` and record status/error/duration around the generator lifecycle.

- [x] **Step 7: Run Python tests**

Run:

```bash
cd ai_interviewer
uv run pytest tests/test_observability_provider_usage.py tests/test_observable_langchain.py tests/test_interviewer_prompt_escaping.py tests/test_interview_technical_transition.py -q
```

Expected: PASS.

- [x] **Step 8: Commit Python observability capture**

```bash
git add ai_interviewer/services/observability ai_interviewer/api/interviewer.py ai_interviewer/api/router.py ai_interviewer/schemas/chat.py ai_interviewer/tests/test_observable_langchain.py
git commit -m "feat: capture ai llm observability"
```

## Task 4: Java Correlation Propagation And Admin APIs

**Files:**
- Modify: `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/PythonChatRequest.java`
- Modify: `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/SSEProxyService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/observability/AiObservabilityController.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/observability/AiObservabilityService.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/observability/mapper/AiObservabilityMapper.java`
- Create: `ai_interviewer_admin/src/main/resources/mapper/AiObservabilityMapper.xml`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/observability/dto/AiTraceQuery.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/observability/dto/AiTraceListItem.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/observability/dto/AiTraceDetailResponse.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/observability/dto/AiObservabilityStatsResponse.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/observability/AiObservabilityServiceTest.java`

- [x] **Step 1: Write failing service tests**

Seed in-memory mapper responses and verify:

```java
@Test
void statsExcludeUnreportedProviderCacheCallsFromCacheDenominator() {
    AiObservabilityStatsResponse stats = service.getStats(queryForToday());

    assertThat(stats.getProviderPromptCacheTokenHitRate()).isEqualByComparingTo("0.600000");
    assertThat(stats.getProviderPromptCacheCallHitRate()).isEqualByComparingTo("0.500000");
    assertThat(stats.getProviderCacheUnreportedCalls()).isEqualTo(2L);
}

@Test
void rawPayloadAccessWritesAuditLog() {
    service.getLlmCallRawPayload(callId, adminUserId, "PROMPT");

    verify(mapper).insertAccessLog(argThat(log -> "PROMPT".equals(log.getAccessType())));
}
```

Run: `cd ai_interviewer_admin && ./mvnw -Dtest=AiObservabilityServiceTest test`

Expected before implementation: FAIL because classes do not exist.

- [x] **Step 2: Propagate Java correlation fields**

Add fields to `PythonChatRequest`:

```java
@JsonProperty("request_id")
private String requestId;
@JsonProperty("java_session_id")
private String javaSessionId;
@JsonProperty("user_id")
private Long userId;
private String username;
@JsonProperty("business_type")
private String businessType;
private String entrypoint;
```

Set them in `SSEProxyService.buildPythonRequest(...)` using local session id, user id, and entrypoint `interview_chat`.

- [x] **Step 3: Add admin read APIs**

Expose:

```text
GET /admin/ai-observability/traces
GET /admin/ai-observability/traces/{traceId}
GET /admin/ai-observability/llm-calls/{callId}/raw?type=PROMPT|RESPONSE
GET /admin/ai-observability/stats
```

Use existing `Result<T>` and `PageResult<T>` response wrappers.

- [x] **Step 4: Implement MyBatis SQL**

Add mapper methods for:

```text
countTraces
selectTraces
selectTraceById
selectTraceSteps
selectLlmCalls
selectLlmCallRawPayload
selectStats
insertAccessLog
```

Cache metrics SQL must use only rows where `cache_reported_by_provider = true` for denominators and expose unreported call count separately.

- [x] **Step 5: Run Java tests**

Run:

```bash
cd ai_interviewer_admin
./mvnw -Dtest=AiObservabilityServiceTest,AiObservabilitySchemaMigrationTest test
cd ../ai_interview_backend
./mvnw -pl ai-interviewer-interview -DskipTests compile
```

Expected: PASS.

- [x] **Step 6: Commit Java observability APIs**

```bash
git add ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/dto/PythonChatRequest.java ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/SSEProxyService.java ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/observability ai_interviewer_admin/src/main/resources/mapper/AiObservabilityMapper.xml ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/observability/AiObservabilityServiceTest.java
git commit -m "feat: expose ai observability admin APIs"
```

## Task 5: Admin Frontend Observability Views

**Files:**
- Modify: `ai_interviewer_admin_front/src/types.ts`
- Modify: `ai_interviewer_admin_front/src/api.ts`
- Modify: `ai_interviewer_admin_front/src/App.tsx`
- Modify: `ai_interviewer_admin_front/src/styles.css`
- Modify: `tests/e2e/playwright/tests/admin-web-smoke.spec.ts`

- [x] **Step 1: Add TypeScript contracts**

Add types:

```ts
export interface AiTraceRow {
  id: string;
  requestId?: string | null;
  userId?: number | null;
  username?: string | null;
  sessionId?: string | null;
  businessType: string;
  status: string;
  totalTokens?: number | null;
  llmCallCount?: number | null;
  providerPromptCacheTokenHitRate?: number | null;
  providerPromptCacheCallHitRate?: number | null;
  durationMs?: number | null;
  startedAt: string;
}

export interface AiObservabilityStats {
  totalTraces: number;
  totalLlmCalls: number;
  totalTokens: number;
  failedCalls: number;
  avgDurationMs?: number | null;
  providerPromptCacheTokenHitRate?: number | null;
  providerPromptCacheCallHitRate?: number | null;
  providerCacheUnreportedCalls: number;
}
```

- [x] **Step 2: Add API client methods**

Add `adminApi.aiTraces`, `adminApi.aiTraceDetail`, `adminApi.aiLlmRawPayload`, and `adminApi.aiObservabilityStats`.

- [x] **Step 3: Add UI route and menu**

Extend `ViewKey` with `aiObservability` and add menu label `AI 观测`.

The first screen must be the usable monitoring view: filters, stats, trace list, and detail drawer/panel. Do not add a marketing or explanatory landing page.

- [x] **Step 4: Build list/detail/stat views**

Use existing admin UI style. Show:

```text
trace id, session id, business type, status, model/provider summary, total tokens, duration, started time
provider cache token hit rate
provider cache call hit ratio
provider cache unreported calls
step timeline
LLM call list
raw prompt / raw response reveal buttons
```

Raw payload reveal must call the raw API only when clicked.

- [x] **Step 5: Add smoke coverage**

Extend admin smoke to confirm:

```text
AI 观测 menu exists
stats area renders
trace table renders empty state or rows
detail panel opens from a row when test data exists
```

- [x] **Step 6: Run frontend checks**

Run:

```bash
cd ai_interviewer_admin_front
npm run build
```

Expected: PASS.

- [x] **Step 7: Commit admin frontend observability views**

```bash
git add ai_interviewer_admin_front/src/types.ts ai_interviewer_admin_front/src/api.ts ai_interviewer_admin_front/src/App.tsx ai_interviewer_admin_front/src/styles.css tests/e2e/playwright/tests/admin-web-smoke.spec.ts
git commit -m "feat: add ai observability admin UI"
```

## Task 6: Cross-Service Verification And Documentation

**Files:**
- Modify: `tests/docs/test-cases.md`
- Modify: `tests/docs/tooling-guide.md`
- Create or modify: `tests/fixtures/payloads/ai-observability-chat.json`
- Create or modify: `tests/api/pytest/test_ai_observability_api.py`

- [ ] **Step 1: Add API smoke fixture**

Create a payload that starts an interview chat through the Java service and includes enough context to produce a Python trace.

- [ ] **Step 2: Add admin API smoke checks**

Add pytest or shell smoke checks that:

```text
calls an interview endpoint
queries /admin/ai-observability/traces
opens the newest trace detail
queries /admin/ai-observability/stats
opens raw prompt or response and verifies access log count increases
```

- [ ] **Step 3: Update test registry**

Document all new tests in `tests/docs/test-cases.md`, including:

```text
provider usage normalization
metadata capture before parser
admin trace list/detail
raw payload audit
provider cache token hit rate
provider cache call hit ratio
unreported provider cache calls
```

- [ ] **Step 4: Update tooling guide**

Document required environment variables:

```text
AI_OBSERVABILITY_ENABLED
AI_OBSERVABILITY_DB_URL
AI_OBSERVABILITY_WRITE_TIMEOUT_MS
AI_OBSERVABILITY_STORE_RAW_PAYLOAD
AI_OBSERVABILITY_MAX_RAW_CHARS
```

- [ ] **Step 5: Run full touched-module verification**

Run:

```bash
cd ai_interviewer && uv run pytest tests/test_observability_provider_usage.py tests/test_observable_langchain.py tests/test_interviewer_prompt_escaping.py tests/test_interview_technical_transition.py -q
cd ../ai_interviewer_admin && ./mvnw test
cd ../ai_interview_backend && ./mvnw -pl ai-interviewer-interview test
cd ../ai_interviewer_admin_front && npm run build
```

Expected: all commands PASS.

- [ ] **Step 6: Mark OpenSpec tasks complete**

After implementation and verification, update `openspec/changes/add-ai-observability-center/tasks.md` so completed items are checked.

- [ ] **Step 7: Commit cross-service verification and docs**

```bash
git add tests/docs/test-cases.md tests/docs/tooling-guide.md tests/fixtures/payloads/ai-observability-chat.json tests/api/pytest/test_ai_observability_api.py openspec/changes/add-ai-observability-center/tasks.md
git commit -m "test: verify ai observability flow"
```

## Self-Review

**Spec coverage:** The plan covers trace persistence, execution steps, provider usage capture, raw prompt/response retention, provider prompt-cache token hit rate, provider prompt-cache call hit ratio, admin queries, and no application-level LLM cache in the first release.

**Placeholder scan:** This plan names concrete files, commands, schema fields, API paths, metrics, and expected outcomes instead of leaving unresolved implementation markers.

**Type consistency:** Provider cache field names match the OpenSpec delta and design doc: `prompt_cache_hit_tokens`, `prompt_cache_miss_tokens`, `prompt_cache_hit_rate`, `cache_reported_by_provider`, `providerPromptCacheTokenHitRate`, `providerPromptCacheCallHitRate`, and `providerCacheUnreportedCalls`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-23-ai-observability-implementation.md`.

Comet build must pause at plan-ready before choosing isolation, execution mode, and TDD mode.
