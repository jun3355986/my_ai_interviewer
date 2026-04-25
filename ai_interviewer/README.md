# AI 面试助手

基于 LangChain 与 Azure 上部署模型的智能面试助手，支持完整的面试流程管理。

## 功能特性

- ✅ **完整面试流程**：简历提交 → 开场白 → 自我介绍 → 项目提问 → 技术面试 → 总结评分
- ✅ **简历解析**：支持 PDF 和纯文本格式
- ✅ **智能提问**：基于简历内容自动生成相关问题
- ✅ **问题库管理**：支持导入、检索面试题（RAG）
- ✅ **评分系统**：每个回答自动打分，面试结束给出总体评分
- ✅ **持久化存储**：面试记录保存到数据库，支持查询和回顾

## 环境变量

### 必需配置

- `AZURE_OPENAI_API_KEY`: Azure AI Foundry API Key（用于 LLM + embedding）

### 可选配置

- `AZURE_OPENAI_ENDPOINT`: 模型服务 endpoint，默认 `https://liuwe-m7o7yvmk-eastus2.services.ai.azure.com/openai/v1/`
- `AZURE_OPENAI_CHAT_MODEL`: 主对话模型，默认 `grok-4-20-reasoning`
- `AZURE_OPENAI_BACKUP_CHAT_MODEL`: 备用对话模型，默认 `gpt-5.4`
- `AZURE_OPENAI_EMBEDDING_MODEL`: 向量模型，默认 `embed-v-4-0`
- `AZURE_OPENAI_EMBEDDING_DIMENSION`: 向量维度，默认 `1024`

### 配置示例

```bash
# 必需 - Azure Foundry Key
export AZURE_OPENAI_API_KEY="your_azure_api_key"

# 可选 - endpoint 与模型
export AZURE_OPENAI_ENDPOINT="https://liuwe-m7o7yvmk-eastus2.services.ai.azure.com/openai/v1/"
export AZURE_OPENAI_CHAT_MODEL="grok-4-20-reasoning"
export AZURE_OPENAI_BACKUP_CHAT_MODEL="gpt-5.4"
export AZURE_OPENAI_EMBEDDING_MODEL="embed-v-4-0"
export AZURE_OPENAI_EMBEDDING_DIMENSION="1024"
```

### 本机密钥配置文件

当前本机约定从以下文件查看 Azure 真实配置（请勿将真实密钥提交到仓库）：

- `/Users/junjielong/myai/My_AI_KEY.md`

## 安装依赖

使用 uv 或 pip 安装：

```bash
# uv（推荐）
uv pip install -e .

# 或 pip
pip install -e .
```

## 快速开始

### 1. 启动服务

```bash
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

服务启动后，访问 `http://localhost:8000/docs` 查看 API 文档。

### 2. 导入面试题（首次使用）

```bash
# 使用 curl 导入 PDF 面试题
curl -X POST "http://localhost:8000/interview/questions/import" \
  -F "file=@your_questions.pdf"
```

或者在 API 文档页面直接上传文件。

### 3. 开始面试

#### 步骤 1: 上传简历并开始面试

```bash
# 上传简历
curl -X POST "http://localhost:8000/interview/upload-resume" \
  -F "file=@resume.pdf"

# 使用返回的 resume_content 开始面试
curl -X POST "http://localhost:8000/interview/start" \
  -H "Content-Type: application/json" \
  -d '{
    "resume_content": "简历内容...",
    "job_requirements": "Java高级开发工程师，要求3年以上经验...",
    "candidate_name": "张三"
  }'
```

返回示例：
```json
{
  "session_id": "xxx-xxx-xxx",
  "opening": "您好，欢迎参加本次面试...",
  "stage": "opening"
}
```

#### 步骤 2: 进入自我介绍环节

```bash
curl -X POST "http://localhost:8000/interview/{session_id}/opening-response"
```

#### 步骤 3: 提交自我介绍

```bash
curl -X POST "http://localhost:8000/interview/{session_id}/self-introduction" \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "xxx-xxx-xxx",
    "answer": "我叫张三，有5年Java开发经验..."
  }'
```

#### 步骤 4: 回答项目问题

系统会根据简历自动生成项目相关问题，逐个回答：

```bash
curl -X POST "http://localhost:8000/interview/{session_id}/project-answer" \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "xxx-xxx-xxx",
    "answer": "我在这个项目中负责..."
  }'
```

每个回答会得到：
- 评分（0-100分）
- 反馈意见
- 下一个问题（或追问）

#### 步骤 5: 技术面试

项目提问结束后，进入技术面试环节：

```bash
# 开始技术面试
curl -X POST "http://localhost:8000/interview/{session_id}/start-technical" \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "xxx-xxx-xxx",
    "question_types": ["Java基础", "多线程", "Spring"],
    "counts": {"Java基础": 3, "多线程": 2, "Spring": 3}
  }'
```

然后回答技术问题：

```bash
curl -X POST "http://localhost:8000/interview/{session_id}/technical-answer" \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "xxx-xxx-xxx",
    "answer": "HashMap的实现原理是..."
  }'
```

#### 步骤 6: 面试总结

所有问题回答完毕后：

```bash
curl -X POST "http://localhost:8000/interview/{session_id}/conclude"
```

返回最终评分和反馈。

## API 接口说明

### 面试流程接口

- `POST /interview/start` - 开始面试
- `POST /interview/upload-resume` - 上传简历文件
- `POST /interview/{session_id}/opening-response` - 进入自我介绍环节
- `POST /interview/{session_id}/self-introduction` - 提交自我介绍
- `POST /interview/{session_id}/project-answer` - 回答项目问题
- `POST /interview/{session_id}/start-technical` - 开始技术面试
- `POST /interview/{session_id}/technical-answer` - 回答技术问题
- `POST /interview/{session_id}/conclude` - 面试总结
- `GET /interview/{session_id}/info` - 获取会话信息

### 问题库管理接口

- `POST /interview/questions/import` - 导入面试题文件（PDF/文本）
- `GET /interview/questions/count` - 获取问题总数
- `POST /interview/questions/search` - 搜索问题

### 简单对话接口（向后兼容）

- `POST /interview/ask` - 简单问答接口

## 代码结构

```
ai_interviewer/
├── api/
│   ├── interviewer.py      # 面试官核心逻辑
│   └── router.py            # API 路由
├── core/
│   ├── model_provider.py    # 模型统一接入（Chat + Embedding）
│   ├── config.py            # Chat LLM 兼容入口
│   └── embeddings.py        # Embedding 兼容入口
├── services/
│   ├── interview_service.py # 面试流程服务
│   ├── interview_session.py # 会话管理
│   ├── question_bank.py     # 问题库管理（RAG）
│   ├── resume_parser.py     # 简历解析
│   └── database.py          # 数据库模型
├── schemas/
│   └── chat.py              # API 数据模型
├── storage/
│   ├── database/            # SQLite 数据库
│   └── vector_db/           # Chroma 向量数据库
└── main.py                  # FastAPI 应用入口
```

## 技术栈

- **框架**: FastAPI
- **LLM**: Azure AI Foundry（`grok-4-20-reasoning` 默认，`gpt-5.4` 备用）
- **向量数据库**: Chroma
- **Embedding**: Azure AI Foundry `embed-v-4-0`
- **数据库**: SQLite (SQLAlchemy)
- **文档解析**: PyPDF, LangChain Document Loaders

## 注意事项

1. **模型配置**：系统统一使用 Azure OpenAI/Foundry OpenAI SDK 接口。默认主模型 `grok-4-20-reasoning`，备用 `gpt-5.4`，向量模型 `embed-v-4-0`。

2. **问题库初始化**：首次使用前，需要导入面试题文件到问题库。

3. **面试流程**：严格按照流程顺序调用 API，每个阶段都有状态校验。

4. **评分标准**：系统会根据回答的完整性、准确性、逻辑性等因素客观评分。

5. **向量维度**：`embed-v-4-0` 支持多种维度配置，默认 1024。更高的维度能捕捉更丰富的语义信息，但也会增加存储和计算成本。

## 开发计划

- [ ] 支持图片简历 OCR（DeepSeek OCR）
- [ ] 面试记录导出功能
- [ ] 更丰富的问题类型筛选
- [ ] 面试数据统计分析
- [ ] Web UI 界面

## 许可证

MIT License
