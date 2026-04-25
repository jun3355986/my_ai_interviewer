# AI Interviewer Monorepo

本仓库包含 3 个独立子项目，通过 Docker Compose 可以在本地一键拉起完整联调环境（前端 + 网关 + Java 微服务 + Python AI + 基础设施）。

## 1. 项目结构

```text
my_ai_interviewer/
├── ai_interview_backend/   # Java 21 + Spring Cloud Alibaba 微服务 + docker-compose
├── ai_interviewer/         # Python FastAPI AI 服务
└── ai_interviewer_front/   # Flutter 前端（Web/iOS）
```

## 2. 部署拓扑

```text
Browser (http://localhost:8088)
  -> frontend (nginx)
      -> /api/* reverse proxy
          -> gateway (http://gateway:9000)
              -> user/resume/interview/job/evaluation services
                    -> postgres / redis / minio / nacos
                    -> python-ai (for resume/interview AI calls)
```

当前联调入口已统一为 **gateway**：
- 浏览器侧走 `frontend -> /api/* -> gateway`
- 不再推荐前端直接请求 `9001/9004` 等业务服务端口

## 3. 环境准备

1. Docker Desktop（或 Docker Engine）可用
2. Docker Compose Plugin 可用（`docker compose version`）
3. 进入后端目录执行编排命令：

```bash
cd ai_interview_backend
```

## 4. 配置环境变量

在 `ai_interview_backend` 目录下：

```bash
cp .env.example .env
```

建议至少修改以下配置：
- `AZURE_OPENAI_API_KEY`
- `AZURE_OPENAI_ENDPOINT`（默认可用）
- `AZURE_OPENAI_CHAT_MODEL`（默认 `grok-4-20-reasoning`）
- `AZURE_OPENAI_BACKUP_CHAT_MODEL`（默认 `gpt-5.4`）
- `AZURE_OPENAI_EMBEDDING_MODEL`（默认 `embed-v-4-0`）
- `JWT_SECRET`

本机模型配置来源文件（不要把真实密钥提交到仓库）：
- `/Users/junjielong/myai/My_AI_KEY.md`

前端网关地址（Flutter 构建参数）：
- `FRONTEND_GATEWAY_BASE_URL=/`（默认推荐）
- 该值会作为 `build-arg` 传入前端镜像构建

## 5. 一键启动（推荐）

在 `ai_interview_backend` 目录执行：

```bash
docker compose up -d --build
```

当你调整了模型配置或更新了 Python/Flutter 代码，建议强制重建关键服务，避免继续使用旧镜像导致 401：

```bash
docker compose up -d --build python-ai frontend
```

启动内容包括：
- 基础设施：`nacos`、`postgres`、`redis`、`minio`
- AI 服务：`python-ai`
- Java 服务：`gateway`、`user`、`resume`、`interview`、`job`、`evaluation`
- 前端：`frontend`

> `notification` 依赖 RocketMQ，默认不启动。如需启用：
>
> ```bash
> docker compose --profile notification up -d notification
> ```

## 6. 访问入口

- 前端（统一联调入口）：`http://localhost:8088`
- Gateway 健康检查：`http://localhost:9000/actuator/health`
- Gateway 文档页：`http://localhost:9000/doc.html`
- Nacos 控制台：`http://localhost:8848/nacos`
- MinIO 控制台：`http://localhost:19001`

说明：
- `http://localhost:9000/` 返回 `404` 在当前配置下是正常现象（网关根路径未配置首页路由）

### 测试账号

- 用户名：`admin`
- 密码：`admin123`
- 说明：仅本地联调用的测试账号，生产环境请立即替换

## 7. 服务与端口

| 服务 | 本机端口 | 容器端口 | 说明 |
|------|----------|----------|------|
| frontend | 8088 | 80 | Flutter Web（Nginx） |
| gateway | 9000 | 9000 | 统一 API 网关 |
| user | 9001 | 9001 | 用户服务 |
| resume | 9002 | 9002 | 简历服务（依赖 MinIO、Python AI） |
| interview | 9003 | 9003 | 面试服务（依赖 Python AI） |
| job | 9004 | 9004 | 职位服务 |
| evaluation | 9005 | 9005 | 评估服务 |
| notification（可选） | 9006 | 9006 | 通知服务（需 RocketMQ） |
| python-ai | 8000 | 8000 | FastAPI AI 服务 |
| postgres | 5433 | 5432 | PostgreSQL |
| redis | 6380 | 6379 | Redis |
| minio | 19000/19001 | 9000/9001 | MinIO API/Console |
| nacos | 8848 | 8848 | 注册中心/配置中心 |

## 8. 健康检查与排障

查看服务状态：

```bash
docker compose ps
```

查看网关与前端日志：

```bash
docker compose logs -f gateway
docker compose logs -f frontend
```

快速验证联调链路（前端代理到网关）：

```bash
curl -i http://localhost:8088/api/v1/auth/login
```

如果返回 `405 Method Not Allowed`，通常表示请求已到达网关且路由正常（只是请求方法与接口定义不匹配）。

## 9. 停止与清理

停止服务：

```bash
docker compose down
```

停止并删除数据卷（会清空数据库/缓存/对象存储数据）：

```bash
docker compose down -v
```

## 10. 补充说明

- 后端与编排详情请查看：`ai_interview_backend/README.md`
- 前端容器化说明请查看：`ai_interviewer_front/README.md`
- Python AI 服务说明请查看：`ai_interviewer/README.md`
