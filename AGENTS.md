# AI Interviewer — Monorepo 根目录

回复、写文档以中文为主，除了专业术语、代码等一些用原生语言比较适合的地方除外。

## 架构

三个相互独立的子项目，各自有**独立的 .git 仓库**，没有共享的构建系统。

```
ai_interviewer/          # Python — AI 核心（FastAPI + LangChain + DeepSeek）
ai_interviewer_front/    # Flutter/Dart — 移动端/Web UI
ai_interview_backend/    # Java 21 — Spring Boot 3.3.5 微服务（9 个模块）
```

## 跨项目数据流

```
Flutter UI ──HTTP──▶ Gateway (:9000) ──lb://──▶ Java microservices
                                                    │
                              Resume Service ──WebClient──▶ Python AI (:8000)
                              Interview Service ──SSE proxy──▶ Python AI
```

- Flutter 目前直连 `http://localhost:9001`（用户服务）和 `http://localhost:9004`（职位服务），绕过了 Gateway
- Java Resume/Interview 服务通过 WebClient 调用 Python AI 服务（配置项为 `python-ai.base-url`）
- 所有认证由 Gateway 统一处理 — 下游服务通过 `X-User-Id`、`X-User-Name`、`X-User-Roles` 请求头获取用户信息

## 基础设施

Docker Compose（`ai_interview_backend/docker-compose.yml`）：
- **Nacos** :8848 — 服务发现 + 配置中心
- **PostgreSQL** :5433（容器内部 5432）— 所有 Java 微服务共用一个实例
- **Redis** :6380（容器内部 6379）— JWT token 黑名单、会话缓存
- **MinIO** :19000/:19001（容器内部 9000/9001）— 文件/简历存储

**Java 微服务端口：**
- User: 9001, Resume: 9002, Interview: 9003, Job: 9004, Evaluation: 9005, Notification: 9006

**目前没有 CI/CD 流水线。** 没有 GitHub Actions、Jenkinsfile 或 Makefile。

## 包管理器

| 项目 | 管理器 | 安装依赖 | 运行 |
|---------|---------|---------|-----|
| ai_interviewer | uv | `uv sync` | `uv run python main.py` |
| ai_interviewer_front | flutter pub | `flutter pub get` | `flutter run` |
| ai_interview_backend | Maven | `./mvnw install` | 按模块 `./mvnw spring-boot:run` |

## 必需的环境变量

```bash
# Python AI 服务
DEEPSEEK_API_KEY=         # 必填 — LLM 提供方
DEEPSEEK_BASE_URL=        # 默认：https://api.deepseek.com/v1
DEEPSEEK_MODEL=           # 默认：deepseek-chat
DASHSCOPE_API_KEY=        # 必填 — 阿里 DashScope embeddings

# Java 后端（通过 Nacos 或 application.yml 配置）
POSTGRES_HOST=            # 默认：localhost
REDIS_HOST=               # 默认：localhost
MINIO_ENDPOINT=           # 默认：http://localhost:19000
```

## 数据库策略

- **PostgreSQL** — Java 后端：表名统一加 `t_` 前缀（t_user、t_resume、t_job 等）
- **SQLite** — Python AI：本地 `interview_records.db`，用于会话持久化
- **ChromaDB** — Python AI：题库 embeddings 的向量存储

## 关键约定

- 所有 Java 服务注册到 Nacos；Gateway 通过 `lb://ai-interviewer-{service}` 路由
- Gateway 白名单：`/auth/login`、`/auth/register`、`/auth/refresh` 绕过 JWT
- 统一响应包装类：`com.aiinterviewer.common.model.Result<T>`
- Python API 使用 Pydantic schema 做请求/响应校验
- Flutter 使用 Provider 模式做状态管理，HTTP 用 Dio

## 测试现状

各项目测试覆盖都很有限：
- Python：`test_interview.py` 是双 Agent 模拟脚本，不是单元测试
- Flutter：只有脚手架 `widget_test.dart`
- Java：测试目录里只有 `PasswordGenerator.java` — 不是真正的测试

## 测试资产管理

根目录 `tests/` 是本项目统一的测试资产中心。

- 跨项目、跨服务、冒烟、API、E2E、性能、安全和 AI 安全测试统一放在 `tests/` 下。
- 各技术栈原生的单元测试保留在其默认位置：
  - Python: `ai_interviewer/tests/`
  - Flutter: `ai_interviewer_front/test/`
  - Java: `*/src/test/java/`
- 新增或变更测试用例时，必须在 `tests/docs/test-cases.md` 中登记。
- 每当新增或修改测试命令、框架或必需环境变量时，同步更新 `tests/docs/tooling-guide.md`。
- 共享测试数据放在 `tests/fixtures/`。
- 提交到仓库的配置模板放在 `tests/config/`；绝不提交密钥。
- 测试产出物放在 `tests/reports/` 或有明确文档说明的证据目录。
- 不要把测试脚本、payload 或报告散落到业务源码目录。
- 修 bug 需要回归测试时，把回归场景及其自动化位置登记到 `tests/docs/test-cases.md`。
- 可复用的本地/CI 执行命令优先做成根目录 `tests/scripts/` 下的封装脚本。

## FDE 学习计划与职业背景

本项目同时是用户 FDE / Applied AI 转型实战的训练场。当前采用“知识单元驱动”的动态学习方式；相关 Obsidian 笔记均位于：

`/Users/junjielong/Library/Mobile Documents/iCloud~md~obsidian/Documents/我的笔记/程序猿/面试/`

| 文件 | 作用 | 何时读 |
|---|---|---|
| `转行FDE 工程师计划.md` | 战略：定位、能力差距、阶段总览、投递门槛（季度级更新） | 讨论方向、简历表述、路线取舍时 |
| `FDE 学习进行中清单.md` | **当前学习工作台**：选题、问题树、官方资料、实验、输出、项目应用和验收 | **开始或继续任何 FDE 学习时先读** |
| `FDE 能力验收与面试题库.md` | 验收：面试问题、项目真实数据锚点、60 秒回答模板 | 准备面试、验收知识点时 |
| `FDE 24周任务清单.md` | 参考：原 24 周任务、工时、AI 分工和实验点子 | 查漏补缺或寻找实验点子时；不决定学习顺序 |
| `FDE 进度看板.md` | 历史参考：原周报聚合、工时、指标基线、投递记录 | 查历史进度或项目指标证据时 |

深圳目标岗位证据：`2026-07-25 FDE 目标岗位 JD 样本（BOSS直聘）.md`；每周周报在 `FDE周报/` 目录。

- 开始或继续 FDE 学习时，先读 `FDE 学习进行中清单.md` 的当前知识单元；讨论战略、知识范围或投递条件时再读主计划，不要依赖复制或记忆中的版本。
- 当前学习不按 24 周顺序推进。用户根据兴趣、项目问题和面试缺口选择一个知识单元，通过“发散提问 → 官方资料/源码 → 亲自实验 → 学习文档 → 本人脱稿视频 → AI 辅助演示 → 项目应用 → 题库验收”完成闭环。
- **遵守当前知识单元的 AI 协作边界**：核心实验、结果解释、技术取舍、学习文档的核心观点与第一版、本人脱稿视频由用户亲自完成；AI 可翻译英文官方文档、解释术语、搭建实验脚手架、准备数据、协助排错、事实核查、当考官以及制作辅助演示素材。验收时先提问和追问，不先给标准答案。
- 一次只维护一个主知识单元。相关问题进入支线；有价值但会改变当前方向的问题写入 `FDE 学习进行中清单.md` 的“问题停车场”，不要立刻扩张任务范围。
- 以现有 Java + Python + Flutter 系统作为主要的学习和证据平台；不要为了换语言/框架而提议重写。
- 项目增量要与知识单元闭环对齐：问题 → 原理/资料 → 可运行实验 → 自动化验证 → 决策与失败记录 → 项目应用 → 可展示成果 → 面试追问。
- 把这套笔记当作学习状态、知识范围、验收标准、JD 差距和证据链接的唯一事实来源；不要把完整计划复制进仓库指令文件。
- 当某个已授权学习或项目任务实质性完成时，用日期、状态、量化证据和项目产物链接更新对应笔记：当前过程和问题写学习进行中清单，卡壳点和新验收问题写题库，历史任务清单不要求同步勾选。不要仅凭代码存在或 AI 生成的输出标记知识单元闭环。
- 新发现的系统问题优先写入当前知识单元的主线、支线或问题停车场；只有选择为当前主题后才展开，不再强制排进某个周次。
- 当本节的路径或使用规则变化时，保持 `AGENTS.md` 和 `CLAUDE.md` 中这一节语义同步。


<!-- setting_my_dev:start -->

每次收到任务时，先判断规模，后续所有工具选择都基于此判断：

| 规模 | 判定条件（满足任一） | 标记 |
|------|-------------------|------|
| 🟢 小 | 预计改动 ≤50 行；纯 bugfix；只改配置/文案/样式 | `[S]` |
| 🟡 中 | 预计 50-300 行；单模块新功能；接口变更 | `[M]` |
| 🔴 大 | 预计 >300 行；跨模块；新增子系统；架构调整 | `[L]` |

判断规模后在回复开头标注（如 `[M] 开始实现用户头像上传`），让我能校准。
如果拿不准，按大的算。

#### 🟢 小变更 [S]
非指定，不要使用 superpowers TDD

#### 项目级 Skill 调用
- `/grill-me`、`/grill-with-docs`、`/brainstorming`、`/writing-plans` 仅在用户明确点名时使用；不要自动触发，也不要通过 `using-superpowers` 代管。
- 项目级 `.codex/skills` 和 `.claude/skills` 不引入 `using-superpowers`；需要时再手动引入。
- gstack 不在用户级安装。项目需要 gstack 能力时，只把对应 gstack skill 从 `/Users/junjielong/job_work_env/gstack/<skill>` 按需软链接到项目 `.codex/skills` / `.claude/skills`，并且仅在用户明确点名时使用。
- 不要让 gstack `ship` 默认接管公司项目发布；commit / push / Jenkins / dev 部署 / live smoke 按项目 AGENTS、CLAUDE 和运维文档执行。
- 旧 gstack `/checkpoint` 已弃用；若项目按需引入 gstack，上下文保存/恢复使用 `context-save` / `context-restore`。
<!-- setting_my_dev:end -->
