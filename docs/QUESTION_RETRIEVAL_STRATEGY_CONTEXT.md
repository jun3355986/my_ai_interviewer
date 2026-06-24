# 技术题检索方案与当前落地背景

> 用途：这是一份给新对话使用的背景信息。它总结了技术题检索的推荐方案、当前项目实际采用的优化方案、已落地的代码边界和后续注意事项。

## 项目背景

当前项目是 AI Interviewer Monorepo，和技术题检索相关的核心模块有：

- `ai_interviewer/`：Python FastAPI AI 服务，负责题库向量库、RAG 检索和面试技术题召回。
- `ai_interviewer_admin/`：Java 21 Spring Boot 后台管理服务，负责题库导入、审核、CRUD、上下架和向量同步调度。
- `ai_interviewer_admin_front/`：React + Vite 后台管理页面，提供题库导入、审核、上下架等操作入口。
- `ai_interview_backend/`：Spring Cloud Java 微服务与 Docker Compose，负责 Gateway、基础设施和本地部署编排。

当前面试技术题推荐链路大致是：

```text
Admin 导入/维护题库
  -> Java Admin 写入 PostgreSQL t_question_bank
  -> Java Admin 调用 Python AI 向量同步接口
  -> Python AI 写入/删除 Chroma 向量库
  -> 面试流程调用 Python QuestionBank.search_questions()
  -> 返回候选技术题
```

## 推荐的 3 个检索方案

### 方案一：Hybrid 检索 + RRF 融合 + 语义重排 + 热度加权

这是最推荐的主线方案。

核心思想：

- 同时做关键词召回和向量召回。
- 关键词召回适合精确术语，例如 `JVM`、`Redis 缓存雪崩`、`Kafka ISR`、`MySQL MVCC`。
- 向量召回适合语义相关，例如“缓存击穿怎么防”可以召回“热点 key 失效保护”。
- 用 RRF，也就是 Reciprocal Rank Fusion，把多路召回结果融合排序。
- 在融合后增加元数据加权，例如题型、难度、技能领域、标签、岗位匹配度。
- 如果预算允许，再接一个 reranker 对 TopN 做语义重排。
- 如果有趋势数据，再加入 `trend_score`、`hot_score`、`popularity_score` 这类热度字段。

优点：

- 工程可控，适合现在的题库规模和架构。
- 对中文技术术语友好。
- 不需要一开始引入复杂图谱或 Agent。
- 后续可以平滑升级到 Elasticsearch/OpenSearch BM25 或独立 reranker。

缺点：

- 如果只用本地关键词扫描，题库很大时性能会下降。
- 如果没有高质量元数据，排序效果会受限。
- 热度加权需要额外维护数据来源，否则容易变成静态权重。

适用场景：

- 技术题库推荐。
- 面试中按岗位、技能栈、候选人表现动态选题。
- 当前项目阶段的最佳落地方案。

### 方案二：Agentic Retrieval 查询规划检索

这是复杂查询增强方案。

核心思想：

- 先用 LLM 对用户或面试策略的查询进行拆解。
- 把复杂需求拆成多个子查询。
- 子查询分别走题库、岗位要求、简历能力点、历史答题弱项等不同来源。
- 最后聚合、去重、重排。

示例：

```text
输入：
3-5 年 Java 后端，偏高并发、缓存一致性、故障排查，生成 8 道渐进式题目。

Agentic Retrieval 可能拆成：
1. Java 并发基础
2. Redis 缓存一致性
3. 高并发限流降级
4. 线上故障排查
5. 难度递进题单
```

优点：

- 对复杂意图更强。
- 适合“生成完整题单”而不只是“找相似题”。
- 可以结合候选人简历、岗位 JD、历史答题表现做个性化规划。

缺点：

- 成本更高。
- 稳定性依赖 LLM 查询拆解质量。
- 调试复杂度比方案一高。

适用场景：

- 后续做“智能面试官策略编排”。
- 根据岗位和候选人画像生成完整面试计划。
- 方案一稳定后再叠加。

### 方案三：GraphRAG / 知识图谱检索

这是覆盖度和体系化题单方案。

核心思想：

- 把题库组织成图谱：技能点、概念、题目、难度、前置关系、岗位要求。
- 检索时不是只找相似题，而是按知识结构覆盖。
- 可以做“某岗位完整知识面覆盖题单”。

示例图谱关系：

```text
Java 后端
  -> JVM
    -> 类加载
    -> GC
  -> 并发
    -> 线程池
    -> 锁
  -> Redis
    -> 缓存雪崩
    -> 缓存击穿
    -> 缓存一致性
```

优点：

- 覆盖度强。
- 适合岗位知识地图、学习路径、面试题单编排。
- 能减少题目扎堆在同一知识点的问题。

缺点：

- 构建和维护成本最高。
- 需要稳定的技能点体系和题目标签治理。
- 不适合作为第一阶段快速上线方案。

适用场景：

- 中后期做题库平台化。
- 做岗位能力模型、知识地图、完整面试路径。
- 对“覆盖度”和“结构化解释”要求很高时使用。

## 当前项目实际采用的方案

当前项目实际采用的是：**方案一的轻量落地版**。

已经从原来的“纯 Chroma 向量相似度检索”升级为：

```text
Chroma 向量召回
  + 本地关键词召回
  + RRF 融合排序
  + 元数据加权
  + 预留热度加权字段
```

当前已落地能力：

- Python `QuestionBank.search_questions()` 不再只调用 `similarity_search()`。
- 检索时会先取更多向量候选。
- 同时从 Chroma 已存文档中做本地关键词召回。
- 使用 RRF 融合向量召回和关键词召回。
- 根据 `question_type`、`difficulty`、`skill_area`、`tags` 做轻量加权。
- 预留 `trend_score`、`hot_score`、`popularity_score`、`usage_score` 热度字段。
- 中文连续短语做了 n-gram 处理，例如 `缓存雪崩怎么解决` 可以命中 `缓存雪崩`。
- 同源文件拆出的多个题块不会因为 `source` 相同而被错误去重。

核心文件：

- `ai_interviewer/services/question_bank.py`
- `ai_interviewer/tests/test_question_bank_hybrid.py`

当前还没有落地的方案一能力：

- 没有接 Elasticsearch/OpenSearch BM25。
- 没有接独立 reranker 模型。
- 没有线上点击率、收藏率、面试通过率等真实热度特征。
- 没有离线评测集和 nDCG/MRR 自动评估。

## 题库管理与向量同步现状

当前后台已经支持：

- 导入 `.pdf`、`.md`、`.docx`、`.txt`、`.csv` 题库文件。
- 文件导入后生成导入批次。
- 非结构化文本会按 `题目/问题/问`、`答案/参考答案/答`、`题型`、`难度`、`技能领域`、`标签` 等字段解析。
- 导入题目默认进入 `待审核`。
- 后台可展示题目列表、导入批次、状态、向量同步状态。
- 后台可对题目做 CRUD、审核通过、驳回、上架、下架、删除。
- 审核通过或上架后自动同步到 Python Chroma 向量库。
- 下架、驳回或删除后自动从 Python Chroma 向量库删除。

题目状态约定：

```text
0 = 已下架
1 = 已上架
2 = 待审核
3 = 已驳回
```

向量状态约定：

```text
PENDING = 待同步
SYNCED = 已同步
FAILED = 同步失败
DELETE_PENDING = 待删除向量
DELETED = 已删除向量
```

相关文件：

- `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionImportService.java`
- `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionController.java`
- `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionVectorSyncService.java`
- `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/client/PythonQuestionBankClient.java`
- `ai_interviewer/api/admin_router.py`
- `ai_interviewer_admin_front/src/App.tsx`
- `ai_interview_backend/docker-compose.yml`

## 新对话中需要注意的事项

### 1. 不要把当前方案误说成完整企业级 Hybrid Search

当前是方案一的轻量版，不是完整的 Elasticsearch/OpenSearch Hybrid Search。

准确说法：

```text
当前已落地 Chroma 向量召回 + 本地关键词召回 + RRF + 元数据加权。
```

不要说：

```text
当前已接入 Elasticsearch BM25、生产级 reranker、完整热度排序。
```

### 2. Chroma 向量库是本地存储，会产生脏数据变更

本地验收或冒烟会改动：

```text
ai_interviewer/storage/vector_db/chroma.sqlite3
ai_interviewer/storage/vector_db/*/*.bin
```

这些通常是运行数据，不应作为代码提交，除非明确要提交题库种子数据。

### 3. Docker Compose 里必须配置向量删除 URL

Admin 容器内不能访问 `localhost:8000` 的 Python 服务，所以 Compose 里需要显式配置：

```yaml
PYTHON_AI_QUESTION_BANK_SYNC_URL: http://python-ai:8000/admin/question-bank/sync
PYTHON_AI_QUESTION_BANK_DELETE_URL: http://python-ai:8000/admin/question-bank/delete
```

如果删除 URL 缺失，下架/驳回/删除题目时会尝试访问容器内 `localhost:8000`，导致向量删除失败。

### 4. JDK 必须按项目约定使用 jenv 确认

本机使用 jenv 管理 JDK。

执行 Java/Maven 相关命令时，不要只看 `java -version`。

建议命令：

```bash
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv version
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv exec mvn test
```

当前 Admin 项目要求 Java 21。

### 5. Jenkins 生产部署受网络限制

运维文档说明访问生产环境前要确认本机 IP 在 `192.168.1.xxx` 网段。

之前本机 IP 是 `192.168.31.130`，访问 Jenkins `192.168.1.199:8880` 时端口可连但 HTTP 返回空响应或超时，因此没有强行触发 Jenkins 生产部署。

本地验收可以使用 Docker Compose：

```bash
cd ai_interview_backend
docker compose up -d --build python-ai admin admin-web
```

验收入口：

```text
后台管理页面：http://localhost:8090
账号：admin
密码：admin123
```

### 6. 文件导入解析格式建议

推荐题库文件写法：

```text
题目：Redis 缓存雪崩怎么解决？
参考答案：预热缓存、随机过期、限流降级、多级缓存。
题型：TECHNICAL
难度：MEDIUM
技能领域：Redis
标签：Redis;缓存;高并发
```

多个题目之间用空行分隔。

如果题目文件是自由散文，没有 `题目：`、`问题：`、`问：` 这类标记，当前规则解析器不会强行抽题。

### 7. 推荐下一阶段优化

如果继续优化检索质量，建议按以下顺序：

1. 建立离线评测集：典型查询、期望题目、人工相关性标注。
2. 增加检索指标：Recall@K、MRR、nDCG。
3. 把本地关键词召回替换为 PostgreSQL full-text、Elasticsearch 或 OpenSearch BM25。
4. 接入 reranker，只对 Top50 或 Top100 候选重排，控制成本。
5. 建立真实热度特征：点击、收藏、被选中次数、答题效果、面试官反馈。
6. 再考虑 Agentic Retrieval 或 GraphRAG。

## 可直接给新对话的简短背景

如果新对话只需要最短上下文，可以直接粘贴下面这段：

```text
项目是 AI Interviewer Monorepo。技术题检索原来是 Python AI 服务里的 Chroma 纯向量相似度检索。现在已按“方案一轻量版”优化为 Chroma 向量召回 + 本地关键词召回 + RRF 融合 + 元数据加权，并预留 trend_score/hot_score/popularity_score/usage_score 热度字段。后台 Java Admin 已支持 pdf/md/docx/txt/csv 题库导入，导入后默认待审核；审核通过/上架自动同步到 Python Chroma；下架/驳回/删除自动从向量库删除。当前还没有接 Elasticsearch/OpenSearch BM25、独立 reranker、GraphRAG 或 Agentic Retrieval。注意本地 Chroma 数据文件是运行数据，通常不要提交；Java/Maven 用 jenv 的 JDK 21；Docker Compose 里 Admin 必须配置 PYTHON_AI_QUESTION_BANK_SYNC_URL 和 PYTHON_AI_QUESTION_BANK_DELETE_URL。
```
