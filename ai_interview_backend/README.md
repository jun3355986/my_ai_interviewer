# AI面试官 - Spring Cloud Alibaba 后端

基于 Spring Cloud Alibaba 的 AI 面试官核心业务后端服务。

## 项目架构

```
Flutter前端 → Spring Cloud Alibaba后端 → Python FastAPI后端(AI服务)
```

## 技术栈

- **Java 21** (LTS)
- **Spring Boot 3.3.x**
- **Spring Cloud Alibaba 2023.0.x**
  - Nacos (注册中心 & 配置中心)
  - Gateway (API网关)
  - Sentinel (流量控制)
- RocketMQ (消息队列，可选；通知 HTTP API 不依赖它)
- **PostgreSQL 16** (数据库)
- **Redis 7** (缓存)
- **MinIO** (对象存储)
- **MyBatis Plus** (ORM)

## 模块说明

| 模块 | 端口 | 说明 |
|------|------|------|
| ai-interviewer-gateway | 9000 | API网关 |
| ai-interviewer-user | 9001 | 用户服务 |
| ai-interviewer-resume | 9002 | 简历服务 |
| ai-interviewer-interview | 9003（容器内） | 面试服务，仅经 Gateway 访问 |
| ai-interviewer-job | 9004 | 职位服务 |
| ai-interviewer-evaluation | 9005（容器内） | 评估服务，仅经 Gateway 访问 |
| ai-interviewer-notification | 9006 | 通知服务 |
| ai-interviewer-common | - | 公共模块 |
| ai-interviewer-api | - | API定义 |

## 快速开始

### 1. 准备环境变量

```bash
cp .env.example .env
```

`.env` 中的 `AZURE_OPENAI_API_KEY` 建议替换成真实值；模型默认使用 `grok-4-20-reasoning`，备用 `gpt-5.4`，向量模型 `embed-v-4-0`。  
如仅做容器连通性验证，可先保留默认 `test-key`（但真实模型调用会失败）。

本机模型配置来源文件（请勿提交真实密钥到仓库）：`/Users/junjielong/myai/My_AI_KEY.md`。

### 2. 一键启动（基础设施 + Python AI + Java 微服务 + Flutter Web 前端 + React Admin Web）

```bash
docker compose up -d --build

# Nacos控制台: http://localhost:8848/nacos (nacos/nacos)
# MinIO控制台: http://localhost:19001 (minioadmin/minioadmin123)
# 用户端前端访问地址: http://localhost:8088
# 后台管理页面访问地址: http://localhost:8090
```

默认会启动：
- 基础设施：`nacos`、`postgres`、`redis`、`minio`
- AI 服务：`python-ai` (`:8000`)
- Java 服务：`gateway`、`user`、`resume`、`interview`、`job`、`evaluation`、`notification`
- 前端：`frontend` (`:8088`)、`admin-web` (`:8090`)

`notification` 的 HTTP API 默认启动；RocketMQ 消费器默认关闭。如需启用消息消费，请提供可用 RocketMQ，并设置 `NOTIFICATION_ROCKETMQ_ENABLED=true`。

前端容器内置 Nginx 反向代理，`/api/*` 会转发到 `gateway:9000`，三端联调入口统一走 Gateway。
`FRONTEND_GATEWAY_BASE_URL` 可在 `.env` 中配置，默认 `/`（最适合当前容器联调）。

后台管理页面容器内置 Nginx 反向代理，`/admin/*` 会转发到 `gateway:9000`，再由 Gateway 转发到 `ai-interviewer-admin`。
`ADMIN_WEB_API_BASE` 可在 `.env` 中配置，默认 `/admin`。

### 默认管理员账号

- 用户名：`admin`
- 默认密码：`admin123`

如果数据库已初始化过，默认账号密码可能已被修改。可用以下 SQL 重置：

```sql
UPDATE t_user
SET password_hash = '$2a$10$7VAPi29XtFii2ZxQ8WdyEeqxqzePiwkyz3amLje.n6lFaxrpYhV6e',
    updated_at = NOW()
WHERE username = 'admin';
```

### 3. 查看状态和日志

```bash
docker compose ps
docker compose logs -f gateway
```

### 4. 停止服务

```bash
docker compose down
```

## API文档

启动服务后访问:
- Gateway: http://localhost:9000
- User服务: http://localhost:9001/swagger-ui.html

## 主要API

### 认证
- `POST /api/v1/auth/register` - 用户注册
- `POST /api/v1/auth/login` - 用户登录
- `POST /api/v1/auth/refresh` - 刷新Token

### 面试
- `POST /api/v1/interviews/chat` - 面试对话(SSE)
- `GET /api/v1/interviews` - 面试列表
- `POST /api/v1/interviews/{id}/resume` - 恢复面试

### 简历
- `POST /api/v1/resumes/upload` - 上传简历
- `GET /api/v1/resumes` - 简历列表

## 环境变量

```bash
# 数据库
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ai_interviewer
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Nacos
NACOS_SERVER_ADDR=localhost:8848

# JWT
JWT_SECRET=your-256-bit-secret-key-for-jwt-signing-must-be-at-least-32-characters

# Python AI服务
PYTHON_AI_BASE_URL=http://localhost:8000
AZURE_OPENAI_API_KEY=your_azure_api_key
AZURE_OPENAI_ENDPOINT=https://liuwe-m7o7yvmk-eastus2.services.ai.azure.com/openai/v1/
AZURE_OPENAI_CHAT_MODEL=grok-4-20-reasoning
AZURE_OPENAI_BACKUP_CHAT_MODEL=gpt-5.4
AZURE_OPENAI_EMBEDDING_MODEL=embed-v-4-0
AZURE_OPENAI_EMBEDDING_DIMENSION=1024
```

## 开发指南

### 代码规范
- 使用Lombok减少样板代码
- 遵循阿里巴巴Java开发手册
- 使用统一的Result返回格式

### 分层结构
```
controller/  - REST API
service/     - 业务逻辑
mapper/      - 数据访问
entity/      - 实体类
dto/         - 数据传输对象
config/      - 配置类
```

### 服务启动顺序

  1. 基础设施（必须先启动）

  docker-compose up -d

  启动顺序：Nacos → PostgreSQL → Redis → MinIO

  2. 微服务（按依赖关系）
  ┌──────┬──────────────┬──────┬──────────────────────────────────────┐
  │ 顺序 │     服务     │ 端口 │                 说明                 │
  ├──────┼──────────────┼──────┼──────────────────────────────────────┤
  │ 1    │ Gateway      │ 9000 │ 网关，所有请求入口                   │
  ├──────┼──────────────┼──────┼──────────────────────────────────────┤
  │ 2    │ User         │ 9001 │ 用户认证，被其他服务依赖             │
  ├──────┼──────────────┼──────┼──────────────────────────────────────┤
  │ 3    │ Job          │ 9004 │ 职位管理                             │
  ├──────┼──────────────┼──────┼──────────────────────────────────────┤
  │ 4    │ Resume       │ 9002 │ 简历管理（依赖 MinIO）               │
  ├──────┼──────────────┼──────┼──────────────────────────────────────┤
  │ 5    │ Interview    │ 9003 │ 面试服务（依赖 User、Resume、Job）   │
  ├──────┼──────────────┼──────┼──────────────────────────────────────┤
  │ 6    │ Evaluation   │ 9005 │ 评估服务（依赖 Interview）           │
  ├──────┼──────────────┼──────┼──────────────────────────────────────┤
  │ 7    │ Notification │ 9006 │ 通知服务（HTTP API 默认启动，RocketMQ 消费可选） │
  └──────┴──────────────┴──────┴──────────────────────────────────────┘
  依赖关系图

             ┌─────────────┐
             │   Gateway   │  ← 入口
             └──────┬──────┘
                    │
      ┌─────────────┼─────────────┐
      │             │             │
  ┌───▼───┐    ┌────▼────┐   ┌────▼────┐
  │  User │    │   Job   │   │ Resume  │
  └───┬───┘    └────┬────┘   └────┬────┘
      │             │             │
      └─────────────┼─────────────┘
                    │
             ┌──────▼──────┐
             │  Interview  │
             └──────┬──────┘
                    │
             ┌──────▼──────┐
             │ Evaluation  │
             └──────┬──────┘
                    │
             ┌──────▼──────┐
             │Notification │
             └─────────────┘

  最小启动集（核心功能）

  只需要启动：
  1. Gateway (9000)
  2. User (9001)

  这样就可以测试用户注册/登录功能。

  启动服务的命令
  进入微服务目录下，mvn spring-boot:run 2>&1

## License

MIT
