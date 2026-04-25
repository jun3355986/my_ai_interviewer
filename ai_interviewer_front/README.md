# ai_interviewer_front

Flutter 前端项目（Web / iOS）。

## 本地开发

```bash
flutter pub get
flutter run -d chrome
```

默认 API 入口通过 Gateway：`http://localhost:9000/api/v1/*`。

## Docker 运行（Web）

该项目在容器内通过 Nginx 提供静态页面，并将 `/api/*` 反向代理到 `gateway:9000`。

建议从后端 compose 目录统一启动：

```bash
cd ../ai_interview_backend
docker compose up -d --build frontend gateway
```

访问地址：`http://localhost:8088`

可通过后端 `.env` 里的 `FRONTEND_GATEWAY_BASE_URL` 调整 Flutter 构建时注入的网关地址（默认 `/`）。

## 登录排障（Web）

如果页面点击“登录”后始终失败，但后端 `POST /api/v1/auth/login` 已经成功，通常是浏览器缓存了旧的 Flutter Web 构建产物（Service Worker/Cache Storage）。

当前容器构建已做两件事避免该问题：

- 本地 `localhost` 环境启动时自动注销旧 Service Worker 并清理 Cache Storage。
- Docker Web 构建禁用 PWA Service Worker 注册，避免联调阶段继续缓存旧包。

另外，如果浏览器 Network 中请求地址是 `http://api/v1/auth/login`（而不是 `http://localhost:8088/api/v1/auth/login`），说明前端网关地址被错误拼接为协议相对地址。当前版本已在 `ApiClient` 内做了 baseUrl 归一化修复。

如果仍遇到异常，先执行一次硬刷新（`Cmd+Shift+R`）再重试。
