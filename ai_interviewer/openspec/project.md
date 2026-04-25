# Project Context

## Purpose

AI 面试助手是一个基于 LangChain 和 DeepSeek 的智能面试系统，提供完整的面试流程管理功能。系统支持简历解析、智能提问、问题库管理（RAG）、自动评分和面试记录持久化存储，帮助企业或面试官更高效地完成面试流程。

核心目标：
- 实现标准化的 AI 辅助面试流程
- 基于简历内容智能生成相关面试问题
- 提供可扩展的问题库管理机制
- 客观评分并生成面试反馈

## Tech Stack

### 后端框架
- **FastAPI**: Web 框架，提供 RESTful API
- **Uvicorn**: ASGI 服务器
- **Python 3.12+**: 项目运行环境

### AI 与 LLM
- **LangChain**: LLM 应用开发框架
- **DeepSeek (deepseek-chat)**: 主要对话模型（OpenAI 兼容 API）
- **阿里云 DashScope (text-embedding-v4)**: 中文 embedding 模型

### 数据存储
- **Chroma**: 向量数据库，用于问题库 RAG 检索
- **SQLite + SQLAlchemy**: 结构化数据存储（面试记录、会话信息）

### 文档处理
- **PyPDF**: PDF 简历和面试题解析
- **LangChain Document Loaders**: 文档加载与处理

### 开发工具
- **uv**: Python 包管理器
- **python-multipart**: 文件上传支持

## Project Conventions

### 代码风格

#### 命名规范
- **文件命名**: 使用 snake_case（小写下划线分隔）
- **类命名**: 使用 PascalCase（大驼峰）
- **函数/变量命名**: 使用 snake_case
- **常量命名**: 使用 UPPER_SNAKE_CASE
- **API 路由**: 使用 kebab-case（短横线分隔）

#### 代码格式
- 使用 **Black** 代码格式化（默认配置）
- 行长度限制：88 字符（Black 默认）
- 导入排序：标准库 → 第三方库 → 本地模块

#### 类型注解
- 所有公开函数必须包含类型注解
- 复杂泛型类型可适当省略，保持代码可读性
- 使用 `Optional[T]` 而非 `Union[T, None]`

#### 文档字符串
- 所有公开类、函数需添加 docstring
- 使用中文注释和文档（项目主要面向中文用户）
- API 接口使用 FastAPI 自动生成文档

### 架构模式

#### 分层架构
```
api/          # API 路由层 - 处理 HTTP 请求/响应
core/         # 核心配置层 - 配置管理、工具函数
services/     # 业务逻辑层 - 核心业务逻辑实现
schemas/      # 数据模型层 - Pydantic 数据模型
storage/      # 存储层 - 数据库、向量库持久化
```

#### 设计原则
- **单一职责**: 每个模块专注于单一功能
- **依赖倒置**: 业务逻辑依赖于抽象接口
- **配置分离**: 环境变量管理所有敏感配置
- **RESTful 设计**: API 遵循 REST 规范

#### 错误处理
- 使用 FastAPI 的 `HTTPException` 处理业务异常
- 全局异常处理器统一处理未预期错误
- 错误响应包含清晰的错误信息和状态码

### 测试策略

#### 测试优先级
1. **核心业务逻辑测试**: interview_service.py, interview_session.py
2. **API 接口测试**: 主要 API 端点的功能测试
3. **集成测试**: 数据库、向量库交互测试

#### 测试原则
- 新功能必须包含对应的测试用例
- 关键路径测试覆盖率不低于 80%
- 使用 pytest 作为测试框架

### Git Workflow

#### 分支策略
- `main`: 主分支，始终保持稳定可发布状态
- `develop`: 开发分支，包含下一版本功能
- `feature/*`: 功能分支，开发新功能
- `hotfix/*`: 紧急修复分支

#### 提交规范
```
<type>(<scope>): <subject>

feat(api): 添加简历上传接口
fix(service): 修复面试状态转换 bug
docs(readme): 更新快速开始指南
chore(deps): 升级 LangChain 版本
```

#### 合并策略
- 所有功能分支通过 Pull Request 合并
- 至少需要 1 人 code review
- 通过所有 CI 检查后合并

## Domain Context

### 面试流程领域模型

#### 面试阶段（按顺序）
1. **opening**: 开场白阶段 - 系统问候并介绍面试流程
2. **self_introduction**: 自我介绍阶段 - 候选人介绍背景
3. **project_question**: 项目提问阶段 - 基于简历内容提问
4. **technical**: 技术面试阶段 - 标准化技术问题问答
5. **concluded**: 结束阶段 - 汇总评分和反馈

#### 核心实体
- **InterviewSession**: 面试会话，管理整个面试生命周期
- **Resume**: 简历内容，用于生成个性化问题
- **QuestionBank**: 问题库，支持 RAG 检索
- **AnswerRecord**: 回答记录，包含评分和反馈

### 技术面试题类型
- Java 基础、多线程、Spring 框架
- Python、数据库、系统设计
- 可扩展的问题类型支持

### 评分维度
- 答案完整性
- 技术准确性
- 表达逻辑性
- 深度与广度

## Important Constraints

### 运行时约束
- 必须配置 `DEEPSEEK_API_KEY` 和 `DASHSCOPE_API_KEY` 环境变量
- Python 版本必须 >= 3.12
- 首次使用需要导入面试题到向量数据库

### 性能约束
- API 响应时间应 < 3 秒（不含 LLM 调用）
- 向量检索使用 Chroma 本地存储
- 单一用户场景，单进程部署

### 安全约束
- API Key 通过环境变量管理，不存入代码库
- 文件上传仅限 PDF 和文本格式
- CORS 配置允许所有来源（开发环境）

### 兼容性约束
- DeepSeek API 保持 OpenAI 兼容接口
- 向量维度支持 64-2048，默认 1024
- SQLite 数据库无外部依赖

## External Dependencies

### 必需 API 服务
- **DeepSeek API**: LLM 对话能力
  - API 端点: `https://api.deepseek.com/v1`
  - 默认模型: `deepseek-chat`
- **阿里云 DashScope API**: 中文 embedding 能力
  - 模型: `text-embedding-v4`
  - 支持维度: 64, 128, 256, 512, 768, 1024, 1536, 2048

### 开发工具
- **uv**: Python 包管理（推荐）
- **pytest**: 测试框架
- **Black**: 代码格式化

### 基础设施
- **Chroma**: 向量数据库（本地文件存储）
- **SQLite**: 关系型数据库（本地文件存储）

## Development Guidelines

### 快速开发流程
1. 在 `services/` 目录实现核心业务逻辑
2. 在 `api/` 目录添加对应的 API 路由
3. 在 `schemas/` 目录定义请求/响应模型
4. 更新 `README.md` 文档（如果添加新功能）
5. 编写对应测试用例

### 代码位置约定
- 配置相关 → `core/config.py`
- API 路由 → `api/router.py`
- 核心逻辑 → `services/interview_service.py`
- 会话管理 → `services/interview_session.py`
- 向量检索 → `services/question_bank.py`
- 数据模型 → `schemas/chat.py`

### API 设计规范
- 使用 FastAPI 依赖注入管理服务实例
- 所有 API 路径以 `/interview/` 开头
- 使用 Pydantic 模型定义请求/响应
- 文件上传使用 `UploadFile` 类型
