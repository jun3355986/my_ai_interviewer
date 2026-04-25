# AI面试官后端 - 详细设计文档

> **重要：项目路径速查**
> ```
> ~/python_project/my_ai_interviewer/
> ├── ai_interviewer_backend/      ← 当前项目 (Spring Cloud Alibaba)
> │   └── openspec/
> │       ├── project.md           ← 项目上下文
> │       ├── DESIGN.md            ← 本文档
> │       └── TASKS.md             ← 任务清单
> ├── ai_interviewer/              ← Python后端 (FastAPI + LangChain)
> │   └── api/
> │       ├── router.py            ← API路由定义
│       │   ├── sse.py             ← SSE事件类型
│       │   └── chat.py            ← 对话Schema
│       └── services/
│           └── orchestrator.py    ← 面试协调器
> └── ai_interviewer_front/        ← Flutter前端 (iOS)
>     └── lib/
>         └── openspec/
>             └── project.md       ← 前端项目规范
> ```
> **快速跳转**:
> - 继续前端开发: `cd ~/python_project/my_ai_interviewer/ai_interviewer_front`
> - 继续Python后端: `cd ~/python_project/my_ai_interviewer/ai_interviewer`
> - 继续Spring后端: `cd ~/python_project/my_ai_interviewer/ai_interviewer_backend`

## 1. 系统架构

### 1.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Flutter 前端应用 (iOS)                           │
│                    状态管理 │ UI渲染 │ HTTP/SSE通信                       │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTPS (JWT Token)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      Spring Cloud Gateway (9000)                         │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        │
│  │ 路由转发    │ │ JWT认证     │ │ Sentinel    │ │ SSE透传     │        │
│  │             │ │             │ │ 限流熔断    │ │             │        │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘        │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
            ┌───────────────────────┼───────────────────────┐
            │                       │                       │
            ▼                       ▼                       ▼
    ┌───────────────┐       ┌───────────────┐       ┌───────────────┐
    │  User Service │       │Interview Svc  │       │ Resume Service│
    │    (9001)     │       │   (9003)      │       │    (9002)     │
    │               │       │               │       │               │
    │ 用户注册登录  │       │ SSE代理       │       │ 文件上传      │
    │ JWT认证      │       │ 会话管理      │       │ MinIO存储     │
    │ OAuth2       │       │ 消息持久化    │       │ 调用Python解析│
    └───────────────┘       └───────────────┘       └───────────────┘
            │                       │                       │
            └───────────────────────┼───────────────────────┘
                                    │
    ┌───────────────┐       ┌───────────────┐       ┌───────────────┐
    │  Job Service  │       │Evaluation Svc │       │Notification   │
    │    (9004)     │       │   (9005)      │       │    (9006)     │
    │               │       │               │       │               │
    │ 职位CRUD      │       │ 评估报告      │       │ 邮件通知      │
    │ JD匹配       │       │ 统计分析      │       │ MQ消费        │
    └───────────────┘       └───────────────┘       └───────────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────┐
│                              基础设施层                                  │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐           │
│  │  Nacos  │ │PostgreSQL│ │  Redis  │ │  MinIO  │ │RocketMQ │           │
│  │ 8848    │ │  5432   │ │  6379   │ │9000/9001│ │  9876   │           │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘           │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTP/SSE
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     Python FastAPI 后端 (8000)                           │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        │
│  │ LangChain   │ │ DeepSeek    │ │ Chroma      │ │ SSE流式     │        │
│  │ 编排        │ │ LLM对话     │ │ 向量库      │ │ 响应        │        │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘        │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 服务依赖关系

```
Gateway
    ├── 依赖 → User Service (认证)
    ├── 路由 → Interview Service
    ├── 路由 → Resume Service
    ├── 路由 → Job Service
    └── 路由 → Evaluation Service

Interview Service
    ├── 依赖 → Python FastAPI (SSE代理)
    ├── 依赖 → Redis (会话缓存)
    ├── 依赖 → PostgreSQL (持久化)
    └── 发布 → RocketMQ (面试完成事件)

Resume Service
    ├── 依赖 → Python FastAPI (简历解析)
    ├── 依赖 → MinIO (文件存储)
    └── 依赖 → PostgreSQL (元数据)

Evaluation Service
    ├── 依赖 → PostgreSQL (评分数据)
    └── 发布 → RocketMQ (报告生成事件)

Notification Service
    └── 消费 → RocketMQ (发送通知)
```

---

## 2. 核心模块设计

### 2.1 SSE代理服务 (核心)

#### 2.1.1 设计目标

- **透明代理**: 将Python后端的SSE响应完整透传给前端
- **事件拦截**: 拦截特定事件（评分、状态）进行持久化
- **会话同步**: 维护本地会话与Python会话的映射关系
- **容错处理**: 网络异常时的优雅降级

#### 2.1.2 核心流程

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│   Flutter   │      │  Interview  │      │   Python    │
│     App     │      │   Service   │      │   FastAPI   │
└──────┬──────┘      └──────┬──────┘      └──────┬──────┘
       │                    │                    │
       │ POST /chat         │                    │
       │ {message, ...}     │                    │
       │───────────────────►│                    │
       │                    │                    │
       │                    │ 1. 获取/创建会话   │
       │                    │ 2. 保存用户消息    │
       │                    │                    │
       │                    │ POST /interview/chat
       │                    │────────────────────►
       │                    │                    │
       │                    │◄────SSE Stream─────│
       │                    │ event: status      │
       │                    │ event: chunk       │
       │                    │ event: score ──────┼──► 持久化到DB
       │                    │ event: done        │
       │                    │                    │
       │◄────SSE Stream─────│                    │
       │                    │                    │
       │                    │ 3. 保存AI响应      │
       │                    │ 4. 更新会话状态    │
       │                    │                    │
```

#### 2.1.3 事件处理策略

| 事件类型 | 处理动作 | 说明 |
|----------|----------|------|
| `status` | 更新会话状态 + 透传 | 首次获取python_session_id |
| `chunk` | 收集内容 + 透传 | 累积AI响应文本 |
| `score` | 持久化 + 透传 | 保存到t_score_record |
| `result` | 更新会话 + 透传 | 保存next_question |
| `done` | 更新状态 + 透传 | 面试完成时更新状态 |
| `error` | 日志 + 透传 | 记录错误日志 |

#### 2.1.4 代码结构

```java
@Service
public class SSEProxyService {

    // 代理对话请求
    public Flux<ServerSentEvent<String>> proxyChat(ChatRequest request, Long userId) {
        // 1. 获取或创建会话
        InterviewSession session = getOrCreateSession(request, userId);

        // 2. 保存用户消息
        saveUserMessage(session.getId(), request.getMessage());

        // 3. 构建Python请求
        PythonChatRequest pythonRequest = buildPythonRequest(request, session);

        // 4. 代理SSE请求
        return webClient.post()
            .uri("/interview/chat")
            .bodyValue(pythonRequest)
            .retrieve()
            .bodyToFlux(ServerSentEvent.class)
            .doOnNext(event -> handleSSEEvent(event, session))  // 拦截处理
            .doOnComplete(() -> finalizeSession(session));       // 完成处理
    }

    // 事件处理
    private void handleSSEEvent(ServerSentEvent event, InterviewSession session) {
        switch (event.event()) {
            case "status" -> handleStatusEvent(event, session);
            case "chunk"  -> handleChunkEvent(event);
            case "score"  -> handleScoreEvent(event, session);  // 持久化
            case "result" -> handleResultEvent(event, session);
            case "done"   -> handleDoneEvent(event, session);
        }
    }
}
```

### 2.2 Gateway认证流程

#### 2.2.1 认证流程图

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│   Flutter   │      │   Gateway   │      │User Service │
└──────┬──────┘      └──────┬──────┘      └──────┬──────┘
       │                    │                    │
       │ POST /auth/login   │                    │
       │ {username, pwd}    │                    │
       │───────────────────►│                    │
       │                    │───────────────────►│
       │                    │                    │ 验证用户
       │                    │◄──────────────────│ 生成JWT
       │◄───────────────────│                    │
       │ {token, refresh}   │                    │
       │                    │                    │
       │ GET /interviews    │                    │
       │ Authorization: Bearer xxx               │
       │───────────────────►│                    │
       │                    │ 验证JWT            │
       │                    │ 提取用户信息       │
       │                    │ 设置X-User-Id头    │
       │                    │──────────────────────────────►
       │                    │                    │ Interview
       │◄──────────────────────────────────────────────────│ Service
       │                    │                    │
```

#### 2.2.2 JWT Token结构

```json
{
  "sub": "123",              // 用户ID
  "username": "zhangsan",    // 用户名
  "roles": ["ROLE_USER"],    // 角色列表
  "exp": 1704153600,         // 过期时间
  "iat": 1704067200          // 签发时间
}
```

#### 2.2.3 认证过滤器

```java
@Component
public class AuthGlobalFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 白名单检查
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 2. 提取Token
        String token = extractToken(request);

        // 3. 验证Token
        Claims claims = jwtUtils.parseToken(token);

        // 4. 传递用户信息到下游
        ServerHttpRequest mutatedRequest = request.mutate()
            .header("X-User-Id", claims.getSubject())
            .header("X-User-Name", claims.get("username"))
            .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}
```

---

## 3. 数据库设计

### 3.1 ER图

```
┌─────────────────┐       ┌─────────────────┐
│     t_user      │       │     t_role      │
├─────────────────┤       ├─────────────────┤
│ id (PK)         │       │ id (PK)         │
│ username        │       │ role_code       │
│ email           │       │ role_name       │
│ password_hash   │       └────────┬────────┘
│ ...             │                │
└────────┬────────┘                │
         │                         │
         │    ┌────────────────────┘
         │    │
         ▼    ▼
┌─────────────────┐
│  t_user_role    │
├─────────────────┤
│ user_id (FK)    │
│ role_id (FK)    │
└─────────────────┘

┌─────────────────┐       ┌─────────────────┐
│    t_resume     │       │     t_job       │
├─────────────────┤       ├─────────────────┤
│ id (PK)         │       │ id (PK)         │
│ user_id (FK)    │       │ title           │
│ file_path       │       │ description     │
│ parsed_content  │       │ requirements    │
│ ...             │       │ ...             │
└────────┬────────┘       └────────┬────────┘
         │                         │
         │                         │
         ▼                         ▼
┌─────────────────────────────────────────┐
│         t_interview_session             │
├─────────────────────────────────────────┤
│ id (PK) - UUID                          │
│ user_id (FK)                            │
│ resume_id (FK)                          │
│ job_id (FK)                             │
│ stage                                   │
│ status                                  │
│ python_session_id                       │
│ ...                                     │
└────────┬───────────────────┬────────────┘
         │                   │
         ▼                   ▼
┌─────────────────┐  ┌─────────────────┐
│t_interview_msg  │  │ t_score_record  │
├─────────────────┤  ├─────────────────┤
│ id (PK)         │  │ id (PK)         │
│ session_id (FK) │  │ session_id (FK) │
│ role            │  │ question        │
│ content         │  │ answer          │
│ sequence        │  │ score           │
│ ...             │  │ feedback        │
└─────────────────┘  └────────┬────────┘
                              │
                              ▼
                     ┌─────────────────┐
                     │  t_evaluation   │
                     ├─────────────────┤
                     │ id (PK)         │
                     │ session_id (FK) │
                     │ overall_score   │
                     │ summary         │
                     │ ...             │
                     └─────────────────┘
```

### 3.2 索引设计

```sql
-- 用户表索引
CREATE INDEX idx_user_email ON t_user(email);
CREATE INDEX idx_user_phone ON t_user(phone);
CREATE INDEX idx_user_username ON t_user(username);

-- 简历表索引
CREATE INDEX idx_resume_user ON t_resume(user_id);
CREATE INDEX idx_resume_default ON t_resume(user_id, is_default);

-- 面试会话索引
CREATE INDEX idx_session_user ON t_interview_session(user_id);
CREATE INDEX idx_session_status ON t_interview_session(status);
CREATE INDEX idx_session_user_status ON t_interview_session(user_id, status);

-- 消息历史索引
CREATE INDEX idx_message_session ON t_interview_message(session_id);
CREATE INDEX idx_message_session_seq ON t_interview_message(session_id, sequence);

-- 评分记录索引
CREATE INDEX idx_score_session ON t_score_record(session_id);

-- 评估报告索引
CREATE INDEX idx_evaluation_user ON t_evaluation(user_id);
```

---

## 4. API详细设计

### 4.1 认证API

#### 4.1.1 用户注册

```
POST /api/v1/auth/register

Request:
{
  "username": "zhangsan",
  "email": "zhangsan@example.com",
  "password": "Password123!"
}

Response:
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "email": "zhangsan@example.com"
  }
}
```

#### 4.1.2 用户登录

```
POST /api/v1/auth/login

Request:
{
  "username": "zhangsan",
  "password": "Password123!"
}

Response:
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 7200,
    "tokenType": "Bearer"
  }
}
```

### 4.2 面试API

#### 4.2.1 统一对话接口 (SSE)

```
POST /api/v1/interviews/chat
Content-Type: application/json
Accept: text/event-stream

Request:
{
  "sessionId": null,                    // 首次为空
  "message": "我准备好了",
  "resumeId": 1,                        // 首次需要
  "jobId": 1,                           // 可选
  "candidateName": "张三"               // 可选
}

Response (SSE Stream):
event: status
data: {"stage":"opening","is_processing":true,"session_id":"abc-123"}

event: chunk
data: {"content":"您好"}

event: chunk
data: {"content":"，张三"}

event: chunk
data: {"content":"，欢迎参加今天的面试。"}

event: score
data: {"score":85,"feedback":"回答完整，表达清晰"}

event: result
data: {"next_question":"请做一下自我介绍","is_followup":false}

event: done
data: {"session_id":"abc-123","stage":"self_introduction","is_interview_complete":false}
```

#### 4.2.2 获取面试列表

```
GET /api/v1/interviews?current=1&size=10

Response:
{
  "code": 200,
  "data": {
    "records": [
      {
        "sessionId": "abc-123",
        "candidateName": "张三",
        "stage": "project_qna",
        "stageDisplay": "项目提问",
        "progress": 45,
        "status": 1,
        "lastQuestion": "请介绍一下你参与的最有挑战的项目",
        "createdAt": "2024-01-11T10:00:00",
        "updatedAt": "2024-01-11T10:30:00"
      }
    ],
    "total": 5,
    "current": 1,
    "size": 10,
    "pages": 1
  }
}
```

### 4.3 错误码定义

| 错误码 | 说明 | HTTP状态码 |
|--------|------|------------|
| 200 | 成功 | 200 |
| 1000 | 系统错误 | 500 |
| 1001 | 参数错误 | 400 |
| 2000 | 未授权 | 401 |
| 2001 | Token过期 | 401 |
| 2003 | 拒绝访问 | 403 |
| 4000 | 会话不存在 | 404 |
| 4002 | 会话已结束 | 400 |
| 4004 | AI服务异常 | 503 |

---

## 5. 配置说明

### 5.1 环境变量

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
JWT_SECRET=your-secret-key-at-least-32-characters-long
JWT_EXPIRATION=7200
JWT_REFRESH_EXPIRATION=604800

# Python AI
PYTHON_AI_BASE_URL=http://localhost:8000
PYTHON_AI_TIMEOUT_CONNECT=5000
PYTHON_AI_TIMEOUT_READ=600000

# MinIO
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin123
MINIO_BUCKET=ai-interviewer
```

### 5.2 服务端口分配

| 服务 | 端口 | 说明 |
|------|------|------|
| Gateway | 9000 | API入口 |
| User Service | 9001 | 用户服务 |
| Resume Service | 9002 | 简历服务 |
| Interview Service | 9003 | 面试服务 |
| Job Service | 9004 | 职位服务 |
| Evaluation Service | 9005 | 评估服务 |
| Notification Service | 9006 | 通知服务 |
| Nacos | 8848 | 注册中心 |
| PostgreSQL | 5432 | 数据库 |
| Redis | 6379 | 缓存 |
| MinIO | 9000/9001 | 对象存储 |
| RocketMQ | 9876 | 消息队列 |
| Python FastAPI | 8000 | AI服务 |

---

## 6. 部署架构

### 6.1 开发环境

```
本地机器
├── Docker Compose
│   ├── Nacos
│   ├── PostgreSQL
│   ├── Redis
│   ├── MinIO
│   └── RocketMQ
│
├── IDEA/VSCode
│   ├── Gateway (9000)
│   ├── User Service (9001)
│   └── Interview Service (9003)
│
└── Python venv
    └── FastAPI (8000)
```

### 6.2 生产环境 (建议)

```
Kubernetes Cluster
├── Ingress (Nginx)
│   └── 统一入口，SSL终止
│
├── Gateway Pod (2+ replicas)
│   └── Spring Cloud Gateway
│
├── Business Pods
│   ├── User Service (2+ replicas)
│   ├── Interview Service (2+ replicas)
│   ├── Resume Service (2+ replicas)
│   └── ...
│
├── StatefulSets
│   ├── Nacos Cluster (3 nodes)
│   └── RocketMQ Cluster
│
└── Managed Services
    ├── RDS PostgreSQL
    ├── ElastiCache Redis
    └── OSS/S3
```

---

## 7. 关键参考文件

> 以下是实现本项目时需要参考的Python后端和前端项目的关键文件路径。

### 7.1 Python后端关键文件

| 文件路径 | 说明 | 用途 |
|----------|------|------|
| `~/python_project/my_ai_interviewer/ai_interviewer/api/router.py` | API路由定义 | 对齐API接口 |
| `~/python_project/my_ai_interviewer/ai_interviewer/api/sse.py` | SSE事件类型 | SSE事件格式参考 |
| `~/python_project/my_ai_interviewer/ai_interviewer/schemas/chat.py` | 数据Schema | DTO对齐 |
| `~/python_project/my_ai_interviewer/ai_interviewer/services/orchestrator.py` | 面试协调器 | 面试流程逻辑 |
| `~/python_project/my_ai_interviewer/ai_interviewer/services/database.py` | 数据库操作 | 数据模型参考 |

### 7.2 Flutter前端关键文件

| 文件路径 | 说明 | 用途 |
|----------|------|------|
| `~/python_project/my_ai_interviewer/ai_interviewer_front/lib/openspec/project.md` | 前端项目规范 | 了解前端架构 |
| `lib/api/` | API调用封装 | 理解前端如何调用 |
| `lib/models/` | 数据模型 | DTO对齐 |

### 7.3 快速跳转命令

```bash
# 查看Python后端SSE接口定义
cat ~/python_project/my_ai_interviewer/ai_interviewer/api/sse.py

# 查看Python后端Schema
cat ~/python_project/my_ai_interviewer/ai_interviewer/schemas/chat.py

# 查看前端项目规范
cat ~/python_project/my_ai_interviewer/ai_interviewer_front/lib/openspec/project.md

# 打开前端项目继续开发
cd ~/python_project/my_ai_interviewer/ai_interviewer_front

# 打开Python后端继续开发
cd ~/python_project/my_ai_interviewer/ai_interviewer
```

### 7.4 接口对接清单

| Python后端接口 | Spring后端对应 | 说明 |
|----------------|----------------|------|
| `POST /interview/chat` | `POST /interviews/chat` | SSE对话接口 |
| `POST /interview/resume` | `POST /interviews/{id}/resume` | 恢复面试 |
| `POST /resume/parse` | `POST /resumes/{id}/parse` | 简历解析 |
| `GET /interview/sessions` | `GET /interviews` | 会话列表 |
| `GET /interview/sessions/incomplete` | `GET /interviews/incomplete` | 未完成会话 |
| `DELETE /interview/sessions/{id}` | `DELETE /interviews/{id}` | 取消面试 |

### 7.5 数据流速查

```
用户输入 → Flutter前端 → Spring Gateway → Interview Service → Python FastAPI
              ↓                   ↓
         保存本地状态      JWT认证/X-User-Id
              ↓
         显示SSE流式响应   ← ← ← ← ← ← ← ← ← ← ← ← ← ← ←
                                          ↑
                                     SSE代理透传
```
