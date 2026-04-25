# Project Context

## Purpose

AI 面试助手（AI Interview Assistant）是一个开源的智能面试练习平台，帮助求职者通过与 AI 面试官的模拟对话提升面试技巧。

系统采用三端架构：
- **前端 (ai_interviewer_front)**: Flutter iOS 应用，提供面试交互 UI
- **后端 (ai_interview_backend)**: Spring Cloud Alibaba 微服务，负责业务逻辑、认证、数据持久化
- **大模型服务 (ai_interviewer)**: Python FastAPI + LangChain + DeepSeek，负责 AI 面试能力

## Architecture Overview

```
Flutter UI ──HTTP──▶ Gateway (:9000) ──lb://──▶ Java Microservices
                                                    │
                              Resume Service ──WebClient──▶ Python AI (:8000)
                              Interview Service ──SSE proxy──▶ Python AI
```

## Sub-Projects

| 子项目 | 技术栈 | 端口 | 职责 |
|--------|--------|------|------|
| ai_interviewer_front | Flutter 3.x | - | 移动端 UI、面试交互 |
| ai_interview_backend | Spring Cloud Alibaba, Java 21 | 9000-9006 | 业务逻辑、认证、数据持久化 |
| ai_interviewer | FastAPI, LangChain, DeepSeek | 8000 | AI 面试、简历解析、RAG 检索、评分 |

## Cross-Project Conventions

### API 契约
- 所有外部 API 通过 Gateway(:9000) 统一入口
- 认证：Gateway 统一 JWT 验证，下游服务通过 `X-User-Id` 头获取用户身份
- 响应格式：Java 后端统一使用 `Result<T>` 包装
- SSE 事件类型：`status`, `chunk`, `score`, `result`, `done`, `error`

### 面试流程（跨端统一）
1. `opening` - 开场白
2. `self_introduction` - 自我介绍
3. `project_question` - 项目提问
4. `technical` - 技术面试
5. `concluded` - 总结评分

### OpenSpec 组织结构
```
根目录 openspec/          → 跨端全局规格（架构、API 契约、跨端流程）
├── specs/_global/         → 全局约束
└── specs/cross-cutting/   → 跨端功能

frontend/openspec/         → 前端独立规格
backend/openspec/          → 后端独立规格
model-service/openspec/    → 大模型服务独立规格
```
