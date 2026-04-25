# AI Interviewer 架构设计文档

本文档是 `my_ai_interviewer` 的项目级架构设计文档，重点覆盖当前面试链路，尤其是技术问题面试环节的运行方式与推荐改造方案。

## 1. 总览架构图

```mermaid
flowchart LR
  U["候选人"]
  F["Flutter 前端<br/>InterviewApi.chat()"]
  J["Java Interview 服务<br/>/interviews/chat<br/>SSEProxyService"]
  P["Python AI 服务<br/>/interview/chat<br/>状态机 + 面试引擎"]
  QB["题库检索<br/>QuestionBank / ChromaDB"]
  PS["Python 会话持久化<br/>SQLite interview_records"]
  JS["Java 业务持久化<br/>PostgreSQL t_interview_session / t_score_record"]

  U -->|"发送消息"| F
  F -->|"SSE 请求"| J
  J -->|"转发请求 + 透传 SSE"| P
  P -->|"技术题检索"| QB
  P -->|"保存阶段/题池/问答"| PS
  P -->|"status/chunk/score/result/done"| J
  J -->|"落库会话状态与评分"| JS
  J -->|"SSE 回传"| F
```

### 设计要点

1. 前端通过统一接口 `/interviews/chat` 交互，不直接感知具体阶段 API。
2. Java `interview` 服务承担会话归属校验、SSE 代理、评分与消息持久化。
3. Python 服务是面试状态机与题目生成/评估核心。
4. 评分与会话在 Java 侧持久化用于业务查询；Python 侧保留面试上下文与问题池。

## 2. 面试状态机图

```mermaid
stateDiagram-v2
  [*] --> opening: 创建会话
  opening --> self_introduction: 开场响应
  self_introduction --> project_qna: 提交自我介绍

  project_qna --> project_qna: 项目回答后触发追问
  project_qna --> project_qna: 项目回答后下发下一项目题
  project_qna --> technical_qna: 达到项目题目标数
  project_qna --> technical_qna: 项目问题池为空

  technical_qna --> technical_qna: 技术回答后继续下发下一题
  technical_qna --> concluded: 技术题池为空(全部回答完成)

  opening --> concluded: 手动总结/异常终止(可选)
  self_introduction --> concluded: 手动总结/异常终止(可选)
  project_qna --> concluded: 手动总结(可选)
  technical_qna --> concluded: 手动总结(可选)
```

### 状态说明

1. `opening`：系统开场阶段。
2. `self_introduction`：候选人自我介绍阶段。
3. `project_qna`：项目问答阶段，可出现追问循环。
4. `technical_qna`：技术问答阶段，按题池推进。
5. `concluded`：面试结束阶段。

## 3. 推荐改造时序图（技术题首题自动初始化）

目标：在进入 `technical_qna` 时，自动初始化并下发第一道技术题，避免“先进入技术阶段，但下一条用户消息被误当作技术题回答”。

```mermaid
sequenceDiagram
  participant C as "候选人"
  participant F as "Flutter (/interviews/chat)"
  participant J as "Java SSE 代理"
  participant P as "Python 状态机"
  participant Q as "QuestionBank"

  C->>F: 回答最后一个项目问题
  F->>J: POST /interviews/chat
  J->>P: POST /interview/chat
  P->>P: 切换 stage = technical_qna

  alt 技术题池为空且未初始化
    P->>Q: select_technical_questions(默认类型/数量)
    Q-->>P: 返回问题列表
    P->>P: 初始化 technical_questions_pool
    P-->>J: next_question = 第1道技术题
    J-->>F: SSE chunk(第1道技术题)
    F-->>C: 展示首题，等待作答
  else 技术题池已存在
    P-->>J: next_question = 题池下一题
    J-->>F: SSE chunk(下一题)
  end

  C->>F: 回答技术题
  F->>J: POST /interviews/chat
  J->>P: POST /interview/chat
  P->>P: 评分 + 下发下一题/结束
  P-->>J: SSE score/result/done
  J-->>F: SSE score/chunk/done
```

## 4. 推荐改造的实施原则

1. 保持前端仍使用统一 `/interviews/chat`，不引入前端阶段分支复杂度。
2. 将“技术题首题初始化”收敛到 Python 状态机内部，保证单一事实来源。
3. `start-technical` 可保留为兼容接口，但统一链路应不依赖它才能正常推进。
4. 对 `technical_qna` 增加兜底校验：若未初始化题池则先初始化，不直接结束。

## 5. 验收标准

1. 项目问答结束后，系统直接下发第 1 道技术题，不再只返回过渡提示语。
2. 候选人发送“好的/继续”等确认词不会被记为技术题答案。
3. 技术题数量与评分记录一致，面试在题池真正耗尽后结束。
4. 恢复会话后，能够继续当前技术题流程，不出现阶段跳跃。
