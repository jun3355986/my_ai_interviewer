# FDE Day 3 — 持久化面试链路基线

日期：2026-08-14

## 目标

跑通真正的持久化（durable）链路，而不是走遗留的 `/interviews/chat` 兼容接口：

```text
Flutter 持久化 start/tail 请求
  -> Gateway
  -> Java Turn Attempt + PostgreSQL 正典状态（canonical state）
  -> Python 持久化 turn 处理器 + 模型/检索
  -> Java 原子提交
  -> 正典 transcript
  -> Evaluation 报告
```

Day 3 的执行脚本是 [`tests/scripts/durable_interview_replay.py`](../tests/scripts/durable_interview_replay.py)。它的 JSON 报告有意排除了 JWT、候选人回答和题目文本。

## 本基线包含的链路修复

1. 项目追问（follow-up）会被显式标记，不占用主项目问题配额。
2. 技术阶段启动时，会拒绝上一条无类型/项目类的 AI 消息，而不是把它当作第一道技术题重复出现。
3. 最后一次技术回答在同一个持久化 turn 内调用一次幂等的 Python 收尾（conclusion）；最终总结作为终态 AI 消息持久化。
4. `is_followup` 从 Python 评分 SSE 一路传递，经 Java `TurnModelResult` 落到 `t_score_record.is_followup`。
5. Flutter 结果页改为请求已完成分支的 Java Evaluation 报告，而不是渲染本地全零的 `MatchResult`。页面展示持久化的总分、四个维度、总结、优势与改进点。

## 度量证据

| 层级 | 结果 |
| --- | --- |
| Python 模型 provider、状态机、持久化处理器、快照重建、SSE、可观测性回归 | 47 个用例通过（`uv run pytest` 聚焦套件） |
| Java 模型 SSE 解析与兼容评分持久化 | JDK 21 全新构建后 6 个用例通过 |
| Java PostgreSQL Turn Attempt 集成 | 26 个用例通过；包含 `t_score_record.is_followup = true` 的数据库断言 |
| Flutter 持久化 Evaluation 结果页 | 在缓存的 Flutter 容器中 1 个 widget 测试通过 |
| OpenCode Go provider 预检 | 兼容版 `/models` 发现和一个最小化非敏感 `deepseek-v4-flash` chat 请求均返回 HTTP 200。可用目标 ID：`deepseek-v4-flash`、`mimo-v2.5`、`mimo-v2.5-pro`。 |
| 本地 Docker 服务 | 回放时 Gateway、Python AI、PostgreSQL、Interview、Evaluation 均健康/可达。Python 运行时显示 OpenCode Go 主模型为 `deepseek-v4-flash`，回退链为 `mimo-v2.5,mimo-v2.5-pro`，SQL trace 存储开启，原始 payload 存储关闭。 |
| 真实持久化回放 | 完成 14 个持久化 Gateway 步骤：开场、9 个项目阶段 turn、3 个技术阶段 turn、1 次终态收尾。正典 Branch 到达状态 `2`（`completed`）并生成 1 份持久化 Evaluation 报告。 |
| PostgreSQL 正典回读 | 已完成的 attempt；26 条正典消息；11 条关联评分记录；1 份 Evaluation 报告；当前阶段 `concluded`。 |
| Trace 回读 | 13 条持久化 trace、21 个 trace step、20 次 LLM 调用、累计 19,330 tokens、累计 LLM 延迟 150,385 ms；观测到的所有模型调用均为 `deepseek-v4-flash`。未存储任何 prompt/响应 payload 字段。 |
| 火山方舟 Agent Plan embedding 运行时 | OpenAI 兼容直连与进程内 LangChain 冒烟均用 `doubao-embedding-vision` 成功；请求 `dimensions=1024` 返回 1024 维。新建的隔离 Collection 从旧 collection 完成重嵌入：源 615 条、目标 615 条，ID 集合完全一致。 |

## 模型与检索配置

对话生成走 OpenCode Go 的 OpenAI 兼容端点，通过通用 provider 配置：

1. 主模型：`deepseek-v4-flash`。
2. 有序回退：`mimo-v2.5`，再到 `mimo-v2.5-pro`。
3. 每个配置的对话模型都构造为独立 client。默认对话 client 使用有序回退链；显式按调用指定模型时则刻意保持单模型。
4. Embeddings 使用火山方舟 Agent Plan 的专用 OpenAI 兼容 `/api/plan/v3` 端点、`doubao-embedding-vision`，并显式请求 `dimensions=1024`。该 API 在缺省 `dimensions` 时实际返回更大的向量，因此维度保持显式声明并在运行时校验。
5. 已有的 `interview_questions` 向量与新模型隔离，存放在 `interview_questions_doubao_embedding_vision_251215_1024_v1`。两个向量空间恰好都是 1024 维，但语义上不可互换。可重复执行的迁移工具只向空目标复制源 ID，每次 provider 请求限制十个输入，并支持限流后的校验续传/退避。
6. 如果 embedding key 缺失或不可用，技术题检索会降级为关键词检索，同时不允许 Chroma 静默下载/使用其他默认 embedding 模型。这保证了面试流程可用，而不是产生隐藏的 provider 漂移。

本次线上回放只覆盖了健康的主模型。回退路由顺序由 `test_model_provider.py` 覆盖；本次运行没有发生运行时回退事件。

## 成功的运行时验收

权威的脱敏证据是 [durable-day3-20260814-011051.json](../tests/reports/durable-replay/durable-day3-20260814-011051.json)。其中不包含 JWT、候选人回答、题目文本、API key、端点值、prompt 或模型响应 payload。单独的 embedding 证明是 [volcengine-agent-plan-doubao-embedding-vision-20260815.json](../tests/reports/embedding-provider/volcengine-agent-plan-doubao-embedding-vision-20260815.json)；它只记录公开的 provider/模型配置和聚合迁移校验结果。

合成回放使用的是刻意通用的候选人回答。因此其低分与 `REJECT` 推荐只说明持久化评分/评估链路可用；不代表对真实候选人的判断，也不是模型质量基准。

使用有效且未提交的 `OPENCODE_GO_API_KEY` 重跑本地门禁：

```bash
OPENCODE_GO_API_KEY='<运行时提供>' \
  docker compose -f ai_interview_backend/docker-compose.yml up -d --build python-ai
python3 tests/scripts/durable_interview_replay.py --timeout 90 --max-turns 30
```

一次通过的运行，需要回读生成的报告以及 PostgreSQL 状态/计数：一个 completed 的 root/branch、一个终态 `t_interview_turn_attempt`、正典业务消息、关联评分记录、一份 Evaluation 报告，以及聚合 trace 数据（原始 payload 存储仍为关闭）。
