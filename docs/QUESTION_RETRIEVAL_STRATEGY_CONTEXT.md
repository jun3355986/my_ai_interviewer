# 技术题检索方案与当前落地背景

> 用途：这份文档用于在新对话中快速恢复背景。它总结了技术题检索的三个推荐方案、当前项目实际采用的优化方案，以及继续开发/部署/验收时的注意事项。

## 1. 项目背景

AI Interviewer 是一个 monorepo，和技术题检索相关的模块主要有：

- `ai_interviewer/`：Python FastAPI AI 服务，负责 Chroma 向量库、题库检索和面试技术题召回。
- `ai_interviewer_admin/`：Java 21 Spring Boot 后台管理服务，负责题库导入、审核、CRUD、上下架、向量同步调度。
- `ai_interviewer_admin_front/`：React + Vite 后台管理页面，提供题库导入、审核、上架、下架等操作入口。
- `ai_interview_backend/`：Spring Cloud Java 微服务和 Docker Compose，本地联调时负责 Gateway、基础设施和服务编排。

当前题库数据流：

```text
后台导入/维护题库
  -> Java Admin 写入 PostgreSQL t_question_bank
  -> Java Admin 调用 Python AI 向量同步/删除接口
  -> Python AI 写入或删除 Chroma 向量库
  -> 面试流程调用 Python QuestionBank.search_questions()
  -> 返回候选技术题
```

## 2. 推荐的 3 个检索方案

### 方案一：Hybrid 检索 + RRF 融合 + 语义重排 + 热度加权

这是当前最推荐、也最适合本项目阶段的主线方案。

核心思路：

- 关键词召回和向量召回并行执行。
- 关键词召回负责精确技术术语，例如 `JVM`、`Redis 缓存雪崩`、`Kafka ISR`、`MySQL MVCC`。
- 向量召回负责语义相近表达，例如“热点 key 失效保护”召回“缓存击穿防护”。
- 用 RRF，也就是 Reciprocal Rank Fusion，把多路召回结果融合。
- 融合后再叠加题型、难度、技能领域、标签、岗位匹配度等业务权重。
- 如果预算允许，再对 TopN 使用 reranker 做语义重排。
- 如果有真实趋势数据，再叠加 `trend_score`、`hot_score`、`popularity_score`、`usage_score` 等热度字段。

优点：

- 工程复杂度适中，适合当前 Chroma + Admin 后台架构。
- 对中文技术术语和岗位技能词更稳。
- 可以渐进升级到 Elasticsearch/OpenSearch BM25、独立 reranker、在线热度特征。

缺点：

- 如果关键词召回只做本地扫描，题库规模变大后性能会下降。
- 如果题目元数据质量差，题型/难度/标签加权的收益会变弱。
- 热度加权需要真实业务数据支撑，否则只是静态权重。

### 方案二：Agentic Retrieval 查询规划检索

这是复杂查询和题单编排的增强方案。

核心思路：

- 先用 LLM 对查询意图进行拆解。
- 将复杂需求拆成多个子查询。
- 子查询分别检索题库、岗位要求、简历能力点、历史答题弱项等不同来源。
- 最后聚合、去重、重排，生成更完整的题单。

示例：

```text
输入：
3-5 年 Java 后端，偏高并发、缓存一致性、故障排查，生成 8 道渐进式题目。

可能拆解为：
1. Java 并发基础
2. Redis 缓存一致性
3. 高并发限流降级
4. 线上故障排查
5. 难度递进题单
```

优点：

- 复杂意图理解更强。
- 更适合生成完整面试计划，而不是只返回相似题。
- 后续可以结合候选人简历、岗位 JD、历史答题表现做个性化面试。

缺点：

- 成本更高。
- 调试复杂度更高。
- 稳定性依赖 LLM 查询拆解质量。

### 方案三：GraphRAG / 知识图谱检索

这是题库平台化和知识覆盖度优化方案。

核心思路：

- 把技能点、概念、题目、难度、前置关系、岗位要求组织成图谱。
- 检索时不仅找相似题，还按知识结构做覆盖。
- 更适合“岗位知识地图”和“完整面试路径”。

示例结构：

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
- 适合做岗位能力模型、学习路径、面试路径。
- 能减少题目集中在同一知识点的问题。

缺点：

- 构建和维护成本最高。
- 需要长期维护技能点体系、题目标签和概念关系。
- 不适合作为第一阶段快速上线方案。

## 3. 当前项目实际采用的方案

当前项目实际按 **方案一的轻量落地版** 做了优化。

从原来的：

```text
Chroma 纯向量相似度检索
```

升级为：

```text
Chroma 向量召回
  + 本地关键词召回
  + RRF 融合排序
  + 元数据加权
  + 预留热度加权字段
```

已经落地的能力：

- `QuestionBank.search_questions()` 不再只是 `similarity_search()`。
- 检索时会多取一批向量候选。
- 同时从 Chroma 已存文档中做本地关键词召回。
- 使用 RRF 融合向量召回和关键词召回。
- 根据 `question_type`、`difficulty`、`skill_area`、`tags` 做轻量加权。
- 预留 `trend_score`、`hot_score`、`popularity_score`、`usage_score` 等热度字段。
- 中文连续短语做了 n-gram 增强，例如 `缓存雪崩怎么解决` 可以命中 `缓存雪崩`。
- 同源文件拆出的多个题块不会因为 `source` 相同而被错误去重。

核心文件：

- `ai_interviewer/services/question_bank.py`
- `ai_interviewer/tests/test_question_bank_hybrid.py`

当前还没有落地的能力：

- 没有接 Elasticsearch/OpenSearch BM25。
- 没有接独立 reranker 模型。
- 没有线上点击率、收藏率、面试通过率等真实热度特征。
- 没有离线评测集和 nDCG/MRR 自动评估。
- 没有 Agentic Retrieval。
- 没有 GraphRAG。

准确表述应该是：

```text
当前已落地 Chroma 向量召回 + 本地关键词召回 + RRF + 元数据加权。
```

不要误表述为：

```text
当前已接入 Elasticsearch BM25、生产级 reranker、完整热度排序。
```

## 4. 后台题库管理与向量同步现状

后台已经支持：

- 导入 `.pdf`、`.md`、`.docx`、`.txt`、`.csv` 题库文件。
- 文件导入后生成导入批次。
- 非结构化文本会按字段解析题目。
- 导入题目默认进入 `待审核`。
- 后台可展示题目列表、导入批次、状态、向量同步状态。
- 后台可对题目做 CRUD、审核通过、驳回、上架、下架、删除。
- 审核通过或上架后自动同步到 Python Chroma 向量库。
- 下架、驳回或删除后自动从 Python Chroma 向量库删除。

推荐导入格式：

```text
题目：Redis 缓存雪崩怎么解决？
参考答案：预热缓存、随机过期、限流降级、多级缓存。
题型：TECHNICAL
难度：MEDIUM
技能领域：Redis
标签：Redis;缓存;高并发
```

多个题目之间用空行分隔。

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

## 5. 重要注意事项

### Chroma 本地向量库是运行数据

本地导入、审核、同步、下架会改动：

```text
ai_interviewer/storage/vector_db/chroma.sqlite3
ai_interviewer/storage/vector_db/*/*.bin
```

这些通常是运行数据，不应作为代码提交，除非明确要提交种子题库。

### Docker Compose 必须配置向量删除 URL

Admin 容器内不能用 `localhost:8000` 访问 Python 服务。

Compose 中需要配置：

```yaml
PYTHON_AI_QUESTION_BANK_SYNC_URL: http://python-ai:8000/admin/question-bank/sync
PYTHON_AI_QUESTION_BANK_DELETE_URL: http://python-ai:8000/admin/question-bank/delete
```

如果删除 URL 缺失，下架、驳回、删除题目时会访问错误地址，导致向量删除失败。

### Java/Maven 必须使用 JDK 21

本机通过 jenv 管理 JDK，不要只看 `java -version`。

推荐命令：

```bash
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv version
JENV_ROOT="$HOME/.jenv" /opt/homebrew/bin/jenv exec mvn test
```

### Jenkins 生产部署受网络限制

运维文档说明使用生产环境前要确认本机 IP 在 `192.168.1.xxx` 网段。

之前本机 IP 是 `192.168.31.130`，访问 Jenkins `192.168.1.199:8880` 时端口可连但 HTTP 返回空响应或超时，所以没有强行触发 Jenkins 生产部署。

本地验收用 Docker Compose：

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

## 6. 推荐下一阶段优化顺序

1. 建立离线评测集：典型 query、期望题目、人工相关性标注。
2. 增加检索指标：Recall@K、MRR、nDCG。
3. 将本地关键词召回替换为 PostgreSQL full-text、Elasticsearch 或 OpenSearch BM25。
4. 接入 reranker，只对 Top50 或 Top100 候选重排，控制成本。
5. 建立真实热度特征：点击、收藏、被选中次数、答题效果、面试官反馈。
6. 在方案一稳定后，再考虑 Agentic Retrieval。
7. 在题库标签和技能体系稳定后，再考虑 GraphRAG。

## 7. 新对话可直接粘贴的短背景

```text
项目是 AI Interviewer Monorepo。技术题检索原来是 Python AI 服务里的 Chroma 纯向量相似度检索。现在已按“方案一轻量版”优化为 Chroma 向量召回 + 本地关键词召回 + RRF 融合 + 元数据加权，并预留 trend_score/hot_score/popularity_score/usage_score 热度字段。后台 Java Admin 已支持 pdf/md/docx/txt/csv 题库导入，导入后默认待审核；审核通过/上架自动同步到 Python Chroma；下架/驳回/删除自动从向量库删除。当前还没有接 Elasticsearch/OpenSearch BM25、独立 reranker、Agentic Retrieval 或 GraphRAG。注意本地 Chroma 数据文件是运行数据，通常不要提交；Java/Maven 用 jenv 的 JDK 21；Docker Compose 里 Admin 必须配置 PYTHON_AI_QUESTION_BANK_SYNC_URL 和 PYTHON_AI_QUESTION_BANK_DELETE_URL。
```
