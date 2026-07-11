# ST-android 架构文档

> 2026-06-24 当前口径：App 内聊天已退出隐藏 WebView runtime，聊天唯一入口为 `NativeChatScreen` + `NativeChatEngine`。旧 `ChatWebViewScreen` / `ChatRuntimeBridge` / `chat_runtime_adapter.js` 已删除。详见 `docs/native-chat-runtime-exit-status.md`。

## 项目概览

ST-android 是一个第三方 SillyTavern Android 客户端。它在设备本地通过 foreground service 运行嵌入式 Node.js + SillyTavern 服务端，并用 Jetpack Compose 构建原生 UI。

**包名**：`io.github.sanitised.st`  
**最低 SDK**：26 (Android 8.0) · **目标 SDK**：36 · **编译 SDK**：36  
**架构**：单模块 Gradle 项目（`app/`）

---

## 系统架构总览

```
┌────────────────────────────────────────────────────────────┐
│                      Android App                           │
│                                                            │
│  ┌──────────────┐  ┌────────────────┐  ┌──────────────┐  │
│  │  Compose UI  │  │ Native Chat    │  │ MainViewModel│  │
│  │  (原生界面)   │  │ Runtime        │  │  (中心状态)   │  │
│  └──────┬───────┘  └───────┬────────┘  └──────┬───────┘  │
│         │                  │                  │          │
│         │   ┌──────────────┴──────────────┐   │          │
│         │   │ NativeChatEngine             │   │          │
│         │   │ NativeGenerationRouter       │   │          │
│         │   │ QuickReply/DataBank/Prompt   │   │          │
│         │   └──────────────┬──────────────┘   │          │
│         │                  │                  │          │
│  ┌──────┴──────────────────┴──────────────────┴──────┐   │
│  │              数据访问层                               │   │
│  │  TavernCoreClient (HTTP API)                        │   │
│  │  LocalTavernLibraryReader (本地文件)                  │   │
│  └─────────────────────┬───────────────────────────────┘   │
│                        │                                    │
│  ┌─────────────────────┴───────────────────────────────┐   │
│  │           NodeService (Foreground Service)           │   │
│  │           Node.js 进程 + SillyTavern 服务端           │   │
│  └─────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────┘
```

---

## 一、核心运行时层

### NodeService

**文件**：`NodeService.kt`

Foreground service，负责启动和管理 Node.js 进程，在其中运行 SillyTavern 服务端。

| 职责 | 说明 |
|------|------|
| 进程管理 | 启动/停止 Node.js 进程 |
| 状态通知 | 通过 `NodeStatusListener` 回调向上层报告状态 |
| 前台通知 | 维持 foreground notification 防止系统杀死进程 |
| 日志收集 | 捕获 Node.js stdout/stderr 输出 |

**生命周期**：

```
ACTION_START → 解压 payload → 启动 Node.js → SillyTavern 监听端口
ACTION_STOP  → 终止 Node.js 进程 → 停止 service
```

### NodePayload

**文件**：`NodePayload.kt`

管理 Node.js 运行环境的部署：

- 首次运行时从 APK assets (`node_payload/`) 解压 Node 二进制和 SillyTavern 源码
- 支持自定义 ST 安装（GitHub 仓库/分支/ZIP 包）

### AppPaths

**文件**：`AppPaths.kt`

所有文件路径的单一来源（single source of truth）：

| 路径 | 用途 |
|------|------|
| `stDir` (`files/st`) | SillyTavern 安装目录 |
| `dataDir` (`files/data`) | 用户数据目录 |
| `configDir` (`files/config`) | 配置文件目录 |
| `logsDir` (`files/logs`) | 日志目录 |
| `tmpDir` (`files/tmp`) | 临时文件目录 |
| `nodeBin(abi)` | Node.js 二进制路径（按 CPU 架构区分） |

---

## 二、数据访问层

ST-android 采用**双路径**数据访问策略：

### 路径 1：HTTP API — `TavernCoreApi` / `TavernCoreClient`

**文件**：`api/TavernCoreApi.kt`

通过 OkHttp 调用本地 SillyTavern HTTP API（默认 `http://127.0.0.1:8000`）。这是**主数据通道**，涵盖所有角色、聊天、设置的 CRUD 操作。

**技术特点**：
- JSON 解析使用 SnakeYAML（不使用 Gson/Moshi）
- JSON 序列化使用手写工具函数（`jsonObject()`、`jsonValue()`、`quoteJson()`）
- CSRF token 自动获取和管理

#### API 接口一览

**角色管理**

| 方法 | 说明 |
|------|------|
| `listCharacters()` | 获取角色列表 |
| `getCharacter(avatar)` | 获取角色详情 |
| `createCharacter(request, avatarUpload?)` | 创建角色 |
| `updateCharacter(request, avatarUpload?)` | 更新角色 |
| `mergeCharacterAttributes(avatar, ...)` | 部分更新角色属性（收藏、标签等） |
| `renameCharacter(avatar, newName)` | 重命名角色 |
| `duplicateCharacter(avatar)` | 复制角色 |
| `deleteCharacter(avatar, deleteChats?)` | 删除角色 |
| `importCharacter(fileName, bytes, ...)` | 导入角色（文件上传） |
| `importExternalCharacter(urlOrUuid, ...)` | 导入外部角色（URL/UUID） |
| `exportCharacter(avatar, format)` | 导出角色（PNG/JSON） |
| `updateCharacterAvatar(avatar, fileName, bytes)` | 更新角色头像 |

**聊天记录**

| 方法 | 说明 |
|------|------|
| `listRecentChats()` | 获取最近聊天列表 |
| `listCharacterChats(avatar)` | 获取角色的聊天历史 |
| `renameCharacterChat(avatar, originalFile, renamedFile)` | 重命名聊天 |
| `deleteCharacterChat(avatar, chatFile)` | 删除聊天 |
| `importCharacterChat(avatar, characterName, ...)` | 导入聊天 |
| `exportCharacterChat(avatar, chatFile, format)` | 导出聊天（JSONL/TXT） |

**群聊**

| 方法 | 说明 |
|------|------|
| `listGroups()` | 获取群聊列表 |
| `createGroup(request)` | 创建群聊 |
| `sendMessage(chatId, text)` | 发送消息（返回 SSE 流） |
| `stopGeneration(chatId)` | 停止生成 |

**世界书 (World Info)**

| 方法 | 说明 |
|------|------|
| `listWorldInfos()` | 获取世界书列表 |
| `getWorldInfo(name)` | 获取世界书详情 |
| `saveWorldInfo(book)` | 保存世界书 |
| `deleteWorldInfo(name)` | 删除世界书 |

**人设 (Persona)**

| 方法 | 说明 |
|------|------|
| `listPersonas()` | 获取人设列表 |
| `savePersona(request)` | 保存人设 |
| `uploadPersonaAvatar(...)` | 上传人设头像 |
| `deletePersona(avatar)` | 删除人设 |

**预设 (Preset)**

| 方法 | 说明 |
|------|------|
| `getPresetLibrary()` | 获取预设库（按 API 分类） |
| `savePreset(apiId, name, presetJson)` | 保存预设 |
| `selectPreset(apiId, name)` | 激活预设 |
| `deletePreset(apiId, name)` | 删除预设 |
| `restorePreset(apiId, name)` | 还原预设到默认 |

**密钥管理 (Secrets)**

| 方法 | 说明 |
|------|------|
| `listSecrets()` | 获取密钥列表（按提供商分组） |
| `writeSecret(key, value, label)` | 写入密钥 |
| `rotateSecret(key, id)` | 轮换密钥 |
| `renameSecret(key, id, label)` | 重命名密钥 |
| `deleteSecret(key, id?)` | 删除密钥 |

**系统设置**

| 方法 | 说明 |
|------|------|
| `getSettings()` | 获取全局设置 |
| `saveSettings(settings)` | 保存全局设置 |
| `fetchModels(providerId)` | 拉取可用模型列表 |
| `healthCheck()` | 健康检查（含版本号） |

**标签 / 连接 / 备份**

| 方法 | 说明 |
|------|------|
| `getTagSettings()` / `saveTagSettings()` | 标签管理 |
| `listConnectionProfiles()` / `saveConnectionProfile()` | API 连接配置 |
| `listSettingsSnapshots()` / `makeSettingsSnapshot()` / `restoreSettingsSnapshot()` | 设置快照 |
| `listChatBackups()` / `downloadChatBackup()` / `deleteChatBackup()` | 聊天备份 |
| `uploadFile()` / `deleteFile()` | 通用文件上传/删除 |

### 路径 2：本地文件 — `LocalTavernLibraryReader`

**文件**：`data/LocalTavernLibraryReader.kt`

直接读取 `data/<user>/` 目录下的文件，作为 SillyTavern 服务未运行时的 fallback。

| 方法 | 说明 |
|------|------|
| `listCharacters(limit)` | 扫描 `characters/` 目录中的 PNG 文件 |
| `listRecentChats(limit)` | 扫描 `chats/` 目录中的 JSONL 文件 |

---

## 三、原生聊天运行时层

聊天页已经退出隐藏 WebView runtime。`NativeChatScreen` 是唯一聊天界面，所有聊天动作通过 `ChatEngine` 接口进入 `NativeChatEngine`，再由原生 JSONL、PromptAssembly 和本地 SillyTavern 后端 endpoint 完成。

### 运行时架构

```
┌──────────────────┐
│ NativeChatScreen │
└────────┬─────────┘
         │ ChatEngine.send / stop / regenerate / continue
┌────────▼─────────┐
│ NativeChatEngine │
└────────┬─────────┘
         ├─ NativeChatRepository / NativeChatJsonOps：JSONL 与 header metadata 保存
         ├─ PromptBuilder / TextPromptBuilder：提示词组装
         ├─ NativeGenerationRouter：连接页 provider 到后端 route
         ├─ QuickReplyRuntime：快捷回复读取与执行
         ├─ ItemizedPromptStore：生成时 prompt 明细记录
         └─ DataBankRepository：settings / 角色 / 聊天附件聚合
```

### ChatEngine

**文件**：`chat/engine/ChatEngine.kt`

UI 唯一入口，保留四个动作：

| 方法 | 说明 |
|------|------|
| `send(text)` | 发送用户消息，写入待发送附件到消息 `extra`，组装 prompt 并生成回复 |
| `stop()` | 停止当前原生 stream |
| `regenerate()` | 为最后一条 AI 消息新增 swipe 并保存 JSONL |
| `continueGeneration()` | 继续当前消息并保存追加内容 |

### NativeGenerationRouter

**文件**：`chat/engine/NativeGenerationRouter.kt`

按当前连接页 provider 选择原生后端 route：

| Provider | Route |
|------|------|
| OpenAI 系 / Chat Completion | `generateChatCompletion*` |
| TextGen WebUI 系 | `generateTextCompletion*` |
| Kobold / Kobold Horde / NovelAI | Text Completion 兼容 route |

不再存在 Bridge fallback；未支持 provider 会明确返回 unsupported 错误。

### ChatStore

**文件**：`chat/ChatStore.kt`

聊天状态容器，所有字段都是 Compose `MutableState`，驱动 UI 自动更新：

| 状态字段 | 类型 | 说明 |
|------|------|------|
| `runtimeState` | `RuntimeState` | 运行时状态（NOT_READY / READY / ERROR） |
| `characterName` | `String` | 当前角色名 |
| `avatarUrl` | `String` | 当前角色头像 |
| `chatFile` | `String` | 当前聊天文件名 |
| `mode` | `String` | 聊天模式（character / group） |
| `isGenerating` | `Boolean` | 是否正在生成 |
| `messages` | `MutableStateList<ChatMessage>` | 消息列表 |
| `pendingAttachments` | `MutableStateList<PendingAttachment>` | 待发送附件 |
| `authorsNote` | `String` | 作者注释 |
| `cfgScale` / `cfgNegativePrompt` / `cfgPositivePrompt` | | CFG 参数 |
| `worldInfoName` | `String` | 绑定的世界书 |

### 数据模型

**ChatMessage** — 单条聊天消息：

```json
{
  "id": 0,
  "name": "角色名",
  "mes": "消息内容（支持 Markdown）",
  "is_user": false,
  "is_system": false,
  "send_date": "2024-01-01",
  "swipe_id": 0,
  "swipes": ["swipe1", "swipe2"],
  "extra": { "media": [], "files": [] }
}
```

**ChatSnapshot** — 聊天快照（完整状态）：

```json
{
  "mode": "character",
  "avatarUrl": "avatar.png",
  "characterName": "角色名",
  "chatFile": "chat_file.jsonl",
  "isGenerating": false,
  "messages": [ ... ],
  "metadata": {
    "authorsNote": "",
    "cfgScale": 1.0,
    "worldInfo": ""
  }
}
```

---

## 四、前端 UI 层

### 导航结构

**入口**：`MainActivity` 托管 `NavHost`  
**路由定义**：`ui/navigation/STNavGraph.kt` (`STRoutes`)  
**底部导航**：`ui/navigation/STBottomBar.kt`

```
首页 (chats/home)
├── 仪表盘 / 状态卡片 / 最近聊天 / 快捷操作

聊天 (chat)
├── 原生 1v1 聊天 (NativeChatScreen)
├── 群聊 (GroupChatScreen)
├── 群聊设置 (GroupSettingsScreen)
├── 群成员 (GroupMembersScreen)
└── 创建群聊 (NewGroupScreen)

角色 (characters)
├── 角色列表
├── 角色详情 (characters/detail/{avatar})
├── 角色编辑 (characters/edit/{avatar})
├── 创建角色 (characters/new)
└── 历史聊天 (characters/chats/{avatar})

工具 (tools)
├── 世界书 (world-info)
├── 人设 (personas)
├── AI 预设 (ai-settings)
├── API 连接 (api-connections)
│   └── 提供商详情 (api-connections/detail/{providerId})
└── 聊天备份 (memory)

设置 (me)
├── 日志 (settings/logs)
├── 配置 (settings/config)
├── 密钥管理 (settings/secrets)
├── 扩展 (settings/extensions)
├── 作者注释 (settings/author-note)
├── 快速回复 (settings/quick-replies)
├── 外观 (settings/appearance)
├── ST 管理 (st-core)
└── 法律信息 (settings/legal)
```

### 页面文件对照

| 路由 | 文件 |
|------|------|
| `chats/home` | `ui/prototype/PrototypeHomeScreen.kt` |
| `chat` | `chat/NativeChatScreen.kt` |
| `characters` | `ui/prototype/PrototypeCharacterScreens.kt` |
| `tools` | `ui/prototype/PrototypeAdvancedScreens.kt` |
| `me` | `ui/prototype/PrototypeSystemScreens.kt` |
| 群聊相关 | `chat/GroupChatScreen.kt`、`chat/GroupSettingsScreen.kt` 等 |
| 群聊列表 | `ui/prototype/PrototypeGroupChatScreen.kt` |

### 主题系统

**文件**：`ui/theme/STTheme.kt`、`ui/theme/STColors.kt`

基于 Material 3，通过 `CompositionLocal` 提供自定义设计 token：

| Token | 说明 |
|------|------|
| `STColors` | 自定义颜色（含 light/dark 变体） |
| `STSpacing` | 间距规范（xs=4dp ~ section=48dp） |
| `STRadius` | 圆角规范（sm=4dp ~ pill=9999dp） |
| `STTypography` | 字体规范（含等宽字体） |

**配色模式**：`ThemeMode.LIGHT` / `DARK` / `AUTO`  
**配色来源**：`ThemeColorSource`（支持 dynamic color）

---

## 五、ViewModel 层

### MainViewModel

**文件**：`MainViewModel.kt`

应用的中心 ViewModel，采用委托模式将功能拆分到专门的 Manager：

| 委托 Manager | 职责 |
|------|------|
| `BackupManager` | 数据备份/恢复 |
| `CustomInstallManager` | 自定义 ST 安装（GitHub 仓库/分支/ZIP） |
| `UpdateManager` | 应用更新检查、UI 偏好设置 |
| `BatteryPromptManager` | 电池优化提示 |

所有 UI 状态通过 `MutableState` 字段暴露，Compose 自动订阅变化。

---

## 六、项目目录结构

```
app/src/main/
├── java/io/github/sanitised/st/
│   ├── MainActivity.kt          # 入口 Activity
│   ├── MainViewModel.kt         # 中心 ViewModel
│   ├── MainViewModelModels.kt   # ViewModel 相关数据模型
│   ├── NodeService.kt           # Node.js 前台服务
│   ├── NodePayload.kt           # Node.js 环境部署
│   ├── AppPaths.kt              # 路径管理
│   ├── BackupManager.kt         # 备份管理
│   ├── CustomInstallManager.kt  # 自定义安装管理
│   ├── UpdateManager.kt         # 更新管理
│   ├── BatteryPromptManager.kt  # 电池优化提示
│   ├── HttpDownloader.kt        # HTTP 下载工具
│   ├── TarUtils.kt              # tar 解压工具
│   ├── Versioning.kt            # 版本管理
│   │
│   ├── api/
│   │   └── TavernCoreApi.kt     # SillyTavern HTTP API 客户端
│   │
│   ├── chat/
│   │   ├── ChatModels.kt        # 聊天消息、附件、提示词和 Data Bank 数据模型
│   │   ├── ChatTarget.kt        # 当前聊天目标
│   │   ├── ChatStore.kt         # 聊天状态容器
│   │   ├── ChatUiState.kt       # 聊天 UI 状态
│   │   ├── NativeChatScreen.kt  # 原生 1v1 聊天界面
│   │   ├── NativeChatRuntime.kt # 原生消息操作
│   │   ├── NativeChatJsonOps.kt # JSONL/header 元数据写入
│   │   ├── QuickReplyRuntime.kt # 原生 Quick Replies
│   │   ├── DataBankRepository.kt # 原生 Data Bank 聚合
│   │   ├── ItemizedPromptStore.kt # Prompt 明细记录
│   │   ├── GroupChatScreen.kt   # 群聊界面
│   │   ├── GroupSettingsScreen.kt # 群聊设置
│   │   ├── GroupMembersScreen.kt  # 群成员管理
│   │   └── NewGroupScreen.kt   # 创建群聊
│   │
│   ├── data/
│   │   └── LocalTavernLibraryReader.kt  # 本地文件读取（fallback）
│   │
│   └── ui/
│       ├── webview/
│       │   └── WebViewErrorPage.kt   # WebView 错误页复用组件
│       │
│       ├── navigation/
│       │   ├── STNavGraph.kt    # 路由定义
│       │   └── STBottomBar.kt   # 底部导航栏
│       │
│       ├── prototype/           # M3 原型界面（迁移中）
│       │   ├── PrototypeHomeScreen.kt       # 首页
│       │   ├── PrototypeCharacterScreens.kt # 角色管理
│       │   ├── PrototypeAdvancedScreens.kt  # 工具页面
│       │   ├── PrototypeSystemScreens.kt    # 设置页面
│       │   ├── PrototypeGroupChatScreen.kt  # 群聊列表
│       │   ├── PrototypePastChatsScreen.kt  # 历史聊天
│       │   ├── PrototypeComponents.kt       # 通用组件
│       │   ├── PrototypeModels.kt           # 原型数据模型
│       │   ├── ApiConnectionState.kt        # API 连接状态
│       │   └── SecretsUiState.kt            # 密钥管理状态
│       │
│       ├── components/
│       │   ├── CharacterSharedComponents.kt # 角色通用组件
│       │   └── STCards.kt                   # 卡片组件
│       │
│       ├── screens/
│       │   ├── CharacterEditTools.kt        # 角色编辑工具
│       │   ├── TavernLibraryHelper.kt       # 库辅助工具
│       │   └── DocumentFileHelpers.kt       # 文件选择辅助
│       │
│       └── theme/
│           ├── STTheme.kt       # 主题定义
│           └── STColors.kt      # 颜色定义
│
├── assets/
│   ├── legal/                   # 开源许可证
│   └── node_payload/            # Node.js + SillyTavern 打包
│       ├── manifest.json
│       ├── st_bundle.tar
│       ├── npm.tar
│       └── bin/                 # Node 二进制（按架构）
│
└── res/
    ├── values/strings.xml       # 字符串资源（默认）
    └── values-zh/strings.xml    # 中文字符串
```

---

## 七、构建与测试

```bash
# 构建 debug APK
./gradlew assembleDebug

# 运行全部单元测试
./gradlew test

# 运行单个测试类
./gradlew testDebugUnitTest --tests "io.github.sanitised.st.api.TavernCoreClientTest"
```

- Debug 变体使用 applicationId 后缀 `.dev`，应用名 "ST dev"
- 仅有本地 JVM 单元测试（`app/src/test/`），无 instrumented test
- Git submodules：`SillyTavern/`（上游源码）、`node/`（Node.js 源码）
