# AI Interviewer — Monorepo Root

## Architecture

Three independent sub-projects, each with **own .git repo**. No shared build system.

```
ai_interviewer/          # Python — AI core (FastAPI + LangChain + DeepSeek)
ai_interviewer_front/    # Flutter/Dart — Mobile/web UI
ai_interview_backend/    # Java 21 — Spring Boot 3.3.5 microservices (9 modules)
```

## Cross-Project Data Flow

```
Flutter UI ──HTTP──▶ Gateway (:9000) ──lb://──▶ Java microservices
                                                    │
                              Resume Service ──WebClient──▶ Python AI (:8000)
                              Interview Service ──SSE proxy──▶ Python AI
```

- Flutter currently points to `http://localhost:9001` (user) and `http://localhost:9004` (job) bypassing gateway
- Java Resume/Interview services call Python AI service via WebClient (configured as `python-ai.base-url`)
- Gateway handles all auth — downstream services receive `X-User-Id`, `X-User-Name`, `X-User-Roles` headers

## Infrastructure

Docker Compose (`ai_interview_backend/docker-compose.yml`):
- **Nacos** :8848 — service discovery + config center
- **PostgreSQL** :5433 (internal 5432) — all Java microservices share one instance
- **Redis** :6380 (internal 6379) — JWT token blacklist, session cache
- **MinIO** :19000/:19001 (internal 9000/9001) — file/resume storage

**Java Microservices Ports:**
- User: 9001, Resume: 9002, Interview: 9003, Job: 9004, Evaluation: 9005, Notification: 9006

**No CI/CD pipeline exists.** No GitHub Actions, Jenkinsfile, or Makefile.

## Package Managers

| Project | Manager | Install | Run |
|---------|---------|---------|-----|
| ai_interviewer | uv | `uv sync` | `uv run python main.py` |
| ai_interviewer_front | flutter pub | `flutter pub get` | `flutter run` |
| ai_interview_backend | Maven | `./mvnw install` | per-module `./mvnw spring-boot:run` |

## Required Environment Variables

```bash
# Python AI service
DEEPSEEK_API_KEY=         # mandatory — LLM provider
DEEPSEEK_BASE_URL=        # default: https://api.deepseek.com/v1
DEEPSEEK_MODEL=           # default: deepseek-chat
DASHSCOPE_API_KEY=        # mandatory — Alibaba DashScope embeddings

# Java backend (via Nacos or application.yml)
POSTGRES_HOST=            # default: localhost
REDIS_HOST=               # default: localhost
MINIO_ENDPOINT=           # default: http://localhost:19000
```

## Database Strategy

- **PostgreSQL** — Java backend: tables prefixed `t_` (t_user, t_resume, t_job, etc.)
- **SQLite** — Python AI: local `interview_records.db` for session persistence
- **ChromaDB** — Python AI: vector store for question bank embeddings

## Key Conventions

- All Java services register with Nacos; gateway routes via `lb://ai-interviewer-{service}`
- Gateway whitelist: `/auth/login`, `/auth/register`, `/auth/refresh` bypass JWT
- Unified response wrapper: `com.aiinterviewer.common.model.Result<T>`
- Python API uses Pydantic schemas for request/response validation
- Flutter uses Provider pattern for state management, Dio for HTTP

## Testing Status

Minimal test coverage across all projects:
- Python: `test_interview.py` is a dual-agent simulation script, not unit tests
- Flutter: only scaffold `widget_test.dart`
- Java: only `PasswordGenerator.java` in test dirs — not actual tests

## Test Asset Management

The root `tests/` directory is the unified test asset center for this project.

- Put cross-project, cross-service, smoke, API, E2E, performance, security, and AI safety tests under `tests/`.
- Keep project-native unit tests in each technology's default location:
  - Python: `ai_interviewer/tests/`
  - Flutter: `ai_interviewer_front/test/`
  - Java: `*/src/test/java/`
- Register every new or changed test case in `tests/docs/test-cases.md`.
- Update `tests/docs/tooling-guide.md` whenever adding or changing a test command, framework, or required environment variable.
- Store shared test data in `tests/fixtures/`.
- Store committed config templates in `tests/config/`; never commit secrets.
- Store generated test output in `tests/reports/` or a clearly documented evidence directory.
- Do not scatter test scripts, payloads, or reports into business source directories.
- When fixing a bug with a required regression test, document the regression scenario and its automation location in `tests/docs/test-cases.md`.
- Prefer root wrapper commands in `tests/scripts/` for repeatable local and CI execution.

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
