## Why

当前 AI 面试流程横跨 Flutter、Java 面试服务、Python AI 服务和后台管理系统，但平台缺少对一次 LLM 回答背后执行过程的可观测能力。出现 token 消耗异常、模型回答异常、接口耗时偏高、厂商 prompt cache 未生效或兜底模型触发时，管理员无法在后台还原调用链路、定位问题并持续优化。

## What Changes

- 新增 AI 调用观测中心能力，记录一次用户对话或面试推进的 trace、业务 step、LLM call 三层数据。
- 记录每次 LLM 调用的模型、provider、调用类型、耗时、状态、错误、token usage、厂商返回的 prompt cache token 和完整 usage 原文。
- 支持保存完整 prompt 和完整 LLM response 原文，并通过后台详情页按权限查看。
- 后台管理系统新增观测查询、详情、统计分析和敏感原文访问审计。
- 统计 token 消耗、调用次数、失败率、平均耗时、厂商缓存 token 命中率、厂商缓存调用命中占比和未上报缓存字段的调用数。
- 明确当前项目第一版不引入应用层 LLM prompt-response 缓存，只统计大模型厂商响应里真实返回的缓存 usage。

## Capabilities

### New Capabilities

- `ai-observability`: AI/LLM 调用观测能力，覆盖 trace 采集、LLM usage 归一化、厂商缓存统计、后台查询和原文审计。

### Modified Capabilities

- None.

## Impact

- Python AI 服务新增观测写入模块、PostgreSQL 写入配置、LangChain/DeepSeek/OpenAI usage 提取逻辑和相关测试。
- Java 面试服务按需透传用户、会话、请求和业务上下文标识，便于 Python AI 服务关联 trace。
- Java 管理后台新增只读查询 API 和敏感原文访问审计。
- React 后台管理前端新增 AI 调用观测菜单、列表、详情和统计视图。
- PostgreSQL 新增 `t_ai_trace`、`t_ai_trace_step`、`t_ai_llm_call`、`t_ai_observability_access_log` 等表。
- 测试资产需要登记跨服务 smoke、API 和 provider usage 归一化用例。
