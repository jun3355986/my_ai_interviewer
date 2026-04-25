# Flutter 前端对接文档 - AI面试官后端

本文档旨在指导 Flutter 前端项目与 Spring Cloud Alibaba 后端服务的对接。

## 1. 基本信息

- **Base URL**: `http://localhost:9000` (Gateway网关地址)
- **认证方式**: JWT (JSON Web Token)
- **请求头**: 
  - `Authorization: Bearer <token>` (登录后所有接口必填)
  - `Content-Type: application/json`

## 2. 通用响应格式

所有 HTTP 接口（非 SSE）均返回如下统一格式：

```json
{
  "code": 200,          // 状态码：200成功，其他失败
  "message": "success", // 提示信息
  "data": {},           // 业务数据
  "timestamp": 1704067200000
}
```

## 3. 核心 API 接口

### 3.1 认证模块 (Auth)

**Base Path**: `/api/v1/auth`

#### 3.1.1 用户登录
- **URL**: `/login`
- **Method**: `POST`
- **Request**:
  ```json
  {
    "username": "zhangsan",
    "password": "Password123!"
  }
  ```
- **Response**:
  ```json
  {
    "accessToken": "eyJ...",    // 访问令牌，用于后续请求
    "refreshToken": "eyJ...",   // 刷新令牌
    "expiresIn": 7200,          // 过期时间(秒)
    "tokenType": "Bearer"
  }
  ```

#### 3.1.2 用户注册
- **URL**: `/register`
- **Method**: `POST`
- **Request**:
  ```json
  {
    "username": "zhangsan",
    "email": "zhang@example.com",
    "password": "Password123!"
  }
  ```

### 3.2 面试模块 (Interview) - 核心

**Base Path**: `/api/v1/interviews`

#### 3.2.1 AI 面试对话 (SSE 流式接口)
这是最核心的接口，用于与 AI 面试官进行实时对话。

- **URL**: `/chat`
- **Method**: `POST`
- **Headers**: 
  - `Accept: text/event-stream`
- **Request**:
  ```json
  {
    "sessionId": "abc-123",     // 会话ID (首次对话传 null)
    "message": "我准备好了",      // 用户回复内容
    "resumeId": 1,              // 简历ID (首次对话必填)
    "jobId": 1                  // 职位ID (可选)
  }
  ```

- **SSE 事件流 (Event Stream)**:
  前端需监听以下事件类型：

  | 事件名 (`event`) | 说明 | 数据示例 (`data`) | 处理建议 |
  |----------------|------|-------------------|----------|
  | `status` | 状态更新 | `{"stage":"opening", "session_id":"..."}` | 更新界面上的会话ID和当前阶段 |
  | `chunk` | 对话内容 | `{"content":"你好"}` | 将内容追加显示到气泡中 |
  | `score` | 评分反馈 | `{"score":85, "feedback":"..."}` | 后台处理或此时暂不显示 |
  | `result` | 阶段结果 | `{"next_question":"..."}` | 准备接收下一题 |
  | `done` | 结束信号 | `{"is_interview_complete":false}` | 本轮回答结束，允许用户输入 |

#### 3.2.2 获取面试列表
- **URL**: `/` (即 `/api/v1/interviews`)
- **Method**: `GET`
- **Params**: `page=1&size=10`
- **Response (Data)**:
  ```json
  {
    "records": [
      {
        "sessionId": "abc-123",
        "stageDisplay": "项目提问", // 当前阶段中文名
        "status": 1,              // 1:进行中, 2:已完成
        "lastQuestion": "...",    // 最后一次提问
        "updatedAt": "..."
      }
    ],
    "total": 10
  }
  ```

#### 3.2.3 恢复/继续面试
用于从列表点击进入某个历史面试。

- **URL**: `/{id}/resume`
- **Method**: `POST`
- **Response**: 返回最新的会话状态信息，前端随后可调用 `/chat` 继续发送空消息或最后一条消息来触发。

### 3.3 简历模块 (Resume)

**Base Path**: `/api/v1/resumes`

#### 3.3.1 上传简历
- **URL**: `/upload`
- **Method**: `POST`
- **Content-Type**: `multipart/form-data`
- **Form Data**:
  - `file`: (二进制文件)
- **Response**:
  ```json
  {
    "id": 1,
    "fileName": "resume.pdf",
    "parsedContent": "..." // 简要解析结果
  }
  ```

#### 3.3.2 简历列表
- **URL**: `/`
- **Method**: `GET`

## 4. 错误码参照

| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| 200 | 成功 | - |
| 401 | 未授权/Token过期 | 跳转登录页，或尝试刷新Token |
| 2003 | 拒绝访问 | 提示无权限 |
| 4004 | AI服务异常 | 提示用户稍后重试 |
| 1000 | 系统错误 | 通用错误提示 |

## 5. 开发调试注意

1. **本地环境**: 确保 Docker 容器 (Nacos, Redis, PosgreSQL) 已启动，且 Gateway (9000) 服务运行正常。
2. **SSE调试**: 推荐使用 Postman 调试 SSE 接口，观察 Event Stream 的输出。
3. **跨域**: 后端已配置 CORS，如遇跨域问题请检查 Gateway 配置。
