## 我的问题
当前这个面试助手在测试时，主流程面试过程是一个很长的、很耗时的流程，测试过程中遇到bug 要复现、修复都需要走一趟，这样非耗时时、消耗资源。有没有什么好的办法可以解决这个问题（遇到bug 容易复现、快速复现、定位）



## codex 的解答

[M] 可以，而且这个问题非常值得专门做一套“可复现测试通道”。核心思路是：**不要再把完整真实面试流程当成唯一复现路径**，而是把长流程拆成“可记录、可回放、可跳转、可替身、可观测”的测试体系。

我建议你给当前面试助手加四类能力：**流程回放、状态快照、AI 替身、分层测试**。

**1. 先做“面试流程事件记录 + 回放”**

主流程长、耗时、依赖 LLM，所以最关键的是：测试时一旦遇到 bug，可以把这次面试过程记录下来，之后用同一份记录快速复现。

记录内容建议保存成 JSONL，每一步一行，放在项目统一测试目录里，比如：

```
tests/fixtures/interview-traces/
  bug-2026-07-05-resume-question-loop.jsonl
  bug-2026-07-05-sse-disconnect.jsonl
```

每条事件大概记录：

```
{
  "sessionId": "test-session-001",
  "step": 7,
  "type": "candidate_answer",
  "input": {
    "questionId": "q_backend_003",
    "answer": "我使用 Redis 做过缓存..."
  },
  "expectedNext": "ai_followup_question",
  "timestamp": "2026-07-05T10:12:00+08:00"
}
```

如果涉及 AI 调用，再记录：

```
{
  "type": "ai_call",
  "request": {
    "scene": "generate_followup_question",
    "resumeId": 123,
    "jobId": 456,
    "candidateAnswer": "..."
  },
  "response": {
    "question": "你刚才提到 Redis 缓存，如何处理缓存穿透？"
  }
}
```

这样之后复现 bug 时，不需要重新完整面试一遍，只需要：

```
tests/scripts/replay-interview.sh tests/fixtures/interview-traces/bug-2026-07-05-resume-question-loop.jsonl
```

回放器可以逐步把历史输入打到 Java Interview Service / Python AI Service，或者直接绕过前端，从 API 层复现。

这个是最推荐优先做的，因为它直接解决“遇到 bug 后难复现”的痛点。

**2. 给长流程加“状态快照 / 检查点”**

很多 bug 不是发生在面试开始，而是发生在第 8 轮、第 15 轮、SSE 推流中途、简历解析后、评分前等阶段。

所以不要每次从登录、上传简历、选岗位、开始面试一路走到问题点。应该设计几个测试检查点：

```
checkpoint_01_user_logged_in
checkpoint_02_resume_uploaded
checkpoint_03_job_selected
checkpoint_04_interview_started
checkpoint_05_after_5_questions
checkpoint_06_before_evaluation
checkpoint_07_sse_streaming
```

落地方式有两种：

第一种是**数据库种子数据**。
例如准备 SQL / JSON fixture，直接构造一个已经面试到第 5 题的 session。

```
tests/fixtures/db/interview-session-after-5-questions.sql
tests/scripts/seed-interview-checkpoint.sh after-5-questions
```

第二种是**测试专用接口**，只在 `local` / `test` profile 开启。
例如：

```
POST /test-support/interview-sessions/from-checkpoint
```

传入：

```
{
  "checkpoint": "after_5_questions",
  "userId": 10001,
  "resumeFixture": "backend-engineer-3-years",
  "jobFixture": "java-backend"
}
```

注意这个接口必须只在测试环境启用，不能进生产 profile。

这样修一个第 10 轮才出现的 bug，复现时间可以从 20 分钟降到 10 秒。

**3. 给 AI / LLM 做“可控替身”，不要每次调真实模型**

现在架构里 Java Resume / Interview Service 会通过 WebClient 调 Python AI 服务，Python AI 又会调 DeepSeek / DashScope。这个链路真实、慢、贵、还不稳定，非常不适合日常 bug 复现。

建议分三种模式：

```
real-ai       真正调用 DeepSeek / DashScope，只用于少量 smoke 或验收
stub-ai       Python AI 服务返回固定响应，用于大多数集成测试
replay-ai     根据历史 trace 返回当时录到的 AI 响应，用于 bug 复现
```

例如 Java 侧配置：

```
python-ai:
  base-url: http://localhost:18000
```

本地起一个测试替身服务：

```
tests/scripts/start-ai-stub.sh
```

这个 stub 服务根据场景返回固定内容：

```
{
  "scene": "generate_question",
  "response": {
    "question": "请介绍一下你最近做过的一个后端项目。",
    "questionType": "project_experience"
  }
}
```

如果是回放模式，stub 直接从 trace 文件里读历史 AI 响应。这样同一个 bug 可以 100% 重现，不会因为模型这次换了回答而变成“偶现”。

Python 侧如果用 LangChain，也可以引入类似 `FakeListLLM` / 自定义 fake provider 的方式，把 LLM 响应固定下来。关键是：**测试主流程时，AI 输出必须可控**。

**4. 把测试分成“快路径”和“真实路径”**

不要让所有测试都跑完整真实链路。建议按层分：

```
快路径：几十秒内完成，日常修 bug 必跑
真实路径：几分钟到几十分钟，发版前或人工验收跑
```

推荐分层如下：

| 层级          | 目标                                        | 是否真实调用 AI | 是否完整流程        |
| ------------- | ------------------------------------------- | --------------- | ------------------- |
| 单元测试      | 测某个状态流转 / prompt 构造 / 评分解析     | 否              | 否                  |
| API 回放测试  | 用 trace 复现某个 bug                       | 否，使用 replay | 否，直达问题阶段    |
| 集成测试      | Java 服务 + Python stub + DB/Redis          | 否              | 部分流程            |
| 本地 smoke    | Gateway + Java + Python stub + Flutter 可选 | 否              | 主流程              |
| 真实 AI smoke | 全链路 + DeepSeek/DashScope                 | 是              | 只跑 1-2 条黄金路径 |

真正耗资源的真实 AI 流程，只保留少量“黄金路径”：

```
tests/fixtures/interview-traces/golden-java-backend-basic.jsonl
tests/fixtures/interview-traces/golden-resume-evaluation-basic.jsonl
```

日常 bug 修复主要依赖 replay / stub / checkpoint。

**5. 增加面试状态机测试，避免只靠端到端发现问题**

面试流程本质上应该是一个状态机，比如：

```
CREATED
RESUME_READY
JOB_SELECTED
INTERVIEWING
WAITING_CANDIDATE_ANSWER
GENERATING_FOLLOWUP
EVALUATING
COMPLETED
FAILED
```

很多 bug 其实是状态流转错了，比如：

```
WAITING_CANDIDATE_ANSWER -> GENERATING_FOLLOWUP
GENERATING_FOLLOWUP -> WAITING_CANDIDATE_ANSWER
INTERVIEWING -> EVALUATING
EVALUATING -> COMPLETED
```

建议把核心流转逻辑抽出来测，不要所有逻辑都藏在 Controller / WebClient / SSE 里。

例如针对这些场景写测试：

```
候选人回答为空时不能进入下一题
AI 生成失败时 session 应该进入可重试状态
SSE 中断后再次连接可以恢复当前问题
第 N 题结束后应进入评分阶段
重复提交同一轮答案不能生成两道追问题
```

这种测试跑起来非常快，定位也非常准。

**6. 给 SSE / 流式输出单独做可复现测试**

面试过程如果有 SSE，bug 常见于：

```
连接中断
重复推送
最后一个 done 事件丢失
前端收到半截 JSON
服务端异常后前端状态卡住
重新连接后上下文错乱
```

这类不要靠人工点前端复现。应该单独做 SSE 脚本：

```
tests/scripts/replay-sse-interview.sh tests/fixtures/interview-traces/bug-sse-disconnect.jsonl
```

脚本可以验证：

```
是否收到 message 事件
是否收到 done 事件
事件顺序是否正确
断线重连后 sessionId 是否一致
重复连接是否产生重复问题
```

也可以准备故障注入：

```
第 3 个 token 后断开
AI 响应延迟 5 秒
AI 返回非法 JSON
Python 服务 500
Redis 短暂不可用
```

这会比“重新跑一整场面试然后等它偶发”高效很多。

**7. 让每次面试都有完整 Timeline，定位会快很多**

要想快速定位，日志不能只打印“调用失败”。建议每个 interview session 记录一条 timeline，至少包含：

```
sessionId
userId
resumeId
jobId
currentRound
currentQuestionId
state
requestId / traceId
AI request scene
AI response summary
DB write result
SSE event count
error code
durationMs
```

比如日志变成这样：

```
[interview-session=abc123] state=WAITING_CANDIDATE_ANSWER round=5 event=answer_received durationMs=12
[interview-session=abc123] state=GENERATING_FOLLOWUP round=5 event=ai_request scene=followup_question
[interview-session=abc123] state=GENERATING_FOLLOWUP round=5 event=ai_response durationMs=1830
[interview-session=abc123] state=WAITING_CANDIDATE_ANSWER round=6 event=question_sent sseEvents=14
```

这样出 bug 时能直接看出卡在哪：

```
是前端没发 answer？
是 Gateway 拦了？
是 Java Interview Service 状态没推进？
是 Python AI 没返回？
是 LLM 返回格式不对？
是 SSE 推送丢了？
是数据库没保存？
```

**8. 当前项目里我建议这样落地**

结合你这个 monorepo 的结构，我建议按这个目录组织：

```
tests/
  scripts/
    replay-interview.sh
    seed-interview-checkpoint.sh
    start-ai-stub.sh
    smoke-interview-flow.sh

  fixtures/
    interview-traces/
      golden-java-backend-basic.jsonl
      bug-sse-disconnect.jsonl
      bug-question-loop.jsonl

    db/
      interview-session-after-5-questions.sql
      resume-ready.sql
      before-evaluation.sql

    ai-responses/
      generate-question-basic.json
      followup-question-basic.json
      evaluation-basic.json

  config/
    local-replay.env.example
    stub-ai.env.example

  reports/
    replay/

  docs/
    test-cases.md
    tooling-guide.md
```

这也符合你项目 AGENTS 里说的：跨项目、smoke、API、E2E、性能、安全、AI safety 测试都放 root `tests/`，不要散落在业务目录里。

**9. 最小可行版本可以很小，不需要一下做大**

第一阶段我建议只做这 4 件事：

1. **增加 AI stub 服务或 stub 模式**
   让 Java Interview Service 可以不调用真实 Python AI / 真实 LLM。
2. **增加一条 replay 脚本**
   从 JSONL 读取候选人输入，按顺序调用面试 API。
3. **保存一条黄金路径 trace**
   比如“Java 后端 3 年经验候选人，完成 3 轮面试”。
4. **补一份文档**
   写清楚如何录制 bug、如何回放 bug、如何新增 trace。

这个版本就已经能解决 60%-70% 的复现痛点。

后面再加：

```
数据库 checkpoint
SSE 专项测试
故障注入
前端自动化回放
CI / 本地一键 smoke
```

**10. 推荐的最终工作流**

以后遇到 bug 时，流程应该变成这样：

```
1. 测试人员跑主流程，发现 bug
2. 保存当前 trace：bug-xxx.jsonl
3. 开发本地启动 Java + Python stub / replay-ai
4. 执行 replay-interview.sh bug-xxx.jsonl
5. 10 秒内复现
6. 查看 timeline / session 状态 / API 响应
7. 修复
8. 把这个 trace 注册到 tests/docs/test-cases.md
9. 以后每次跑 replay 都能防回归
```

而不是：

```
重新登录
重新上传简历
重新选岗位
重新回答一堆问题
等待 AI 生成
等到第 N 轮
祈祷 bug 再出现
```

**我的建议优先级**

最优先做：

```
AI stub / replay-ai
面试 trace JSONL
replay-interview.sh
关键 session timeline 日志
```

第二优先：

```
数据库 checkpoint
SSE 专项回放
故障注入
状态机单元测试
```

第三优先：

```
Flutter 自动化 E2E
真实 AI 黄金路径 smoke
CI 集成
```

一句话总结：
**把“真实长面试”变成一次性录制，把“bug 复现”变成短路径回放，把“AI 输出”变成可控输入，把“定位”变成看 timeline 和状态机。** 这样后面修 bug 的成本会明显下降，而且每修一个 bug，都会沉淀成一个可重复跑的回归用例。