---
comet_change: add-ai-observability-center
role: technical-design
canonical_spec: openspec
---

# AI 调用观测中心设计文档

Generated on 2026-06-23

Status: Draft

## 1. 背景

当前平台的 AI 面试流程横跨 Flutter 前端、Java 面试服务、Python AI 服务和后台管理系统。候选人侧一次对话可能触发创建会话、生成开场白、生成项目问题、评估回答、生成追问、检索技术题、SSE 输出等多个步骤。

现有系统能完成面试流程，但缺少对 LLM 调用过程的持续观测能力。出现回答异常、评分不稳定、接口耗时过高、token 消耗异常或缓存未生效时，管理员无法从后台还原一次回答背后的完整执行链路。

本设计新增“AI 调用观测中心”，用于记录每次用户对话背后的业务步骤、LLM 调用明细、token 消耗、缓存命中、错误与兜底情况，并在后台管理系统中提供查询和分析入口。

## 2. 目标

第一版采用完整观测底座方案，目标同时覆盖调试、成本监控和后续质量优化。

1. 记录每次 LLM 调用的 prompt token、completion token、total token、模型、耗时、状态、错误和缓存命中情况。
2. 记录每次用户对话或面试推进的完整执行步骤，能从后台查看业务链路时间线。
3. 统计 token 消耗、调用次数、失败率、平均耗时、厂商 prompt cache 命中率和高消耗调用类型。
4. 支持保存完整 prompt 和完整 LLM response 原文，用于测试、排障和 prompt 优化。
5. 区分真实 token 和估算 token，避免后台成本数据看起来精确但实际不可用。
6. 观测写入失败不能影响候选人正常面试流程。

## 3. 已确认产品决策

### 3.1 第一版选择完整观测底座

第一版不只做 token 统计，而是建设 trace、step、llm call 三层数据。

```text
一次用户请求 / 面试推进 = ai_trace
  ├── 业务步骤 1 = ai_trace_step
  │     └── LLM 调用 A = ai_llm_call
  ├── 业务步骤 2 = ai_trace_step
  │     ├── LLM 调用 B = ai_llm_call
  │     └── LLM 调用 C = ai_llm_call
  └── SSE 输出 / 结果汇总 = ai_trace_step
```

原因：

1. 单独记录 token 只能看到成本，无法解释回答异常。
2. 单独记录业务流程无法定位具体哪次模型调用消耗高或失败。
3. 当前 Python AI 服务内有多个 `chain.invoke()`，不同阶段的调用语义不同，必须用 `call_type` 和 `trace_id` 关联起来。

### 3.2 保存完整 prompt 和完整 response 原文

第一版允许并默认保存完整 prompt 和完整 LLM response 原文。

原因：

1. 当前阶段重点是测试、修复 bug 和优化项目，完整原文对排查问题最有价值。
2. 只保存摘要会导致 prompt 拼接错误、上下文缺失、模型误解、JSON 输出异常等问题难以复现。
3. 后续做 prompt 版本对比、模型对比和评分稳定性分析时，完整样本是必要数据。

约束：

1. 原文保存必须只面向后台管理员开放。
2. 后台列表默认展示摘要，详情页才展示完整 prompt/response。
3. 详情页访问完整原文需要写入后台操作审计。
4. 数据库字段必须支持后续关闭原文保存或迁移到加密存储。
5. 文档、测试样例、日志和截图不得包含真实用户敏感原文。

## 4. 非目标

第一版不做以下内容：

1. 不引入 LangSmith、OpenTelemetry Collector 或完整 APM 平台。
2. 不做 token 成本金额精算，只统计 token 和预留价格字段。
3. 不做 Prompt A/B 实验平台。
4. 不做自动质量评分体系。
5. 不记录每个 SSE chunk，避免数据量膨胀。
6. 不建设复杂 Agent 工具调用树。当前系统以普通 LLM 调用和题库检索为主，工具调用先抽象为 trace step。

## 5. 当前系统落点

### 5.1 Python AI 服务

Python AI 服务是观测采集的主落点。

关键文件：

1. `ai_interviewer/api/interviewer.py`
   - 包含 `generate_opening`
   - 包含 `ask_self_introduction`
   - 包含 `generate_project_questions`
   - 包含 `evaluate_answer`
   - 包含 `generate_followup_question`
   - 包含 `select_technical_questions`
   - 包含 `conclude_interview`
   - 包含向后兼容的 `ask`

2. `ai_interviewer/services/interview_service.py`
   - 管理完整面试状态机
   - 适合记录业务步骤和阶段切换

3. `ai_interviewer/api/router.py`
   - `/interview/chat` 统一 SSE 对话入口
   - 适合创建 trace 并把 trace context 传入业务流程

4. `ai_interviewer/core/model_provider.py`
   - 统一创建 ChatOpenAI / fallback LLM
   - 适合补充模型配置元信息，但不建议把业务观测逻辑塞进这里

### 5.2 Java 面试服务

Java 面试服务目前主要是 SSE 代理和用户归属入口。

关键文件：

1. `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/controller/InterviewController.java`
   - `/interviews/chat`
   - 获取 `X-User-Id`
   - 转发请求到 Python AI 服务

2. `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/SSEProxyService.java`
   - 负责代理 SSE
   - 后续可透传 `trace_id` 或在请求头中带上用户信息

Java 面试服务不负责计算 token。token 的一手数据来自 Python AI 服务和 LLM 响应。

### 5.3 Java Admin 后台服务

Java Admin 后台负责查询和展示观测数据。

关键文件：

1. `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/dashboard/DashboardController.java`
2. `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/dashboard/DashboardService.java`
3. `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/interview/`
4. `ai_interviewer_admin/src/main/resources/mapper/`

建议新增 `aiobservability` 模块。

### 5.4 React Admin 前端

React Admin 前端新增一级菜单 `AI 监控`。

关键文件：

1. `ai_interviewer_admin_front/src/App.tsx`
2. `ai_interviewer_admin_front/src/api.ts`
3. `ai_interviewer_admin_front/src/types.ts`

## 6. 总体架构

```mermaid
flowchart LR
  Candidate["候选人"]
  Flutter["Flutter 前端"]
  JavaInterview["Java Interview 服务<br/>/interviews/chat"]
  PythonAI["Python AI 服务<br/>/interview/chat"]
  Observability["Python Observability 模块"]
  PG["PostgreSQL<br/>AI 观测表"]
  AdminAPI["Java Admin 服务<br/>/admin/ai-observability/**"]
  AdminWeb["React Admin<br/>AI 监控"]

  Candidate --> Flutter
  Flutter --> JavaInterview
  JavaInterview --> PythonAI
  PythonAI --> Observability
  Observability --> PG
  AdminWeb --> AdminAPI
  AdminAPI --> PG
```

### 6.1 数据写入边界

第一版采用“Python 采集并直接写 PostgreSQL，Java Admin 只读查询”的边界。

原因：

1. token、厂商 cache usage、prompt/response 原文都在 Python LLM 调用现场最完整。
2. 如果先经 Java 代理再落库，会增加 SSE 链路复杂度，也容易丢失 LangChain 原始 metadata。
3. Java Admin 的职责是后台查询、权限控制和审计，不参与候选人实时 AI 流程。

边界约束：

1. 观测表 migration 由仓库内正式 migration 管理，不能由 Python 运行时临时建表。
2. Python 只写入 `t_ai_trace`、`t_ai_trace_step`、`t_ai_llm_call`。
3. Java Admin 读取观测表，并写入 `t_ai_observability_access_log`。
4. Python 观测写库失败只能记录日志，不能影响面试主流程。
5. 后续如果要把写入收敛到 Java Admin API，可新增异步采集接口，但 v1 不引入这层跳转。

### 6.2 Python PostgreSQL 配置

Python AI 服务新增观测数据库配置，优先复用 Java 后端 PostgreSQL。

建议环境变量：

```text
AI_OBSERVABILITY_ENABLED=true
AI_OBSERVABILITY_DB_URL=postgresql+psycopg://postgres:postgres@localhost:5433/ai_interviewer
AI_OBSERVABILITY_WRITE_TIMEOUT_MS=300
AI_OBSERVABILITY_STORE_RAW_PAYLOAD=true
AI_OBSERVABILITY_MAX_RAW_CHARS=200000
```

说明：

1. `AI_OBSERVABILITY_ENABLED=false` 时不采集观测数据。
2. `AI_OBSERVABILITY_WRITE_TIMEOUT_MS` 用于限制观测写入阻塞时间。
3. `AI_OBSERVABILITY_STORE_RAW_PAYLOAD=true` 是本次已确认决策，默认保存完整 prompt/response。
4. `AI_OBSERVABILITY_MAX_RAW_CHARS` 防止极端 prompt 或 response 造成单行数据异常膨胀。超过上限时截断并在 `metadata_json` 或 `raw_usage_json` 附近记录 `raw_truncated=true`。

## 7. 数据模型

### 7.1 `t_ai_trace`

一条用户请求或一次面试推进对应一条 trace。

```sql
CREATE TABLE IF NOT EXISTS t_ai_trace (
  id BIGSERIAL PRIMARY KEY,
  trace_id VARCHAR(64) NOT NULL UNIQUE,
  session_id VARCHAR(128),
  user_id BIGINT,
  source_service VARCHAR(64) NOT NULL,
  entrypoint VARCHAR(128) NOT NULL,
  stage_before VARCHAR(64),
  stage_after VARCHAR(64),
  request_summary TEXT,
  response_summary TEXT,
  status VARCHAR(32) NOT NULL,
  started_at TIMESTAMP NOT NULL,
  ended_at TIMESTAMP,
  duration_ms BIGINT,
  error_message TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

索引：

```sql
CREATE INDEX IF NOT EXISTS idx_ai_trace_session_id ON t_ai_trace(session_id);
CREATE INDEX IF NOT EXISTS idx_ai_trace_user_id ON t_ai_trace(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_trace_started_at ON t_ai_trace(started_at);
CREATE INDEX IF NOT EXISTS idx_ai_trace_status ON t_ai_trace(status);
```

### 7.2 `t_ai_trace_step`

记录业务流程步骤。

```sql
CREATE TABLE IF NOT EXISTS t_ai_trace_step (
  id BIGSERIAL PRIMARY KEY,
  trace_id VARCHAR(64) NOT NULL,
  step_name VARCHAR(128) NOT NULL,
  step_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  started_at TIMESTAMP NOT NULL,
  ended_at TIMESTAMP,
  duration_ms BIGINT,
  input_summary TEXT,
  output_summary TEXT,
  metadata_json JSONB,
  error_message TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

索引：

```sql
CREATE INDEX IF NOT EXISTS idx_ai_trace_step_trace_id ON t_ai_trace_step(trace_id);
CREATE INDEX IF NOT EXISTS idx_ai_trace_step_step_name ON t_ai_trace_step(step_name);
CREATE INDEX IF NOT EXISTS idx_ai_trace_step_status ON t_ai_trace_step(status);
```

典型 `step_name`：

```text
create_session
generate_opening
handle_opening_response
ask_self_introduction
handle_self_introduction
generate_project_questions
evaluate_project_answer
generate_followup_question
initialize_technical_questions
select_technical_questions
evaluate_technical_answer
conclude_interview
stream_sse_response
save_session
question_bank_search
```

### 7.3 `t_ai_llm_call`

每一次真实或缓存返回的 LLM 调用对应一条记录。

```sql
CREATE TABLE IF NOT EXISTS t_ai_llm_call (
  id BIGSERIAL PRIMARY KEY,
  trace_id VARCHAR(64) NOT NULL,
  step_id BIGINT,
  session_id VARCHAR(128),
  user_id BIGINT,
  call_type VARCHAR(128) NOT NULL,
  provider VARCHAR(64),
  model VARCHAR(128),
  fallback_model VARCHAR(128),
  prompt_tokens BIGINT,
  completion_tokens BIGINT,
  total_tokens BIGINT,
  estimated_tokens BOOLEAN NOT NULL DEFAULT FALSE,
  prompt_cache_hit_tokens BIGINT,
  prompt_cache_miss_tokens BIGINT,
  prompt_cache_hit_rate NUMERIC(8, 6),
  duration_ms BIGINT,
  cache_hit BOOLEAN NOT NULL DEFAULT FALSE,
  cache_type VARCHAR(64) NOT NULL DEFAULT 'NONE',
  cache_key VARCHAR(256),
  cache_reported_by_provider BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(32) NOT NULL,
  prompt_summary TEXT,
  response_summary TEXT,
  prompt_text TEXT,
  response_text TEXT,
  prompt_hash VARCHAR(128),
  response_hash VARCHAR(128),
  raw_usage_json JSONB,
  error_message TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

索引：

```sql
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_trace_id ON t_ai_llm_call(trace_id);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_session_id ON t_ai_llm_call(session_id);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_user_id ON t_ai_llm_call(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_call_type ON t_ai_llm_call(call_type);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_model ON t_ai_llm_call(model);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_created_at ON t_ai_llm_call(created_at);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_status ON t_ai_llm_call(status);
CREATE INDEX IF NOT EXISTS idx_ai_llm_call_cache_hit ON t_ai_llm_call(cache_hit);
```

典型 `call_type`：

```text
generate_opening
ask_self_introduction
generate_project_questions
evaluate_answer
generate_followup_question
select_technical_questions
conclude_interview
ask_compat
```

### 7.4 `t_ai_observability_access_log`

记录后台管理员查看完整 prompt/response 原文的行为。

```sql
CREATE TABLE IF NOT EXISTS t_ai_observability_access_log (
  id BIGSERIAL PRIMARY KEY,
  admin_id BIGINT,
  admin_username VARCHAR(128),
  target_type VARCHAR(64) NOT NULL,
  target_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  reason TEXT,
  ip_address VARCHAR(64),
  user_agent TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

使用场景：

1. 管理员打开 LLM 调用详情页并展开完整 prompt。
2. 管理员打开完整 response。
3. 管理员复制原文。

第一版可以先记录“查看详情页”行为，复制事件可后续补充。

## 8. token 与厂商缓存统计策略

token 来源按优先级处理。

### 8.0 metadata 采集位置

当前 Python 代码大量使用：

```python
chain = prompt | self.llm | StrOutputParser()
response = chain.invoke(...)
```

第一版实现时不能只在这个最终 `response` 字符串上采集。`StrOutputParser()` 会把模型返回的 message 对象转换为字符串，容易丢失 `usage_metadata`、`response_metadata`、厂商原始 usage 和 tool call 信息。

正确采集顺序：

```text
1. 使用 ChatPromptTemplate 渲染 messages
2. 调用 llm.invoke(messages) 拿到 AIMessage
3. 从 AIMessage.usage_metadata / response_metadata / additional_kwargs 提取 token 和厂商 cache 字段
4. 从 AIMessage.content 得到 response_text
5. 如业务需要，再交给 StrOutputParser 或 JSON 解析逻辑
6. 写入 t_ai_llm_call
```

这是 v1 的实现硬约束。否则 DeepSeek 的 `prompt_cache_hit_tokens`、OpenAI/Azure 的 `cached_tokens` 很可能在 parser 后丢失，后台无法统计厂商缓存。

### 8.1 模型响应 usage metadata

优先读取 LangChain 返回对象里的 usage 信息，例如：

1. `AIMessage.usage_metadata`
2. `AIMessage.response_metadata.token_usage`
3. OpenAI/Azure/DeepSeek 兼容响应中的 usage 字段

如果可以拿到：

```text
prompt_tokens
completion_tokens
total_tokens
```

则写入真实 token，并设置：

```text
estimated_tokens = false
```

如果 usage 中包含厂商侧 prompt/context cache 字段，必须同步解析并写入：

```text
prompt_cache_hit_tokens
prompt_cache_miss_tokens
prompt_cache_hit_rate
cache_hit
cache_type
cache_reported_by_provider
raw_usage_json
```

DeepSeek 场景优先读取：

```text
usage.prompt_cache_hit_tokens
usage.prompt_cache_miss_tokens
```

OpenAI / Azure OpenAI 场景优先读取：

```text
usage.prompt_tokens_details.cached_tokens
```

如果只拿到 OpenAI / Azure 的 `cached_tokens`，则：

```text
prompt_cache_hit_tokens = cached_tokens
prompt_cache_miss_tokens = prompt_tokens - cached_tokens
```

如果厂商没有返回缓存字段，不能用应用层推断伪造命中率，只能设置：

```text
cache_reported_by_provider = false
cache_type = NONE
```

### 8.1.1 Provider usage 归一化

不同厂商字段不同，采集模块需要把原始 usage 归一化为统一结构。

统一输出：

```text
prompt_tokens
completion_tokens
total_tokens
prompt_cache_hit_tokens
prompt_cache_miss_tokens
cache_reported_by_provider
raw_usage_json
```

DeepSeek 映射：

```text
prompt_cache_hit_tokens = usage.prompt_cache_hit_tokens
prompt_cache_miss_tokens = usage.prompt_cache_miss_tokens
prompt_tokens = usage.prompt_tokens 或 prompt_cache_hit_tokens + prompt_cache_miss_tokens
completion_tokens = usage.completion_tokens
total_tokens = usage.total_tokens 或 prompt_tokens + completion_tokens
```

OpenAI / Azure OpenAI 映射：

```text
prompt_cache_hit_tokens = usage.prompt_tokens_details.cached_tokens
prompt_cache_miss_tokens = usage.prompt_tokens - usage.prompt_tokens_details.cached_tokens
prompt_tokens = usage.prompt_tokens
completion_tokens = usage.completion_tokens
total_tokens = usage.total_tokens
```

通用 fallback：

```text
如果只有 prompt_tokens/completion_tokens/total_tokens，则记录 token，但 cache_reported_by_provider=false。
如果 token 和 cache 字段都缺失，则按本地估算逻辑处理，并保留 raw_usage_json。
```

### 8.2 callback 汇总

如果当前 LangChain 版本支持 usage callback，可在统一 LLM 调用包装器中启用 callback 采集。

### 8.3 本地估算

如果模型或兼容网关不返回 usage，则使用本地估算。

要求：

1. 估算值必须设置 `estimated_tokens = true`。
2. 后台统计中必须能筛选“只看真实 token”或“包含估算 token”。
3. 成本金额计算如果以后加入，默认只能基于真实 token，估算 token 必须单独标识。

## 9. cache 统计口径

本项目第一版重点统计“大模型厂商侧 prompt/context cache”，不是应用自己实现的 LLM response cache。

当前项目已经有 Redis 基础设施和少量业务配置，但从代码现状看，还没有专门的应用层 LLM cache 架构，例如 LangChain LLM cache、Redis prompt-response cache、semantic cache 或自建模型响应缓存。当前 Python 侧更接近：

1. 面试会话使用内存状态和数据库恢复。
2. 问题库使用 Chroma 持久化向量库。
3. Java 侧 Redis 主要用于认证、限流或未来业务缓存配置。

因此 v1 统计时必须把“厂商侧 prompt cache”和“我们应用内缓存”分开。

第一版区分以下 cache 类型：

```text
NONE
PROVIDER_PROMPT_CACHE
APP_LLM_RESPONSE_CACHE
BUSINESS
VECTOR_SEARCH
```

### 9.1 厂商侧 prompt/context cache

含义：请求仍然发送给大模型厂商，但厂商在服务端复用了相同 prompt 前缀的 KV/context cache，并在 usage 中返回命中 token。

这是本项目第一版最关心的 cache 指标。

记录要求：

1. `cache_reported_by_provider = true`
2. `prompt_cache_hit_tokens > 0` 时设置 `cache_hit = true`
3. `cache_type = PROVIDER_PROMPT_CACHE`
4. `prompt_cache_hit_rate = prompt_cache_hit_tokens / prompt_tokens`
5. `raw_usage_json` 保存厂商原始 usage，便于后续兼容不同字段
6. `cache_key` 通常无法从厂商获得，保持为空；不要伪造 cache key

统计口径：

```text
厂商缓存 token 命中率 = sum(prompt_cache_hit_tokens) / sum(prompt_cache_hit_tokens + prompt_cache_miss_tokens)
厂商缓存调用命中占比 = count(prompt_cache_hit_tokens > 0) / count(cache_reported_by_provider = true)
```

这两个指标都要展示。前者表示 token 维度命中率，后者表示调用次数维度命中率。

#### 9.1.1 厂商缓存 token 命中率

定义：

```text
provider_prompt_cache_token_hit_rate =
  sum(prompt_cache_hit_tokens) /
  sum(prompt_cache_hit_tokens + prompt_cache_miss_tokens)
```

用途：

1. 衡量输入 token 中有多少比例被厂商侧 prompt/context cache 复用。
2. 适合评估长系统提示词、题库上下文、简历上下文和重复对话前缀是否真正带来缓存收益。
3. 后续如果加入成本金额统计，该指标可用于估算厂商缓存节省的输入 token 成本。

统计规则：

1. 只统计 `cache_reported_by_provider = true` 的 LLM 调用。
2. 如果厂商返回 `prompt_cache_hit_tokens` 和 `prompt_cache_miss_tokens`，优先使用二者相加作为输入 token 分母。
3. 如果厂商只返回 `prompt_tokens` 和 `cached_tokens`，则：

```text
prompt_cache_hit_tokens = cached_tokens
prompt_cache_miss_tokens = prompt_tokens - cached_tokens
```

4. 如果厂商没有返回 cache 字段，该调用不纳入厂商缓存命中率分母，后台应单独展示“未报告厂商缓存字段调用数”。
5. 如果分母为 0，返回 `null`，不要显示为 `0%`。

后台字段命名：

```text
providerPromptCacheHitTokens
providerPromptCacheMissTokens
providerPromptCacheTokenHitRate
providerCacheReportedCalls
providerCacheUnreportedCalls
```

#### 9.1.2 厂商缓存调用命中占比

定义：

```text
provider_prompt_cache_call_hit_rate =
  count(prompt_cache_hit_tokens > 0) /
  count(cache_reported_by_provider = true)
```

用途：

1. 衡量多少次 LLM 调用发生过厂商侧缓存命中。
2. 适合排查调用模式是否稳定复用相同 prompt 前缀。
3. 与 token 命中率一起看，避免误判。例如少数超长调用命中会让 token 命中率很高，但调用命中占比可能很低。

统计规则：

1. 只统计厂商明确返回 cache 字段的调用。
2. `prompt_cache_hit_tokens > 0` 算命中调用。
3. `prompt_cache_hit_tokens = 0` 且厂商返回了 cache 字段，算未命中调用。
4. 厂商未返回 cache 字段的调用不纳入分母，单独计入 `providerCacheUnreportedCalls`。
5. 如果分母为 0，返回 `null`，不要显示为 `0%`。

后台字段命名：

```text
providerPromptCacheHitCalls
providerPromptCacheMissCalls
providerPromptCacheCallHitRate
```

### 9.2 应用层 LLM response cache

含义：应用自己缓存完整 LLM response，同 prompt、同模型、同参数时不再请求厂商。

当前项目没有这类缓存架构。第一版不主动实现它，只预留枚举：

```text
APP_LLM_RESPONSE_CACHE
```

如果后续实现，记录要求：

1. `cache_hit = true`
2. `cache_type = APP_LLM_RESPONSE_CACHE`
3. `cache_key` 写入应用生成的稳定 hash
4. 该类命中不会调用厂商，因此不应计入厂商 prompt cache 命中率
5. 可以单独统计“应用缓存节省的潜在 token”

### 9.3 Business cache

含义：业务流程复用已有结果，例如项目问题池已经生成过，本轮不需要重新调用 LLM。

记录方式：

1. 记录为 `t_ai_trace_step`
2. `metadata_json.cache_type = BUSINESS`
3. 不写入 `t_ai_llm_call`，因为它不是一次模型调用
4. 不计入厂商 prompt cache 命中率

### 9.4 Vector search cache

含义：题库检索或向量检索命中缓存。

第一版只预留 step 记录，不强制实现。

## 10. Python 采集设计

### 10.1 新增模块结构

建议新增：

```text
ai_interviewer/services/observability/
├── __init__.py
├── context.py
├── models.py
├── recorder.py
├── llm.py
├── token_usage.py
└── summarizer.py
```

职责：

1. `context.py`：维护 `trace_id`、`session_id`、`user_id`、当前 step。
2. `models.py`：定义 trace、step、llm call 的 Python 数据结构。
3. `recorder.py`：负责数据库写入，失败时只记录日志，不抛出到主流程。
4. `llm.py`：统一包装 LLM 调用。
5. `token_usage.py`：从 LangChain response/callback 中提取 token。
6. `summarizer.py`：生成 prompt/response 摘要。

### 10.2 统一 LLM 调用包装器

所有 `chain.invoke()` 逐步替换为统一包装器。

伪代码：

```python
result = invoke_llm_with_trace(
    call_type="evaluate_answer",
    prompt_template=prompt,
    llm=self.llm,
    parser=StrOutputParser(),
    variables={"context": context},
    session_id=session.session_id,
)
```

包装器负责：

1. 记录开始时间。
2. 使用 `prompt_template.invoke(variables)` 渲染 messages。
3. 序列化并保存完整 prompt 原文。
4. 调用 `llm.invoke(messages)`，先拿到原始 `AIMessage`。
5. 在 parser 前提取 usage、厂商 cache 字段、模型名、response metadata。
6. 从 `AIMessage.content` 取得完整 response 原文。
7. 如传入 parser，再对 `AIMessage.content` 做文本或 JSON 解析。
8. 保存摘要、hash、模型名、耗时、状态。
9. 捕获异常并写入失败记录。
10. 重新抛出异常给原业务逻辑，让现有兜底流程继续生效。

禁止实现为：

```python
chain = prompt | llm | StrOutputParser()
text = chain.invoke(...)
record_usage(text)
```

原因：这个写法只能拿到文本，拿不到稳定的厂商 usage/cache metadata。

### 10.2.1 fallback 模型记录

当前 `build_chat_llm()` 可能返回 `primary_llm.with_fallbacks([backup_llm])`。观测模块需要尽量记录实际生效模型。

要求：

1. `model` 记录本次响应 metadata 中实际返回的模型名，若取不到则记录配置中的 primary model。
2. `fallback_model` 记录备用模型配置。
3. 如果能从异常或 response metadata 判断本次使用了 fallback，在 `metadata_json` 中记录 `used_fallback=true`。
4. 如果无法可靠判断，不要猜测，只保留 `fallback_model` 配置值。

### 10.3 trace 创建位置

`ai_interviewer/api/router.py` 的 `/interview/chat` 是统一 SSE 入口，适合创建 trace。

要求：

1. 每次请求创建一个 `trace_id`。
2. 如果 Java 面试服务传入 `X-Trace-Id`，优先沿用。
3. trace 记录 `session_id`、`stage_before`、`entrypoint`。
4. 请求结束时更新 `stage_after`、`duration_ms`、`status`。

### 10.4 用户 ID 传递

Java 面试服务已有 `X-User-Id`。

需要在 Java 到 Python 的 SSE 代理请求中透传：

```text
X-User-Id
X-Trace-Id
```

Python AI 服务读取后写入观测表。

## 11. Admin API 设计

Java Admin 服务新增模块：

```text
ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/aiobservability/
```

建议接口：

```text
GET /admin/ai-observability/overview
GET /admin/ai-observability/traces
GET /admin/ai-observability/traces/{traceId}
GET /admin/ai-observability/llm-calls
GET /admin/ai-observability/llm-calls/{id}
GET /admin/ai-observability/errors
POST /admin/ai-observability/access-log
```

### 11.1 总览接口

`GET /admin/ai-observability/overview`

查询参数：

```text
startTime
endTime
includeEstimatedTokens
```

返回：

```json
{
  "totalCalls": 120,
  "successCalls": 116,
  "failedCalls": 4,
  "totalTokens": 352000,
  "promptTokens": 270000,
  "completionTokens": 82000,
  "providerPromptCacheHitTokens": 120000,
  "providerPromptCacheMissTokens": 150000,
  "providerPromptCacheTokenHitRate": 0.4444,
  "providerPromptCacheHitCalls": 42,
  "providerPromptCacheMissCalls": 78,
  "providerPromptCacheCallHitRate": 0.35,
  "providerCacheReportedCalls": 120,
  "providerCacheUnreportedCalls": 0,
  "estimatedTokenCalls": 8,
  "averageDurationMs": 1830,
  "topCallTypes": [],
  "topModels": [],
  "recentErrors": []
}
```

### 11.2 trace 列表

`GET /admin/ai-observability/traces`

筛选：

```text
current
size
sessionId
userId
status
entrypoint
startTime
endTime
```

### 11.3 trace 详情

`GET /admin/ai-observability/traces/{traceId}`

返回：

1. trace 基本信息。
2. step 时间线。
3. 每个 step 关联的 LLM call。
4. token 汇总。
5. 错误和兜底信息。

### 11.4 LLM 调用列表

`GET /admin/ai-observability/llm-calls`

筛选：

```text
current
size
traceId
sessionId
userId
callType
model
status
cacheHit
cacheType
providerCacheReported
estimatedTokens
startTime
endTime
```

列表默认不返回完整 `prompt_text` 和 `response_text`。

### 11.5 LLM 调用详情

`GET /admin/ai-observability/llm-calls/{id}`

返回完整详情，包括完整 prompt 和完整 response。

安全要求：

1. 只有拥有 `AI_OBSERVABILITY_DETAIL` 权限的管理员可访问。
2. 每次访问详情写入 `t_ai_observability_access_log`。
3. 返回数据时可在前端加二次确认提示，提醒包含候选人隐私信息。

## 12. React Admin 页面设计

新增一级菜单：

```text
AI 监控
```

页面包含四个视角。

### 12.1 总览

卡片：

1. 今日 LLM 调用数。
2. 今日 token。
3. 平均耗时。
4. 失败率。
5. 厂商 prompt cache token 命中率。
6. 估算 token 调用数。
7. 厂商 prompt cache 命中调用占比。

图表：

1. 近 7/30 天 token 趋势。
2. 按调用类型分布。
3. 按模型分布。
4. 厂商 prompt cache 命中 token 趋势。

### 12.2 调用明细

表格字段：

```text
时间
会话 ID
用户 ID
调用类型
模型
prompt tokens
prompt cache hit tokens
prompt cache miss tokens
completion tokens
total tokens
耗时
cache 类型
厂商是否返回 cache 字段
状态
是否估算
错误摘要
操作
```

操作：

1. 查看详情。
2. 跳转 trace。

### 12.3 会话链路

按 trace 展示时间线：

```text
用户提交回答
  -> handle_project_answer
  -> evaluate_answer
  -> generate_followup_question
  -> save_session
  -> stream_sse_response
```

每个节点展示：

1. 状态。
2. 耗时。
3. 关联 LLM 调用。
4. 错误信息。
5. 输入输出摘要。

### 12.4 错误与异常

聚合展示：

1. 模型调用失败。
2. JSON 解析失败。
3. token usage 缺失。
4. fallback 模型启用。
5. 业务兜底问题启用。
6. 观测写入失败。

## 13. 安全与隐私

因为第一版保存完整 prompt 和完整 LLM response，必须把安全边界写进实现。

### 13.1 权限

建议新增后台权限：

```text
AI_OBSERVABILITY_VIEW
AI_OBSERVABILITY_DETAIL
AI_OBSERVABILITY_EXPORT
```

第一版可以先实现：

1. `AI_OBSERVABILITY_VIEW`：查看总览、列表、摘要和 trace 时间线。
2. `AI_OBSERVABILITY_DETAIL`：查看完整 prompt 和完整 response。

`AI_OBSERVABILITY_EXPORT` 预留，第一版不开放导出。

### 13.2 审计

以下行为写入审计：

1. 查看 LLM 调用详情。
2. 查看完整 prompt。
3. 查看完整 response。
4. 后续如果支持导出，导出必须审计。

### 13.3 保留周期

建议配置：

```text
AI_OBSERVABILITY_RETENTION_DAYS=90
AI_OBSERVABILITY_RAW_RETENTION_DAYS=30
```

含义：

1. trace、step、token、状态等观测摘要默认保留 90 天。
2. 完整 prompt/response 原文默认保留 30 天。
3. 第一版可以先只实现配置和文档，不做自动清理任务。
4. 第二版加入定时清理。

### 13.4 前端展示

列表页不展示完整原文。

详情页展示完整原文时：

1. 默认折叠。
2. 展开前提示“内容可能包含候选人简历、回答和职位要求”。
3. 展开动作触发访问审计。

## 14. 失败处理

观测系统不能影响主流程。

要求：

1. 写入观测表失败时，主流程继续执行。
2. LLM 调用失败时，必须记录失败调用，然后把异常交还现有业务兜底逻辑。
3. token 解析失败时，记录 `estimated_tokens = true` 或 token 字段为空，并写入 metadata。
4. 后台查询失败不影响候选人侧服务。

## 15. 测试策略

测试资产按项目约定登记到 `tests/docs/test-cases.md`。

### 15.1 Python 单元测试

覆盖：

1. `invoke_llm_with_trace` 成功记录完整 prompt/response。
2. usage metadata 能正确解析 token。
3. usage 缺失时能标记估算 token。
4. LLM 调用异常时能记录失败并重新抛出。
5. 观测写库失败不影响主流程。
6. DeepSeek usage 能解析 `prompt_cache_hit_tokens` 和 `prompt_cache_miss_tokens`。
7. OpenAI/Azure usage 能解析 `prompt_tokens_details.cached_tokens`。
8. 厂商未返回 cache 字段时，`cache_reported_by_provider=false` 且不计入厂商缓存命中率分母。
9. parser 前采集 metadata，parser 后返回业务所需文本。

### 15.2 Python 流程测试

覆盖：

1. `/interview/chat` 新建会话生成 trace。
2. 自我介绍进入项目问题时记录 `generate_project_questions`。
3. 项目回答评分时记录 `evaluate_answer`。
4. 追问触发时记录 `generate_followup_question`。
5. 技术题初始化时记录题库检索 step。

### 15.3 Java Admin 测试

覆盖：

1. overview 聚合统计。
2. trace 分页筛选。
3. trace 详情返回 step 和 llm call。
4. llm call 列表默认不返回完整原文。
5. llm call 详情返回完整原文并写访问审计。
6. overview 正确返回厂商缓存 token 命中率和调用命中占比。
7. 厂商未报告 cache 字段的调用单独计入 `providerCacheUnreportedCalls`。

### 15.4 React Admin 验证

覆盖：

1. `AI 监控` 菜单可进入。
2. 总览卡片显示数据。
3. 调用明细支持筛选。
4. trace 详情能展示时间线。
5. 完整 prompt/response 默认折叠，展开后能查看。

## 16. 实施顺序

### 阶段 1：Python 观测底座

1. 新增 `services/observability` 模块。
2. 新增 trace context。
3. 新增 recorder。
4. 新增 LLM 调用包装器。
5. 改造 `Interviewer` 中的 LLM 调用。
6. 改造 `InterviewService` 的关键流程 step。

### 阶段 2：数据库表

1. 新增观测表 migration。
2. 明确 PostgreSQL 为观测数据主存储。
3. 由正式 migration 创建 `t_ai_trace`、`t_ai_trace_step`、`t_ai_llm_call`、`t_ai_observability_access_log`。
4. Python 通过 `AI_OBSERVABILITY_DB_URL` 直接写入同一 PostgreSQL。

第一版不使用“Python 调 Java Admin API 再落库”的链路，减少 SSE 路径复杂度和 metadata 丢失风险。

### 阶段 3：Java SSE 透传上下文

1. Java Interview 服务生成或透传 `X-Trace-Id`。
2. 透传 `X-User-Id` 到 Python AI 服务。
3. Python trace 记录 user/session 维度。

### 阶段 4：Java Admin 查询接口

1. 新增 `aiobservability` 模块。
2. 实现 overview。
3. 实现 trace 列表和详情。
4. 实现 llm call 列表和详情。
5. 实现访问审计。

### 阶段 5：React Admin 页面

1. 新增 `AI 监控` 菜单。
2. 新增总览视图。
3. 新增 LLM 调用明细。
4. 新增 trace 时间线详情。
5. 新增原文查看折叠区和提示。

### 阶段 6：文档与 smoke

1. 更新 `tests/docs/test-cases.md`。
2. 更新 `tests/docs/tooling-guide.md`。
3. 执行一次完整面试 smoke。
4. 验证后台能看到 trace、step、llm call、完整 prompt、完整 response。
5. 用构造的 DeepSeek/OpenAI usage fixture 验证厂商缓存 token 命中率和调用命中占比。

## 17. 验收标准

1. 后台能看到每次 LLM 调用的 token 消耗。
2. 后台能按会话查看完整 AI 执行时间线。
3. 后台能查看完整 prompt 和完整 LLM response 原文。
4. 查看完整原文会写入访问审计。
5. 后台能区分真实 token 和估算 token。
6. 后台能统计厂商侧 prompt/context cache 的命中 token、未命中 token、token 命中率和调用命中占比。
7. 后台能区分 `NONE`、`PROVIDER_PROMPT_CACHE`、`APP_LLM_RESPONSE_CACHE`、`BUSINESS`、`VECTOR_SEARCH` cache 类型。
8. 任意一次回答异常时，可以定位失败发生在哪个 step 或哪次 LLM 调用。
9. 观测写入失败不会中断候选人面试。
10. 列表页默认不直接暴露完整 prompt/response。
11. smoke 流程覆盖新建会话、项目问答、评分、追问、技术题初始化。

## 18. 后续扩展

1. token 成本金额统计。
2. 模型价格表配置。
3. prompt 版本管理。
4. prompt/model A/B 对比。
5. 自动异常聚类。
6. 质量评估报表。
7. 原文自动脱敏或加密存储。
8. 定时清理过期原文。
9. 接入 OpenTelemetry 或 LangSmith。
10. 导出受控样本用于离线评测。
