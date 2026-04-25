# Change: Initial Backend Implementation

## Why
搭建基于 Spring Cloud Alibaba 的 AI 面试官后端服务，实现完整的微服务架构。

## What Changes
- 创建 Maven 多模块项目骨架和公共模块
- 实现 Gateway 网关服务（认证、路由、SSE 透传）
- 实现 User 用户服务（注册、登录、JWT）
- 实现 Interview 面试服务（核心 SSE 代理）
- 实现 Resume 简历服务（MinIO 存储、解析）
- 实现 Job 职位服务（CRUD、匹配分析）
- 实现 Evaluation 评估服务（报告生成）
- 实现 Notification 通知服务（RocketMQ 消费）

## Impact
- Affected specs: gateway-auth, sse-proxy, user-service, interview-session, resume-service, job-service, evaluation, notification
- Affected code: All microservice modules
