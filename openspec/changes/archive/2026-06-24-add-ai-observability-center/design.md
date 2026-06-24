## Context

AI 面试回答由 Java 服务触发，核心 LLM 调用发生在 Python AI 服务中，后台管理系统目前没有统一的调用观测入口。现有 Redis 主要承担认证、限流和会话类缓存，ChromaDB 用于向量检索，项目内没有已成体系的应用层 LLM prompt-response 缓存。

长版技术设计文档位于 `docs/superpowers/specs/2026-06-23-ai-observability-design.md`，本文仅保留 OpenSpec change 的高层架构决策摘要。

## Goals / Non-Goals

**Goals:**

- 在不影响候选人面试主流程的前提下，记录 AI 回答过程的 trace、step 和 LLM call。
- 使用厂商真实返回的 usage 统计 prompt/completion/total token，并区分真实 token 和估算 token。
- 从 DeepSeek `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens`、OpenAI/Azure `cached_tokens` 等字段归一化厂商 prompt cache 命中数据。
- 在后台管理系统提供查询、详情、趋势统计、厂商缓存统计和敏感原文审计。
- 第一版保存完整 prompt 和完整 LLM response 原文，支持后续通过配置关闭或迁移到加密存储。

**Non-Goals:**

- 第一版不建设应用层 LLM prompt-response 缓存。
- 第一版不做 token 成本金额精算，只保留价格字段扩展点。
- 第一版不引入 LangSmith、OpenTelemetry Collector 或完整 APM 平台。
- 第一版不记录每个 SSE chunk，也不建设复杂 Agent 工具调用树。

## Decisions

1. **Python AI 服务直接写入 PostgreSQL 观测表。**
   - Rationale: LLM 调用、prompt、response 和 provider usage 都在 Python 服务内最完整，直接写入可以避免跨服务丢失 metadata。
   - Alternative: 通过 Java 面试服务转发观测事件。该方案增加链路和序列化成本，且 Java 侧拿不到完整 provider usage。

2. **Java 管理后台只读查询观测数据，敏感原文访问写审计日志。**
   - Rationale: 后台管理天然适合做权限控制、分页查询和审计；写入路径保持在 Python 侧可降低耦合。
   - Alternative: Python 服务直接提供管理查询 API。该方案会把管理权限和后台鉴权能力扩散到 AI 服务。

3. **三层数据模型：`trace`、`trace_step`、`llm_call`。**
   - Rationale: token 统计只能解释成本，业务 step 才能解释一次回答的执行过程；LLM call 则承载厂商 usage 和原文。
   - Alternative: 只建单表记录 LLM 调用。该方案无法表达同一次回答内多个业务步骤和多个模型调用的关系。

4. **厂商缓存统计只基于 provider usage，不把应用层缓存混入同一指标。**
   - Rationale: 用户关心的是调用大模型厂商时命中的 prompt/context cache。项目当前没有应用层 LLM 缓存，混算会误导成本判断。
   - Alternative: 先做应用层缓存后统一统计。该方案会扩大第一版范围，也无法回答厂商缓存是否生效。

5. **LangChain metadata 必须在 `StrOutputParser` 之前采集。**
   - Rationale: parser 后通常只剩字符串，`AIMessage.response_metadata` / `usage_metadata` 会丢失，无法可靠统计 provider usage。
   - Alternative: 在最终字符串响应处补估算 token。该方案只能作为 fallback，不能替代厂商真实 usage。

## Risks / Trade-offs

- [Risk] 保存完整 prompt/response 会引入敏感数据风险 → 通过管理员权限、详情页懒加载、访问审计、配置开关和后续加密扩展缓解。
- [Risk] 观测写入失败影响面试主流程 → Python 写入采用 best-effort，失败只记录日志和降级状态，不阻塞业务响应。
- [Risk] 不同厂商 usage 字段不一致 → 使用 provider-specific normalizer，并把原始 usage 存入 `raw_usage_json` 便于追溯。
- [Risk] 部分厂商或调用路径不返回缓存字段 → 统计时仅纳入 `cache_reported_by_provider=true` 的调用，并展示未上报调用数。
- [Risk] 观测表数据增长快 → 第一版先提供索引和时间范围查询，后续按实际数据量追加归档或分区策略。

## Migration Plan

1. 新增 PostgreSQL 表结构和索引，默认不影响现有业务表。
2. Python AI 服务增加观测配置，默认允许通过 `AI_OBSERVABILITY_ENABLED` 控制写入开关。
3. 在核心 LLM 调用路径接入 trace/step/llm call 写入。
4. Java 管理后台新增只读查询和敏感原文访问审计 API。
5. React 后台新增 AI 调用观测页面。
6. 完成 provider usage、缓存指标、API、页面和 smoke 测试。

Rollback strategy: 关闭 `AI_OBSERVABILITY_ENABLED` 可停止新观测写入；后台菜单可隐藏；新增表不影响既有业务读写。

## Open Questions

- 第一版是否需要对 prompt/response 原文字段启用数据库层加密，还是先保留配置开关和审计日志。
- 观测数据默认保留周期是否需要在第一版落地为清理任务，还是作为上线后运营配置补充。
