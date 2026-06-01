# CLAUDE.md

给我讲中文。

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

ST-android 是一个第三方 SillyTavern Android 客户端。它在设备上通过 foreground service 运行嵌入式 Node.js + SillyTavern 服务端，并用 Jetpack Compose 构建原生 UI 外壳。聊天界面已基本完成从 WebView 到原生 Compose 的迁移：采用 JS Bridge 架构，隐藏 WebView 仅作运行时容器复用 SillyTavern 的聊天语义（提示词组装、世界书、流式生成等），用户可见的聊天 UI 全部由原生 Compose 渲染。P0/P1/P2 + P3 阶段 A/B/C 已落地（详见 `docs/chat-interface-migration.md`），剩余 logprobs（上游阻塞）、TTS/翻译/生图（后续专项）等少量高级能力。

## Build & Test

```bash
# 构建 debug APK（需要 Android SDK + JDK 17+）
./gradlew assembleDebug
# 输出: app/build/outputs/apk/debug/app-debug.apk

# 运行全部单元测试
./gradlew test

# 运行单个测试类
./gradlew testDebugUnitTest --tests "io.github.sanitised.st.api.TavernCoreClientTest"

# Docker 构建（仅 Linux，从源码编译 Node.js，首次约 2-3 小时）
git submodule update --init --recursive
./ci/scripts/build_apk_docker.sh
```

仅有本地 JVM 单元测试（`app/src/test/`），无 instrumented test。无自定义 lint 配置。

`gradle.properties` 中的 `org.gradle.java.home` 是开发机专用路径，不要在提交中修改。

## Architecture

**单模块 Gradle 项目**（`app/`）。包名：`io.github.sanitised.st`。

### 核心运行时

- `NodeService` — foreground service，启动 Node.js 进程运行 SillyTavern，通过 `NodeStatusListener` 回调通信。
- `NodePayload` — 首次运行时从 APK assets 解压 Node 二进制和 SillyTavern 源码。管理自定义 ST 安装（GitHub 仓库/分支/ZIP）。
- `MainViewModel` — 中心 ViewModel，委托给 `BackupManager`、`CustomInstallManager`、`UpdateManager`、`BatteryPromptManager`。UI 状态以 `MutableState` 字段暴露。
- `AppPaths` — 所有文件路径的单一来源（`stDir`、`dataDir`、`configDir` 等）。

### 导航与 UI

`MainActivity` 托管 `NavHost`，底部导航定义在 `ui/navigation/STNavGraph.kt`（`STRoutes`）。主要标签页：

- **Home** (`chats/home`) — 仪表盘，状态卡片 + 最近聊天/角色 + 快捷操作。位于 `ui/prototype/PrototypeHomeScreen.kt`。
- **Chat** (`chat`) — 双层架构：
  - `ui/webview/ChatWebViewScreen` — WebView 加载 SillyTavern 前端，同时作为聊天运行时容器。
  - `chat/NativeChatScreen` — 原生 Compose 聊天界面，通过 JS Bridge 与 WebView 运行时通信。
- **Characters** (`characters`) — 原生 Compose 角色管理：列表、详情、编辑。位于 `ui/prototype/PrototypeCharacterScreens.kt`。
- **Tools** (`tools`) — 工具页面（世界书、预设、API 连接等）。位于 `ui/prototype/` 下。
- **Settings** (`me`) — 设置页面。位于 `ui/prototype/PrototypeSystemScreens.kt`。

### Chat 迁移架构（当前进行中）

聊天界面正在从纯 WebView 迁移到原生 Compose。核心设计：

- **运行时复用**：SillyTavern Web 前端仍在隐藏 WebView 中运行，负责聊天生成语义（提示词组装、世界书、流式生成、扩展注入等）。原生端不直接调用 `/api/backends/*/generate`。
- **JS Bridge**：`STAndroidBridge`（注入为 `window.STAndroid`）+ `chat_runtime_adapter.js`（assets 中）构成双向通信通道。
- **chat 包结构**：
  - `ChatRuntimeBridge` — 管理 WebView 运行时连接，发送命令（生成/停止/swipe 等），接收事件。
  - `ChatStore` — 聊天状态容器（`RuntimeState`、消息列表、生成状态等），Compose state 驱动。
  - `ChatBridgeModels` — Bridge 消息协议（`BridgeEvent`、`ChatMessage`、`ChatSnapshot`）。
  - `NativeChatScreen` — 1v1 聊天原生 UI。
  - `GroupChatScreen`、`GroupMembersScreen`、`GroupSettingsScreen`、`NewGroupScreen` — 群聊相关。
- **WebViewTarget** — `ui/webview/WebViewNavigator.kt` 中定义，表示 WebView 导航目标：`CHAT`、`CharacterChat`、`GroupChat`。

### 数据访问 — 双路径

- **API 路径**：`api/TavernCoreApi.kt` 中的 `TavernCoreClient`，通过 OkHttp 调用本地 SillyTavern HTTP API。使用 SnakeYAML 解析 JSON（不用 Gson/Moshi），JSON 序列化用手写工具函数（`jsonObject()`、`jsonValue()`、`quoteJson()`）。CSRF token 自动获取。
- **本地文件路径**：`data/LocalTavernLibraryReader` 直接读取 `data/<user>/` 下的角色 PNG 和聊天 JSONL，作为服务未运行时的 fallback。

### 主题

`ui/theme/STTheme.kt` 定义 `STAppTheme`，自定义设计 token（`STColors`、`STSpacing`、`STRadius`、`STTypography`）通过 `CompositionLocal` 提供。支持 light/dark/dynamic 配色，`ThemeColorSource` 和 `ThemeMode` 枚举在 `MainViewModelModels.kt` 中。

### Git submodules

- `SillyTavern/` — 上游 SillyTavern 源码（原封不动打包进 APK）
- `node/` — Node.js 源码（Docker 构建时为 Android arm64 编译）

## Key Conventions

- UI 语言是中文。字符串资源在 `res/values/strings.xml` 和 `res/values-zh/strings.xml`。
- Compose + Material 3（`androidx.compose.material3`）。Compose 编译器扩展版本 `1.5.14`，无 Compose compiler plugin。
- `TavernCoreClient` 中 JSON 构造是手写的，不依赖 JSON 序列化库。Bridge 层使用 `org.json.JSONObject`。
- Debug 变体使用 applicationId 后缀 `.dev`，应用名 "ST dev"。
- `minSdk = 26`（Android 8.0），`targetSdk = 36`，`compileSdk = 36`。
- `ui/prototype/` 包含正在迁移的 M3 原型界面（首页、角色、工具、设置等 Compose 屏幕）。
