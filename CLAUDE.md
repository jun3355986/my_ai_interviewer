# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

- Flutter defaults to gateway at `http://localhost:9000`; compile-time flag `USE_LEGACY_DIRECT_PORTS=true` switches to direct ports (9001 user, 9002 resume, 9003 interview, 9004 job)
- Java Resume/Interview services call Python AI service via WebClient (configured as `python-ai.base-url`)
- Gateway handles all auth — downstream services receive `X-User-Id`, `X-User-Name`, `X-User-Roles` headers

## Infrastructure

Docker Compose (`ai_interview_backend/docker-compose.yml`):
- **Nacos** :8848 — service discovery + config center
- **PostgreSQL** :5433 (internal 5432) — all Java microservices share one instance
- **Redis** :6380 (internal 6379) — JWT token blacklist, session cache
- **MinIO** :19000/:19001 (internal 9000/9001) — file/resume storage

```bash
# Start all infra
cd ai_interview_backend && docker compose up -d
```

**Java Microservices Ports:**
- Gateway: 9000, User: 9001, Resume: 9002, Interview: 9003, Job: 9004, Evaluation: 9005, Notification: 9006

**No CI/CD pipeline exists.** No GitHub Actions, Jenkinsfile, or Makefile.

## Commands

### Python AI Service (`ai_interviewer/`)

```bash
uv sync                          # install deps
uv run python main.py            # production (port 8000)
uv run python run_debug.py       # hot-reload dev mode
uv run python test_interview.py  # dual-agent simulation (not unit tests)
```

### Flutter Frontend (`ai_interviewer_front/`)

```bash
flutter pub get                  # install deps
flutter run                      # run (prompts device selection)
flutter run --dart-define=USE_LEGACY_DIRECT_PORTS=true   # bypass gateway, hit services directly
flutter build web                # web build
```

### Java Backend (`ai_interview_backend/`)

```bash
# From repo root — build entire multi-module project
./mvnw clean install -DskipTests

# Run a specific service (example: interview service)
cd ai-interviewer-interview && ../mvnw spring-boot:run

# Run all services (no script exists — open separate terminals per module)
```

## Required Environment Variables

```bash
# Python AI service
DEEPSEEK_API_KEY=         # mandatory — LLM provider
DEEPSEEK_BASE_URL=        # default: https://api.deepseek.com/v1
DEEPSEEK_MODEL=           # default: deepseek-chat
DASHSCOPE_API_KEY=        # mandatory — Alibaba DashScope embeddings

# Java backend (via Nacos or application.yml)
DB_HOST=                  # default: localhost
DB_PORT=                  # default: 5433
DB_USERNAME=              # default: postgres
DB_PASSWORD=              # default: postgres
REDIS_HOST=               # default: localhost
REDIS_PORT=               # default: 6380
MINIO_ENDPOINT=           # default: http://localhost:19000
NACOS_SERVER_ADDR=        # default: localhost:8848
```

## Python AI Service Structure

```
api/
  router.py          # /interview/* endpoints (SSE + JSON)
  resume_router.py   # /resume/* endpoints
  interviewer.py     # LangChain chain assembly
  sse.py             # SSE event helpers (EVENT_STATUS/CHUNK/SCORE/RESULT/DONE/ERROR)
core/
  config.py          # get_llm() — builds DeepSeek ChatOpenAI client
  embeddings.py      # DashScope embeddings for ChromaDB
services/
  interview_session.py  # InterviewStage enum + InterviewSession dataclass
  interview_service.py  # session CRUD, delegates to Interviewer
  question_bank.py      # ChromaDB-backed question search/import
  resume_parser.py      # PDF parsing via pypdf
  database.py           # SQLite via SQLAlchemy (interview_records.db)
schemas/
  chat.py            # Pydantic request/response models
storage/             # ChromaDB vector store files
```

**Interview flow stages** (`InterviewStage`):
`RESUM_SUBMITTED` → `OPENING` → `SELF_INTRO` → `PROJECT_QNA` → `TECHNICAL_QNA` → `CONCLUDED`

## Java Backend Structure

Each microservice follows the same internal layout:
```
{service}/src/main/java/com/aiinterviewer/{service}/
  controller/   # REST controllers
  service/      # business logic interfaces + impls
  mapper/       # MyBatis-Plus mappers
  entity/       # DB entities (table prefix t_)
  dto/          # request/response DTOs
  config/       # Spring beans, security config
  {Service}Application.java
```

`ai-interviewer-common` provides: `Result<T>` response wrapper, `ErrorCode`, `PageRequest/PageResult`, shared exceptions and utils.
`ai-interviewer-api` contains Feign client interfaces shared between services.

## Database Strategy

- **PostgreSQL** — Java backend: tables prefixed `t_` (t_user, t_resume, t_job, etc.), ORM via MyBatis-Plus
- **SQLite** — Python AI: `interview_records.db` for session persistence
- **ChromaDB** — Python AI: vector store for question bank embeddings

## Key Conventions

- All Java services register with Nacos; gateway routes via `lb://ai-interviewer-{service}`
- Gateway whitelist: `/auth/login`, `/auth/register`, `/auth/refresh` bypass JWT
- Unified response wrapper: `com.aiinterviewer.common.model.Result<T>`
- Python API uses Pydantic schemas for request/response validation
- Flutter uses Provider pattern for state management, Dio for HTTP; `ApiClient` centralizes base URLs and JWT injection

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

## FDE Learning Plan and Career Context

This project is also the practical training ground for the user's FDE / Applied AI career transition. The authoritative, continuously maintained plan is the Obsidian note:

`/Users/junjielong/Library/Mobile Documents/iCloud~md~obsidian/Documents/我的笔记/程序猿/面试/转行FDE 工程师计划.md`

Related Shenzhen target-job evidence is maintained in:

`/Users/junjielong/Library/Mobile Documents/iCloud~md~obsidian/Documents/我的笔记/程序猿/面试/2026-07-25 FDE 目标岗位 JD 样本（BOSS直聘）.md`

- Before planning FDE learning, portfolio work, interview preparation, résumé claims, or AI-interviewer roadmap changes, read the current Obsidian plan instead of relying on copied or remembered versions.
- Use this existing Java + Python + Flutter system as the main learning and evidence platform; do not propose rewriting it solely to adopt another language or framework.
- Align relevant project increments with the plan's loop: theory → runnable increment → automated test/evaluation → decision and failure record → demonstrable result → interview practice.
- Treat the Obsidian plan as a living source of truth for priorities, weekly tasks, acceptance criteria, progress, JD-derived gaps, and evidence links. Do not duplicate the full plan into repository instruction files.
- When the user asks to update the learning plan, or when an authorized task materially completes or invalidates a listed milestone, update the Obsidian note with the date, status, measured evidence, and links to project artifacts. Do not mark work complete based only on code presence or AI-generated output.
- Keep this section in `AGENTS.md` and `CLAUDE.md` semantically synchronized when its paths or usage rules change.

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
