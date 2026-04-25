# Flutter iOS 客户端开发学习计划

## 📋 当前情况

### 个人背景
- **开发经验**：10 年 Java 开发经验
- **前端经验**：懂一些 Web 前端技术
- **iOS 开发经验**：零基础，没有做过 iOS 开发
- **学习目标**：开发 iOS 客户端，对接现有的 AI 面试官后端

### 项目背景
- **后端项目**：AI 面试官（基于 FastAPI + LangChain + DeepSeek）
- **后端 API 地址**：`http://localhost:8000`（开发环境）
- **后端主要接口**：
  - `POST /interview/upload-resume` - 上传简历文件
  - `POST /interview/start` - 开始面试
  - `POST /interview/{session_id}/opening-response` - 进入自我介绍环节
  - `POST /interview/{session_id}/self-introduction` - 提交自我介绍
  - `POST /interview/{session_id}/project-answer` - 回答项目问题
  - `POST /interview/{session_id}/technical-answer` - 回答技术问题
  - `POST /interview/{session_id}/conclude` - 面试总结
  - `GET /interview/{session_id}/info` - 获取会话信息

### 需要实现的功能
1. **用户注册登录** - 用户认证功能
2. **文字对话聊天** - 面试对话界面，支持问答交互
3. **上传 PDF 文件** - 简历上传功能
4. **面试历史记录** - 保存和查看历史面试记录

### 学习方式
- **边学边做**：通过实际开发项目来学习 Flutter
- **快速入门**：希望尽快上手，能够开发出可用的应用

---

## 🎯 学习目标

### 短期目标（1个月内）
- [ ] 掌握 Flutter 基础开发
- [ ] 完成 iOS 客户端核心功能开发
- [ ] 应用能在 iOS 真机上运行

### 长期目标（3个月内）
- [ ] 优化应用性能和用户体验
- [ ] 完善所有功能细节
- [ ] 准备发布到 App Store（可选）

---

## 📚 Flutter iOS 客户端学习实践计划

### 阶段一：环境搭建与基础（第1-2天）

#### 学习任务
- [ ] 安装 Flutter SDK 和 iOS 开发环境
  - 安装 Flutter SDK
  - 安装 Xcode（如果还没有）
  - 配置 iOS 模拟器
  - 运行 `flutter doctor` 检查环境
- [ ] 创建第一个 Flutter 项目并运行在 iOS 模拟器
  - 使用 `flutter create` 创建项目
  - 理解项目结构
  - 运行到 iOS 模拟器
- [ ] 学习 Dart 基础语法（变量、函数、类）
  - 变量声明（var, final, const）
  - 函数定义和调用
  - 类和对象（类似 Java）
  - 异步编程（async/await，类似 JavaScript）
- [ ] 理解 Widget 概念（StatelessWidget、StatefulWidget）
  - Widget 是什么
  - StatelessWidget vs StatefulWidget
  - Widget 树的概念

#### 实践任务
- [ ] 创建一个简单的 "Hello World" 应用
- [ ] 修改应用，显示你的名字和欢迎信息
- [ ] 添加一个按钮，点击后显示不同内容

**检查点**：应用能在 iOS 模拟器上运行，并能响应按钮点击

---

### 阶段二：UI 基础与布局（第3-4天）

#### 学习任务
- [ ] 学习常用 Widget（Text、Container、Column、Row、ListView）
  - Text：显示文本
  - Container：容器，设置样式
  - Column/Row：垂直/水平布局
  - ListView：列表展示
- [ ] 学习样式设置（颜色、字体、间距）
  - Color 和 Theme
  - TextStyle
  - Padding 和 Margin
- [ ] 学习页面导航（Navigator、路由）
  - Navigator.push/pop
  - 命名路由
  - 路由传参

#### 实践任务
- [ ] 创建登录页面 UI（输入框、按钮，暂不实现功能）
  - 用户名输入框
  - 密码输入框
  - 登录按钮
  - 注册链接
- [ ] 创建主页面 UI（底部导航栏或侧边栏）
  - 底部导航栏（首页、历史、设置）
  - 或侧边栏菜单
- [ ] 实现页面间的跳转（登录页 → 主页）

**检查点**：能创建多个页面，并能实现页面跳转

---

### 阶段三：网络请求与状态管理（第5-7天）

#### 学习任务
- [ ] 学习 `dio` 或 `http` 包进行网络请求
  - 安装 dio 包
  - 创建 HTTP 客户端
  - GET/POST 请求
  - 请求头和参数设置
- [ ] 学习 JSON 序列化（`json_serializable` 或手动解析）
  - 创建数据模型类
  - JSON 转对象
  - 对象转 JSON
- [ ] 学习状态管理（`Provider` 或 `Riverpod`）
  - Provider 基础概念
  - 创建 Provider
  - 在 Widget 中使用 Provider
  - 状态更新和 UI 刷新
- [ ] 学习异步编程（`async/await`、`Future`）
  - Future 和 async/await
  - 错误处理（try-catch）
  - 加载状态管理

#### 实践任务
- [ ] 创建 API 服务类，封装 HTTP 请求
  - 创建 `api_service.dart`
  - 封装基础请求方法
  - 设置 base URL 和超时
- [ ] 实现测试接口调用（如 `/health`）
  - 调用后端健康检查接口
  - 显示返回结果
- [ ] 创建全局状态管理（用户信息、API 配置）
  - 创建 UserProvider
  - 创建 ApiConfigProvider
- [ ] 实现错误处理和加载状态显示
  - Loading 状态
  - Error 状态
  - Success 状态

**检查点**：能成功调用后端 API，并能显示返回的数据

---

### 阶段四：功能一 - 用户注册登录（第8-10天）

#### 学习任务
- [ ] 学习本地存储（`shared_preferences` 或 `hive`）
  - 安装 shared_preferences 包
  - 保存和读取数据
  - 删除数据
- [ ] 学习表单验证
  - TextFormField
  - 验证规则
  - 错误提示
- [ ] 学习 Token 管理
  - 保存 Token
  - 读取 Token
  - Token 过期处理

#### 实践任务
- [ ] 实现注册页面（用户名、密码输入）
  - 注册表单 UI
  - 表单验证
  - 注册逻辑（如果后端有接口）
- [ ] 实现登录页面（用户名、密码输入）
  - 登录表单 UI
  - 表单验证
  - 登录逻辑
- [ ] 实现本地 Token 存储（登录后保存）
  - 登录成功后保存 Token
  - 保存用户信息
- [ ] 实现自动登录（启动时检查 Token）
  - 应用启动时检查 Token
  - 如果有 Token，自动跳转到主页
  - 如果没有 Token，跳转到登录页
- [ ] 实现登录状态管理（Provider/Riverpod）
  - 创建 AuthProvider
  - 管理登录状态
  - 登出功能

**注意**：如果后端暂无注册登录接口，可先实现：
- 本地模拟登录（用户名密码存本地）
- 或等待后端接口，先做 UI 和状态管理

**检查点**：能实现登录流程，并能保存和读取登录状态

---

### 阶段五：功能二 - 文字对话聊天（第11-15天）

#### 学习任务
- [ ] 学习聊天界面布局（消息气泡、输入框）
  - 消息气泡设计
  - 输入框和发送按钮
  - 键盘弹出处理
- [ ] 学习列表滚动和自动滚动到底部
  - ListView 或 CustomScrollView
  - 滚动到底部
  - 键盘弹出时自动滚动
- [ ] 学习消息模型设计
  - 消息数据模型
  - 消息类型（用户消息、AI 消息）
  - 时间戳显示

#### 实践任务
- [ ] 创建聊天页面 UI（消息列表、输入框、发送按钮）
  - 消息列表（ListView）
  - 消息气泡（区分用户和 AI）
  - 输入框和发送按钮
  - 时间显示
- [ ] 实现消息模型（问题、回答、时间戳）
  - 创建 Message 模型类
  - 消息类型枚举
- [ ] 对接后端面试流程 API：
  - [ ] `POST /interview/start` - 开始面试
    - 需要参数：resume_content, job_requirements, candidate_name
    - 返回：session_id, opening, stage
  - [ ] `POST /interview/{session_id}/opening-response` - 进入自我介绍
    - 返回：question, stage
  - [ ] `POST /interview/{session_id}/self-introduction` - 提交自我介绍
    - 需要参数：answer
    - 返回：question, stage, message
  - [ ] `POST /interview/{session_id}/project-answer` - 回答项目问题
    - 需要参数：answer
    - 返回：question, stage, score, feedback, message
  - [ ] `POST /interview/{session_id}/start-technical` - 开始技术面试
    - 需要参数：question_types, counts
    - 返回：question, stage, remaining_questions
  - [ ] `POST /interview/{session_id}/technical-answer` - 回答技术问题
    - 需要参数：answer
    - 返回：question, stage, score, feedback, message
  - [ ] `POST /interview/{session_id}/conclude` - 面试总结
    - 返回：final_score, feedback, summary
- [ ] 实现消息发送和接收显示
  - 发送用户消息
  - 接收 AI 消息
  - 显示评分和反馈（如果有）
- [ ] 实现面试流程状态管理（当前阶段、问题计数等）
  - 创建 InterviewProvider
  - 管理面试状态（stage）
  - 管理问题计数
  - 管理评分
- [ ] 显示评分和反馈（如果有）
  - 在消息中显示评分
  - 显示反馈内容
  - 显示面试进度

**检查点**：能完成一次完整的面试对话流程，包括开始面试、问答、结束面试

---

### 阶段六：功能三 - 上传 PDF 文件（第16-18天）

#### 学习任务
- [ ] 学习文件选择（`file_picker` 包）
  - 安装 file_picker 包
  - 选择文件
  - 获取文件路径和内容
- [ ] 学习文件上传（`dio` 的 multipart/form-data）
  - FormData 创建
  - 文件上传
  - 上传进度监听
- [ ] 学习文件权限处理（iOS 权限配置）
  - Info.plist 配置
  - 权限请求

#### 实践任务
- [ ] 实现文件选择功能（选择 PDF 文件）
  - 添加文件选择按钮
  - 选择 PDF 文件
  - 显示选中的文件名
- [ ] 对接后端 `POST /interview/upload-resume` 接口
  - 创建 FormData
  - 上传文件
  - 处理返回结果（resume_content）
- [ ] 实现上传进度显示
  - 显示上传进度条
  - 显示上传百分比
- [ ] 实现上传成功/失败提示
  - 成功提示
  - 失败提示和错误信息
- [ ] 将上传的简历内容保存到状态中，用于开始面试
  - 保存到 InterviewProvider
  - 在开始面试时使用

**检查点**：能选择 PDF 文件并成功上传到后端，获得简历内容

---

### 阶段七：功能四 - 面试历史记录（第19-22天）

#### 学习任务
- [ ] 学习本地数据库（`sqflite` 或 `hive`）
  - 安装数据库包
  - 创建数据库和表
  - 增删改查操作
- [ ] 学习列表展示和搜索
  - 历史记录列表
  - 搜索功能
  - 筛选功能
- [ ] 学习详情页面设计
  - 详情页布局
  - 数据展示

#### 实践任务
- [ ] 设计历史记录数据模型（session_id、时间、评分等）
  - 创建 InterviewHistory 模型
  - 字段：session_id, candidate_name, created_at, final_score, stage
- [ ] 实现历史记录本地存储（每次面试结束保存）
  - 面试结束时保存记录
  - 保存到本地数据库
- [ ] 创建历史记录列表页面
  - 显示历史记录列表
  - 显示基本信息（时间、评分）
  - 点击进入详情
- [ ] 对接后端 `GET /interview/{session_id}/info` 获取详情
  - 调用接口获取会话信息
  - 解析返回数据
- [ ] 实现历史记录详情页面（显示完整对话、评分、反馈）
  - 显示会话信息
  - 显示完整对话历史
  - 显示最终评分和反馈
- [ ] 实现历史记录搜索和筛选
  - 按时间筛选
  - 按评分筛选
  - 搜索功能（可选）

**检查点**：能保存面试记录，并能查看历史记录详情

---

### 阶段八：整合与优化（第23-25天）

#### 实践任务
- [ ] 整合所有功能，实现完整流程：
  - 登录 → 上传简历 → 开始面试 → 对话 → 查看历史
- [ ] 优化 UI/UX（加载状态、错误提示、空状态）
  - 加载状态显示
  - 错误提示优化
  - 空状态页面（无历史记录等）
- [ ] 实现面试流程引导（提示用户当前步骤）
  - 显示当前面试阶段
  - 提示下一步操作
  - 进度指示器
- [ ] 添加面试总结页面（显示最终评分和反馈）
  - 总结页面 UI
  - 显示最终评分
  - 显示详细反馈
  - 返回主页或查看历史
- [ ] 测试所有功能，修复 bug
  - 功能测试
  - 边界情况测试
  - Bug 修复

**检查点**：所有功能能正常工作，用户体验良好

---

### 阶段九：iOS 打包与发布（第26-28天）

#### 学习任务
- [ ] 学习 iOS 应用配置（Info.plist、权限配置）
  - Info.plist 配置
  - 权限配置
  - 应用图标和启动页
- [ ] 学习应用图标和启动页设置
  - 准备图标资源
  - 设置启动页
- [ ] 学习 Flutter 打包 iOS 应用
  - Debug 打包
  - Release 打包
  - 真机测试

#### 实践任务
- [ ] 配置应用信息（名称、图标、版本号）
  - 修改应用名称
  - 设置应用图标
  - 设置版本号
- [ ] 配置网络权限和文件访问权限
  - Info.plist 配置网络权限
  - 配置文件访问权限
- [ ] 打包 iOS 应用（Debug 和 Release）
  - Debug 打包测试
  - Release 打包
- [ ] 在真机上测试
  - 连接真机
  - 安装应用
  - 测试所有功能
- [ ] 准备发布到 App Store（可选）
  - 准备应用截图
  - 准备应用描述
  - 了解发布流程

**检查点**：应用能在 iOS 真机上正常运行

---

## 📚 学习资源优先级

### 必看资源
1. [Flutter 官方文档 - 基础](https://flutter.dev/docs/get-started/codelab)
2. [Flutter 中文网](https://flutter.cn/) - 中文教程
3. [Dart 语言教程](https://dart.cn/guides) - 快速了解语法

### 按需查阅
- `dio` 包文档 - 网络请求
- `provider` 或 `riverpod` 文档 - 状态管理
- `file_picker` 包文档 - 文件选择
- `sqflite` 或 `hive` 文档 - 本地存储

---

## ⏰ 每日学习建议

### 时间分配（每天 2-3 小时）
- **30 分钟**：看文档/教程
- **90-120 分钟**：动手实践
- **30 分钟**：调试和解决问题

### 学习方法
1. **先看后做**：先看官方文档的基础部分，再动手实践
2. **遇到问题先查文档**：遇到问题先查官方文档，再搜索解决方案
3. **及时测试**：每完成一个功能就测试，不要堆积问题
4. **版本控制**：使用 Git 保存代码，方便回退和对比

---

## 🎯 关键里程碑检查点

- [ ] **第 7 天**：能成功调用后端 API 并显示数据
- [ ] **第 15 天**：能完成一次完整的面试对话流程
- [ ] **第 22 天**：所有核心功能都能正常工作
- [ ] **第 28 天**：应用能在 iOS 真机上运行

---

## ⚠️ 注意事项

1. **后端接口确认**：确认后端 API 地址和接口格式，确保能正常访问
2. **用户认证**：如果后端暂无注册登录接口，可以先做 UI 和本地模拟登录
3. **错误处理**：每个网络请求都要处理错误情况，给用户友好的提示
4. **测试**：每完成一个功能就在模拟器和真机上测试，确保兼容性
5. **性能优化**：注意内存管理和性能优化，避免应用卡顿

---

## 📝 学习进度记录

### 已完成
- [ ] 阶段一：环境搭建与基础
- [ ] 阶段二：UI 基础与布局
- [ ] 阶段三：网络请求与状态管理
- [ ] 阶段四：用户注册登录
- [ ] 阶段五：文字对话聊天
- [ ] 阶段六：上传 PDF 文件
- [ ] 阶段七：面试历史记录
- [ ] 阶段八：整合与优化
- [ ] 阶段九：iOS 打包与发布

### 遇到的问题
（记录学习过程中遇到的问题和解决方案）

### 学习心得
（记录学习过程中的心得体会）

---

## 🔄 后续扩展计划

### 功能扩展
- [ ] 语音输入功能
- [ ] 面试结果导出（PDF/Excel）
- [ ] 面试数据统计分析
- [ ] 多语言支持
- [ ] 深色模式

### 技术优化
- [ ] 性能优化（列表虚拟化、图片缓存等）
- [ ] 代码重构和架构优化
- [ ] 单元测试和集成测试
- [ ] CI/CD 配置

### 发布准备
- [ ] App Store 发布
- [ ] 用户反馈收集
- [ ] 版本迭代计划

---

**开始学习吧！记住：最好的学习方式是在实际项目中应用这些知识。** 🚀

