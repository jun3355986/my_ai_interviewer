# 技术面试环节修复背景上下文

这份文档用于在新的对话中快速引入背景：为什么技术面试环节会卡住或被跳过、当前采用了什么修复方向、推荐的新修复流程应该怎样推进。完整架构图、状态机图和推荐改造时序图见 [ARCHITECTURE.md](./ARCHITECTURE.md)。

## 1. 问题现象

测试面试流程时，项目提问结束后，系统会先提示：

```text
项目提问环节结束，进入技术面试环节
```

但候选人回复“好的”之后，系统没有真正提技术题，而是直接提示：

```text
所有技术问题已回答，面试结束
```

表面上看是“跳过技术面试”，实际是统一聊天接口在 `technical_qna` 阶段把候选人的确认语误当成了技术题答案。

## 2. 根因

当前前端只走统一 SSE 接口 `/interviews/chat`，不会单独调用 Python 旧的分阶段接口 `/interview/{session_id}/start-technical`。

原始设计里，技术题池只会在 `start_technical_interview` 中初始化：

1. 选择技术问题。
2. 将第一道题写入对话历史。
3. 将剩余技术题写入 `technical_questions_pool`。

但统一 `/chat` 链路在项目问答结束时只做了阶段切换：

```text
project_qna -> technical_qna
```

它没有自动初始化技术题池，也没有下发第一道技术题。于是下一轮用户输入到达时，Python 状态机看到当前阶段已经是 `technical_qna`，就直接调用 `handle_technical_answer`。这会导致：

1. 用户的“好的/继续”被当成技术题答案。
2. `technical_questions_pool` 为空。
3. 系统判断所有技术题已答完，直接进入 `concluded`。

## 3. 当前修复方向

当前修复方向不是让前端改成手动调用 `start-technical`，而是保持前端继续使用统一 `/interviews/chat`。

原因：

1. 前端已经按统一聊天接口设计，不应该感知太多后端阶段细节。
2. Java `interview` 服务只是 SSE 代理和业务持久化层，技术题状态机的单一事实来源应该仍在 Python AI 服务。
3. 技术题初始化属于面试状态机内部职责，应该由 Python 在进入 `technical_qna` 时自动完成。

因此推荐修复策略是：

```text
进入 technical_qna 时，若技术题未初始化，则自动初始化并直接下发第一道技术题。
```

## 4. 新修复方案流程

推荐流程如下：

1. 候选人回答最后一个项目问题。
2. Python 状态机完成项目回答评分。
3. Python 判断项目题已达到目标数，或项目问题池为空。
4. Python 将阶段切换为 `technical_qna`。
5. Python 检查技术题池是否已初始化。
6. 如果未初始化，Python 调用 `select_technical_questions` 选择技术题。
7. Python 将第一道技术题写入对话历史。
8. Python 将剩余技术题保存到 `technical_questions_pool`。
9. Python 通过 SSE 返回第一道技术题，而不是只返回“进入技术面试环节”。
10. 候选人下一条输入才会被视为第一道技术题的回答。
11. 每答完一道技术题，Python 从 `technical_questions_pool` 中 `pop(0)` 取下一题。
12. 只有当最后一道真实技术题回答完成，并且题池为空时，系统才进入 `concluded`。

对应的推荐改造时序图已写在 [ARCHITECTURE.md](./ARCHITECTURE.md) 的“推荐改造时序图（技术题首题自动初始化）”章节。

## 5. 关键验收点

修复后至少要满足：

1. 项目问答结束后，系统直接下发第一道技术题。
2. “好的/继续/明白”等确认语不会被记为技术题答案。
3. 技术题数量与评分记录数量一致。
4. 技术面试只在真实技术题全部回答后结束。
5. 恢复会话时，仍能找回当前技术题或下一道技术题，不出现阶段跳跃。

## 6. 新对话建议起点

如果在新对话中继续实现，可以直接引用本文件并说明目标：

```text
请基于 docs/TECHNICAL_INTERVIEW_FIX_CONTEXT.md 和 docs/ARCHITECTURE.md，
实现技术面试环节的自动首题初始化，保持前端继续走统一 /interviews/chat。
```
