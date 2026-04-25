# Flutter 和 iOS 开发环境安装指南（macOS）

本指南将帮助你从零开始安装和配置 Flutter 和 iOS 开发环境。

## 📋 目录

- [前置要求](#前置要求)
- [第一步：安装 Xcode](#第一步安装-xcodeios-开发必需)
- [第二步：安装 Flutter](#第二步安装-flutter)
- [第三步：验证 Flutter 安装](#第三步验证-flutter-安装)
- [第四步：安装 IDE](#第四步安装-ide选择其中一个)
- [第五步：创建第一个 Flutter 项目](#第五步创建第一个-flutter-项目)
- [第六步：配置 iOS 开发证书](#第六步配置-ios-开发证书用于真机测试)
- [常见问题排查](#常见问题排查)
- [验证安装完成](#验证安装完成)

---

## 前置要求

- ✅ macOS 操作系统（必须）
- ✅ 至少 2.8 GB 可用磁盘空间
- ✅ 网络连接（用于下载）

---

## 第一步：安装 Xcode（iOS 开发必需）

### 1. 从 App Store 安装 Xcode

- 打开 **App Store**
- 搜索 "**Xcode**"
- 点击"获取"或"安装"（约 12-15 GB，下载时间较长）
- 等待下载和安装完成

### 2. 安装 Xcode 命令行工具

```bash
# 打开终端，运行以下命令
xcode-select --install
```

- 会弹出对话框，点击"安装"
- 等待安装完成（约 5-10 分钟）

### 3. 接受 Xcode 许可协议

```bash
# 打开 Xcode 一次，接受许可协议
# 或者直接在终端运行：
sudo xcodebuild -license accept
```

### 4. 安装 CocoaPods（iOS 依赖管理工具）

#### 方法 1：使用系统 Ruby（简单，但可能遇到权限问题）

```bash
# 使用系统自带的 Ruby 安装 CocoaPods
sudo gem install cocoapods
```

#### 方法 2：使用 Homebrew 安装 Ruby（推荐，避免权限问题）

**步骤 1：安装 Ruby**

```bash
brew install ruby
```

**步骤 2：配置 PATH 和环境变量**

安装完成后，Homebrew 会提示你配置环境变量。按照提示执行以下命令：

```bash
# 将 Homebrew 的 Ruby 添加到 PATH（优先使用）
echo 'export PATH="/opt/homebrew/opt/ruby/bin:$PATH"' >> ~/.zshrc

# 配置编译器和 pkg-config 环境变量（可选，但推荐）
echo 'export LDFLAGS="-L/opt/homebrew/opt/ruby/lib"' >> ~/.zshrc
echo 'export CPPFLAGS="-I/opt/homebrew/opt/ruby/include"' >> ~/.zshrc
echo 'export PKG_CONFIG_PATH="/opt/homebrew/opt/ruby/lib/pkgconfig"' >> ~/.zshrc

# 重新加载配置
source ~/.zshrc
```

**步骤 3：验证 Ruby 安装**

```bash
# 检查 Ruby 版本和路径
which ruby
ruby --version

# 应该显示 Homebrew 安装的 Ruby 路径：
# /opt/homebrew/opt/ruby/bin/ruby
```

**步骤 4：安装 CocoaPods**

```bash
# 现在可以使用 gem 安装 CocoaPods（不需要 sudo）
gem install cocoapods

# 或者使用 Homebrew 直接安装 CocoaPods（更简单）
brew install cocoapods
```

**步骤 5：验证 CocoaPods 安装**

```bash
pod --version
# 应该显示版本号，例如：1.15.2
```

#### 方法 3：直接使用 Homebrew 安装 CocoaPods（最简单）

```bash
# 直接使用 Homebrew 安装 CocoaPods（会自动处理依赖）
brew install cocoapods
```

### 5. 验证 Xcode 安装

```bash
# 检查 Xcode 版本
xcodebuild -version

# 应该显示类似：
# Xcode 15.0
# Build version 15A240d
```

---

## 第二步：安装 Flutter

### 方法 1：使用 Flutter 官方安装包（推荐）

#### 1. 下载 Flutter SDK

```bash
# 进入你想要安装的目录（例如用户主目录）
cd ~

# 下载 Flutter SDK（使用 Git）
git clone https://github.com/flutter/flutter.git -b stable

# 或者直接下载 ZIP 文件：
# 访问 https://flutter.dev/docs/get-started/install/macos
# 下载最新的 stable 版本 ZIP 文件
# 解压到 ~/flutter 目录
```

#### 2. 将 Flutter 添加到 PATH

```bash
# 编辑 shell 配置文件（如果使用 zsh，编辑 ~/.zshrc；如果使用 bash，编辑 ~/.bash_profile）
# 对于 zsh（macOS 默认）：
nano ~/.zshrc

# 在文件末尾添加以下行：
export PATH="$PATH:$HOME/flutter/bin"

# 保存文件（Ctrl+O，然后 Enter，然后 Ctrl+X）

# 重新加载配置
source ~/.zshrc
```

### 方法 2：使用 Homebrew 安装（更简单）

```bash
# 安装 Homebrew（如果还没有安装）
# 访问 https://brew.sh/ 或运行：
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 使用 Homebrew 安装 Flutter
brew install --cask flutter
```

---

## 第三步：验证 Flutter 安装

### 1. 运行 Flutter 医生检查

```bash
flutter doctor
```

### 2. 查看检查结果

运行 `flutter doctor` 后，你会看到类似以下的输出：

**示例输出（可能包含一些问题）：**

```
Doctor summary (to see all details, run flutter doctor -v):
[✓] Flutter (Channel stable, 3.38.3, on macOS 26.1 25B78 darwin-arm64, locale zh-Hans-CN)
[✗] Android toolchain - develop for Android devices
    ✗ Unable to locate Android SDK.
      Install Android Studio from: https://developer.android.com/studio/index.html
      On first launch it will assist you in installing the Android SDK components.
      (or visit https://flutter.dev/to/macos-android-setup for detailed instructions).
      If the Android SDK has been installed to a custom location, please use
      `flutter config --android-sdk` to update to that location.
[!] Xcode - develop for iOS and macOS (Xcode 26.1.1)
    ✗ Unable to get list of installed Simulator runtimes.
[✓] Chrome - develop for the web
[✓] Connected device (2 available)
[✓] Network resources

! Doctor found issues in 2 categories.
```

**说明：**
- `[✓]` 表示检查通过
- `[✗]` 表示有问题需要修复
- `[!]` 表示部分功能可用，但有警告
- 如果你只开发 iOS 应用，Android toolchain 的问题可以暂时忽略

### 常见问题修复

#### 问题 1：Android SDK 未找到（如果只开发 iOS，可以忽略）

```
[✗] Android toolchain - develop for Android devices
    ✗ Unable to locate Android SDK.
```

**解决方案：**

如果你**只开发 iOS 应用**，可以忽略这个问题。

如果你**需要开发 Android 应用**，请按以下步骤操作：

1. 安装 Android Studio：
   - 访问 https://developer.android.com/studio
   - 下载并安装 Android Studio
   - 首次启动时，Android Studio 会自动安装 Android SDK

2. 配置 Android SDK 路径（如果已安装但 Flutter 找不到）：
   ```bash
   # 如果 Android SDK 安装在自定义位置，使用以下命令配置
   flutter config --android-sdk <你的SDK路径>
   ```

#### 问题 2：Xcode 无法获取 Simulator runtimes 列表

```
[!] Xcode - develop for iOS and macOS (Xcode 26.1.1)
    ✗ Unable to get list of installed Simulator runtimes.
```

**解决方案：**

1. **打开 Xcode 并安装模拟器运行时：**
   - 打开 Xcode
   - 进入 `Preferences` > `Platforms`（或 `Components`）
   - 下载并安装所需的 iOS 模拟器运行时版本

2. **或者使用命令行安装：**
   ```bash
   # 列出可用的模拟器运行时
   xcrun simctl runtime list
   
   # 如果需要，可以通过 Xcode 的 Components 页面下载
   ```

3. **验证 Xcode 配置：**
   ```bash
   # 确保 Xcode 命令行工具指向正确位置
   sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer
   
   # 接受 Xcode 许可协议
   sudo xcodebuild -license accept
   ```

4. **重新运行检查：**
   ```bash
   flutter doctor
   ```

#### 问题 3：Xcode 未安装或未配置

```
[!] Xcode - develop for iOS and macOS
    ✗ Xcode not installed; this is necessary for iOS development.
```

**解决方案：**
- 确保已从 App Store 安装 Xcode
- 运行 `sudo xcodebuild -license accept`
- 运行 `sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer`

#### 问题 4：CocoaPods 未安装

```
[!] CocoaPods not installed.
```

**解决方案：**
```bash
# 推荐使用 Homebrew 安装（最简单）
brew install cocoapods

# 或者使用 gem 安装
gem install cocoapods
```

#### 问题 5：Android 许可协议未接受（如果不需要 Android 开发，可以忽略）

```
[!] Android toolchain - develop for Android devices
    ✗ Android licenses not accepted.
```

**解决方案：**
```bash
flutter doctor --android-licenses
# 然后输入 'y' 接受所有许可
```

---

## 第四步：安装 IDE（选择其中一个）

### 选项 1：VS Code（推荐，轻量级）

#### 1. 安装 VS Code

- 访问 https://code.visualstudio.com/
- 下载并安装 macOS 版本

#### 2. 安装 Flutter 扩展

- 打开 VS Code
- 按 `Cmd+Shift+X` 打开扩展市场
- 搜索 "**Flutter**"
- 点击"安装"（会自动安装 Dart 扩展）

#### 3. 验证安装

- 按 `Cmd+Shift+P` 打开命令面板
- 输入 "Flutter: New Project"
- 如果能看到 Flutter 命令，说明安装成功

### 选项 2：Android Studio（功能更全面）

#### 1. 安装 Android Studio

- 访问 https://developer.android.com/studio
- 下载并安装 macOS 版本

#### 2. 安装 Flutter 和 Dart 插件

- 打开 Android Studio
- 进入 `Preferences` > `Plugins`
- 搜索 "**Flutter**" 并安装
- 会自动安装 Dart 插件

#### 3. 配置 Flutter SDK 路径

- 进入 `Preferences` > `Languages & Frameworks` > `Flutter`
- 设置 Flutter SDK 路径（通常是 `~/flutter`）

---

## 第五步：创建第一个 Flutter 项目

### 1. 创建项目

```bash
# 使用命令行创建项目
flutter create my_first_app

# 进入项目目录
cd my_first_app
```

### 2. 运行项目（iOS 模拟器）

#### 方法 A：使用命令行

```bash
# 列出可用的设备
flutter devices

# 启动 iOS 模拟器（如果没有运行）
open -a Simulator

# 运行 Flutter 应用
flutter run
```

#### 方法 B：使用 VS Code

- 打开项目文件夹
- 按 `F5` 或点击右上角的"运行"按钮
- 选择 iOS 模拟器

#### 方法 C：使用 Android Studio

- 打开项目
- 点击右上角的设备选择器
- 选择 iOS 模拟器
- 点击运行按钮

---

## 第六步：配置 iOS 开发证书（用于真机测试）

### 1. 注册 Apple Developer 账号（可选，真机测试需要）

- 访问 https://developer.apple.com/
- 注册账号（免费账号也可以进行真机测试，但功能有限）

### 2. 在 Xcode 中配置账号

- 打开 Xcode
- 进入 `Preferences` > `Accounts`
- 点击 "+" 添加 Apple ID
- 登录你的 Apple ID

### 3. 配置项目签名

- 在 Xcode 中打开项目：`open ios/Runner.xcworkspace`
- 选择 `Runner` 项目
- 进入 `Signing & Capabilities`
- 选择你的 Team（你的 Apple ID）
- Xcode 会自动生成开发证书

---

## 常见问题排查

### 问题 1：Flutter 命令未找到

```bash
# 检查 PATH 配置
echo $PATH

# 应该包含 Flutter 的 bin 目录
# 如果没有，重新配置 PATH（见第二步）
```

### 问题 2：iOS 模拟器无法启动

```bash
# 检查模拟器是否安装
xcrun simctl list devices

# 如果列表为空，需要安装模拟器
# 打开 Xcode > Preferences > Components
# 下载 iOS 模拟器
```

### 问题 3：CocoaPods 安装失败

**情况 A：权限问题（Permission denied）**

如果使用 `sudo gem install cocoapods` 遇到权限问题，使用 Homebrew 安装 Ruby：

```bash
# 1. 安装 Ruby
brew install ruby

# 2. 配置 PATH（重要！）
echo 'export PATH="/opt/homebrew/opt/ruby/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# 3. 验证 Ruby 路径
which ruby
# 应该显示：/opt/homebrew/opt/ruby/bin/ruby

# 4. 安装 CocoaPods（不需要 sudo）
gem install cocoapods
```

**情况 B：Ruby 版本不匹配**

如果系统 Ruby 和 Homebrew Ruby 冲突：

```bash
# 检查当前使用的 Ruby
which ruby
ruby --version

# 如果显示系统 Ruby（/usr/bin/ruby），需要配置 PATH
echo 'export PATH="/opt/homebrew/opt/ruby/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

**情况 C：最简单的方法（推荐）**

直接使用 Homebrew 安装 CocoaPods，它会自动处理所有依赖：

```bash
# 直接安装 CocoaPods（推荐方法）
brew install cocoapods

# 验证安装
pod --version
```

**情况 D：gem 安装路径问题**

如果 gem 安装的二进制文件找不到：

```bash
# 检查 gem 的 bin 目录
gem environment

# 将 gem bin 目录添加到 PATH
echo 'export PATH="/opt/homebrew/lib/ruby/gems/3.4.0/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

### 问题 4：网络问题（在中国大陆）

```bash
# 配置 Flutter 使用国内镜像
export PUB_HOSTED_URL=https://pub.flutter-io.cn
export FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn

# 将这两行添加到 ~/.zshrc 文件中，永久生效
```

---

## 验证安装完成

运行以下命令，确保所有检查都通过：

```bash
flutter doctor -v
```

**理想状态应该是：**
- ✅ Flutter 已安装
- ✅ Xcode 已安装并配置
- ✅ CocoaPods 已安装
- ✅ iOS 工具链正常
- ✅ IDE 已配置（VS Code 或 Android Studio）

---

## 下一步

安装完成后，你可以：
1. 阅读 [Flutter 官方文档](https://flutter.dev/docs)
2. 完成 [Flutter Codelabs](https://flutter.dev/docs/codelabs)
3. 开始开发你的 AI 面试官 iOS 应用！

---

## 相关文档

- [iOS 前端开发方案推荐指南](./IOS_DEVELOPMENT_GUIDE.md)
- [Flutter 学习计划](./FLUTTER_LEARNING_PLAN.md)

