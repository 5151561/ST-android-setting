# CLAUDE.md

给我讲中文。

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

ST-android 是一个第三方 SillyTavern Android 客户端。它在设备上通过 foreground service 运行嵌入式 Node.js + SillyTavern 服务端（core），并用 Jetpack Compose 构建原生 UI。聊天界面已完成从 WebView 到原生 Compose 的迁移并**退出了 WebView 运行时**（2026-06-24，详见 `docs/native-chat-runtime-exit-status.md`）：提示词组装、世界书扫描、instruct 模板、流式生成等聊天语义全部由原生 Kotlin 实现，直接调用本地 ST 服务端 HTTP API。剩余能力缺口：logprobs（上游阻塞）、TTS/翻译/生图（后续专项）、slash command 与第三方前端扩展（原生架构下不支持）。

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

`gradle.properties` 中的 `org.gradle.java.home` 是开发机专用路径（当前指向 VSCode redhat.java 扩展自带的 JDK 21，扩展升级后路径会失效，需同步更新该行和 `GradleJavaHomeTest`）。

## Architecture

**单模块 Gradle 项目**（`app/`）。包名：`io.github.sanitised.st`。当前架构详版见 `docs/architecture.md`。

### 核心运行时

- `NodeService` — foreground service，启动 Node.js 进程运行 SillyTavern，通过 `NodeStatusListener` 回调通信。
- `NodePayload` — 首次运行时从 APK assets 解压 Node 二进制和 SillyTavern 源码。管理自定义 ST 安装（GitHub 仓库/分支/ZIP）。
- `MainViewModel` — 中心 ViewModel，委托给 `BackupManager`、`CustomInstallManager`、`UpdateManager`、`BatteryPromptManager`。UI 状态以 `MutableState` 字段暴露。
- `AppPaths` — 所有文件路径的单一来源（`stDir`、`dataDir`、`configDir` 等）。

### 导航与 UI

`MainActivity` 托管 `NavHost`，路由定义在 `ui/navigation/STNavGraph.kt`（`STRoutes`），导航外壳是抽屉（`STNavigationScaffold`，无底栏）。主要页面：

- **Home** (`chats/home`) — 对话列表：单聊 + 群聊按最后更新混排，筛选 chips（收藏/进行中/群聊/检查点）、未读打点（`ChatSeenStore`）。位于 `ui/screens/STHomeScreen.kt`。
- **Chat** (`chat`) — `chat/NativeChatScreen`，纯原生 Compose 聊天界面（无 WebView）。
- **Characters** (`characters`) — 原生 Compose 角色管理：列表、详情、编辑。位于 `ui/screens/STCharacterScreens.kt`。
- **Tools** (`tools`) — 工具页面（世界书、预设、API 连接等）。位于 `ui/screens/` 下。
- **Settings** (`me`) — 设置页面。位于 `ui/screens/STSystemScreens.kt`。

### 原生聊天架构

聊天生成完全在原生侧完成，不再依赖 SillyTavern Web 前端：

- **`chat/engine/`**：
  - `NativeChatEngine` — 唯一 `ChatEngine` 实现：设备端组装 prompt，经 `TavernCoreClient` 调 `/api/backends/*/generate`（SSE 流式，含非流式 fallback）。
  - `NativeGenerationRouter` — 连接页 provider 到 chat/text completion 路由的映射。
  - `NativeGroupGenerator` — 群聊多角色轮流生成。
- **`chat/prompt/`**：原生重实现的 ST 生成语义 — `PromptBuilder`（chat completion）、`TextPromptBuilder`（text completion）、`WorldInfoScanner`（世界书扫描）、`InstructTemplate`、`RegexEngine`、`StopStringBuilder`、`ExtensionPromptRegistry`。**这些语义以契约测试对照上游**（`app/src/test/` 下的 `ContractFixtures` 与各 `*ContractTest`），上游升级流程见 `docs/upstream-upgrade.md`。
- **chat 包其他**：`ChatStore`（Compose state 驱动的聊天状态容器）、`ChatModels`（消息/附件/提示词数据结构）、`NativeChatRuntime`、`QuickReplyRuntime`（slash command 明确不支持）、`DataBankRepository`、`ItemizedPromptStore`。
- **群聊 UI**：`GroupChatScreen`、`GroupMembersScreen`、`GroupSettingsScreen`、`NewGroupScreen`。
- **架构守卫**：`NativeRuntimeExitArchitectureTest` 禁止 WebView 运行时代码回流，改聊天架构前先看它。

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
- Compose + Material 3（`androidx.compose.material3`）。AGP 9（built-in Kotlin，不再应用 `org.jetbrains.kotlin.android` 插件），Compose 编译器由 `org.jetbrains.kotlin.plugin.compose` 插件提供，版本需与 AGP 内嵌的 Kotlin Gradle Plugin 一致（根 `build.gradle.kts` 有注释说明）。
- `TavernCoreClient` 中 JSON 构造是手写的，不依赖 JSON 序列化库。chat 层 JSON 操作使用 `org.json.JSONObject`。
- 图片加载用 Coil 3（`coil3.compose.AsyncImage`），网络图片依赖 `coil-network-okhttp` 组件。
- Debug 变体使用 applicationId 后缀 `.dev`，应用名 "ST dev"。
- `minSdk = 26`（Android 8.0），`targetSdk = 36`，`compileSdk = 37`。
- `ui/screens/` 存放正式的 M3 原生界面（首页、角色、工具、设置等 Compose 屏幕）。该包原名 `ui/prototype/`，因名不副实已于 2026-07 改名为 `ui/screens/`。
