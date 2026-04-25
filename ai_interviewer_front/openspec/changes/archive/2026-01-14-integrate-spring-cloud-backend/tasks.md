# 任务：集成 Spring Cloud 后端

- [ ] **基础设施搭建**
    - [ ] 在 `pubspec.yaml` 中添加依赖：`dio`, `shared_preferences`, `json_annotation`, `json_serializable`。
    - [ ] 运行 `flutter pub get`。
    - [ ] 创建目录结构：`lib/api/`, `lib/models/`, `lib/services/`, `lib/utils/`。

- [ ] **数据模型实现**
    - [ ] 实现 `User` 和 `LoginResponse` 模型（包含 JSON 序列化）。
    - [ ] 实现 `Job` 以及 `JobMatchRequest / Response` 模型。

- [ ] **API 和服务层实现（全量覆盖）**
    - [ ] 实现针对 JWT 的 `DioClient` 及拦截器。
    - [ ] 实现 `AuthApi`: 包含 `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/logout` 接口。
    - [ ] 实现 `UserApi`: 包含 `/users/me` (GET/PUT), `/users/{id}` (GET) 接口。
    - [ ] 实现 `JobApi`: 包含 `/api/v1/jobs` (GET/POST), `/api/v1/jobs/{id}` (GET/PUT/DELETE), `/api/v1/jobs/{id}/close` (PUT), `/api/v1/jobs/{id}/match` (POST), `/api/v1/jobs/search` (GET), `/api/v1/jobs/my` (GET)。
    - [ ] 创建对应的 Service 类，封装上述**所有**接口的业务逻辑方法。

- [ ] **功能集成：身份验证**
    - [ ] 更新 `LoginPage`，调用 `AuthService.login()` 替换模拟逻辑。
    - [ ] 实现注册逻辑，对接后端注册接口。
    - [ ] 实现登录状态持久化，app 启动时若已登录则自动跳转至首页。

- [ ] **功能集成：职位列表**
    - [ ] 更新 `HomePage`，通 `JobService.getJobs()` 从后端动态获取职位。
    - [ ] 实现首页的职位搜索功能。

- [ ] **功能集成：匹配分析**
    - [ ] 更新 `UploadResumePage`，在简历上传成功后调用 `JobService.matchJob()` 进行匹配分析。
    - [ ] 在 `InterviewResultPage` 中动态展示 `matchScore` 和匹配详情。

- [ ] **校验与优化**
    - [ ] 为所有 API 调用添加 Loading 加载状态。
    - [ ] 完善错误处理机制，展示具体的业务错误提示。
    - [ ] 验证 Token 过期后的自动刷新逻辑。
