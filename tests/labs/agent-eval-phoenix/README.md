# Agent 评测 · Phoenix 本地实验台

这个目录只提供实验环境约定；`agent1-case-001` 的 Case、Trace 初始化、Runner、Rubric 和实验结论由 Drake 亲自编写、运行和解释。

## 一次性准备

1. 在仓库根目录复制本地配置：

   ```bash
   cp tests/config/phoenix.local.env.example tests/config/phoenix.local.env
   ```

2. 在一个 Cursor 终端启动 Phoenix：

   ```bash
   tests/scripts/start-phoenix-local.sh
   ```

3. 浏览器打开 <http://127.0.0.1:6006>。

4. 新建 Notebook 后，在 Cursor 的 Kernel 选择器中选中：

   ```text
   Python (AI Interviewer · Phoenix Lab)
   ```

`register()` 会从仓库根目录的忽略配置 `.env.phoenix` 自动发现本地 Endpoint 和项目名，因此 Notebook 不需要复制这些配置；仍可以显式传参来学习每个配置项的作用。

## 实验边界

- 仅使用脱敏的简历摘要、自我介绍和 JD；不要将候选人的真实姓名、联系方式或完整隐私材料写入 Notebook、Case 或 Phoenix Trace。
- 首轮固定 Agent 代码、模型、Prompt、Tool、数据版本和环境；只观察重复运行带来的差异。
- 首轮保持 `LANGSMITH_TRACING=false`，避免把同一个调用同时上报到 LangSmith 和 Phoenix。
- Trace 只负责定位和解释过程；是否成功由你先定义的业务标准、人工 Rubric 与后续 Grader 决定。
- 本地 Phoenix 已关闭产品遥测、Playground 外网访问和服务器端 Bash；首轮也隐藏外部模型 Provider。它们都不是 Trace 观察所需能力。

## 建议的 Notebook Cell 顺序

1. 写清 `agent1-case-001` 的成功、严重失败和运行前预测。
2. 读取本地 Phoenix 环境变量并初始化 Trace Provider。
3. 以真实 `Interviewer.generate_project_questions()` 路径运行一次脱敏 Case。
4. 在 Phoenix 中核对根 Trace、LLM 子 Span、耗时、错误和关联元数据。
5. 固定输入重复运行 3 次，记录每次输出和 Trace 链接，再由你人工评分。

在你能解释这 3 次结果之前，不要扩展到自动 Grader、LLM-as-Judge 或 Evaluation Center。
