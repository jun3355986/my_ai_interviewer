# Git 版本管理与运维部署复盘

复盘日期：2026-06-25

## 结论摘要

`ai_interviewer/storage/vector_db/chroma.sqlite3` 进入 Git 不是一个合适的长期设计，更像是仓库合并和后续提交时把运行态 ChromaDB 持久化目录一并纳入版本管理导致的遗留问题。

当前项目对数据库和状态组件的迭代运维支持不完整：

- PostgreSQL 首次初始化有 `ai_interview_backend/sql/init.sql`。
- Admin 后台库表有 Flyway 迁移 `ai_interviewer_admin/src/main/resources/db/migration/V*.sql`。
- Java 主业务库表仍主要依赖 Docker entrypoint 的初始化 SQL，不具备完整的持续迁移机制。
- Redis、Nacos、MinIO、ChromaDB 只有 Compose 持久化卷或宿主机目录挂载，没有备份、恢复、升级、回滚脚本。

## 为什么 chroma.sqlite3 会被 Git 跟踪

现状证据：

- 当前 Git 仍跟踪 `ai_interviewer/storage/vector_db/chroma.sqlite3`。
- 该文件是 SQLite 数据库，大小约 11 MB，内部包含 Chroma 表，例如 `collections`、`embeddings`、`embedding_metadata`、`migrations`、`segments`。
- Git 历史显示它在 `6b0735f chore: flatten subprojects into root repository` 中从 0 增加到约 11 MB，说明是在扁平化导入子项目时被一并提交。
- 后续 `fc53e43 chore: commit current project changes` 又更新了该二进制文件，说明运行或测试产生的向量库变化继续被当作源码变更提交。
- 当前根 `.gitignore` 只忽略了 `ai_interviewer/storage/database/*.db`，没有忽略 `ai_interviewer/storage/vector_db/`。
- Python AI 的 `.dockerignore` 已经忽略 `storage/vector_db`，说明镜像构建层面已经把它视为运行态数据，但 Git 层面没有同步这个规则。

代码层面也能确认它是运行态状态：

- `QuestionBank.__init__` 固定把 Chroma 持久化目录设为 `ai_interviewer/storage/vector_db`。
- Admin 题库同步接口 `/admin/question-bank/sync` 会调用 `sync_structured_questions()` 写入向量库。
- Admin 题库删除接口 `/admin/question-bank/delete` 会调用 `delete_structured_questions()` 删除向量库记录。

因此，`chroma.sqlite3` 和旁边的 HNSW 索引文件不是源码、配置或迁移脚本，而是会随本地运行、测试、题库同步改变的派生状态。

## Git 应该跟踪什么

应该跟踪：

- 业务源代码：Python、Java、Flutter、React/Admin Web 源码。
- 构建定义：`pom.xml`、`pyproject.toml`、`uv.lock`、`pubspec.yaml`、`pubspec.lock`、`package.json`、`package-lock.json`。
- 容器与编排定义：`Dockerfile`、`.dockerignore`、`docker-compose.yml`、Nginx 配置、启动脚本。
- 无密钥配置模板：`.env.example`、测试环境配置模板、示例 endpoint 配置。
- 数据库结构版本：Flyway/Liquibase 迁移脚本、初始化 SQL、可重复执行的 seed 脚本。
- 自动化测试资产：`tests/` 下的脚本、fixtures、测试文档、`.gitkeep`。
- 项目文档：README、架构文档、部署说明、测试策略、复盘文档。
- 必需的静态资源：前端源码内的图片、图标、字体、公开素材。
- 项目级 AI/agent/worktree 配置：`.cursor/`、`.codex/`、`.comet/`、`.claude/`、`.mcp.json`。这些文件用于让新 worktree 继续具备相同的 skill、MCP 和 agent 配置，前提是其中不能包含本机私钥、token 或账号密码。

不应该跟踪：

- 运行态数据库：`ai_interviewer/storage/database/*.db`、`ai_interviewer/storage/vector_db/**`。
- ChromaDB 生成文件：`chroma.sqlite3`、`data_level0.bin`、`header.bin`、`length.bin`、`link_lists.bin` 等。
- 本地密钥和环境文件：`.env`、`.env.*`、`tests/config/local.env`，但保留 `.env.example`。
- 本地生成工具状态：`.codegraph/`、`.sisyphus/`、`.tools/`、`.my-dev-setup-backups/`。
- 构建产物：`target/`、`dist/`、`build/`、`.dart_tool/`、`node_modules/`、`.venv/`。
- 缓存和日志：`__pycache__/`、`*.pyc`、`.pytest_cache/`、`logs/`、`*.log`、测试报告输出。
- IDE 与系统文件：`.idea/`、`.vscode/`、`.DS_Store`。

例外原则：

- 如果需要交付一份固定题库，不要提交 ChromaDB 二进制产物；应该提交可审查的源数据，例如 JSON、CSV、Markdown、SQL seed 或 Admin 题库导入文件。
- 如果需要测试 Chroma 行为，只能提交很小的测试 fixture，且应放在 `tests/fixtures/`，不能复用真实运行目录。
- `.cursor/`、`.codex/`、`.comet/`、`.claude/`、`.mcp.json` 属于本项目约定的开发环境资产，可以进入 Git；新增或修改时需要先检查是否夹带本机绝对路径、密钥、cookie、session token 或个人账号信息。

## 当前已跟踪但建议移出 Git 的内容

本次检查发现以下内容已经被 Git 跟踪，但按版本管理边界不应继续跟踪：

- `ai_interviewer/storage/vector_db/chroma.sqlite3`
- `ai_interviewer/storage/vector_db/cb8c407c-16d3-4521-9392-71dd7b603c8c/data_level0.bin`
- `ai_interviewer/storage/vector_db/cb8c407c-16d3-4521-9392-71dd7b603c8c/header.bin`
- `ai_interviewer/storage/vector_db/cb8c407c-16d3-4521-9392-71dd7b603c8c/length.bin`
- `ai_interviewer/storage/vector_db/cb8c407c-16d3-4521-9392-71dd7b603c8c/link_lists.bin`

清理时建议只移出索引，不删除本地文件：

```bash
git rm -r --cached ai_interviewer/storage/vector_db
```

然后在根 `.gitignore` 增加：

```gitignore
# Runtime vector database
ai_interviewer/storage/vector_db/
```

如果要保留空目录，使用 `.gitkeep`，不要保留实际数据库文件。

## 运维部署现状

当前本地一键部署主要依赖 `ai_interview_backend/docker-compose.yml`：

- 基础组件：Nacos、PostgreSQL、Redis、MinIO。
- 应用组件：Python AI、Java 网关与各微服务、Admin 服务、用户端前端、Admin 前端。
- PostgreSQL、Redis、Nacos、MinIO 使用 Docker volume 持久化。
- Python AI 使用宿主机目录挂载：`../ai_interviewer/storage:/app/storage`。

这套机制适合本地联调和整栈启动，但不能等同于完整运维升级体系。

## 数据库与组件升级支持情况

### PostgreSQL

已有能力：

- `postgres:16-alpine` 镜像版本固定。
- `postgres_data` volume 保存数据。
- `ai_interview_backend/sql/init.sql` 可在空库首次启动时初始化基础业务表。
- Admin 服务引入 Flyway，`V1__admin_schema.sql`、`V2__ai_observability.sql`、`V3__question_media.sql` 可随 Admin 启动迭代迁移。

缺口：

- Docker entrypoint 的 `init.sql` 只在空数据目录首次初始化时执行，已有库不会自动重放。
- Java 主业务服务没有统一 Flyway/Liquibase 迁移目录。
- 当前没有 `pg_dump` 备份、恢复、迁移前校验、迁移后 smoke 的脚本。
- `init.sql` 与 Admin Flyway 同时管理同一个 PostgreSQL 实例，长期容易出现边界混乱。

建议：

- 把所有正式库表演进收敛到迁移工具，至少 PostgreSQL 统一使用 Flyway。
- `init.sql` 只保留最小空库 bootstrap，或由 Flyway 初始版本完全替代。
- 每次变更新增不可修改的 `V{n}__description.sql`，已发布 migration 不回写修改。
- 为迁移增加 Testcontainers 验证，覆盖空库建库和已有库升级。

### ChromaDB / Python AI storage

已有能力：

- Docker Compose 挂载 `ai_interviewer/storage`，容器重建不会丢失宿主机 Chroma 与 SQLite 状态。
- Python 镜像 `.dockerignore` 排除了 `storage/vector_db`，不会把本地向量库打进镜像。

缺口：

- Git 仍跟踪向量库运行态文件。
- 没有向量库备份、恢复、重建脚本。
- 没有从 PostgreSQL `t_question_bank` 或题库源文件重建 ChromaDB 的标准命令。
- 模型 embedding 维度或 provider 变化时，没有声明如何清理、重建、校验向量库。

建议：

- Git 不跟踪 ChromaDB 二进制目录。
- 题库主数据以 PostgreSQL/Admin 题库或可审查 seed 文件为准。
- 提供 `scripts/rebuild-vector-db.sh` 或等价命令：清空 Chroma 目录，读取已启用题库，调用 Python sync 接口重建。
- 每次 embedding 模型、维度或切分策略变化，必须执行向量库重建并记录兼容性说明。

### Redis

已有能力：

- `redis:7-alpine` 镜像版本固定。
- 开启 AOF：`redis-server --appendonly yes`。
- 使用 `redis_data` volume 持久化。

缺口：

- 没有 AOF/RDB 备份和恢复脚本。
- 没有 Redis 版本升级前后的数据校验脚本。
- 没有区分缓存数据与关键状态数据的清理策略。

建议：

- 明确 Redis 存储内容边界：JWT 黑名单、会话缓存、短期状态。
- 升级前执行 `BGSAVE` 或复制 AOF/RDB，升级后验证关键 key schema。
- 如果 Redis 只承载缓存，部署文档应写明可清空范围；如果承载关键状态，必须进入备份流程。

### MinIO

已有能力：

- 使用 `minio_data` volume 持久化。
- Resume 服务通过 MinIO 保存简历文件。

缺口：

- Compose 使用 `minio/minio:latest`，不利于可重复部署和安全回滚。
- 没有 bucket 初始化脚本、对象备份脚本、恢复脚本。
- 没有对象存储结构和生命周期策略说明。

建议：

- 固定 MinIO 版本，不使用 `latest`。
- 提供 bucket 初始化脚本，确保首次部署可重复。
- 使用 `mc mirror` 或对象存储备份策略，升级前后验证简历上传、下载、解析链路。

### Nacos

已有能力：

- `nacos/nacos-server:v2.3.0` 镜像版本固定。
- 使用 `nacos_data` volume。

缺口：

- 没有 Nacos 配置导入、导出、备份脚本。
- 当前应用主要靠本地 `application.yml` 和环境变量，Nacos 配置是否为权威源不清晰。

建议：

- 明确每个环境的配置权威源：Git 模板、环境变量、Nacos 配置三者不要混用不明。
- 升级 Nacos 前导出 namespace/config/service 元数据。
- 把非密钥配置模板纳入 Git，把密钥放在环境变量或密钥管理系统。

## 推荐运维脚本能力矩阵

建议补齐以下脚本或等价 runbook：

| 能力 | 推荐入口 | 说明 |
|------|----------|------|
| 环境检查 | `scripts/env-check.sh` | 检查 Docker、Compose、端口、`.env` 必填项 |
| 启动/重建 | `scripts/up.sh` | 包装 `docker compose up -d --build`，输出服务状态 |
| 停止 | `scripts/down.sh` | 默认不删 volume，危险清理单独确认 |
| 数据备份 | `scripts/backup.sh` | 备份 PostgreSQL、Redis、MinIO、Nacos、Python storage |
| 数据恢复 | `scripts/restore.sh` | 从指定备份恢复，并做版本校验 |
| 数据迁移 | `scripts/migrate.sh` | 执行 Flyway 或专用迁移任务 |
| 向量库重建 | `scripts/rebuild-vector-db.sh` | 从题库主数据重建 ChromaDB |
| 升级组件 | `scripts/upgrade-component.sh` | 镜像升级前备份，升级后 health/smoke |
| 冒烟验证 | `tests/scripts/run-smoke.sh` | 已存在测试入口，建议纳入部署后固定流程 |

## 推荐落地顺序

1. 先修 Git 边界：忽略并移出 `ai_interviewer/storage/vector_db/**`，保留 `.cursor/`、`.codex/`、`.comet/`、`.claude/`、`.mcp.json` 作为项目级开发环境配置。
2. 补一份“状态数据不进 Git”的 README 说明，明确 ChromaDB、SQLite、PostgreSQL volume、Redis、MinIO 的管理方式。
3. 为 ChromaDB 增加可重建入口，以 PostgreSQL/Admin 题库或 seed 文件作为主数据。
4. 将 PostgreSQL schema 演进统一到 Flyway，避免 `init.sql` 只管空库、迁移脚本只管 Admin 的割裂状态。
5. 补备份/恢复/升级 runbook，再把高频步骤固化为脚本。
6. 固定 MinIO 镜像版本，所有状态组件升级前必须先有备份和回滚路径。
