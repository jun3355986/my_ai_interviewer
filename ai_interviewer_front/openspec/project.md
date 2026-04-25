# Project Context

## Purpose

AI 面试官助手 (AI Interview Assistant) 是一款 AI 驱动的模拟面试移动应用，帮助求职者通过与 AI 面试官对话练习面试技巧。

核心功能：
- 用户可以上传简历，系统自动解析简历内容
- AI 面试官基于简历生成个性化面试问题
- 支持完整的面试流程：开场 → 自我介绍 → 项目经验 → 技术问答 → 总结评分
- 每个回答自动打分，面试结束给出总体评分和反馈
- 面试记录持久化存储，支持历史查询和回顾

这是一个 Flutter iOS 前端项目，与 Python FastAPI 后端（ai_interviewer）配合使用。

## Tech Stack

### 前端
- **框架**: Flutter 3.x
- **平台**: iOS (目标平台)
- **UI 风格**: iOS 原生设计语言 (Cupertino)
- **包管理**: pub.dev

### 后端（配套项目）
- **框架**: FastAPI (Python)
- **AI 引擎**: DeepSeek (通过 LangChain 调用)
- **向量数据库**: Chroma
- **Embedding**: 阿里云 DashScope text-embedding-v4
- **数据库**: SQLite (SQLAlchemy)
- **文档解析**: PyPDF, LangChain Document Loaders

### API 通信
- RESTful API
- 后端服务地址: `http://localhost:8000`
- API 文档: `http://localhost:8000/docs`

## Project Conventions

### Code Style

#### 命名规范
- 文件名: 使用 snake_case (例如: `login_page.dart`, `home_page.dart`)
- 类名: 使用 PascalCase (例如: `HomePage`, `LoginPage`)
- 变量和函数: 使用 camelCase (例如: `userName`, `buildLoginCard`)
- 常量: 使用 kCamelCase (例如: `kPrimaryColor`)

#### 组件规范
- 每个页面独立一个文件
- 使用 `StatelessWidget` 或 `StatefulWidget` 根据需要选择
- 复杂的 UI 组件提取为独立方法或小部件
- 始终为 StatefulWidget 添加 `dispose()` 方法清理资源

#### 注释规范
- 使用中文注释（项目以中文为主）
- 公开方法和类添加文档注释
- TODO 注释标记待实现功能

#### 格式规范
- 使用 `dart format` 自动格式化
- 使用 `analysis_options.yaml` 中的 lint 规则
- 偏好使用 `const` 构造函数

### Architecture Patterns

#### 页面结构
- 每个页面是一个独立的 StatefulWidget 或 StatelessWidget
- 复杂 UI 分解为多个私有方法 (`_buildXxx()`)
- 页面间导航使用 `Navigator.pushNamed()` 或命名路由

#### 状态管理
- 当前使用 Flutter 内置的 `setState()` 进行局部状态管理
- 未来考虑引入 Provider 或 Riverpod 进行复杂状态管理

#### API 集成模式
- 使用 `http` 包进行网络请求
- 封装 API 客户端类处理请求/响应
- 异步操作使用 `async/await`
- 错误处理使用 try-catch

### Testing Strategy

- 使用 Flutter test 框架编写单元测试
- Widget 测试覆盖关键 UI 组件
- 集成测试验证用户流程
- 目标：关键路径测试覆盖率达到 80%+

### Git Workflow

#### 分支策略
- `main`: 主分支，始终保持稳定可发布状态
- `develop`: 开发分支，功能集成后合并到此
- `feature/*`: 功能分支，从 develop 创建
- `fix/*`: 修复分支，从 develop 创建

#### 提交规范
- 使用中文提交信息或英文提交信息
- 格式: `<type>(<scope>): <description>`
- 类型: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
- 示例: `feat(login): 添加第三方登录功能`

#### Code Review
- 所有合并到 main/develop 需要 Pull Request
- 至少一人 review 后才能合并
- 确保 CI/CD 流水线通过

## Domain Context

### 面试流程
1. **开场**: AI 面试官问候，确认开始面试
2. **自我介绍**: 用户进行自我介绍，AI 给出反馈和评分
3. **项目经验**: 基于简历中的项目，AI 提问相关问题
4. **技术问答**: 从问题库中检索相关技术问题进行提问
5. **总结**: 给出总体评分、各环节评分和改进建议

### 角色说明
- **用户**: 求职者，使用应用进行面试练习
- **AI 面试官**: 由 DeepSeek 驱动的智能面试官角色

### 核心数据模型
- **用户信息**: 用户名、头像、第三方登录信息
- **简历**: PDF 文件或文本内容
- **面试会话**: 包含多个问题和回答的完整会话记录
- **评分**: 每个回答的分数和反馈

## Important Constraints

1. **平台限制**: 当前仅支持 iOS 平台
2. **API 依赖**: 必须配合后端服务使用（localhost:8000）
3. **API Key**: 需要配置 DeepSeek API Key 和 DashScope API Key
4. **文件格式**: 简历仅支持 PDF 格式，最大 10MB
5. **网络要求**: 需要网络连接才能进行 AI 面试
6. **iOS 版本**: 最低支持 iOS 12.0

## External Dependencies

### 后端服务
- **FastAPI 服务**: `http://localhost:8000`
- **API 文档**: Swagger UI at `/docs`
- **健康检查**: `GET /health`

### 第三方服务
- **DeepSeek AI**: LLM 对话服务
- **阿里云 DashScope**: 向量嵌入服务

### 内部服务
- **SQLite**: 本地数据存储（后端）
- **Chroma**: 向量数据库（后端）

### 前端依赖
- `flutter/material.dart`: Material Design 组件
- `flutter/cupertino.dart`: iOS 风格组件
- `http`: HTTP 请求库
- 未来可能添加: `provider`, `shared_preferences`, `file_picker`
