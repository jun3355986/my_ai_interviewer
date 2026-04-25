## 这个是初版的【ai面试助手】UI 设计的prompt，提供给figma生成 UI 用的

### 提示词如下：

我想创建一个ios 面试助手应用。AI 面试官助手 - iOS 客户端功能描述：应用概述： 一款 AI 驱动的模拟面试应用，帮助求职者通过与 AI 面试官对话练习面试技巧。用户可以上传简历，进行模拟面试对话，获得实时评分和反馈，并查看历史面试记录。使用flutter框架来构建这个应用。
实现以下页面：

页面1：登录/注册页面，功能描述： 
> Design a clean and modern iOS login page for an AI interview assistant app. Include: > - App logo and name "AI 面试官助手" at the top > - Username input field with icon > - Password input field with eye toggle for visibility > - "登录" (Login) primary button with gradient or solid color > - "注册新账号" (Register) text link below the button > - "忘记密码?" (Forgot password) link > - Social login options (optional: Apple, WeChat) > - Soft pastel or professional blue color scheme > - iOS style with rounded corners and subtle shadows


🏠 页面2：主页/首页
功能描述：
> Design an iOS home page for an AI interview assistant app. Include:
> - Top navigation bar with user avatar and notification bell icon
> - Welcome greeting "你好，[用户名]" with motivational text
> - Large prominent card for "开始新面试" (Start New Interview) with illustration
> - Quick stats section showing: 总面试次数 (Total interviews), 平均得分 (Average score), 最近面试日期
> - Recent activity section with 2-3 recent interview cards showing date, score, and status
> - Bottom tab bar with 4 icons: 首页 (Home), 面试 (Interview), 历史 (History), 设置 (Settings)
> - Modern minimalist design with card-based layout and subtle gradients


📄 页面3：简历上传页面
功能描述：
> Design an iOS resume upload page for an AI interview assistant app. Include:
> - Back navigation arrow and page title "上传简历"
> - Large dashed border upload area with upload icon and text "点击上传 PDF 简历" or "拖拽文件到此处"
> - Supported file format hint "支持 PDF 格式，最大 10MB"
> - After file selected: show file name, file size, and a remove/replace button
> - Upload progress bar (for uploading state)
> - Success state with green checkmark and "简历上传成功" message
> - "继续" (Continue) button at the bottom to proceed
> - Clean white background with accent color highlights


💬 页面4：面试对话页面（核心页面）
功能描述：
> Design an iOS chat interface for an AI interview assistant app. Include:
> - Top navigation bar with back button, interview title "模拟面试", and stage indicator (自我介绍/项目经验/技术问答)
> - Progress bar or step indicator showing current interview stage
> - Chat message list with:
> - AI interviewer messages on left side with avatar (robot/professional avatar), gray/white bubble
> - User messages on right side with blue/gradient bubble
> - Timestamp below each message
> - Optional: Score badge (如: ⭐ 8.5分) and feedback for answered questions
> - Typing indicator when AI is thinking "面试官正在思考..."
> - Bottom input area with:
> - Text input field with placeholder "输入你的回答..."
> - Send button (arrow icon)
> - Microphone button (optional for voice input)
> - Modern chat UI similar to iMessage style with smooth rounded bubbles

📊 页面5：面试进行中状态指示
功能描述：
> Design interview stage indicator components for iOS. Include:
> - Horizontal stepper/progress showing stages: 开场 → 自我介绍 → 项目经验 → 技术问答 → 总结
> - Current stage highlighted with color, completed stages with checkmark
> - Interview timer showing elapsed time "已进行 12:35"
> - Question counter "问题 3/5"
> - Compact card showing current stage name and brief description
> - Clean minimal design that doesn't distract from conversation
🏆 页面6：面试总结/结果页面
功能描述：
> Design an iOS interview results/summary page for an AI interview assistant. Include:
> - Celebration illustration or icon at top (trophy, success graphic)
> - Large final score display with circular progress indicator "总评分 85分"
> - Score breakdown cards for each stage:
> - 自我介绍: 8.5/10
> - 项目经验: 8.0/10
> - 技术问答: 9.0/10
> - Detailed feedback section with expandable text areas
> - Strengths (优点) and Areas for improvement (待改进) sections with bullet points
> - Action buttons at bottom:
> - "查看详细对话" (View full conversation)
> - "保存结果" (Save results)
> - "返回首页" (Back to home)
> - Confetti or subtle celebration animation elements

📚 页面7：面试历史列表页面
功能描述：
> Design an iOS interview history list page. Include:
> - Top navigation bar with title "面试历史" and search icon
> - Filter/sort options: 按时间 (By date), 按评分 (By score)
> - Search bar with placeholder "搜索面试记录"
> - List of interview history cards, each card showing:
> - Date and time of interview
> - Interview type or job position
> - Final score with color indicator (green for good, yellow for average, red for needs improvement)
> - Brief status or result summary
> - Right arrow for navigation
> - Empty state illustration with text "暂无面试记录，开始你的第一次面试吧！" when no history
> - Floating action button to start new interview
> - Pull-to-refresh functionality indicator

📝 页面8：面试历史详情页面
功能描述：
> Design an iOS interview history detail page. Include:
> - Top navigation bar with back button and title showing interview date
> - Summary section at