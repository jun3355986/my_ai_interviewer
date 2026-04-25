# Project Context

> **相关文档**
> - [规格目录](./specs/) - 各服务能力规格定义
> - [变更历史](./changes/archive/) - 已完成的变更归档
> - [根级全局规格](../../openspec/) - 跨端架构和 API 契约

## Purpose

AI面试官后端服务 - 基于Spring Cloud Alibaba的核心业务后端，负责用户管理、面试会话、简历管理、评估报告等核心业务逻辑。

核心功能：
- 用户注册/登录、JWT认证、OAuth2第三方登录
- 简历上传/存储/解析（调用Python后端解析）
- 面试会话管理、SSE流式响应透传
- 职位管理、JD匹配
- 评分持久化、评估报告生成
- 消息通知（邮件、站内信）

这是一个三层架构中的中间层：
```
Flutter前端 → Spring Cloud Alibaba后端(本项目) → Python FastAPI后端(AI服务)
```

## Tech Stack

### 核心框架
- Java 21 (LTS)
- Spring Boot 3.3.x
- Spring Cloud 2023.0.x
- Spring Cloud Alibaba 2023.0.x

### 微服务组件
- Nacos (注册中心 & 配置中心)
- Spring Cloud Gateway (API网关)
- Sentinel (流量控制)
- RocketMQ (消息队列)

### 数据存储
- PostgreSQL 16 (主数据库)
- Redis 7 (缓存)
- MinIO (对象存储)

### ORM & 工具
- MyBatis Plus
- Lombok
- MapStruct
- Hutool

## Project Conventions

### Code Style

#### 命名规范
- 包名: 小写，使用点分隔 (com.aiinterviewer.user)
- 类名: PascalCase (UserService, LoginRequest)
- 方法/变量: camelCase (getUserById, accessToken)
- 常量: UPPER_SNAKE_CASE (TOKEN_PREFIX)
- 数据库表: snake_case，前缀t_ (t_user, t_interview_session)

#### 分层结构
```
controller/  - REST API控制器
service/     - 业务逻辑接口和实现
mapper/      - MyBatis Mapper接口
entity/      - 数据库实体类
dto/         - 数据传输对象
config/      - 配置类
```

#### 返回格式
所有API使用统一的Result<T>响应格式：
```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1704067200000
}
```

### Architecture Patterns

#### 微服务划分
| 服务 | 端口 | 职责 |
|------|------|------|
| gateway | 9000 | API网关、认证、限流、SSE透传 |
| user | 9001 | 用户管理、JWT认证 |
| resume | 9002 | 简历管理、MinIO存储 |
| interview | 9003 | 面试会话、SSE代理 |
| job | 9004 | 职位管理 |
| evaluation | 9005 | 评估报告 |
| notification | 9006 | 消息通知 |

#### 服务间通信
- 同步调用: OpenFeign / WebClient
- 异步消息: RocketMQ
- SSE透传: WebClient + Flux

### Testing Strategy

- 单元测试: JUnit 5 + Mockito
- 集成测试: @SpringBootTest + TestContainers
- API测试: MockMvc / WebTestClient

### Git Workflow

#### 分支策略
- main: 主分支，稳定可发布
- develop: 开发分支
- feature/*: 功能分支
- fix/*: 修复分支

#### 提交规范
- feat: 新功能
- fix: 修复bug
- docs: 文档更新
- refactor: 重构
- test: 测试相关

## Domain Context

### 面试流程
1. 开场(opening): AI面试官问候
2. 自我介绍(self_introduction): 用户介绍自己
3. 项目提问(project_qna): 基于简历提问
4. 技术面试(technical_qna): 技术问题
5. 总结(conclusion): 评分和反馈

### 核心实体
- User: 用户信息
- Resume: 简历
- Job: 职位
- InterviewSession: 面试会话
- ScoreRecord: 评分记录
- Evaluation: 评估报告

## Important Constraints

1. **SSE透传**: Gateway和Interview服务必须支持SSE长连接
2. **认证**: Gateway统一认证，JWT Token
3. **Python对接**: 面试服务需要与Python后端保持会话同步
4. **事务**: 评分保存使用分布式事务(Seata)或最终一致性

## External Dependencies

### 后端服务
- Python FastAPI: `http://localhost:8000` (AI面试服务)
- Nacos: `http://localhost:8848` (注册中心)
- Sentinel: `http://localhost:8080` (流控控制台)

### 基础设施
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`
- MinIO: `localhost:9000` / `localhost:9001`(控制台)
- RocketMQ: `localhost:9876`
