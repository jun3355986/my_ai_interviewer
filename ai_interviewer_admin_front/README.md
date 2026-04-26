# AI Interviewer Admin Web

独立 React 后台管理前端，面向 `ai_interviewer_admin` 后端 API。

## 功能范围

- 管理员登录
- 总览仪表盘
- 用户列表与停用/重置密码入口
- 职位列表与新建职位
- 面试列表
- 题库列表、新建题目、触发向量同步
- 操作审计日志

## 本地开发

```bash
npm install
npm run dev
```

开发服务默认监听：

```text
http://localhost:8090
```

Vite 会把 `/admin/**` 代理到：

```text
http://localhost:9000/admin/**
```

## Docker 运行

当前项目已接入根仓库的 Docker Compose：

```bash
cd ../ai_interview_backend
docker compose up -d --build admin-web
```

页面入口：

```text
http://localhost:8090
```

默认账号：

```text
admin / admin123
```
