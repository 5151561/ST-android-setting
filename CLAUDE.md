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

项目通过 `app/build.gradle.kts` 中的 `java.toolchain` 绑定 JDK 21，无需在 `gradle.properties` 中硬编码本地绝对路径；`GradleJavaHomeTest` 会校验运行时 JVM 主版本。

## Architecture

**单模块 Gradle 项目**（`app/`）。包名：`io.github.sanitised.st`。当前架构详版见 `docs/architecture.md`。

### 核心运行时与依赖注入

- **Hilt 依赖注入**（`di/AppModule.kt`）：`@HiltAndroidApp` 标注 `STApplication`，`@AndroidEntryPoint` 标注 `MainActivity`，单例提供 `AppPaths`、`LocalTavernClientProvider`、`UserPreferencesRepository` 以及各领域 Repository。
- `NodeService` — foreground service，启动 Node.js 进程运行 SillyTavern，通过 `NodeStatusListener` 回调通信。
- `NodePayload` — 首次运行时从 APK assets 解压 Node 二进制和 SillyTavern 源码。管理自定义 ST 安装（GitHub 仓库/分支/ZIP）。
- `STApplication` / `AppContainer` — 应用级依赖容器，统一持有本地 API provider（`tavernApi` / `tavernApiForPort`）与各领域 Repository。
- `MainViewModel` — `@HiltViewModel` 中心 ViewModel，委托给 `BackupManager`、`CustomInstallManager`、`UpdateManager`、`BatteryPromptManager`。应用级与界面级偏好统一委托给 `data/preferences/UserPreferencesRepository`。
- `AppPaths` — 所有文件路径的单一来源（`stDir`、`dataDir`、`configDir` 等）。

### 导航与 UI

`MainActivity`（精简至约 470 行，仅负责生命周期、Service 绑定、权限与系统跳转）托管 `STAppChrome`、`STAppNavHost`、`STAppDialogs`。导航与外壳拆分在 `ui/navigation/`：
- `STNavGraph.kt`（`STRoutes` 路由常量与构建器）
- `STAppChrome.kt`（抽屉菜单项、顶栏、导航安全扩展函数）
- `STAppNavHost.kt`（按业务域拆分为 `homeAndChatGraph`、`characterGraph`、`worldInfoAndToolsGraph`、`settingsAndSystemGraph` 四个子图扩展）
- `STAppDialogs.kt`（全局确认与更新弹窗）

主要页面（位于 `ui/screens/`，按单一职责独立成文件）：
- **Home** (`chats/home`) — 对话列表：单聊 + 群聊按最后更新混排，筛选 chips（收藏/进行中/群聊/检查点）、未读打点（`ChatSeenStore`）。位于 `ui/screens/STHomeScreen.kt`。
- **Chat** (`chat`) — `chat/NativeChatScreen`，纯原生 Compose 聊天界面（无 WebView）。
- **Characters** (`characters`) — 原生 Compose 角色管理：列表、详情、编辑（`STCharacterScreens.kt`、`STCharacterEditScreens.kt`、`CharacterViewModels.kt`）。
- **Tools & Settings** — 单一职责屏幕文件：`STWorldInfoScreen.kt`、`STWorldBookScreens.kt`、`STPersonaScreen.kt`、`STAISettingsScreen.kt`、`STApiConnectionScreens.kt`、`STProviderDetailScreen.kt`、`STSecretsScreen.kt`、`STExtensionsScreen.kt`、`STAuthorNoteCFGScreen.kt`、`STMemoryScreen.kt`、`STAppearanceScreen.kt`、`STAppearanceDeepScreens.kt`、`STMeScreen.kt`、`STStCoreScreen.kt`，共享组件位于 `STPremiumComponents.kt` 与 `STSystemSharedComponents.kt`。

### 原生聊天架构

聊天生成完全在原生侧完成，不再依赖 SillyTavern Web 前端：

- **`ChatViewModel`** — 持有 `ChatStore`、loader、runtime 和 engine；生成任务运行在 `viewModelScope`，不再绑定 Activity 的 Compose scope。

- **`chat/engine/`**：
  - `NativeChatEngine` — 唯一 `ChatEngine` 实现：设备端组装 prompt，经 `TavernCoreClient` 调 `/api/backends/*/generate`（SSE 流式，含非流式 fallback）。
  - `NativeGenerationRouter` — 连接页 provider 到 chat/text completion 路由的映射。
  - `NativeGroupGenerator` — 群聊多角色轮流生成。
- **`chat/prompt/`**：原生重实现的 ST 生成语义 — `PromptBuilder`（chat completion）、`TextPromptBuilder`（text completion）、`WorldInfoScanner`（世界书扫描）、`InstructTemplate`、`RegexEngine`、`StopStringBuilder`、`ExtensionPromptRegistry`。**这些语义以契约测试对照上游**（`app/src/test/` 下的 `ContractFixtures` 与各 `*ContractTest`），上游升级流程见 `docs/upstream-upgrade.md`。
- **chat 包其他**：`ChatStore`（Compose state 驱动的聊天状态容器）、`ChatModels`（消息/附件/提示词数据结构）、`NativeChatRuntime`、`QuickReplyRuntime`（slash command 明确不支持）、`DataBankRepository`、`ItemizedPromptStore`。
- **群聊 UI**：`GroupChatScreen`、`GroupMembersScreen`、`GroupSettingsScreen`、`NewGroupScreen`。
- **架构守卫**：`NativeRuntimeExitArchitectureTest` 禁止 WebView 运行时代码回流，改聊天架构前先看它。

### 数据访问 — 双路径与领域 Repository

- **API 路径（按领域拆分）**：
  - `api/TavernCoreApi.kt`：`TavernCoreApi` 接口定义与 `TavernCoreClient` 门面。
  - `api/TavernHttpTransport.kt`：OkHttp 传输、CSRF token 自动获取、CookieJar 与 JSON 辅助。
  - `api/TavernCharacterEndpoints.kt`：角色 CRUD、头像上传、聊天记录 JSONL 接口实现。
  - `api/TavernWorldPersonaEndpoints.kt`：世界书 (WorldInfo) 与人设 (Persona) 接口实现。
  - `api/TavernPresetSecretEndpoints.kt`：预设、密钥、系统设置、连接配置、快照备份、背景图与群聊接口实现。
  - JSON 编解码用 `api/StJson.kt`（基于 kotlinx.serialization，不用 Gson/Moshi）。
- **本地文件路径**：`data/LocalTavernLibraryReader` 直接读取 `data/<user>/` 下的角色 PNG 和聊天 JSONL，作为服务未运行时的 fallback。
- **领域 Repository 与偏好仓库**：
  - `data/preferences/UserPreferencesRepository.kt`：统一管理全部 14 项用户与界面偏好（暴露 `StateFlow<UserPreferences>`）。
  - `data/CharacterRepository.kt`：角色列表、详情、编辑、创建、导入及角色历史聊天管理。
  - `data/repository/WorldInfoRepository.kt`：世界书 CRUD 与全局世界书设置。
  - `data/repository/ConnectionRepository.kt`：API 连接配置、密钥管理、模型拉取、连通性测试与设置快照。
  - `data/repository/PersonaPresetRepository.kt`：人设、预设库、标签、背景图与通用文件上传。
  - `data/repository/GroupChatRepository.kt`：群聊列表、创建、编辑、删除与群聊 JSONL 读写。
- **架构边界守卫**：`ui/ArchitectureBoundaryContractTest.kt` 严禁 `ui/` 与 `chat/` 层直接实例化 `TavernCoreClient(`，并守护 `MainActivity.kt`、`TavernCoreApi.kt` 行数上限及防止巨型屏幕文件回流。

### 主题

`ui/theme/STTheme.kt` 定义 `STAppTheme`，自定义设计 token（`STColors`、`STSpacing`、`STRadius`、`STTypography`）通过 `CompositionLocal` 提供。支持 light/dark/dynamic 配色，`ThemeColorSource` 和 `ThemeMode` 枚举在 `MainViewModelModels.kt` 中。

### Git submodules

- `SillyTavern/` — 上游 SillyTavern 源码（原封不动打包进 APK）
- `node/` — Node.js 源码（Docker 构建时为 Android arm64 编译）

## Key Conventions

- UI 语言是中文。字符串资源在 `res/values/strings.xml` 和 `res/values-zh/strings.xml`。
- Compose + Material 3（`androidx.compose.material3`）。AGP 9（built-in Kotlin，不再应用 `org.jetbrains.kotlin.android` 插件），Compose 编译器由 `org.jetbrains.kotlin.plugin.compose` 插件提供，版本需与 AGP 内嵌的 Kotlin Gradle Plugin 一致（根 `build.gradle.kts` 有注释说明）。
- `TavernCoreClient` 的 JSON 编解码走 `StJson`（kotlinx.serialization）。chat 层 JSON 操作使用 `org.json.JSONObject`。SnakeYAML 仅用于 YAML（`ConfigFormTools`、`NodeBackup` 处理 config.yaml），不再参与 JSON 解析。
- 图片加载用 Coil 3（`coil3.compose.AsyncImage`），网络图片依赖 `coil-network-okhttp` 组件。
- Debug 变体使用 applicationId 后缀 `.dev`，应用名 "ST dev"。
- `minSdk = 26`（Android 8.0），`targetSdk = 36`，`compileSdk = 37`。
- `ui/screens/` 存放正式的 M3 原生界面（首页、角色、工具、设置等 Compose 屏幕）。该包原名 `ui/prototype/`，因名不副实已于 2026-07 改名为 `ui/screens/`。
