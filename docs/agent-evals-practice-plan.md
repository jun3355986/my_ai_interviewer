# AI 面试 Agent 评测体系实践方案

> 把《Evaluating AI Agents》课程内容落到 ai_interviewer 项目。
> 课程讲义索引：Obsidian `程序猿/ai/agent 评测/AI 评测学习文档.md`
> Phoenix 本地实验台：`tests/labs/agent-eval-phoenix/`（两个已完成 notebook，含新版 API 迁移实战记录）
> 状态：规划中 → 按阶段推进，每阶段完成后更新状态和量化证据

---

## 一、背景：为什么这么设计

### 1.1 课程主线与本方案的映射

课程主线是：**可观测性（L4-5）→ 组件级评估（L6-7）→ 路径级评估（L8-9）→ 结构化实验（L10-11）→ 评估器自我校准（L12）→ 生产闭环（L13）**。

本方案不按课程顺序照搬，而是按「依赖关系」重排成 7 个阶段（阶段 0-6）。原因：

1. **评测器没有数据可评就是空转**——必须先有「可回放的面试运行记录」当数据集，所以数据基建放阶段 0；
2. **Code-Based 评估器 100% 准确、零成本**，先建它；它同时是后面 L12「校准 judge」的标尺；
3. Convergence（L8-9）看起来最难应用，其实依赖最少的评估器（不需要 ground truth），但需要先有批量运行能力，所以放在 judge 之后、EDD 之前。

### 1.2 面试 Agent 的「课程式拆解」

课程的 Agent 模型是 Router + Skills + Memory。我们的面试 Agent 不是 tool-calling 架构，而是**阶段处理器架构**，映射关系如下（这个映射本身就是一个值得写进学习文档的思考点）：

| 课程概念 | 面试 Agent 中的对应物 | 代码位置 |
|---|---|---|
| Router（路由决策） | 阶段分发：根据 InterviewStage 决定调哪个 handler | `services/interview_service.py` 的 stage 分发 |
| Skill（技能） | 出题、评估回答（打分+追问判断）、追问、开场白、总结 | `api/interviewer.py` 的 `Interviewer` 类各方法 |
| Memory & State | 会话状态机 + QA 列表 + 追问计数 | `services/interview_session.py`、SQLite `history` |
| Path / Trajectory | 一场完整面试（或一个回合）的实际流转路径 | trace 体系 + 会话记录 |

### 1.3 现状盘点：已有资产 vs 缺口

**已有资产（比预想的好，方案建立在这些之上）：**

| 资产 | 位置 | 对应课程 |
|---|---|---|
| 自建 trace 体系（t_ai_trace / t_ai_trace_step / t_ai_llm_call） | `services/observability/` | L4-5 插桩，**已完成** |
| LangSmith / Phoenix OTel 接入 | `agent_runtime/langsmith.py` + dev 依赖 | L5 |
| 录制-回放链路（trace JSONL → 回放 → 报告） | `tests/scripts/interview_replay.py`、`durable_interview_replay.py` | 数据集来源 |
| 确定性评估器雏形（HTTP 状态、stage 存在性、事件非空、耗时上限） | `services/agent_runtime/evaluation.py` | L6 code-based evals 的**雏形** |
| LangSmith 数据集同步脚本 | `tests/scripts/langsmith_eval.py` | L10 数据集机制 |
| 双 Agent 模拟（Interviewer + Candidate 跑完整场面试） | `ai_interviewer/test_interview.py` | 批量运行的 task 函数 |
| Java Evaluation 服务五维评分模型 | `ai-interviewer-evaluation`（:9005） | 用户侧/端侧评分视角 |
| **Phoenix 新版 API 实战经验**（phoenix 20.x + phoenix-client 3.2.0，含 API 迁移踩坑记录） | `tests/labs/agent-eval-phoenix/` 两个已完成 notebook | L5-L9 的 tracing / evals / experiment 已在 mini-agent 上跑通 |

**核心缺口（本方案要补的）：**

1. Code-Based 评估器**只查「存在性」不查「合法性」**——stage 有值 ≠ 转换合法；没有 JSON schema 校验、没有追问计数校验；
2. **完全没有 LLM-as-a-judge**——回答质量、评分合理性全靠人看；
3. **没有 Convergence**——面试官追问行为是否稳定、回合成本是否受控，无量化；
4. **没有实验机制**——改 prompt / 换模型后无法回答「到底变好还是变坏」；
5. 已有评估器结果**没有回流可视化平台**，无法横向对比变体。

---

## 二、评估器全景图

最终要建成的评估器清单（一张表看全貌，实施细节见分阶段计划）：

| # | 评估器 | 手段 | 评什么 | 数据来源 | 阶段 |
|---|---|---|---|---|---|
| E1 | 状态机合法性 | Code | 阶段转换序列是否符合合法转换表 | 回放报告 / 会话记录 | 1 |
| E2 | 输出结构合法性 | Code | evaluate_answer 输出 JSON schema、score 域 | LLM 调用记录 | 1 |
| E3 | SSE 事件序列完整性 | Code | 每回合事件流的先后与完整性 | 回放报告 | 1 |
| E4 | 追问计数守恒 | Code | 追问次数 ≤ 配置上限、计费正确 | 会话记录 | 1 |
| E5 | 引用真实性 | Code + 语义 | 面试官引用的简历/前文内容是否真实存在 | 会话记录 + 简历原文 | 1 |
| E6 | 回答理解准确性 | LLM judge | Agent 对考生回答的理解/复述是否准确 | 考生回答 + Agent 反馈 | 2 |
| E7 | 上下文结合度 | LLM judge | 追问/反馈是否结合了简历与历史回答（含幻觉检查） | 多轮上下文 + Agent 输出 | 2 |
| E8 | 表达清晰度 | LLM judge | 面试官提问与反馈是否清晰、结构良好 | Agent 输出 | 2 |
| E9 | 评分合理性 | LLM judge | 给出的 score + feedback 是否与回答质量匹配 | 考生回答 + score + feedback | 2 |
| E10 | 追问行为收敛度 | Code（Convergence） | 同类问题下追问轮数是否稳定收敛 | 批量模拟运行 | 4 |
| E11 | 回合成本收敛度 | Code（Convergence） | 同类回合的 token/调用次数/耗时的收敛 | t_ai_llm_call | 4 |
| E12 | 整场模拟收敛度 | Code（Convergence） | 双 Agent 全场模拟的总路径长度收敛 | test_interview.py 批量运行 | 4 |

**判断「一个指标该用什么手段」的两条决策线**（L6）全程适用：
① 能用代码定义的吗（确定性、零成本优先）？② 能容忍误差吗（LLM judge 永远 <100% 准确）？

---

## 二·五、Phoenix 新版 API 基线与踩坑注意点（重要，先读这节再动手）

> 背景：课程视频与讲义基于 **一年半前的旧版 phoenix**（`px.Client()` 时代，约 phoenix 4-6.x）。
> 本项目实验环境是 **phoenix 20.x + 内置 phoenix-client 3.2.0**，旧 API **已全部移除**——
> 直接照抄课程代码会全部报错。以下对照表和注意点来自 `tests/labs/agent-eval-phoenix/` 两个
> notebook 的实战验证（agent1 = Lab 2/3 复刻，agent2 = Lab 4 复刻，均已跑通），实践时以本节为准。

### 2.5.1 新旧 API 对照表（实践时的翻译字典）

| 用途 | 旧版（课程/L7-L9 写法） | 新版（本项目 phoenix-client 3.2.0） |
|---|---|---|
| 客户端 | `px.Client()` | `from phoenix.client import Client; Client(base_url=...)` |
| 导出 span | `px.Client().query_spans(SpanQuery(), project_name=...)` | `client.spans.get_spans_dataframe(query=SpanQuery(), project_name=..., start_time=...)` |
| LLM judge 执行 | `llm_classify(df, template, rails, OpenAIModel(...))` | `create_classifier(name, prompt_template, llm, choices) + evaluate_dataframe(df, evaluators=[clf])` |
| judge 模型包装 | `OpenAIModel(model="gpt-4o")` | `LLM(provider="openai", model=..., api_key=..., base_url=...)` |
| 内置 router 模板 | `TOOL_CALLING_PROMPT_TEMPLATE` | **新版不内置**，自己写 judge prompt（agent1 notebook 有验证过的中文版可抄） |
| 回写评估结果 | `log_evaluations([SpanEvaluations(...)])` | `client.spans.log_span_annotations_dataframe(dataframe=..., annotation_name=..., annotator_kind="LLM"/"CODE", sync=True)` |
| 上传数据集 | `px_client.upload_dataset(dataframe=..., dataset_name=..., input_keys=...)` | `client.datasets.create_dataset(name=..., dataframe=..., input_keys=...)` |
| 跑实验 | `from phoenix.experiments import run_experiment`（顶层函数） | `client.experiments.run_experiment(dataset=..., task=..., timeout=..., retries=...)` |
| 事后补评估 | `evaluate_experiment(experiment, evaluators=[...])` | `client.experiments.evaluate_experiment(experiment=..., evaluators=[...])` |
| evaluator 装饰器 | `@create_evaluator(name=..., kind="CODE")` | **不需要装饰器**，直接传普通函数；名字自动用函数名 |
| 实验结果转表 | `experiment.as_dataframe()` | **已移除**。返回 `RanExperiment` TypedDict，手动从 `experiment["task_runs"]` / `experiment["evaluation_runs"]` 组装 DataFrame |
| Example 类型 | `from phoenix.experiments.types import Example` | 不需要 import；task 单参数命名为 `example` 即自动绑定，兼容 `example.input.get(...)` 旧式访问 |
| Phoenix UI 入口 | 项目固定 | 主项目 = `.env.phoenix` 里的 `PHOENIX_PROJECT_NAME`（`ai-interviewer-agent-eval`）；**实验 trace 落在独立的 `Experiment-<hash>` 项目**（见 2.5.3 坑 5） |

### 2.5.2 新版 judge / evaluator 的行为差异

1. **`choices` 的两种写法决定有没有 score 列**：
   - `choices=["clear", "unclear"]`（list）→ 只产出 label，score 为 None；
   - `choices={"clear": 1, "unclear": 0}`（dict）→ 产出 label + score + explanation。
   实践统一用 **dict 写法**，省掉旧版手动 `map` 补 score 的一步。
2. **`evaluate_dataframe` 的输出结构变了**：结果在 `{name}_score` 列里，每格是
   `{"label":..., "score":..., "explanation":...}` dict，需要手动展平成三列（agent1 notebook 的
   `router_eval_df["label"] = ...` 三连就是标准写法）。且**要防御索引错位**：展平后校验
   `list(df.index)` 是否还等于原始 `context.span_id` 列表，不等就重设——否则回写 annotation 会挂错 span。
3. **classifier 内部走结构化输出 / function calling 拿标签** → 裁判模型必须支持 tool calling。
   项目当前用的 `mimo-v2.5`（opencode go 渠道）已验证支持；换 judge 模型时先探活确认这一点。
4. **evaluator 返回值解析规则**：支持 `float` / `(score, label)` 二元组 / `(score, label, explanation)`
   三元组 / `{"score","label","explanation"}` dict 四种。**坑**：`(score, explanation)` 二元组会把
   explanation 当成 label，解释静默丢失——统一用 dict 写法最不歧义（agent2 已验证）。
5. **模板占位符 ↔ 列名的隐式契约在新版依然成立**：`create_classifier` 的
   `prompt_template` 占位符名必须和 DataFrame 列名一致（`{question}` ↔ `question` 列），对不上照样静默填空。

### 2.5.3 实测踩过的坑（每条都真实咬过人，实践时对照检查）

1. **Trace 导出有秒级延迟，跑完立刻查会漏数据**。agent1 开发时 6 个问题只查到 5 条——
   span 从进程导出到 Phoenix 落库是最终一致，不是同步的。**实践约定**：每次跑批后必须
   轮询等待（连续两次计数一致才算稳定），复用 agent1 的 `wait_for_traces()` 模式；
   评估脚本里不要写「跑完立刻 query」的代码。
2. **「跑完立刻统计」会得出错误结论，连 A/B 实验都会翻车**。agent1 的 suppress A/B 对照
   第一版因为导出延迟得到完全相反的结果。教训泛化：**测量本身也要考虑可观测性系统的
   最终一致性**——任何「跑完 → 查 → 统计」的链路都要插稳定等待。
3. **时间窗过滤是防数据污染的第一道闸**。所有评测用 `SpanQuery` 都必须带
   `start_time=RUN_START`（跑批前记录时间戳），只认本次跑批之后的 span。不圈时间窗，
   历史冒烟调用、上一轮实验的 span 全会混进评测集。
4. **`suppress_tracing()` 在新版依然必须用**，且被包装的 openai SDK 全局插桩让这个坑
   更隐蔽：judge 调用如果不抑制，会作为新 LLM span 上报进同一项目，下次捞数据时
   被当成被测样本，评测集滚雪球式自我污染。agent1 用 A/B 实验验证过：未抑制的
   judge 调用进 trace ≥1 次，抑制后 0 次。**CI 检查点**：跑完评估后
   `span_kind == 'LLM'` 的 span 增量应为 0。
5. **实验 trace 落在独立项目**：`experiments.run_experiment` 内建独立 TracerProvider，
   实验期间的 trace 整体落在 `Experiment-<hash>` 项目，**不是** `.env.phoenix` 配置的
   主项目。排查 trace 先看对项目；「实验里一条 trace 都没有」八成是在主项目里找。
6. **过滤 span 靠 span 命名约定**：新版捞某类工具的执行记录用
   `span_kind == 'TOOL' and name == 'execute_tool.get_interview_stats'`——所以**工具执行
   span 的名字必须带上具体工具名**（`execute_tool.{tool_name}`），agent1 的 v2 版本
   专门为此升级过。本项目的面试 Agent 落地时同理：给各阶段 handler 的 span
   起可过滤的名字（如 `handler.evaluate_answer`），否则评估时捞不出目标 span。
7. **只评第一轮路由决策**：agent 多轮循环里，第二轮请求的消息历史已带 `tool` 角色
   消息，那是执行后的续问不是路由决策。过滤技巧：数请求体里 `"role"` 出现次数 ≤1。
   本项目评「阶段分发」时同理——只评进入该阶段的第一跳。
8. **裁判输入要先清洗**：`input.value` 是完整请求体 JSON 串，直接喂 judge 噪声很大，
   先解析出纯 user 内容再进模板。agent1 的 `extract_user_question()` 可复用。
9. **实验失败的 task 有 error 字段**：新版 `task_runs[i]["error"]` 非空即失败，且
   失败 run 的 output 为 `{}`——收敛计算前必须排除（呼应课程坑二「只对完整运行计分」），
   否则失败样本的空 path 会把 S_optimal 拉到假低。
10. **本地 Phoenix 已做安全收口**：遥测关闭、Playground 断外网、服务器端 Bash 禁用；
    双上报冲突要防——首轮实验保持 `LANGSMITH_TRACING=false`，别把同一调用同时
    报给 LangSmith 和 Phoenix（README 实验边界）。

### 2.5.4 对实践方案的直接影响

基于以上，方案各阶段的技术选型确认如下：

- **阶段 1（Code evals）**：直接复用 agent1 的五步工作流（固定问题集 → 记时间戳 →
  SpanQuery 回收 → 断言+裁判 → 统一格式回写 annotation），但数据源从 mini-agent 的
  span 换成面试 Agent 的真实 trace / 回放报告；
- **阶段 2（LLM judge）**：judge 三件套用 `create_classifier + evaluate_dataframe +
  log_span_annotations_dataframe`；E6-E8 的中文 judge prompt 可参考 agent1 已验证的
  模板结构（定义分档含义 → [BEGIN DATA] → 先解释后标签）；
- **阶段 4（Convergence）**：experiment 全套用 `client.experiments.*`；
  `run_experiment` 传 `timeout`（单 task 超时）和 `retries` 参数；结果组装参考 agent2 的
  task_runs/evaluation_runs 手动拼表模式；
- **阶段 5（EDD）**：golden dataset 用 `client.datasets.create_dataset` 上传；
  数据集实验对比页在 `/datasets/<id>/experiments`，天然支持多次实验横向对比——
  这就是看板的现成底座，不必自建；
- **通用纪律**：所有查询带时间窗 + 稳定轮询；评估全程 suppress_tracing；span 命名
  带可过滤语义；judge/evaluator 返回值统一 dict 形态。

---

## 三、分阶段实施计划

> 遵守本仓库测试资产管理约定：所有评测脚本放 `tests/` 下，新增用例登记 `tests/docs/test-cases.md`，改命令/环境变量同步 `tests/docs/tooling-guide.md`。
> 遵守 FDE 学习协作边界：每个阶段的核心实验、结果解释、结论由 Drake 亲自完成；脚手架、数据准备、排错可由 AI 协助。

### 阶段 0 · 数据基建：让「一场面试」变成「一条可评的数据」

**目标**：解决「评测器没有数据可评」的问题。所有后续评估器都消费这一层产出的标准化数据。

**做法**：

1. 定义**评测数据单元**（eval record）的 JSON schema，每个回合一条：
   ```
   { session_id, stage_before, stage_after, user_input(考生回答),
     agent_output(提问/反馈/评分), llm_calls:[{prompt_tokens, completion_tokens, latency_ms, model}],
     sse_events:[...], resume_snippets(本场引用的简历片段), ts }
   ```
2. 数据源打通（三选一或并行）：
   - **回放链路**（已有）：`interview_replay.py` 产出的报告增强上述字段；
   - **双 Agent 模拟**（已有）：`test_interview.py` 跑完导出全程 eval records；
   - **t_ai_llm_call 落库**：确认每回合的 LLM 调用记录可以按 session 关联导出。
3. 建 `tests/evals/` 目录（评测器 + 数据集 + 报告的家），fixture 放 `tests/fixtures/`。
4. 搭建**种子数据集**：手工构造 10-20 个核心场景（好回答/差回答/答非所问/超短回答/挑战面试官），覆盖 6 个 stage 的关键转换。来源三种都要有（L10）：手写 + trace 挑选 + LLM 生成。

**产出**：eval record schema 文档 + 种子数据集 + `tests/evals/` 目录骨架。
**验收**：任选一场录制的面试，能一条命令导出全部 eval records。
**坑**：双 Agent 模拟里 Candidate 的回答也是 LLM 生成的——注意把「考生回答」当成数据集的一部分固定下来（可复现），否则每次跑数据集都漂移。

---

### 阶段 1 · Code-Based 评估器：流程与结构的守门员（L4-L7）

**目标**：把「面试流程是否正常」从人眼看变成确定性断言。这类评估 100% 准确、零成本，后续也是校准 judge 的 ground truth 标尺。

**具体评估器**：

- **E1 状态机合法性**：扩展 `agent_runtime/evaluation.py`。现在只查 `stage_present`，要升级为查**转换合法性**：把 `InterviewStage` 的合法转换表显式写成代码（`resume_submitted → opening → self_introduction → project_qna → technical_qna → concluded` + 合法回退/追问路径），逐回合断言 `stage_before → stage_after` 在表内。非法转换 = FAIL。
- **E2 输出结构合法性**：`evaluate_answer` 的输出必须是合法 JSON 且含约定字段（score 为数值且在域内、followup 布尔等）。用 Pydantic schema 校验，这等价于课程里"聊天机器人输出必须可解析"的经典 code eval。
- **E3 SSE 事件序列**：每回合事件流必须满足形如 `status → (question|chunk)* → done` 的正则式序列，出现 `error` 事件即 FAIL（雏形已有，补序列顺序断言）。
- **E4 追问计数守恒**：`current_question_followup_count` 与实际 QA 列表长度一致，且不超过配置上限。
- **E5 引用真实性**：面试官反馈中引用的简历内容，必须能在简历原文中找到（先做归一化字符串匹配，再做 embedding 余弦相似度兜底——复用 DASHSCOPE embeddings）。这一条同时是幻觉检测的 code-based 版本。

**产出**：`tests/evals/code_evals/` 下 5 个评估器 + 单测。
**验收**：对种子数据集全量跑一遍，产出 PASS/FAIL 报告；人为注入 2 个坏样本（伪造非法 stage 跳转、截断 JSON）确认能抓到。
**登记**：在 `tests/docs/test-cases.md` 登记用例 ID。

**课程对照的坑**：
- 评估最难的往往是**筛出正确的数据子集**（L7 讲师原话）——先写好「哪条数据该进哪个评估器」的过滤断言；
- 新版数据流直接复用 agent1 已验证的模式：**跑批前记 `RUN_START` 时间戳 → 所有 `SpanQuery` 带 `start_time=` 过滤 → `wait_for_traces()` 轮询到导出稳定再评估**（见 2.5.3 坑 1、3）；
- 评估结果统一成 label / score / explanation 三列后，用 `client.spans.log_span_annotations_dataframe()` 以 span annotation 形式回写 Phoenix（新版不再有 `SpanEvaluations` / `log_evaluations`，见 2.5.1 对照表），UI 里打开任一 span 的 Annotations 区即可对照排查；
- 工具/阶段 handler 的 span 命名要带可过滤语义（`execute_tool.{tool_name}`、`handler.evaluate_answer` 这种），否则捞不出目标 span（2.5.3 坑 6）。

---

### 阶段 2 · LLM-as-a-Judge：四个质量维度（L6）

**目标**：评那些「代码评不了」的定性维度。E6-E9 四个 judge。

**Judge 模板规范（每条都是课程血泪教训，写 judge 时逐条对照）**：

1. **只输出离散分类标签，绝不打连续分**（L6 最重要一条）：LLM 分不清 83 分和 79 分。标签设计：
   - E6 回答理解准确性：`accurate / inaccurate`
   - E7 上下文结合度：`contextual / generic / hallucinated`（三分类，多出 hallucinated 这个高价值标签）
   - E8 表达清晰度：`clear / unclear`
   - E9 评分合理性：`reasonable / too_lenient / too_harsh`（三分类，方向性比二分类更有用）
2. **把判据写死在模板里**：参考课程 CLARITY 模板——明确"即使事实正确，表达混乱也算 unclear"这类排除歧义的定义。
3. **要求先解释后给标签，且解释里不许出现标签**（CoT 变体）。
4. **judge 模型档次必须 ≥ 被测模型**（L6）："用弱模型当裁判，等于用一个更差的判断去衡量你的系统"。当前主模型是 deepseek-chat 的话，judge 优先用 DeepSeek reasoner 档或另配更强的 API；如果只能用同档模型，必须在阶段 3 加密人工校准力度，并在报告里声明。
5. **rails 归轨**：judge 输出 snap 到预定义标签。新版 `create_classifier` 的 `choices={"clear": 1, "unclear": 0}` dict 写法自带归轨 + 分数映射（list 写法只有 label 没有 score，见 2.5.2 第 1 条），统一用 dict。
6. **关掉 judge 调用的追踪**：judge 调用也是 LLM 调用，会被 observability 捕获污染 trace 数据，形成「评估样本池自我污染」（L7 的 `suppress_tracing` 坑）。每次批量 judge 必须包在 `with suppress_tracing():` 里——agent1 已用 A/B 实验验证过不抑制的后果（2.5.3 坑 4）。

**E9 评分合理性的特殊设计**：judge 的输入是三元组（考生回答原文 + Agent 给的 score + feedback），judge 判断这个分数与回答质量是否匹配。这是「评估你的评估」的第一层雏形——面试 Agent 本身就是一个 evaluator（给考生打分），所以 E9 天然是 double-judge 结构，也天然衔接阶段 3。

**产出**：`tests/evals/judges/` 下 4 个模板 + 执行器。
**技术形态（新版 API）**：`create_classifier(name, prompt_template, llm, choices=dict) + evaluate_dataframe(df, evaluators=[clf])`，结果从 `{name}_score` 列展平出 label/score/explanation 三列（注意展平后校验索引未错位，2.5.2 第 2 条）；裁判模型用 `LLM(provider="openai", model=..., api_key=..., base_url=...)` 包装，且必须支持 function calling（classifier 内部走结构化输出，2.5.2 第 3 条）。中文 judge prompt 结构可参考 agent1 notebook 已验证的 `ROUTER_JUDGE_PROMPT` / `CLARITY_JUDGE_PROMPT`（分档定义 → [BEGIN DATA] → 先解释后标签）。
**验收**：种子数据集全量跑一遍，人工核对 judge 结论与自己的直觉是否一致（这个一致性记录直接留给阶段 3 当校准数据）。
**坑**：模板变量名 ↔ DataFrame 列名的隐式契约，对不上会静默填空、judge 输出一堆随机标签——每个评估器旁边写列名断言（新版依然如此，2.5.2 第 5 条）。

---

### 阶段 3 · 校准你的 Judge：judge your judge（L12）

**目标**：回答「凭什么信 judge」。用 100% 准确的 ground truth 标尺量 judge 的准确率。

**做法**：

1. 建**人工标注集**：从种子数据集 + 真实 trace 里挑 50-100 条，Drake 亲自给 E6-E9 各标一遍 ground truth（这正是 L6 的人工标注层——你本人就是 annotation queue）。50 条起步，够判方向；后面随失败样本积累扩到 200 条。
2. **递归套 experiment**：把「judge 的输入/输出」降级成新的 test case——外层 input = 喂给 judge 的内容（Agent 的输入输出），外层 expected = 你标注的标签。跑不同 judge prompt 变体、不同 judge 模型，用代码比对算 judge 准确率。
3. 改进抓手（L12）：① 加 few-shot 示例（从历史判定里挑）；② 换 judge 模型。**judge 准确率 <85% 不许上量**。
4. E7/E9 的解释文本如果也参与比对，用**语义相似度**而非字符串匹配（L12：expected 是 "analysis is clear because…" 而 judge 说 "easy to understand because…"，字面不同意思相同）。

**产出**：judge 校准报告（各 judge 准确率/与人工一致率）+ 定稿的 judge prompt 版本管理（v1/v2/v3 的准确率对比表——这就是最好的实验看板雏形）。
**验收**：每个 judge 有一个带数字的准确率结论，且该结论可复现（固定温度、固定样本集）。
**坑**：标注时别看 judge 的输出再标（锚定效应）；标注标准本身先写下来，标注中途不改。

---

### 阶段 4 · Convergence：过程贵不贵（L8-L9）—— 三个落地场景

**目标**：量化 Agent 走的路径是否稳定高效。课程核心公式：`收敛分 = mean( min(1, S_optimal / S_agent_i) )`，对**一批相似查询**统计，不是对单条打分。

用户原话「暂时想不到哪里可以应用」——以下是三个现成的应用场景，按推荐优先级排列：

**场景 A（首推）：追问行为收敛（E10）**
- **问题背景**：面试官最典型的失控模式是追问轮数不稳定——同一个考生回答，有时追问 1 轮就放过，有时死磕 5 轮。追问过多 = 考生体验差 + token 成本高；追问过少 = 挖掘深度不够。这正是「结果对但过程抖」的典型轨迹问题。
- **做法**：数据集 = 同一项目的 N 种考生回答变体（LLM 改写生成，相似到理应走同样的追问策略）。path 长度 = 该题下的 QA 轮数。跑 N 次算收敛分。
- **防坑配置**：设**人工预期上限**（如项目题追问 ≤3 轮）交叉验证——课程坑一：收敛度抓不到"每次都多走一步"的系统性绕路，所有人都追问 4 轮时收敛分仍是 1.0。

**场景 B：回合成本收敛（E11）**
- **问题背景**：每个 `/chat` 回合的 LLM 调用次数、token 总量、耗时应稳定在同类水平。如果技术问答回合有时 1 次调用有时 4 次，背后可能是反复重试或上下文重建。
- **做法**：t_ai_llm_call 已有原始数据。按**回合类型分组**（opening / project-answer / technical-answer / conclude 分别算，课程坑五：不同类型最优路径本来就不同，混算无意义），path = 回合累计 token 数（直接升级课程里 `len(messages)` 的粗糙近似——讲义工程判断 3：真实成本是 token × 单价）。S_optimal 取该组最小值。
- **额外纪律**（课程坑二）：**只对完整成功的回合计分**——崩在半路的回合调用少，会被误判成"最优路径"。

**场景 C：整场模拟收敛（E12）**
- **问题背景**：双 Agent 模拟同岗位同简历，全场总轮数 / 总 token / 总耗时应收敛。这是端到端的路径指标，也最接近真实成本结构。
- **做法**：固定简历 + 固定岗位跑 20-30 场，path = 总 token。收敛分低 = 面试官行为抖动大，回到 trace 定位是哪个 stage 抖。

**产出**：`tests/evals/convergence/` 三个收敛评估器 + 各自的数据集。
**验收**：场景 A 报告里有收敛分 + 抖动最大的前 3 条 trace 链接；故意构造「全部运行都多追问一轮」的数据集，验证人工上限能抓到而收敛分抓不到（这个实验本身值得写进学习文档）。
**新版 API 实现要点**（agent2 notebook 已跑通，照抄模式即可）：
- 数据集：`client.datasets.create_dataset(name=f"...{timestamp}", dataframe=..., input_keys=[...])`——名字带时间戳避免互相覆盖；
- 实验：`client.experiments.run_experiment(dataset=..., task=..., experiment_name=..., timeout=180, retries=2)`——**要传 timeout 和 retries**（单 task 超时 + 失败重试，教学版没有这层保护）；
- task：单参数命名 `example` 即可，无需 import Example，`example.input.get("question")` 旧式访问照用；返回 `{"path_length":..., "messages":...}` dict；
- 结果组装：`experiment["task_runs"]` 手动拼 DataFrame（`as_dataframe()` 已移除）；失败 run 的 `error` 非空、output 为空 dict，**计算 S_optimal 前必须过滤**（呼应课程坑二，2.5.3 坑 9）；
- evaluator：直接传普通函数（无装饰器），返回 `{"score":..., "label":..., "explanation":...}` dict——**(score, explanation) 二元组会把 explanation 当 label，解释静默丢失**（2.5.2 第 4 条）；
- 补评估：`client.experiments.evaluate_experiment(experiment=..., evaluators=[...])`——收敛分依赖全量结果，仍是「先跑完再评估」的两段式；
- trace 归属：实验 trace 落在 `Experiment-<hash>` 独立项目，UI 里切项目才能看到（2.5.3 坑 5）；`task_runs[i]["trace_id"]` 是实验记录 ↔ trace 明细的关联钥匙；
- 看板入口：数据集实验对比页 `/datasets/<id>/experiments`，天然支持多轮实验横向对比。

---

### 阶段 5 · EDD 闭环：数据集 + 实验 + 看板（L10-L11）

**目标**：把 E1-E12 组装成可重复运行的实验，能回答「这次改动到底有没有变好」。

**做法**：

1. **建 golden dataset**（L13 的构成原则提前落地）：两部分缺一不可——
   - 通用性能样本：覆盖 6 个 stage 的正常场景；
   - **已知失败模式样本**：每修一个 bug，把触发它的样本加进来（这条写进 code review checklist，不做回归必然复发）。
2. **experiment 组织**：`run_experiment(dataset, task, evaluators)` 模式——
   - dataset：golden dataset（eval records）；
   - task：回放运行或双 Agent 模拟；
   - evaluators：E1-E12 全家桶，输出**实验看板**——一行 = 一次运行，一列 = 一个评估器。
3. **首批变体实验**（每次只改一个变量，L10）：
   - 改 `evaluate_answer` 的 prompt（比如要求评分前先复述考生要点）→ 看 E9、E7 变化；
   - 换出题模型 / 改出题 prompt → 看 E8、E10 变化；
   - 调整追问上限 → 看 E10 收敛分与 E6 的 trade-off。
4. 看板落地：**确定 Phoenix 为主看板**（不再"二选一"悬置）——理由：① 本地已部署、实验台已跑通（agent1/agent2）；② 数据集实验对比页 `/datasets/<id>/experiments` 天然支持多实验横向对比，是现成的 EDD 看板；③ span annotation 回写机制已验证。LangSmith 脚本（`tests/scripts/langsmith_eval.py`）降级为备选同步通道，首轮实验保持 `LANGSMITH_TRACING=false` 避免双上报（README 实验边界）。

**产出**：`tests/evals/run_experiment.py` 一键跑全量评估器 + 实验看板截图/链接。
**验收**：完成一次真实变体实验，看板上能对比 baseline vs 变体，并写出一段「数据说明改好/改坏了」的结论。
**坑**：一次只改一个变量；样本量小时（<100）结论只能当方向性指示（L7），别当统计结论。

---

### 阶段 6 · 飞轮与发布闸门（L13）

**目标**：让评测从「一次性工程」变成持续循环。这是整门课的终点形态：生产数据回流数据集，实验当发布闸门。

**做法**：

1. **失败样本回流机制**：真实使用（或双 Agent 模拟）中发现的坏 case → 登记 → 变成 golden dataset 的新样本 → 下次实验必须通过。飞轮的转动就是这个动作的频率。
2. **发布闸门**：改 prompt / 换模型 / 升级依赖后，跑 golden dataset 实验，**每个评估器设独立阈值**，任何一个跌破就拦住（课程仪表盘教训：sql_result 0.50 被 1.00×4 的平均分掩盖）。阈值先人工定，跑出基线后校准。
3. **生产监控指标**（对接已有 observability）：
   - 延迟 P50 / P99（不看平均值，长尾才决定体验）；
   - 每场面试 token 成本；
   - 质量指标（E6-E9 抽样跑）与性能指标并列展示。
4. **「评估 vs 用户反馈背离」检查**（L13 全课最该记住的一句）：如果 judge 全绿但 Java Evaluation 服务的五维分 / 人工复盘很差，**先怀疑评估器，再怀疑 Agent**。排查顺序：数据管道 → 评估标准定义 → 反馈采样偏差 → 最后才是 Agent 逻辑。
5. 自动回流样本设 **10% 人工抽检**，防止数据集被 Agent 自己的输出污染。

**产出**：发布 checklist（跑哪些评估器、阈值表）+ 首份月度评测报告。
**验收**：完成一次「发现坏 case → 入库 → 实验拦截回归 → 修复 → 通过」的完整飞轮循环。

---

## 四、其他值得补的实践点（课程内容里你没提到的）

1. **「中间步骤可单独评估」（L10 思考题）**：`evaluate_answer` 内部的「打分」和「追问判断」是两个中间产物，可以拆开单独评——比如只评追问判断对不对（该追问吗），再单独评分数合理不合理。拆开看才能定位问题在 prompt 的哪一段。
2. **模型评估 ≠ 系统评估（L2）**：所有实验以**系统级端到端**（整场面试）为主结论，组件级（单回合 judge）为辅助定位。别陷入只调组件分。
3. **余弦相似度做语义 ground truth 比对（L6/L12）**：E5 和 judge 校准都会用到 embedding 相似度，DASHSCOPE 已在依赖里，ChromaDB 的 embedding 设施可复用。
4. **人工标注即你自己（L6）**：你本人就是 annotation queue——每次面试复盘时顺手标注几条，就是最高质量的评估数据来源。这比单独找时间标注可持续得多。
5. **评估脚本的 CI 化可以延后**：先让流程在本地一键可跑（`tests/evals/run_experiment.py`），稳定后再挂 CI。课程三件套里 CI/CD 是最后一环，别本末倒置。

## 五、成本与风险的账

| 项 | 估算与对策 |
|---|---|
| Judge 调用成本 | 4 个 judge × 每条 1 次 LLM 调用。先用 seed 集（~50 条）跑通，全量前先算单价。judge 用强模型是必要成本，别省 |
| 模拟运行成本 | 双 Agent 模拟一场 ≈ 双方 LLM 轮数总和。Convergence 场景 C 要 20-30 场，用固定 fixture 回放可省大部分 |
| Trace 污染 | judge 调用必须包 `suppress_tracing()`；CI 加一条检查：跑完评估后 LLM span 数不应增长（agent1 已验证此坑真实存在） |
| **Trace 导出延迟**（新版实测） | 跑完立刻查询会漏 span、甚至得出反向结论。所有「跑批 → 查询 → 统计」链路必须插 `wait_for_traces()` 稳定轮询（2.5.3 坑 1、2） |
| **实验 trace 落错项目**（新版实测） | experiment 的 trace 落 `Experiment-<hash>` 独立项目，不在主项目。排查前先切对项目（2.5.3 坑 5） |
| **双上报冲突** | 同一调用同时报 LangSmith + Phoenix 会让两边数据都失真。首轮实验保持 `LANGSMITH_TRACING=false`（README 实验边界） |
| 数据漂移 | 换模型/改 prompt 后，旧的 ground truth 标注可能过期——golden dataset 每季度复审一次 |

## 六、执行顺序与里程碑

```
阶段 0（数据基建）→ 阶段 1（Code evals）→ 阶段 2（LLM judge）→ 阶段 3（judge 校准）
                                                                    ↓
                                          阶段 4（Convergence）→ 阶段 5（EDD 闭环）→ 阶段 6（飞轮）
```

- 阶段 0-1 是地基，做完就有「流程守门员」可日常使用；
- 阶段 2-3 是核心增量，做完才有质量维度的量化；
- 阶段 4 依赖批量运行能力（阶段 0 的双 Agent 模拟打通即可做）；
- 阶段 5-6 是把前面全部串成飞轮，也是面试叙事里最值钱的部分：**「我用评估驱动开发迭代了一个多阶段面试 Agent，有 12 个评估器、校准过的 judge、收敛度指标和发布闸门」**——这句话后面每个词都有数字和产物支撑。

**起点比方案初版预估的更靠前**：mini-agent 上 tracing（Lab 2）、组件级评估（Lab 3 扩展）、收敛度实验（Lab 4）已在 `tests/labs/agent-eval-phoenix/` 全部跑通并沉淀了新版 API 迁移经验——阶段 1/2/4 的技术形态不再是"从课程翻译"，而是"从已验证的 notebook 模式扩展到面试 Agent 真实数据源"。

> 与 FDE 学习闭环的对应：每阶段 = 问题（本方案的缺口）→ 原理（对应讲义）→ 可运行实验 → 自动化验证 → 决策与失败记录 → 项目应用（评估器进入日常）→ 可展示成果（看板/报告）→ 题库验收（每阶段完成时把新面试追问写进 `FDE 能力验收与面试题库.md`）。
