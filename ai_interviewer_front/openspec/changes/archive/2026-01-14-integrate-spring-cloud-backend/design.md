# 设计：Spring Cloud 后端集成方案

## 架构概览

前端将与多个微服务交互。为了有效管理，我们将采用集中的 API 客户端结构。

### API 层 (`lib/api/`)
- **`api_client.dart`**: 基础 Dio 配置，包含拦截器：
    - 自动添加 `Authorization` 请求头。
    - 处理 401 未授权（Token 自动刷新）。
    - 日志记录及通用错误处理。

### 待集成 API 列表

#### 用户服务 (端口 9001)
| 功能 | 方法 | 路径 | 描述 |
| :--- | :--- | :--- | :--- |
| 登录 | `POST` | `/auth/login` | 用户身份验证 |
| 注册 | `POST` | `/auth/register` | 用户注册 |
| 登出 | `POST` | `/auth/logout` | Token 作废 |
| 刷新 Token | `POST` | `/auth/refresh` | 刷新访问令牌 |
| 个人资料 | `GET` | `/users/me` | 获取当前用户信息 |
| 更新资料 | `PUT` | `/users/me` | 更新个人信息 |
| 用户详情 | `GET` | `/users/{id}` | 获取特定用户信息 |

#### 职位服务 (端口 9004)
| 功能 | 方法 | 路径 | 描述 |
| :--- | :--- | :--- | :--- |
| 职位列表 | `GET` | `/api/v1/jobs` | 获取所有职位发布 |
| 搜索职位 | `GET` | `/api/v1/jobs/search` | 按关键词搜索 |
| 创建职位 | `POST` | `/api/v1/jobs` | 发布新职位 |
| 职位详情 | `GET` | `/api/v1/jobs/{id}` | 获取职位详情 |
| 更新职位 | `PUT` | `/api/v1/jobs/{id}` | 更新职位信息 |
| 删除职位 | `DELETE` | `/api/v1/jobs/{id}` | 删除职位发布 |
| 关闭职位 | `PUT` | `/api/v1/jobs/{id}/close`| 关闭招聘状态 |
| 匹配分析 | `POST` | `/api/v1/jobs/{id}/match` | 简历-职位匹配分析 |
| 我的职位 | `GET` | `/api/v1/jobs/my` | 用户发布的职位列表 |

### 服务层 (`lib/services/`)
服务层封装 API 调用，处理数据转换和业务逻辑。
- **`auth_service.dart`**: 登录、注册、Token 持久化管理。
- **`job_service.dart`**: 职位相关操作及匹配分析业务逻辑。

### 数据模型 (`lib/models/`)
使用 `fromJson` 和 `toJson` 的 POJO 类。
- `User`: id, username, email 等。
- `Job`: id, title, company, salary 等。
- `MatchResult`: matchScore, matchLevel 等。

## 认证流程
1. 用户登录 -> 后端返回 `accessToken` 和 `refreshToken`。
2. 将 Token 存储至 `SharedPreferences`。
3. `ApiClient` 拦截器自动为后续请求添加 `accessToken`。
4. 若请求返回 401，拦截器自动调用 `/auth/refresh` 刷新 Token 并重试原始请求。

## 多服务访问策略
由于服务位于不同端口，我们将配置 `ApiClient` 支持动态 Base URL 或为每个服务实例化不同的 Dio 客户端。

## 错误处理
- 使用统一的响应模型处理成功和失败。
- 在 UI 层通过 SnackBar 或对话框展示网络/业务错误。
