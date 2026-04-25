# AI面试官后端 - 任务清单

> **项目路径速查**
> ```
> ~/python_project/my_ai_interviewer/
> ├── ai_interviewer_backend/      ← 当前项目 (Spring Cloud Alibaba)
> │   └── openspec/
> │       ├── project.md           ← 项目上下文
> │       ├── DESIGN.md            ← 详细设计
> │       └── TASKS.md             ← 本文档
> ├── ai_interviewer/              ← Python后端 (FastAPI + LangChain)
> │   └── api/
> │       ├── router.py            ← API路由
> │       ├── sse.py               ← SSE事件类型
> │       └── chat.py              ← 对话Schema
> └── ai_interviewer_front/        ← Flutter前端 (iOS)
>     └── lib/
>         └── openspec/
>             └── project.md       ← 前端规范
> ```
> **相关项目**:
> - 前端开发: `cd ~/python_project/my_ai_interviewer/ai_interviewer_front`
> - Python后端: `cd ~/python_project/my_ai_interviewer/ai_interviewer`
> - Spring后端: `cd ~/python_project/my_ai_interviewer/ai_interviewer_backend`

## 项目进度概览

| 阶段 | 状态 | 完成度 |
|------|------|--------|
| Phase 1: 基础架构 | ✅ 完成 | 100% |
| Phase 2: 用户服务 | ✅ 基础完成 | 80% |
| Phase 3: 面试服务(核心) | ✅ 核心完成 | 90% |
| Phase 4: 简历服务 | ✅ 完成 | 100% |
| Phase 5: 其他服务 | ✅ 完成 | 100% |
| Phase 6: 测试与部署 | 🔲 待开始 | 0% |

---

## Phase 1: 基础架构搭建 ✅

### 1.1 项目骨架
- [x] 创建Maven多模块项目结构
- [x] 配置父POM依赖管理 (Spring Cloud Alibaba BOM)
- [x] 创建所有子模块POM文件
- [x] 配置.gitignore

### 1.2 公共模块 (ai-interviewer-common)
- [x] 统一响应类 Result<T>
- [x] 错误码枚举 ErrorCode
- [x] 分页结果 PageResult<T>
- [x] 分页请求 PageRequest
- [x] 业务异常 BusinessException
- [x] 全局异常处理器 GlobalExceptionHandler
- [x] JWT工具类 JwtUtils
- [x] 安全工具类 SecurityUtils
- [x] 常量定义 CommonConstants
- [x] 面试阶段枚举 InterviewStage
- [x] 会话状态枚举 SessionStatus

### 1.3 基础设施
- [x] docker-compose.yml (Nacos, PostgreSQL, Redis, MinIO, RocketMQ)
- [x] sql/init.sql 数据库初始化脚本
- [x] README.md 项目说明

### 1.4 Gateway服务 (ai-interviewer-gateway)
- [x] 启动类 GatewayApplication
- [x] 路由配置 GatewayConfig
- [x] CORS配置 CorsConfig
- [x] 认证过滤器 AuthGlobalFilter
- [x] SSE透传过滤器 SSEResponseFilter
- [x] 日志过滤器 LoggingFilter
- [x] 异常处理器 GatewayExceptionHandler
- [x] application.yml 配置

---

## Phase 2: 用户服务 ✅

### 2.1 实体与数据层
- [x] User 实体类
- [x] Role 实体类
- [x] UserMapper
- [x] RoleMapper

### 2.2 DTO
- [x] LoginRequest
- [x] RegisterRequest
- [x] UserDTO
- [x] TokenResponse

### 2.3 服务层
- [x] UserService 接口和实现
- [x] AuthService 接口和实现

### 2.4 控制器
- [x] AuthController (登录/注册/刷新Token)
- [x] UserController (用户信息CRUD)

### 2.5 配置
- [x] SecurityConfig
- [x] application.yml

### 2.6 待完善
- [ ] OAuth2第三方登录 (GitHub, 微信)
- [ ] 密码重置功能
- [ ] 邮箱验证功能

---

## Phase 3: 面试服务(核心) ✅

### 3.1 实体与数据层
- [x] InterviewSession 面试会话实体
- [x] InterviewMessage 消息历史实体
- [x] ScoreRecord 评分记录实体
- [x] InterviewSessionMapper
- [x] InterviewMessageMapper
- [x] ScoreRecordMapper

### 3.2 DTO
- [x] ChatRequest 对话请求
- [x] PythonChatRequest Python后端请求
- [x] SessionDTO 会话信息

### 3.3 SSE相关
- [x] SSEEventType 事件类型枚举

### 3.4 核心服务
- [x] **SSEProxyService** ★核心SSE代理服务
  - [x] proxyChat() 代理对话请求
  - [x] proxyResume() 代理恢复会话
  - [x] getOrCreateSession() 会话管理
  - [x] handleSSEEvent() 事件拦截处理
  - [x] 评分事件持久化
  - [x] 消息历史保存
- [x] InterviewService 业务服务
  - [x] listSessions() 会话列表
  - [x] listIncompleteSessions() 未完成会话
  - [x] getSession() 会话详情
  - [x] cancelSession() 取消会话
  - [x] getSessionHistory() 消息历史

### 3.5 控制器
- [x] InterviewController
  - [x] POST /chat (SSE流式)
  - [x] POST /{id}/resume (SSE流式)
  - [x] GET / 面试列表
  - [x] GET /incomplete 未完成列表
  - [x] GET /{id} 面试详情
  - [x] DELETE /{id} 取消面试

### 3.6 配置
- [x] WebClientConfig (SSE代理配置)
- [x] application.yml

### 3.7 待完善
- [ ] 消息历史API (GET /{id}/history)
- [ ] 会话Redis缓存
- [ ] 断线重连机制
- [ ] 心跳检测

---

## Phase 4: 简历服务 🔲

### 4.1 实体与数据层
- [x] Resume 简历实体
- [x] ResumeVersion 版本历史实体
- [x] ResumeMapper

### 4.2 DTO
- [x] ResumeDTO
- [x] ResumeUploadRequest
- [x] ParseResumeResponse

### 4.3 服务层
- [x] ResumeService
  - [x] uploadResume() 上传简历
  - [x] parseResume() 调用Python解析
  - [x] listResumes() 简历列表
  - [x] getResume() 简历详情
  - [x] deleteResume() 删除简历
  - [x] setDefault() 设为默认
- [x] FileStorageService (MinIO)
  - [x] uploadFile()
  - [x] downloadFile()
  - [x] deleteFile()

### 4.4 控制器
- [x] ResumeController
  - [x] POST /upload
  - [x] POST /{id}/parse
  - [x] GET /
  - [x] GET /{id}
  - [x] DELETE /{id}
  - [x] PUT /{id}/default

### 4.5 配置
- [x] MinioConfig
- [x] application.yml

---

## Phase 5: 其他服务 ✅

### 5.1 职位服务 (ai-interviewer-job)
- [x] Job 实体
- [x] JobRequirement 实体
- [x] JobMapper
- [x] JobService
- [x] JobController
  - [x] CRUD接口
  - [x] 职位-简历匹配度分析

### 5.2 评估服务 (ai-interviewer-evaluation)
- [x] Evaluation 实体
- [x] EvaluationMapper
- [x] EvaluationService
  - [x] generateReport() 生成报告
  - [x] getReport() 获取报告
  - [x] getScores() 获取评分详情
  - [x] getStatistics() 统计数据
- [x] EvaluationController
  - [x] GET /{sessionId}
  - [x] GET /{sessionId}/scores
  - [x] GET /statistics
  - [x] POST /{sessionId}

### 5.3 通知服务 (ai-interviewer-notification)
- [x] Notification 实体
- [x] NotificationMapper
- [x] EmailService
- [x] NotificationService
- [x] RocketMQ消费者
  - [x] 面试完成通知
  - [x] 报告生成通知
- [x] NotificationController

---

## Phase 6: 测试与部署 🔲

### 6.1 单元测试
- [ ] UserService测试
- [ ] InterviewService测试
- [ ] SSEProxyService测试

### 6.2 集成测试
- [ ] AuthController测试
- [ ] InterviewController测试 (SSE)
- [ ] 端到端测试

### 6.3 部署
- [ ] Dockerfile (各服务)
- [ ] docker-compose.prod.yml
- [ ] Kubernetes配置 (可选)

### 6.4 文档
- [ ] API文档完善 (Swagger)
- [ ] 部署文档
- [ ] 运维文档

---

## 关键文件清单

### 已完成的核心文件

```
ai-interviewer-backend/
├── pom.xml                                          # ✅ 父POM
├── docker-compose.yml                               # ✅ 基础设施
├── sql/init.sql                                     # ✅ 数据库初始化
│
├── ai-interviewer-common/
│   └── src/main/java/com/aiinterviewer/common/
│       ├── model/Result.java                        # ✅ 统一响应
│       ├── model/ErrorCode.java                     # ✅ 错误码
│       ├── model/PageResult.java                    # ✅ 分页结果
│       ├── exception/BusinessException.java         # ✅ 业务异常
│       ├── exception/GlobalExceptionHandler.java    # ✅ 全局异常处理
│       ├── util/JwtUtils.java                       # ✅ JWT工具
│       └── constant/InterviewStage.java             # ✅ 面试阶段
│
├── ai-interviewer-gateway/
│   └── src/main/java/com/aiinterviewer/gateway/
│       ├── GatewayApplication.java                  # ✅ 启动类
│       ├── config/GatewayConfig.java                # ✅ 路由配置
│       ├── filter/AuthGlobalFilter.java             # ✅ 认证过滤器
│       ├── filter/SSEResponseFilter.java            # ✅ SSE透传
│       └── handler/GatewayExceptionHandler.java     # ✅ 异常处理
│
├── ai-interviewer-user/
│   └── src/main/java/com/aiinterviewer/user/
│       ├── UserApplication.java                     # ✅ 启动类
│       ├── entity/User.java                         # ✅ 用户实体
│       ├── dto/LoginRequest.java                    # ✅ 登录请求
│       ├── service/AuthService.java                 # ✅ 认证服务
│       └── controller/AuthController.java           # ✅ 认证控制器
│
├── ai-interviewer-interview/
│   └── src/main/java/com/aiinterviewer/interview/
│       ├── InterviewApplication.java                # ✅ 启动类
│       ├── entity/InterviewSession.java             # ✅ 会话实体
│       ├── entity/InterviewMessage.java             # ✅ 消息实体
│       ├── entity/ScoreRecord.java                  # ✅ 评分实体
│       ├── dto/ChatRequest.java                     # ✅ 对话请求
│       ├── dto/PythonChatRequest.java               # ✅ Python请求
│       ├── dto/SessionDTO.java                      # ✅ 会话DTO
│       ├── mapper/InterviewSessionMapper.java       # ✅ 会话Mapper
│       ├── mapper/InterviewMessageMapper.java       # ✅ 消息Mapper
│       ├── mapper/ScoreRecordMapper.java            # ✅ 评分Mapper
│       ├── service/SSEProxyService.java             # ✅ ★核心SSE代理
│       ├── service/InterviewService.java            # ✅ 业务服务
│       ├── controller/InterviewController.java      # ✅ 控制器
│       ├── sse/SSEEventType.java                    # ✅ 事件类型
│       ├── config/WebClientConfig.java              # ✅ WebClient配置
│       └── src/main/resources/application.yml       # ✅ 应用配置
```

---

## 下一步工作建议

### 优先级 1 (必须)
1. **简历服务**: 实现文件上传和Python解析调用
2. **联调测试**: 与Python后端进行SSE联调
3. **Gateway集成**: 完成认证流程端到端测试

### 优先级 2 (重要)
1. **评估服务**: 实现报告生成功能
2. **Redis缓存**: 会话缓存优化
3. **错误处理**: 完善异常处理和重试机制

### 优先级 3 (可选)
1. **OAuth2**: 第三方登录
2. **通知服务**: 邮件通知
3. **Kubernetes**: 容器化部署

---

## 测试清单

### 启动测试

```bash
# 1. 启动基础设施
cd ai-interviewer-backend
docker-compose up -d

# 2. 检查服务状态
docker-compose ps

# 3. 启动Python后端
cd ../ai_interviewer
python -m uvicorn main:app --reload

# 4. 编译项目
cd ../ai_interviewer_backend
mvn clean install -DskipTests

# 5. 启动面试服务
cd ai-interviewer-interview
mvn spring-boot:run
```

### API测试

```bash
# 健康检查
curl http://localhost:9003/actuator/health

# SSE对话测试
curl -X POST http://localhost:9003/interviews/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"message":"我准备好了","resumeContent":"张三，5年Java经验"}' \
  --no-buffer

# 获取会话列表
curl http://localhost:9003/interviews \
  -H "X-User-Id: 1"

# 获取未完成会话
curl http://localhost:9003/interviews/incomplete \
  -H "X-User-Id: 1"
```

---

## 更新日志

| 日期 | 内容 |
|------|------|
| 2024-01-11 | 创建项目骨架，完成Phase 1基础架构 |
| 2024-01-11 | 完成用户服务基础功能 |
| 2024-01-11 | 完成面试服务核心SSE透传功能 |
