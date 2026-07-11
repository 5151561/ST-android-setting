# SillyTavern Chat 原生界面迁移方案

> 2026-06-24 当前口径：本文早期章节描述的“原生 UI + 隐藏 WebView runtime/Bridge 兜底”已经被 runtime exit 取代。当前聊天路径为纯原生 `NativeChatScreen` / `NativeChatEngine`，见 `docs/native-chat-runtime-exit-status.md`。本文其余内容保留为迁移历史。

版本：0.15
日期：2026-06-03
状态：**已归档为历史迁移方案**。2026-06-24 后聊天路径已完成 runtime exit，当前实现以 `docs/native-chat-runtime-exit-status.md` 为准。
适用范围：历史上的 Chat 原生化、JS Bridge、SillyTavern 运行时复用、API 对接设计记录。

## 1. 背景和目标

当前 App 的 Chat 页由内置 WebView 加载 `http://127.0.0.1:{port}/`，直接复用 SillyTavern 原版前端。角色、工具、设置等非 Chat 页面已经开始由 Compose 原生页面承接，并通过 `TavernCoreClient` 调用本地 SillyTavern API。

下一阶段目标是迁移 Chat 可见界面：让用户看到和操作的是 Android 原生 Chat UI，同时继续复用 SillyTavern 的聊天运行时、提示词组装、世界书触发、扩展注入、流式生成和保存语义。

迁移目标不是一次性重写 SillyTavern 前端，而是先完成稳定的原生聊天闭环：

1. 原生 Chat UI 展示当前角色或群聊的消息。
2. 原生输入栏发送消息、停止生成、重生成、继续生成。
3. 运行时仍复用 SillyTavern Web 前端的 `Generate()` 相关逻辑。
4. Chat 文件和消息结构保持 JSONL 兼容。
5. 角色、聊天文件、附件等管理能力能在 API 和 Bridge 之间清晰分工。
6. 高级能力按阶段补齐：消息编辑、删除、swipe、附件、群聊、Author's Note、世界书、扩展。

一句话原则：

> 原生端可以重做 Chat 界面，但不要轻易重做 SillyTavern 的聊天语义；先用 JS Bridge 复用运行时，再逐步抽取和替换可控能力。

## 2. 当前架构约束

### 2.1 已有能力

当前仓库已经具备：

| 能力 | 当前实现 | 迁移含义 |
|---|---|---|
| Core 服务 | App 内启动 Node/SillyTavern，监听 `127.0.0.1:{port}` | Chat 原生 UI 不需要自行实现 ST 后端 |
| Chat WebView | `ChatWebViewScreen` 加载 ST 原版前端 | 可继续作为运行时容器 |
| Android Bridge | `STAndroidBridge` 注入为 `window.STAndroid` | 可扩展为 Chat 双向通信通道 |
| Runtime flags | `WebViewNavigator.injectAndroidRuntimeFlags()` 注入 `window.ST_ANDROID` | 可作为 Android 环境识别信号 |
| Core API Client | `TavernCoreClient` 调用 `/api/characters/*` 等本地 API | 适合角色、聊天文件、设置等数据读写 |
| 文件选择 | WebView `onShowFileChooser` 和原生文档选择器 | 附件和导入可复用或迁移到原生入口 |

### 2.2 关键约束

SillyTavern 的“聊天生成”不是一个简单的后端 API。后端 `/api/backends/*/generate` 接收的是已经组装好的 generation payload，而不是“chat id + user message”的高级聊天请求。

完整生成语义大量发生在 Web 前端：

1. 角色卡字段注入。
2. 历史消息裁剪。
3. 世界书扫描和激活。
4. Author's Note、CFG、Instruct、Chat Completion 模板。
5. stopping strings、正则替换、扩展 prompt 注入。
6. slash command、generation interceptor。
7. streaming、reasoning、tool calls、swipe。
8. 附件内容拼接和消息 `extra` 维护。

因此，下一阶段不应让原生端直接调用 `/api/backends/chat-completions/generate` 来替代完整聊天生成。P0/P1 应通过 JS Bridge 调用或驱动 ST Web 运行时，让原版前端继续负责生成链路。

## 3. 推荐架构

### 3.1 总体形态

推荐采用“原生可见 UI + 运行时 WebView + 双向 Bridge + 本地 API”的混合架构：

```text
Compose Native Chat UI
  ├─ ChatStore：原生消息镜像、加载状态、生成状态
  ├─ ChatController：发送、停止、重生成、继续、编辑等用户动作
  ├─ TavernCoreClient：角色列表、聊天文件列表、导入导出等稳定 API
  └─ ChatRuntimeBridge：与 ST Web runtime 通信

Runtime WebView
  ├─ 加载 http://127.0.0.1:{port}/
  ├─ 保留 SillyTavern 前端状态和 Generate() 链路
  ├─ 注入 window.ST_ANDROID / bridge runtime adapter
  └─ window.STAndroid.postChatEvent(...) 回传事件给 Android

SillyTavern Core
  ├─ /api/characters/*
  ├─ /api/chats/*
  ├─ /api/groups/*
  └─ /api/backends/*
```

### 3.2 单一事实源

迁移期必须避免 WebView 和原生端同时写同一份活动聊天状态。

推荐分工：

| 状态类型 | 事实源 | 原生端职责 |
|---|---|---|
| 当前活动聊天 `chat`、`chat_metadata` | Runtime WebView 内 ST 前端 | 镜像展示，用户动作通过 Bridge 发给 runtime |
| 生成中状态、streaming、abort | Runtime WebView 内 ST 前端 | 展示状态，发送停止命令 |
| 角色列表、角色详情 | ST 本地 API，必要时 runtime 同步 | 复用当前角色原生页面已有 API 能力 |
| 聊天文件列表、导入、导出、重命名、删除 | ST 本地 API 优先 | 对未打开的聊天文件可直接 API 操作 |
| 当前打开聊天切换、新建、保存 | 原生快照 + Runtime WebView 对齐 | 原生生成开关下角色聊天先 API 读取 JSONL 快照；Bridge 后台打开同一目标，保留未迁移动作 |
| 附件选择、分享、复制、通知、TTS/STT | Android 原生能力 | 通过 `STAndroidBridge` 提供给 Web runtime 或直接由原生 UI 调用 |

换句话说：**活动聊天由 JS runtime 负责写，原生 UI 负责显示和发命令；非活动数据管理优先走本地 API。**

### 3.3 Bridge 不是替代所有 API

下一阶段“通过 JS Bridge”主要指 Chat 运行时控制和事件同步，不代表所有数据请求都走 Bridge。

| 场景 | 推荐通道 | 原因 |
|---|---|---|
| 发送消息、停止、重生成、继续 | Bridge | 必须复用 `Generate()`、streaming、扩展和保存语义 |
| 当前活动聊天加载、切换 | 原生快照优先（角色聊天 + 原生生成开关），Bridge 对齐兜底 | 先让 Compose 有可显示的目标 JSONL；随后保持 `this_chid`、`chat`、事件总线和 UI runtime 一致 |
| 角色列表、角色详情 | API 优先 | 已有 `TavernCoreClient`，稳定且易测试 |
| 聊天文件列表、导入导出、重命名删除 | API 优先，活动聊天操作需同步 runtime | 文件管理是服务端能力，原生 API 更直接 |
| 附件上传前的系统文件选择 | 原生 + Bridge/API | Android 文件能力在原生侧，最终写入仍需 ST 语义 |
| 主题、分享、复制、常亮、通知、TTS/STT | Bridge/原生 | 属于 Android 平台增强 |

## 4. Bridge 设计

### 4.1 命名建议

保留现有 `window.STAndroid` 作为 Android 能力入口，在其上扩展通用消息方法：

```js
window.STAndroid.postChatEvent(json)
window.STAndroid.postBridgeResult(json)
window.STAndroid.getRuntimeInfo()
window.STAndroid.getThemeMode()
window.STAndroid.copyToClipboard(text)
window.STAndroid.shareText(text)
window.STAndroid.setKeepScreenOn(enabled)
```

Runtime WebView 内再注入一个 JS 侧适配器：

```js
window.STAndroidChatRuntime.dispatch(json)
window.STAndroidChatRuntime.getSnapshot()
window.STAndroidChatRuntime.openCharacterChat(payload)
window.STAndroidChatRuntime.sendMessage(payload)
window.STAndroidChatRuntime.stopGeneration()
```

Android 到 JS 的方向通过 `WebView.evaluateJavascript(...)` 调用 `window.STAndroidChatRuntime.dispatch(...)`。JS 到 Android 的方向通过 `window.STAndroid.postChatEvent(...)` 回调。

### 4.2 消息信封

Bridge 消息统一用 JSON 字符串，避免后续方法爆炸。

```json
{
  "id": "uuid",
  "kind": "command",
  "name": "chat.send",
  "payload": {
    "text": "hello"
  },
  "timestamp": 1790000000000
}
```

字段约定：

| 字段 | 说明 |
|---|---|
| `id` | 命令 ID，用于关联 result/error |
| `kind` | `command`、`event`、`result`、`error` |
| `name` | 事件或命令名，如 `chat.send` |
| `payload` | 具体数据 |
| `timestamp` | 发送时间，便于日志和排查 |

### 4.3 P0 命令

| 命令 | 方向 | 说明 |
|---|---|---|
| `runtime.getSnapshot` | Android -> JS | 获取当前角色、聊天、消息、生成状态 |
| `runtime.save` | Android -> JS | 手动触发 ST runtime 保存，用于保存错误提示的重试入口；保存失败检测 best-effort |
| `chat.openCharacter` | Android -> JS | 选择角色并打开指定聊天文件 |
| `chat.send` | Android -> JS | 发送普通文本，内部调用 ST 发送/生成逻辑 |
| `generation.stop` | Android -> JS | 停止当前生成 |
| `generation.regenerate` | Android -> JS | 重生成最后一条回复 |
| `generation.continue` | Android -> JS | 继续最后一条回复 |
| `chat.new` | Android -> JS | 新建当前角色聊天 |
| `chat.reload` | Android -> JS | 从 ST runtime 重新同步当前聊天 |

### 4.4 P0/P1 事件

| 事件 | 方向 | 说明 |
|---|---|---|
| `runtime.ready` | JS -> Android | ST 前端初始化完成，可以接收命令（对应 ST `APP_READY` 事件） |
| `runtime.error` | JS -> Android | runtime 初始化或命令执行失败 |
| `chat.loaded` | JS -> Android | 聊天打开完成，携带 snapshot（对应 ST `chatLoaded` 事件） |
| `chat.changed` | JS -> Android | 当前聊天文件或角色变化（对应 ST `chat_id_changed` 事件） |
| `message.added` | JS -> Android | 用户消息或 AI 消息新增（对应 ST `MESSAGE_SENT` / `MESSAGE_RECEIVED`） |
| `message.updated` | JS -> Android | streaming delta、编辑、swipe 切换等（对应 ST `MESSAGE_UPDATED`） |
| `message.deleted` | JS -> Android | Bridge 删除命令确认删除的消息 id；ST `MESSAGE_DELETED` 本身只可靠触发 snapshot 同步 |
| `message.swiped` | JS -> Android | swipe 切换（对应 ST `MESSAGE_SWIPED`） |
| `generation.started` | JS -> Android | 生成开始（对应 ST `generation_started`） |
| `generation.ended` | JS -> Android | 生成完成（对应 ST `generation_ended`） |
| `generation.stopped` | JS -> Android | 用户停止生成（对应 ST `generation_stopped`） |
| `generation.error` | JS -> Android | 生成失败 |
| `stream.token` | JS -> Android | 单个 streaming token（对应 ST `STREAM_TOKEN_RECEIVED`，需节流） |
| `bridge.result` | JS -> Android | 命令成功结果 |
| `bridge.error` | JS -> Android | 命令失败结果 |

> **ST 原生事件映射说明**：adapter 应监听 ST `eventSource` 的事件，转发为 Bridge 事件。注意 ST 事件名有命名不一致问题（如 `chatLoaded` vs `chat_id_changed` vs `generation_started`），adapter 内部需要统一处理。完整事件枚举见 `SillyTavern/public/scripts/events.js`。
>
> **P1+ 需关注的额外 ST 事件**：`MESSAGE_EDITED`（编辑确认）、`MESSAGE_SWIPED`（swipe 切换）、`MESSAGE_SWIPE_DELETED`（删除单个 swipe）、`MESSAGE_REASONING_EDITED`/`MESSAGE_REASONING_DELETED`（reasoning 编辑）、`CHAT_CREATED`/`CHAT_DELETED`（聊天文件新建/删除）、`STREAM_REASONING_DONE`（reasoning 流式结束）、`TOOL_CALLS_PERFORMED`/`TOOL_CALLS_RENDERED`（工具调用）。

### 4.5 Snapshot 数据

P0 snapshot 尽量小，先服务原生 Chat UI：

```json
{
  "mode": "character",
  "avatarUrl": "Alice.png",
  "characterName": "Alice",
  "chatFile": "2026-05-26.jsonl",
  "isGenerating": false,
  "messages": [
    {
      "id": 0,
      "name": "Alice",
      "mes": "Hello",
      "is_user": false,
      "is_system": false,
      "send_date": "May 26, 2026 12:00pm",
      "swipe_id": 0,
      "swipes": ["Hello"],
      "extra": {}
    }
  ],
  "metadata": {
    "integrity": "uuid"
  }
}
```

原生端不要要求 P0 snapshot 一次覆盖全部 ST 内部状态。`extra`、`chat_metadata`、`swipes` 等字段必须原样保留，未知字段不能丢。

## 5. 代码入口地图

### 5.1 ST Web 前端入口

| 模块 | 文件 | 迁移关注点 |
|---|---|---|
| 主页面结构 | `SillyTavern/public/index.html` | Chat 容器、输入栏、选项菜单、消息模板、聊天文件弹窗、角色/群聊侧栏 |
| 主聊天逻辑 | `SillyTavern/public/script.js` | 初始化（`firstLoadInit`）、角色选择（`selectCharacterById`）、聊天读取/保存（`getChat`/`saveChatConditional`）、消息渲染（`redisplayChat`/`addOneMessage`）、发送（`sendTextareaMessage`/`sendMessageAsUser`）、生成（`Generate`/`StreamingProcessor`/`sendGenerationRequest`/`sendStreamingRequest`）、编辑（`messageEditDone`，注意：非 export）、删除（`deleteMessage`）、swipe（`swipe`/`syncSwipeToMes`）、停止（`stopGeneration`）|
| 单聊辅助 | `SillyTavern/public/scripts/chats.js` | 附件、文件嵌入、媒体、隐藏消息、Data Bank、聊天工具初始化 |
| 群聊逻辑 | `SillyTavern/public/scripts/group-chats.js` | 群聊读取/保存、群成员、群聊生成（`regenerateGroup`）、新建/删除/导入群聊 |
| Chat Completion | `SillyTavern/public/scripts/openai.js` | OpenAI/Claude/OpenRouter 等 Chat Completion 提示词组装和请求 |
| 工具调用 | `SillyTavern/public/scripts/tool-calling.js` | `ToolManager`：工具调用支持检测、调用执行、渲染。`StreamingProcessor` 内部会收集 `toolCalls` 并在流结束后调用 `ToolManager.invokeFunctionTools` |
| 事件总线 | `SillyTavern/public/scripts/events.js` | 103 个事件类型，Bridge adapter 需监听的核心事件见 4.4 节 |
| 世界书 | `SillyTavern/public/scripts/world-info.js` | 世界书扫描和激活逻辑，`Generate()` 内部调用 |
| 推理/reasoning | `SillyTavern/public/scripts/reasoning.js` | reasoning 展示和编辑，影响消息渲染和 `extra.reasoning` 字段 |
| Swipe 选择器 | `SillyTavern/public/scripts/swipe-picker.js` | swipe 选择 UI 和跳转逻辑 |
| Author's Note | `SillyTavern/public/scripts/authors-note.js` | Author's Note 面板和提示词注入 |
| CFG Scale | `SillyTavern/public/scripts/cfg-scale.js` | CFG 面板和参数 |
| Slash 命令 | `SillyTavern/public/scripts/slash-commands.js` + `slash-commands/` | slash command 解析、执行，`processCommands()` 在 `Generate()` 前调用 |
| 常量定义 | `SillyTavern/public/scripts/constants.js` | `SWIPE_STATE`、`SWIPE_DIRECTION`、`SWIPE_SOURCE`、`MEDIA_TYPE` 等枚举 |
| SSE 流处理 | `SillyTavern/public/scripts/sse-stream.js` | Server-Sent Events 流解析 |
| API 注册 | `SillyTavern/src/server-startup.js` | 路由挂载：`/api/chats`、`/api/characters`、`/api/groups`、`/api/backends/*`、`/api/settings`、`/api/worldinfo`、`/api/novelai`、`/api/horde` 等 30+ 路由 |
| 服务主入口 | `SillyTavern/src/server-main.js` | CSRF（`/csrf-token` GET）、ping（`/api/ping` POST）、版本（`/version` GET）、Express 配置 |
| 聊天 API | `SillyTavern/src/endpoints/chats.js` | 聊天文件读写、导入导出、搜索、最近聊天、群聊聊天文件 |
| 角色 API | `SillyTavern/src/endpoints/characters.js` | 角色列表、角色详情、角色聊天列表、角色增删改导入导出、重命名、属性编辑 |
| 群聊 API | `SillyTavern/src/endpoints/groups.js` | 群聊元数据增删改查（all、create、edit、delete） |
| 设置 API | `SillyTavern/src/endpoints/settings.js` | 设置读写、快照管理 |

### 5.2 Android 侧入口

| 模块 | 文件 | 迁移关注点 |
|---|---|---|
| Chat WebView 容器 | `app/src/main/java/io/github/sanitised/st/ui/webview/ChatWebViewScreen.kt` | 当前加载 ST Web UI，可演进为 Runtime WebView 容器 |
| Android Bridge | `app/src/main/java/io/github/sanitised/st/ui/webview/STAndroidBridge.kt` | 扩展 `postChatEvent`、结果回调、原生能力 |
| WebView JS 注入 | `app/src/main/java/io/github/sanitised/st/ui/webview/WebViewNavigator.kt` | 注入 runtime flags 和 Chat runtime adapter |
| Core API | `app/src/main/java/io/github/sanitised/st/api/TavernCoreApi.kt` | 继续封装角色、聊天文件、设置等本地 API |
| 原生角色页 | `app/src/main/java/io/github/sanitised/st/ui/screens/*Character*.kt` | Chat 入口可复用角色选择、聊天列表、头像和标签能力 |
| 导航入口 | `app/src/main/java/io/github/sanitised/st/MainActivity.kt` | Chat tab 从 WebView 页面切到原生 Chat screen；`webViewTargetSaver()` 支持 `rememberSaveable` 跨配置恢复 |
| Chat UI 辅助 | `app/src/main/java/io/github/sanitised/st/chat/ChatUiState.kt` | 消息过滤（`visibleChatMessages`）、日期标签、滚动目标、消息 key、目标命令 key |
| WebView 目标 | `app/src/main/java/io/github/sanitised/st/ui/webview/WebViewNavigator.kt` | `WebViewTarget` sealed class：`CHAT`、`CharacterChat(avatar, chatFile?)`、`GroupChat(groupId, chatId?)` |
| 原型首页 | `app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeHomeScreen.kt` | 对话列表（置顶/全部过滤）、服务状态内联卡片 |
| 原型群聊 | `app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeGroupChatScreen.kt` | 群聊列表、创建群聊（含 activationStrategy/allowSelfResponses）、打开群聊 |
| 原型组件 | `app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeComponents.kt` | 可复用 UI 组件：`PrototypeSearchBar`、`PrototypeAvatar`、`PrototypeChipRow` 等 |
| 原型模型 | `app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeModels.kt` | 数据映射（`ChatSummary.toPrototypeChatItem`、`CharacterSummary.toPrototypeCharacterCard`）、标签过滤 |

## 6. Chat 界面入口迁移

### 6.1 应用初始化

Web 端入口是 `/`，服务端返回 `public/index.html`，页面加载 `public/script.js` 后执行初始化。原生迁移后仍要让 Runtime WebView 完成初始化，但可见 UI 由 Compose 渲染。

手机端至少需要承接：

| 动作 | Web 端逻辑 | 推荐通道 |
|---|---|---|
| 服务 ready 检查 | App 已有 health check | API：`GET /` 或 `/api/ping` |
| 获取 CSRF | `firstLoadInit()` | API 或 Runtime WebView 自行完成 |
| 获取设置 | `getSettings()` | Runtime WebView 必须完成；原生需要时可走 `/api/settings/get` |
| 获取角色 | `getCharacters()` | API 优先，runtime 也会维护自身 `characters` |
| Runtime ready | ST 前端初始化完成 | Bridge：`runtime.ready` |
| 初始 snapshot | 当前聊天状态 | Bridge：`runtime.getSnapshot` |

### 6.2 角色聊天入口

Web 端角色入口主要在右侧角色面板：

| UI 入口 | Web 端函数 | API/状态 |
|---|---|---|
| 打开角色列表 | `select_rm_characters()` / `printCharacters()` | `POST /api/characters/all` |
| 选择角色 | `selectCharacterById(id)` | 需要时 `POST /api/characters/get` |
| 读取当前角色聊天 | `getChat()` | `POST /api/chats/get` |
| 查看角色聊天文件 | `displayPastChats()` / `getPastCharacterChats()` | `POST /api/characters/chats` |

原生端建议拆成：

1. `PrototypeCharacterLibraryScreen`：复用当前原生角色库入口。
2. `PrototypeCharacterProfileScreen`：历史聊天列表可从这里进入 Chat。
3. `NativeChatScreen`：当前角色聊天。
4. `ChatRuntimeWebViewHost`：不可作为主要视觉 UI，只负责保持 ST runtime。

从原生角色页进入 Chat 时，先通过 API 确定 `avatar_url` 和目标 `chatFile`，再通过 Bridge 发送 `chat.openCharacter`，由 ST runtime 选择角色并打开聊天。

> **`openCharacterChat(file_name)` 内部行为**：
> 1. 等待当前保存完成（`waitUntilCondition(() => !isChatSaving)`）
> 2. `clearChat({ clearData: true })` 清空当前聊天 UI 和数据
> 3. 设置 `characters[this_chid].chat = file_name`
> 4. 重置 `chat_metadata = {}`
> 5. `await getChat()` 重新加载聊天
> 6. `createOrEditCharacter()` 保存角色的当前聊天指向
>
> 因此 adapter 调用前必须确保 `this_chid` 已正确设置（通过 `selectCharacterById` 先选中角色）。如果是打开当前角色的不同历史聊天，可以直接调用 `openCharacterChat`；如果是切换角色，必须先 `selectCharacterById` 再等待 `getChat` 完成。

### 6.3 输入栏入口

Web 端输入栏核心入口：

| UI | DOM | Web 端动作 |
|---|---|---|
| 输入框 | `#send_textarea` | 输入文本、slash command、Markdown 快捷输入 |
| 发送 | `#send_but` | `sendTextareaMessage()` |
| 停止生成 | `#mes_stop` | `stopGeneration()` |
| 继续 | `#mes_continue` | `Generate('continue')` |
| 代写用户消息 | `#mes_impersonate` | `Generate('impersonate')` |
| 附件表单 | `#file_form_input` / `#embed_file_input` | 上传文件、图片、媒体，写入消息 `extra` |

原生 UI 对应：

1. 输入框。
2. 发送按钮。
3. 生成中停止按钮。
4. 附件按钮。
5. 更多操作底部弹窗。

P0 发送文本时，原生端不直接修改 JSONL，也不直接调用后端生成 API，而是发送 Bridge 命令：

```json
{
  "kind": "command",
  "name": "chat.send",
  "payload": {
    "text": "用户输入"
  }
}
```

Runtime WebView 内部负责把文本放入 ST 输入状态并触发原版发送逻辑。生成过程通过 `message.added`、`message.updated`、`generation.*` 事件同步给原生 UI。

### 6.4 Chat 操作菜单

| 菜单项 | Web 端动作 | 原生迁移优先级 | 推荐通道 |
|---|---|---|---|
| 新建聊天 | `doNewChat({ deleteCurrentChat? })`，deleteCurrentChat=true 会删除当前聊天后新建 | P0 | Bridge |
| 管理聊天文件 | `displayPastChats()` | P1 | API + 活动聊天 Bridge 同步 |
| 重生成 | `Generate('regenerate')` | P0/P1 | Bridge |
| 继续 | `Generate('continue')` | P1 | Bridge |
| 删除消息 | `openMessageDelete()` | P1 | Bridge |
| 关闭聊天 | `closeCurrentChat()` | P1 | Bridge |
| Author's Note | 打开浮动提示词面板 | P2 | 先用 Bridge 打开/同步原版状态，完整原生页另行拆分 |
| CFG Scale | 打开 CFG 面板 | P2 | 先用 Bridge 打开/同步原版状态，完整原生页另行拆分 |
| 转群聊 | 单聊转群聊 | P3 | Bridge + API |

### 6.5 消息操作入口

| 操作 | Web 端能力 | 迁移优先级 | 推荐通道 |
|---|---|---|---|
| 复制 | 复制消息文本 | P0 | 原生 |
| 编辑 | `messageEditDone(div)` 后保存（注意：此函数非 export，Bridge 需要自行维护等价的核心保存和事件语义） | P1 | Bridge |
| 删除 | `deleteMessage()` | P1 | Bridge |
| 隐藏/取消隐藏 | `hideChatMessageRange()` 切换 `message.is_system` | P1/P2 | Bridge |
| swipe 切换 | `syncSwipeToMes()` | P1/P2 | Bridge |
| swipe 生成 | `swipe(event, direction, { source, repeated, message, forceMesId, forceSwipeId, forceDuration })` | P2 | Bridge |
| 上移/下移 | 调整消息顺序 | P2 | Bridge |
| 朗读 | TTS | P3 | 原生或 Bridge |
| 翻译、生图 | 扩展能力 | P3 | 后续评估 |

## 7. 数据结构承接

### 7.1 Chat 文件结构

SillyTavern 聊天文件是 JSONL：

1. 第一行是 `ChatHeader`。
2. 后续每一行是 `ChatMessage`。

保存时 Web 端会把当前 `chat_metadata` 放入 header：

```json
{
  "chat_metadata": {
    "integrity": "uuid",
    "tainted": true
  },
  "user_name": "unused",
  "character_name": "unused"
}
```

活动聊天保存应优先由 ST runtime 完成，原生端只保存镜像状态。只有聊天文件管理、导入导出或离线兜底场景，原生端才直接组装 `[ChatHeader, ...ChatMessage[]]` 调 `/api/chats/save`。

### 7.2 ChatMessage

核心字段：

```json
{
  "name": "Character",
  "mes": "message text",
  "is_user": false,
  "is_system": false,
  "send_date": "timestamp",
  "gen_started": "timestamp",
  "gen_finished": "timestamp",
  "swipes": ["candidate 1", "candidate 2"],
  "swipe_id": 0,
  "swipe_info": [],
  "extra": {
    "api": "openai",
    "model": "model-name",
    "reasoning": "",
    "token_count": 123,
    "files": [],
    "media": []
  }
}
```

迁移注意：

1. `is_user=true` 表示用户消息。
2. `is_system=true` 表示隐藏或特殊系统消息，不能简单删除。
3. `extra.reasoning`、`extra.tool_invocations`、`extra.media`、`extra.files` 都会影响渲染或提示词组装。
4. `swipes` + `swipe_id` 是候选回复机制，切换 swipe 时 `syncSwipeToMes()` 会将 `swipes[swipe_id]` 同步到 `mes` 字段。
5. 原生端必须保留未知字段，不要在镜像转换时丢字段。
6. `saveReply()` 当前签名为 `saveReply({ type, getMessage, fromStreaming, title, swipes, reasoning, imageUrls, reasoningSignature })`，其中 `reasoning` 和 `reasoningSignature` 是新增字段，支持 thinking/reasoning 模型。
7. `extra.tool_invocations` 是 tool calling 记录数组，`ToolManager` 在流结束后通过 `invokeFunctionTools` 执行工具调用并将结果写入此字段。包含 `tool_invocations` 的系统消息在生成时不会被过滤掉（`canUseTools` 条件判断）。

## 8. 核心流程

### 8.1 打开单角色聊天

推荐流程：

```text
用户在原生角色页选择角色/聊天
  -> NativeChatScreen 创建或复用 Runtime WebView
  -> 等待 runtime.ready
  -> Android 发送 chat.openCharacter { avatarUrl, chatFile? }
  -> JS runtime 调用 selectCharacterById / openCharacterChat / getChat
  -> ST 前端完成 chat、chat_metadata、事件总线状态更新
  -> JS runtime 发送 chat.loaded snapshot
  -> 原生 ChatStore 镜像 snapshot 并渲染
```

手机端必须保证：

1. 当前角色 `avatar_url` 和 `chatFile` 正确。
2. 空聊天的首条消息由 ST runtime 按原版逻辑生成。
3. 打开聊天后的保存和事件由 ST runtime 负责。
4. 原生 UI 按 snapshot 中消息顺序显示。

### 8.2 发送普通消息

推荐流程：

```text
用户在原生输入框点击发送
  -> Native ChatController 发送 bridge command chat.send
  -> JS runtime adapter 将文本写入 #send_textarea 并调用 sendTextareaMessage()
      -> sendTextareaMessage() 检查 swipeState，若非 NONE 则拒绝
      -> Generate('normal', ...)
          -> emit GENERATION_STARTED
          -> processCommands()（处理 slash commands，可能中断生成）
          -> sendMessageAsUser()（将用户消息添加到 chat[]）
          -> saveChatConditional()
          -> 组装提示词、世界书扫描（world-info.js）、Author's Note、CFG、扩展注入
          -> ToolManager.canPerformToolCalls() 检查工具调用
          -> 流式：new StreamingProcessor() + sendStreamingRequest()
          -> 非流式：sendGenerationRequest()
          -> saveReply({type, getMessage, ...})（保存 AI 回复到 chat[]）
          -> saveChatConditional()
          -> 若 auto_swipe 启用且回复被过滤 -> 自动调用 swipe()
          -> emit GENERATION_ENDED
      -> JS runtime adapter 将 message/generation 事件转发给 Android Bridge
  -> 原生 ChatStore 增量更新 UI
```

> **关键注意**：`Generate()` 完整签名为 `Generate(type, { automatic_trigger, force_name2, quiet_prompt, quietToLoud, skipWIAN, force_chid, signal, quietImage, quietName, jsonSchema, depth }, dryRun)`。adapter 调用时只需传 `type` 即可，其余参数使用默认值。
>
> **swipeState 保护**：`sendTextareaMessage()` 在 `swipeState == EDITING` 或 `!= NONE` 时会直接 return，adapter 需要感知此状态以给原生 UI 正确反馈。

P0 可以采用”发送后等待 snapshot 刷新”的保守方式；P1 再做 streaming delta 级别更新。

### 8.3 停止生成

```text
用户点击停止
  -> Android 发送 generation.stop
  -> JS runtime adapter 调用 stopGeneration()（script.js:5518）
      -> if (streamingProcessor) streamingProcessor.onStopStreaming()
         -> this.abortController.abort()（StreamingProcessor 自有的 AbortController）
         -> this.isFinished = true
      -> if (abortController) abortController.abort('Clicked stop button')
         （模块级 abortController，用于非流式请求）
         -> hideStopButton()
      -> eventSource.emit(GENERATION_STOPPED)
  -> adapter 监听 GENERATION_STOPPED 事件
  -> 回传 generation.stopped + 最新 snapshot 给 Android
```

> **注意**：`stopGeneration()` 中有两个不同的 `abortController`：
> 1. `streamingProcessor.abortController` — `StreamingProcessor` 实例自有，用于中止流式生成
> 2. 模块级 `abortController`（`script.js:628`）— 用于中止非流式请求
>
> 两者都会被 abort。adapter 不需要区分，直接调用 `stopGeneration()` 即可。

原生端不要自己取消后端 HTTP 请求，除非后续进入完整原生生成链路。

### 8.4 重生成和继续

| 能力 | Bridge 命令 | Web runtime 逻辑 |
|---|---|---|
| 重生成 | `generation.regenerate` | `Generate('regenerate')` 或群聊 `regenerateGroup()` |
| 继续 | `generation.continue` | `Generate('continue')` |
| 代写 | `generation.impersonate` | `Generate('impersonate')`，P2 后置 |

### 8.5 消息编辑、删除和 swipe

P1 起，编辑和删除也应通过 Bridge 调用 ST 前端函数，确保：

1. `chat` 数组和 DOM/runtime 状态一致。
2. `saveChatConditional()` 仍执行完整保存逻辑。
3. ST 事件语义不丢。
4. `swipes`、`swipe_id`、`swipe_info` 同步更新。

## 9. API 承接表

### 9.1 基础 API

| API | 方法 | 用途 | 推荐阶段 |
|---|---|---|---|
| `/csrf-token` | GET | 获取 CSRF token | P0 |
| `/api/ping` | POST | 服务可达检查 | P0 |
| `/version` | GET | 版本信息（定义在 `server-main.js`） | P0/P1 |
| `/api/settings/get` | POST | 获取全局设置 | P0 |
| `/api/settings/save` | POST | 保存全局设置 | P1 |
| `/api/settings/get-snapshots` | POST | 获取设置快照列表 | P2 |
| `/api/settings/make-snapshot` | POST | 创建设置快照 | P2 |
| `/api/settings/load-snapshot` | POST | 加载设置快照 | P2 |
| `/api/settings/restore-snapshot` | POST | 恢复设置快照 | P2 |

### 9.2 角色 API

| API | 方法 | 用途 | 推荐通道 |
|---|---|---|---|
| `/api/characters/all` | POST | 角色列表 | API |
| `/api/characters/get` | POST | 单个角色完整数据 | API |
| `/api/characters/chats` | POST | 角色聊天文件列表 | API |
| `/api/characters/create` | POST FormData | 新建角色 | API，已由角色原生页承接 |
| `/api/characters/edit` | POST FormData | 保存角色 | API，已由角色原生页承接 |
| `/api/characters/import` | POST FormData | 导入角色 | API，已由角色原生页承接 |
| `/api/characters/rename` | POST | 重命名角色 | API |
| `/api/characters/edit-avatar` | POST | 修改角色头像 | API |
| `/api/characters/edit-attribute` | POST | 修改角色单个属性 | API |
| `/api/characters/merge-attributes` | POST | 批量合并角色属性 | API |
| `/api/characters/duplicate` | POST | 复制角色 | API |
| `/api/characters/delete` | POST | 删除角色 | API |
| `/api/characters/export` | POST | 导出角色 | API，已由角色原生页承接 |

### 9.3 单聊 Chat API

| API | 方法 | 用途 | 推荐通道 |
|---|---|---|---|
| `/api/chats/get` | POST | 读取聊天 JSONL | Runtime 内部使用；原生文件管理可用 |
| `/api/chats/save` | POST | 保存聊天 JSONL | 活动聊天由 Runtime 保存；原生兜底可用 |
| `/api/chats/rename` | POST | 重命名聊天文件 | API，若当前活动聊天被改名需 Bridge 刷新 |
| `/api/chats/delete` | POST | 删除聊天文件 | API，若当前活动聊天被删需 Bridge 关闭/切换 |
| `/api/chats/export` | POST | 导出文本或 JSONL | API |
| `/api/chats/import` | POST FormData | 导入聊天 | API，导入后 Bridge 刷新列表 |
| `/api/chats/search` | POST | 聊天搜索结果 | API |
| `/api/chats/recent` | POST | 最近聊天 | API |

保存时 `chat` 必须是 `[ChatHeader, ...ChatMessage[]]`。但活动聊天 P0/P1 不建议由原生端直接保存。

### 9.4 群聊 API

| API | 方法 | 用途 | 优先级 |
|---|---|---|---|
| `/api/groups/all` | POST | 群聊列表 | P2 |
| `/api/groups/create` | POST | 新群聊 | P2 |
| `/api/groups/edit` | POST | 保存群聊元数据 | P2 |
| `/api/groups/delete` | POST | 删除群聊和关联聊天 | P3 |
| `/api/chats/group/get` | POST | 群聊消息数组 | P2 |
| `/api/chats/group/save` | POST | 保存群聊聊天 | P2 |
| `/api/chats/group/info` | POST | 聊天文件摘要 | P2 |
| `/api/chats/group/delete` | POST | 删除群聊聊天文件 | P3 |
| `/api/chats/group/import` | POST FormData | 导入群聊聊天 | P3 |

群聊生成更依赖 ST 前端状态，原生接管前必须先通过 Bridge 复用 runtime。

### 9.5 生成 API

`main_api` 到后端接口映射：

| `main_api` | 非流式入口 | 说明 |
|---|---|---|
| `openai` | `POST /api/backends/chat-completions/generate` | OpenAI、Claude、OpenRouter、Gemini、Mistral、Cohere 等都经此路由分发 |
| `kobold` | `POST /api/backends/kobold/generate` | Kobold/KoboldCpp 类 |
| `textgenerationwebui` | `POST /api/backends/text-completions/generate` | text-generation-webui、llama.cpp、vLLM、Tabby 等 |
| `novel` | `POST /api/novelai/generate` | NovelAI |
| `koboldhorde` | `POST /api/horde/generate-text` | Horde 不是普通同步生成 |

注意：

1. 这些接口不应作为 Chat 原生迁移 P0 的直接入口。P0/P1 由 Bridge 调用 ST runtime，后续若要做完整原生生成，需要单独设计 Prompt Builder、World Info、Extensions、Streaming Processor 和兼容测试。
2. 所有后端路由在 `server-startup.js` 中挂载，前端通过 `sendGenerationRequest()` 和 `sendStreamingRequest()` 两个统一入口发送请求（`script.js:6027,6058`），内部根据 `main_api` 选择对应路由。
3. 流式生成使用 Server-Sent Events，前端解析在 `scripts/sse-stream.js`。`StreamingProcessor` 类（`script.js:3461`）负责逐 token 更新 DOM、收集 tool calls、处理 reasoning token，并在结束后触发 `saveReply()` 和 `saveChatConditional()`。

## 10. 原生模块建议

| 模块 | 职责 |
|---|---|
| `NativeChatScreen` | 原生 Chat 页面，负责消息列表、输入栏、操作菜单 |
| `ChatStore` | 保存 runtime snapshot 镜像、加载状态、生成状态 |
| `ChatController` | 将用户动作转换为 Bridge 命令 |
| `ChatRuntimeBridge` | 封装 `evaluateJavascript`、命令 ID、结果等待、超时、日志。已合并 `ChatController` 和 `ChatBridgeEventHandler` 职责（见 §15 偏差说明） |
| `ChatUiState` | Chat UI 辅助函数：消息过滤（`visibleChatMessages`）、日期标签、滚动目标索引、消息 item key、`readyTargetCommandKey` 防重复触发 |
| `ChatRuntimeWebViewHost` | 管理 runtime WebView 生命周期、ready 状态、注入 adapter（当前实现为 `NativeChatScreen` 内嵌 1dp `ChatWebViewScreen`） |
| ~~`ChatBridgeEventHandler`~~ | 已合并到 `ChatRuntimeBridge` |
| `ChatApiService` | 聊天文件列表、导入导出、搜索、最近聊天等 API |
| `MessageRenderer` | Markdown、reasoning、media、files、swipe、tool calls 展示 |
| `AttachmentService` | Android 文件选择、上传、预览、删除（当前内联在 `NativeChatScreen` 中，使用 `TavernCoreClient.uploadFile()` + `PendingAttachment`） |

## 11. 迁移优先级

### P0：原生 Chat 镜像可用 — ✅ 全部完成

1. ✅ 保留 Runtime WebView 加载 ST 原版前端。
2. ✅ 注入 Chat runtime adapter。
3. ✅ 新增 Bridge 事件：`runtime.ready`、`chat.loaded`、`message.added`、`message.updated`、`generation.started`、`generation.ended`、`generation.error`。
4. ✅ 原生 `NativeChatScreen` 展示 snapshot 消息。
5. ✅ 从角色页进入 Chat 时通过 `chat.openCharacter` 打开指定角色/聊天。
6. ✅ 原生输入栏通过 `chat.send` 发送普通文本。
7. ✅ 原生停止按钮通过 `generation.stop` 停止生成。
8. ✅ 退出重进后仍能恢复当前聊天（`webViewTargetSaver` + `rememberSaveable`）。

### P1：常规聊天体验 — 🔶 基本可用

1. ✅ Streaming delta 增量更新（`stream.token` 80ms 节流回传，已实现）。
2. ✅ 新建聊天（`ChatRuntimeBridge.newChat()` Bridge 已通）、聊天文件列表和切换历史聊天（`PrototypePastChatsScreen` 已实现，含搜索、重命名、导出、删除操作）。
3. ✅ 重生成、继续生成（`ChatRuntimeBridge.regenerate()` / `continueGeneration()` 已实现）。
4. ✅ 消息复制、编辑、删除（长按消息弹出操作 sheet，内联编辑模式，通过 Bridge `message.edit` / `message.delete` 命令同步 ST runtime；删除优先走 ST `deleteMessage()`）。
5. 🔶 保存 integrity 错误处理（`runtime.save` 可手动触发保存；`save.error` 只能捕获 wrapper 级失败，ST 内部保存失败仍为 best-effort）。
6. 🔶 基础 swipe 展示与切换（`ChatRuntimeBridge.swipePrevious()` / `swipeNext()` Bridge 已通，NativeChatScreen 已有 swipe 按钮；需真机验证长链路）。
7. ✅ Bridge 超时追踪（`pendingCommands` + 分级超时 15/30/60s）、runtime 崩溃恢复（`onRenderProcessGone` + `RENDER_PROCESS_GONE` 错误页 + `loadUrl` 自动恢复）。

### P2：接近 SillyTavern 核心体验 — 🔶 核心能力已落地

1. 🔶 群聊打开、发送、停止、历史切换。
   - ✅ 群聊列表和创建（`PrototypeGroupChatScreen` + `TavernCoreClient.createGroup` 含 `activationStrategy`/`allowSelfResponses`/`generationMode`）
   - ✅ 打开群聊导航（`WebViewTarget.GroupChat` + `ChatRuntimeBridge.openGroup()`）
   - ✅ 群聊 NativeChatScreen 中消息按 `message.name` 区分成员；发送/停止复用同一 Bridge 通道；regenerate 分流 `regenerateGroup()`
   - 🔶 群聊长链路仍需真机验证
2. ✅ 附件上传和展示，保留 `extra.files`、`extra.media`。
   - ✅ Android 文件/图片选择器（`ActivityResultContracts.GetContent`）、`TavernCoreClient.uploadFile()` HTTP 上传、`PendingAttachmentStrip` 预览条、`MessageBubble` 内嵌 `AsyncImage`（Coil）和 `MessageFileCard` 渲染
3. ✅ Author's Note、CFG、世界书基础接入。
   - ✅ Author's Note 已通过 `authorsNote.get/set`、`AuthorsNoteDialog`、snapshot metadata 同步落地
   - ✅ CFG 已通过 `cfg.get/set`、`CfgScaleDialog`（Slider + 正/负提示词）、snapshot metadata 同步落地
   - ✅ 世界书已通过 `worldInfo.get`、`WorldInfoSheet`（只读浏览 + 当前绑定高亮）、snapshot metadata 同步落地
4. 🔶 Slash commands 的结果和错误展示。
   - ✅ slash command 文本可通过 `chat.send` 进入 ST 原生处理链路，正常结果按消息同步
   - ✅ ST toastr 通知（命令错误/警告/成功）通过 adapter 包裹 `toastr.error/warning/info/success` 转发 `runtime.toast`，原生端 `RuntimeToastHost` 按类型着色展示
5. ✅ 消息隐藏/取消隐藏（通过 `hideChatMessageRange()` 切换 `is_system`，原生端用 `isSystem` 视觉标识）。
6. ✅ 文件嵌入到提示词上下文（无需额外开发：ST `appendFileContent()` 在 `Generate()` 期间自动从 `extra.files` 读取文件内容拼入提示词，整个过程在隐藏 WebView 内完成；v0.9 附件上传已正确写入 `extra.files`）。

### P3：高级能力 — 🔶 阶段 A+B+C 已落地

1. 🔶 扩展系统兼容策略（`extensions.list` 命令读取已加载扩展名到 `ChatStore.loadedExtensions`；toastr 通道已覆盖扩展提示；扩展 UI 面板原生化未做）。
2. ✅ Quick Replies（`quickReply.list/execute` + `QuickReplyStrip` 输入栏上方水平滚动 chip，runtime ready / chat changed 时刷新）。
3. ⬜ TTS、翻译、生图（后续专项处理）。
4. ⛔ logprobs（**阻塞**：ST `logprobs.js` 的 `state` 为模块私有 const，未 export/不在 getContext/window，干净 bridge 读不到；需上游改动，保持 submodule 原封不动故不做）。
5. ✅ checkpoint、branch（`chat.createCheckpoint`/`createBranch`/`openCheckpoint` → `createNewBookmark`/`branchChat`；`BubbleMeta` 标识、`MessageActionSheet` 新增项、`CheckpointDialog`、`BranchListSheet`）。
6. ✅ tool calls 渲染（系统消息 `extra.tool_invocations` → `ToolCallGroup`/`ToolCallCard`，参数折叠 + 结果展示 + 执行中状态）。
7. ✅ itemized prompts（`itemizedPrompt.get` → `itemizedParams()`；`MessageActionSheet` "提示词分析"入口 + `ItemizedPromptSheet` token 构成进度条 + 元信息）。
8. ✅ Data Bank（`dataBank.list` → `getDataBankAttachmentsForSource`；ChatHeader "数据银行"菜单 + `DataBankSheet` 全局/角色/聊天三 Tab 只读浏览，点击 Intent 打开。**改为聊天内 sheet 入口**，因为附件清单只在前端运行时、独立 Tools 屏幕无 WebView）。
9. ✅ Reasoning/Thinking 展示（`extra.reasoning` → `ReasoningSection` 可折叠区，`STREAM_REASONING_DONE` 流式刷新）。

### P4：可选的完整原生生成链路

只有在 Bridge 方案稳定、测试覆盖充分后，再评估是否把部分生成链路从 ST Web 前端抽到原生端。

这需要单独迁移或重建：

1. Prompt Builder。
2. World Info 扫描。
3. Author's Note、CFG、Instruct、Chat Completion 模板。
4. Extension interceptors。
5. Streaming Processor。
6. Swipe 和 reasoning/tool calls 保存。
7. 与原版 Web 端的契约测试。

## 12. 关键风险

### 12.1 Bridge 不是魔法 API

Bridge 只能帮原生 UI 调用 Web runtime 和接收事件。真正复杂的是运行时状态同步：当前角色、当前聊天、生成中状态、streaming、保存、扩展事件都必须有清晰边界。

### 12.2 WebView 生命周期

如果 Runtime WebView 被销毁、后台暂停或页面 reload，原生 UI 需要能：

1. 标记 runtime 不可用。
2. 阻止发送新命令或进入排队。
3. 自动重新初始化。
4. 重新打开当前角色/聊天。
5. 从 snapshot 恢复 UI。

不要依赖一个永远不会重载的 WebView。

### 12.3 活动聊天不能双写

不要让原生端一边直接 `/api/chats/save`，Web runtime 另一边也 `saveChatConditional()`。这会造成 integrity 冲突、消息丢失或覆盖。

### 12.4 事件粒度和性能

Streaming 每个 token 都通过 `@JavascriptInterface` 回调可能造成频繁跨边界调用。P0 可用 snapshot 刷新，P1 需要做节流：

1. 按 50-100ms 合并 delta。
2. 大消息只传增量或最后一条消息。
3. 长列表只传窗口内必要信息。

### 12.5 扩展和 slash commands

`Generate()` 在真正发送前会执行 `processCommands()`，扩展也可以阻止或修改生成。只要复用 ST runtime，这些能力能最大程度保留；但原生 UI 必须能展示命令错误、扩展提示或生成被拦截的结果。

### 12.6 Tool Calling 和 Reasoning

当前 ST 前端支持 tool calling（`ToolManager` + `StreamingProcessor.toolCalls`）和 reasoning/thinking（`extra.reasoning`、`reasoningSignature`、`STREAM_REASONING_DONE` 事件）。这两个能力对原生 UI 有以下影响：

1. **Tool calling**：生成可能不直接产生回复文本，而是先执行工具调用，然后递归调用 `Generate(type, {..., depth: depth+1})` 继续生成。原生 UI 需要能展示"工具调用中"状态，以及工具调用结果（`TOOL_CALLS_PERFORMED`/`TOOL_CALLS_RENDERED` 事件）。
2. **Reasoning**：streaming 期间可能先收到 reasoning tokens（不可见于最终消息），再收到实际回复。原生 UI 需要决定是否展示 reasoning 折叠区域。`extra.reasoning` 字段和 `extra.reasoningSignature` 需要在 snapshot 中传递。
3. **递归生成深度**：`Generate()` 有 `depth` 参数和 `ToolManager.RECURSE_LIMIT` 限制。Bridge adapter 不需要感知递归，但需要正确传递中间状态事件。

### 12.7 SWIPE_STATE 和并发保护

`script.js` 维护全局 `swipeState`（枚举：`NONE`、`SWIPING`、`EDITING`），多个操作会检查此状态：

- `sendTextareaMessage()` 在 `swipeState != NONE` 时拒绝发送
- swipe 操作在 `SWIPING` 时互斥
- 编辑中的 swipe 需要先确认

Bridge adapter 需要在命令失败时向原生端返回明确的错误原因（如"swipe 编辑中，无法发送"），而不是静默丢弃。

## 13. 验收清单

### P0 验收

1. 原生 Chat 页面能等待 Runtime WebView ready。
2. 从角色列表或角色详情进入 Chat，能打开正确角色。
3. 指定历史聊天时，能打开对应 chat file。
4. 原生消息列表显示 ST runtime 的实际消息。
5. 空聊天能显示角色首条消息。
6. 原生输入栏能发送一条用户消息。
7. 能收到 AI 回复。
8. 生成中能停止。
9. 聊天由 ST runtime 保存，重启 App 后能恢复。
10. Runtime WebView reload 后能重新同步 snapshot。

### P1 验收

1. Streaming 期间原生 UI 能持续更新最后一条 AI 消息。
2. 可以新建聊天。
3. 可以切换历史聊天。
4. 可以重生成最后一条回复。
5. 可以继续最后一条回复。
6. 可以编辑消息并保存。
7. 可以删除消息并保存。
8. 保存入口可重试；保存失败提示为 best-effort，不承诺捕获 ST 内部吞掉的异常。
9. Bridge 命令超时会提示，并允许重试。

### P2 验收

1. 可以打开群聊。
2. 可以发送群聊消息并触发群成员回复。
3. 可以保存和切换群聊历史。
4. 可以上传并展示图片或文件附件。（✅ 文件/图片选择器 + HTTP 上传 + 预览条 + 消息内渲染）
5. Author's Note、CFG、世界书均有基础接入。（✅ 对话框/Sheet + Bridge 命令 + snapshot 同步）
6. Slash command 不会破坏普通发送。

## 14. 推荐迁移路线

推荐路线（带当前进度）：

1. ✅ 先做 Runtime WebView 管理和 Bridge adapter，不改可见 Chat UI。
2. ✅ 再做原生 Chat 只读镜像，确保 snapshot 和事件能稳定同步。
3. ✅ 接管原生输入栏：发送、停止、生成状态。
4. ✅ 补重生成、继续、新建、历史聊天切换。（Bridge 与 UI 已落地；群聊历史通过内联 sheet 切换）
5. ✅ 补编辑、删除、swipe、附件。（编辑/删除/swipe/隐藏/附件上传和展示均已落地）
6. ✅ 补群聊、Author's Note、世界书和扩展相关能力。（群聊打开/发送/历史切换、Author's Note、CFG、世界书、toastr 原生提示均已落地；扩展只读 UI 待后续）
7. ⬜ 最后再评估是否抽离部分生成链路到原生端。

阶段性目标应该是”原生 UI 体验明显改善，但聊天语义仍和原版 ST 一致”。在没有契约测试前，不建议重写提示词组装和生成请求。

> **v0.12 进度说明**：步骤 1-6 已完成（含 P3 阶段 A+B+C）。剩余主要是 logprobs（ST 未导出，阻塞）、TTS/翻译/生图（后续专项）、扩展只读 UI，以及真机长链路验证（步骤 7 的生成链路抽离仍不建议在契约测试前做）。

## 15. 实现进度

日期：2026-06-03（v0.15 Native ChatSession Phase 1 安全护栏 + v0.14 Text Completion 原生生成首批接线 + v0.13 原生生成承接混合过渡 + v0.12 P3 阶段 C + v0.11 P3 阶段 B + v0.10 P3 阶段 A + v0.9 P2 附件/CFG/世界书收口 + v0.8 文档收口 + v0.7 阶段 C P2 接续 + adapter 契约修正）
状态：**P0 基础框架落地 + P1 基本可用 + P2 全部落地 + P3 阶段 A+B+C 已落地 + 原生生成（Chat Completion + 首批 Text Completion）混合过渡已落地 + Native ChatSession Phase 1 已补安全护栏（实验开关，WebView 保留兼容兜底）**

### v0.15 Native ChatSession Phase 1：单聊事实源与安全护栏（2026-06-03）

本轮沿 `docs/native-chat-webview-exit-plan.md` v0.5 口径推进，重点不是直接删除 WebView，而是先避免 split-brain：

- **原生生成成功路径**：`NativeChatEngine` 成功保存 JSONL 后不再主动 `bridge.reloadChat()`；UI 由 native store + JSONL 保持一致。
- **Bridge 写前对齐**：fallback send、regenerate、continue 仍走 Bridge 时，先 `reloadChat()` 再派发写命令，避免隐藏 WebView 用旧快照覆盖磁盘。
- **安全保存**：`NativeChatRepository.save` 统一执行写前 integrity 校验、退避备份、刷新 header `chat_metadata.integrity`、同一 avatar/chatFile 写串行化；`NativeChatEngine` 也复用该 Repository 保存，不再绕过护栏。
- **单聊消息操作原生化**：`NativeChatRuntime` + `NativeChatJsonOps` 覆盖编辑、删除、隐藏/取消隐藏、移动、reasoning、附件/媒体 metadata、swipe previous/next/create/delete；`NativeChatScreen` 单聊优先调用 native runtime。
- **隐藏 WebView 启动收敛**：原生生成开启时，进入 Chat 或打开角色聊天不再自动激活隐藏 WebView；群聊和未迁移能力仍保留兼容路径。
- **测试修正**：Phase 1 契约测试已从源码 grep 改为行为/产物断言：`NativeChatEnginePhase1ContractTest`、`NativeChatRepositorySafetyTest`、`NativeChatUiRoutingTest`、`NativeChatRuntimeTest`、`NativeChatJsonOpsTest`。

限制：checkpoint / branch 已有原生实现，但按 v0.5 不纳入 Phase 1 验收；完整 prompt 语义、群聊统一 runtime、扩展能力仍后置。

### v0.14 Text Completion 原生生成首批接线：E1+E2+E3（2026-06-02）

Phase E 已把「原生生成（实验）」从 Chat Completion 扩到首批 Text Completion 后端：`ooba` / `koboldcpp` / `llamacpp` / `ollama`。核心策略仍是保守 gating：单聊、无附件、支持的 `api_type`、简单 context story string 才走原生；复杂 Handlebars、未覆盖后端、群聊、附件、regenerate/continue 继续 bridge 兜底。

- **API 层（E1）**：`TavernCoreApi` 新增 `generateTextCompletion` / `generateTextCompletionStream`，走 `/api/backends/text-completions/generate`；SSE delta 解析抽到 `GenerationDeltaParser`，CC/TC 共用，覆盖 `choices[0].delta.content`、`choices[0].text`、Anthropic `delta.text`、Google parts、llama.cpp `{content}`。`resolveTextGenServer(settings, apiType)` 成为 TC server 唯一解析来源：固定 hosted server 仅 `featherless/mancer/togetherai/infermaticai/dreamgen/openrouter`，其余读 `textgenerationwebui_settings.server_urls[type]`，并把 `localhost` 归一到 `127.0.0.1`；状态检查不再给 `ollama/koboldcpp/llamacpp` 自造默认 URL。
- **提示词组装（E2）**：新增纯 Kotlin `InstructTemplate` / `StopStringBuilder` / `TextPromptBuilder`。支持 `names_behavior`、`system_same_as_user`、input/output/system sequence、first/last sequence、suffix、wrap、`{{name}}/{{char}}/{{user}}` macro；context `story_string` 只做简单占位替换，遇到 `{{#`、`{{/`、`{{else}}`、helper 调用、`instruct.enabled=false`、story prefix/suffix、作者注等未实现语义直接 `Unsupported` 让引擎兜底。TC prompt 会按粗略 token budget 裁掉旧历史，并向 payload 写入 `truncation_length`（Ollama/llama.cpp 另带 `num_ctx`）。
- **引擎接线（E3）**：`NativeChatEngine` 增加 `engineMode(settings)`：`openai` 走 CC，支持的 `textgenerationwebui` 走 TC，其他走 fallback。TC 分支复用世界书扫描/persona 上下文，调用 `TextPromptBuilder` → `generateTextCompletionStream` → 同样的占位消息、~60ms 节流、JSONL 落盘、`bridge.reloadChat()` 对齐；落盘 `extra.api = "textgenerationwebui"`，并记录 `type`。
- **原生打开角色聊天补线**：`NativeChatLoader` 在「原生生成（实验）」打开时先通过 API 读取角色卡 + JSONL，构造 `ChatSnapshot` 并填充 `ChatStore`，不再必须等隐藏 WebView 的 `chat.openCharacter` / `getChat` 快照才能显示目标聊天；隐藏 runtime ready 后仍打开同一目标，作为编辑、swipe、quick reply、checkpoint 等未迁移动作的兜底对齐。

#### 单元测试

新增 `GenerationDeltaParserTest`、`TextGenerationServerResolverTest`、`InstructTemplateTest`、`StopStringBuilderTest`、`TextPromptBuilderTest`、`NativeEngineModeTest`、`NativeChatLoaderTest`，覆盖 E1/E2/E3 的纯逻辑与 API 契约。

#### 仍后置

复杂 Handlebars 条件/循环与宏全集、instruct disabled 非模板格式、story prefix/suffix、作者注插入位置、instruct `activation_regex`/角色卡覆盖、`sampler_order/sampler_priority`、`<START>` 示例块精细解析、reasoning/thinking、tool calling、正则、扩展注入、`vllm/tabby/mancer/openrouter(text)` 等后端继续兜底。

### v0.13 原生生成承接（混合过渡）：引擎接缝 + 原生 Chat Completion + 流式（2026-06-02）

bridge 架构反复出现「运行时内存状态 vs 原生/磁盘状态」不同步（已配置 API 但 `online_status` 未连接、native 选的模型没回传运行时导致用错模型/限额）。决策：**逐步把生成搬到原生、直接调后端 `/api/backends/chat-completions/generate`，但保留隐藏 WebView 作为兜底，原生每达标一块就切原生、未达标的能力继续兜底。** 隐藏 WebView 运行时仍常驻，作为状态镜像源 + 复杂语义兜底。

#### 过渡期即时修复（让 WebView 兜底可用）

- **运行时设置同步**：adapter 新增 `runtime.reloadSettings`（`import('./script.js').getSettings()` 把磁盘设置重载进运行中前端内存）。native 设置页保存 API/模型后置「脏」标志（`runtimeSettingsDirty`），进聊天时 `NativeChatScreen` 触发 `bridge.reloadSettings()`。修复「模型选了但聊天用的还是旧/默认模型 → 达到限额」。
- **运行时连接**：adapter 新增 `runtime.connect`（按 `mainApi` 点对应连接按钮 `#api_button_openai` / `_textgenerationwebui` / `_novel` / `#api_button`），支持 `auto`（静默，失败不刷屏）与显式两种。`runtime.ready` + 进聊天时自动静默连接。`getGenerationContext` 改为 `ensureGenerationContext`：发送/重写/继续前若 `no_connection` 先自动连接再继续（≤12s，压在 `chat.send` 15s 超时内）。
- **WebView 运行时宿主提升**（修每次进 chat 转圈几秒）：宿主从 NavHost 内的 `NativeChatScreen` 提升到 `MainActivity` 的 Scaffold `Box`、NavHost 之外，1dp 隐藏常驻（`chatRuntimeActivated` 首次进聊天后保活），跨 tab 不再销毁重载。`ChatWebViewScreen` 加 `enableBackHandler`（常驻宿主关闭以免全局拦截返回）+ `onRuntimeError`（页面级错误回传 store）。去掉切 tab 时的 `chatStore.reset()`，防串台改由 `targetMatched` 门控。

#### 可切换生成引擎接缝（Phase A）

- `chat/engine/ChatEngine.kt`（接口：send/stop/regenerate/continue）+ `BridgeChatEngine`（包装 `ChatRuntimeBridge`，默认/兜底）+ `NativeChatEngine`。
- `NativeChatScreen` 的发送/停止/重写/继续走 `engine`；其余高级动作（编辑/删除/swipe/checkpoint/quickReply/世界书 sheet 等）仍直连 bridge。
- 「原生生成（实验）」开关：`UpdateManager` 持久化（`PREF_NATIVE_GENERATION`）→ `MainViewModel` → 设置页开关 → `MainActivity` 据此注入 `NativeChatEngine` 或 `BridgeChatEngine`。默认关。

#### 原生 Chat Completion（Phase B + C + D）

- **TavernCoreClient**：实现 `getChatJsonl`/`saveChatJsonl`（无损读写真实 JSONL，沿用 yaml 解析 + `jsonValue` 序列化，保留未知字段）；`generateChatCompletion`（非流式，后端已把各 source 归一化为 `choices[0].message.content`）；`generateChatCompletionStream`（SSE，`callbackFlow` + 独立长超时 client `generationHttpClient`：无 callTimeout、120s read；容错 delta 解析兼容 OpenAI/Claude/Google 三种 SSE 形态）。
- **PromptBuilder**（纯 Kotlin，可测）：system = 世界书(前) → 角色 systemPrompt/description/personality/scenario → persona 描述（`power_user.persona_description`）→ message examples → 世界书(后)；历史按 `{{char}}`/`{{user}}` macro 替换映射 user/assistant；按 `openai_max_context - max_tokens` 估算裁剪最旧历史；作者注按深度（默认 4）作为 system 轮插入。复用 `ApiConnectionState` 的 provider/model 映射解析模型与 source。
- **WorldInfoScanner**（纯 Kotlin，可测）：扫描角色内嵌世界书（`character.world`）+ 聊天绑定世界书（`chat_metadata.world_info`）对最近 3 条消息；`constant` 常驻 / 关键字命中激活，`selective` 需次关键字；按 `order` 排序、按 `position`(0/1) 拆「角色定义前/后」。
- **NativeChatEngine**：发送 = 乐观插入用户消息 + 空 assistant 占位 → 组装 payload → SSE 流式逐字更新占位（~60ms 节流）→ 落盘 JSONL（读真实文件追加，无损）→ `bridge.reloadChat()` 让运行时按磁盘对齐（单一写者）。停止 = `stopRequested` + `takeWhile` 让流在下一 token 干净结束（`awaitClose` 取消 OkHttp Call），**保留已生成部分**。流式无 token 时回退非流式。

#### 能力边界（审计收紧，避免打开开关踩真实回归）

原生路径**仅在**：`mode != group` + `main_api == "openai"`（Chat Completion source）+ 无待发附件 时启用；否则一律 `bridge.sendMessage` 兜底。具体：
- **群聊 / 非 CC 后端（Ooba/Kobold/Novel 等，Phase E 再做）/ 待发附件** → bridge 兜底。
- **regenerate** → bridge（原生重写会丢 `swipes/swipe_id` 历史，待 swipe 语义对齐前不接管）。
- **continue（继续生成）** → bridge。
- 生成调用（流式 + 非流式兜底）统一走 `generationHttpClient`，不再吃共享 client 的 15s `callTimeout`。

#### 单元测试

`PromptBuilderTest`（4：模型/source/macro 组装、上下文裁剪、persona+示例、世界书+作者注深度）+ `WorldInfoScannerTest`（4：关键字/位置、constant/disabled、selective 次关键字、order 排序）。能力路由（gating/附件/regenerate 兜底）是运行期行为，需真机验证。

#### 仍后置

- 流式 reasoning/thinking（`extra.reasoning`）、continue/swipe 原生实现、Text Completion（Phase E）+ instruct/context 模板、世界书递归/概率/分组/@depth、对话示例 `<START>` 块精细解析、tool calling、正则、扩展注入 —— 这些场景继续走 WebView 兜底。
- 非流式兜底路径的同步 `execute()` 中途不可取消（仅未知 SSE 格式时触发，120s read 兜底）。

#### 文件变更汇总

| 文件 | 变更 |
|---|---|
| `assets/chat_runtime_adapter.js` | `runtime.connect`（auto/显式）、`runtime.reloadSettings`、`ensureGenerationContext`（发送前自动连接） |
| `chat/engine/ChatEngine.kt`（新） | 生成引擎接口 |
| `chat/engine/BridgeChatEngine.kt`（新） | WebView bridge 引擎（默认/兜底） |
| `chat/engine/NativeChatEngine.kt`（新） | 原生流式生成 + 能力边界 + JSONL 落盘 + 运行时对齐 |
| `chat/prompt/PromptBuilder.kt`（新） | 提示词组装（角色卡/persona/示例/世界书/作者注/裁剪） |
| `chat/prompt/WorldInfoScanner.kt`（新） | 世界书关键字激活与位置拆分 |
| `api/TavernCoreApi.kt` | `getChatJsonl`/`saveChatJsonl`/`generateChatCompletion`/`generateChatCompletionStream` + `generationHttpClient` + `postJsonForGeneration` |
| `chat/ChatRuntimeBridge.kt` | `connect(auto)`/`reloadSettings`、`runtime.ready` 自动连接 |
| `chat/NativeChatScreen.kt` | 走 `engine`、`settingsDirty` 重载、移除内置 WebView 宿主 |
| `ui/webview/ChatWebViewScreen.kt` | `enableBackHandler`/`onRuntimeError`，作为常驻隐藏宿主 |
| `MainActivity.kt` | 常驻运行时宿主、引擎注入（按开关）、`runtimeSettingsDirty`、配置页 `onSettingsChanged` |
| `UpdateManager.kt`/`MainViewModel.kt`/`PrototypeSystemScreens.kt` | 「原生生成（实验）」开关持久化 + UI |
| `test/.../PromptBuilderTest.kt`、`WorldInfoScannerTest.kt`（新） | 提示词组装 + 世界书扫描单测 |

### v0.12 P3 阶段 C：Itemized Prompts + Data Bank（logprobs 阻塞）（2026-05-31）

完成 P3 阶段 C 的可行项。调研发现两处与原计划假设的偏差，已按用户决策处理。

#### 调研偏差与决策

- **C1 logprobs ⛔ 阻塞**：`logprobs.js` 的 `state`（含 `messageLogprobs` Map）是模块私有 `const`，未 export、不在 `getContext()` 或 `window`。唯一读取路径是改 ST 源码加 export，违反 CLAUDE.md「submodule 原封不动打包」。决定跳过，仅文档记录。
- **C3 Data Bank**：原计划「Tools 独立屏幕 + API 管理」不可行——`/api/files/*` 只有 upload/delete/verify 无 list，附件清单存前端运行时（`extension_settings.attachments` / `chat_metadata.attachments` / `character_attachments`），只能经 bridge 读。改为**聊天内 sheet 入口**（ChatHeader 菜单），复用现有 bridge。

#### C2：Itemized Prompts ✅

- **adapter**：新增 `itemizedPrompt.get`，`import('./scripts/itemized-prompts.js')` 读 live `itemizedPrompts` 数组（script.js 与该模块共享同一 ES binding），`findItemizedPromptSet()` 定位 + `itemizedParams()` 计算，归一化为 `{available,total,components[],presetName,modelUsed,apiUsed,tokenizer}`（OAI / 非 OAI 字段差异在 adapter 内抹平）。
- **ChatBridgeModels**：`ItemizedPrompt` + `ItemizedPromptComponent` 数据类与解析。
- **ChatStore**：`itemizedPrompt`/`itemizedPromptLoading`/`itemizedPromptError` + `beginItemizedPromptLoad`/`applyItemizedPrompt`/`recordItemizedPromptError`/`clearItemizedPrompt`。
- **ChatRuntimeBridge**：`loadItemizedPrompt(messageId)`，结果按 `pending.name` 路由；`available=false` 时提示「仅本会话生成过的消息可用」。
- **NativeChatScreen**：`MessageActionSheet` 新增"提示词分析"（仅 AI 消息）；`ItemizedPromptSheet`（总 token 数 + 各组件 token 进度条 + 预设/模型/API/分词器元信息）。

#### C3：Data Bank ✅

- **adapter**：新增 `dataBank.list`，`import('./scripts/chats.js').getDataBankAttachmentsForSource('global'|'character'|'chat')`，归一化 `{url,name,size,created}`。
- **ChatBridgeModels**：`DataBankAttachment` + `DataBankAttachments`（三源）数据类与解析。
- **ChatStore**：`dataBank`/`dataBankLoading` + `beginDataBankLoad`/`applyDataBank`/`clearDataBank`。
- **ChatRuntimeBridge**：`loadDataBank()`，结果/错误按命令名路由。
- **NativeChatScreen**：ChatHeader DropdownMenu 新增"数据银行"；`DataBankSheet`（全局/角色/当前聊天三 Tab + 计数 chip，文件列表只读，点击 `ACTION_VIEW` Intent 打开）。

#### 单元测试

`ChatBridgeModelsTest` 新增 2 项：`ItemizedPrompt` 组件/元信息解析（含空名过滤）、`DataBankAttachments` 三源解析。当前共 15 项全绿。

> **注意（JVM 签名冲突）**：`var itemizedPrompt` / `var dataBank` 的 Compose 自动 setter（`setItemizedPrompt`/`setDataBank`）会与同名方法冲突，故 store 方法命名为 `applyItemizedPrompt`/`applyDataBank`/`recordItemizedPromptError`。

#### 文件变更汇总

| 文件 | 变更 |
|---|---|
| `assets/chat_runtime_adapter.js` | `itemizedPrompt.get`、`dataBank.list` 命令与 handler |
| `chat/ChatBridgeModels.kt` | `ItemizedPrompt`/`ItemizedPromptComponent`、`DataBankAttachment`/`DataBankAttachments` |
| `chat/ChatStore.kt` | itemizedPrompt/dataBank 状态 + loading/error + apply/record/clear |
| `chat/ChatRuntimeBridge.kt` | `loadItemizedPrompt`/`loadDataBank`、结果与错误按命令名路由 |
| `chat/NativeChatScreen.kt` | `ItemizedPromptSheet`/`ItemizedComponentRow`、`DataBankSheet`、ActionSheet"提示词分析"、ChatHeader"数据银行" |
| `test/.../ChatBridgeModelsTest.kt` | ItemizedPrompt/DataBank 解析测试 |

### v0.11 P3 阶段 B：Quick Replies + Checkpoint/Branch + 扩展列表（2026-05-31）

完成 P3 阶段 B 三项功能，设计严格对照 `docs/P3-ui-design-spec.md` 与设计稿 JSX（`chat.jsx` QuickReplyStrip / `sheets.jsx` MessageActionSheet/CheckpointDialog/BranchListSheet）。

#### B1：Quick Replies ✅

- **adapter**：新增 `quickReply.list`（读 `window.quickReplyApi.settings` 的 config/chatConfig/charConfig setList，过滤 `isVisible`/`!isHidden`，匹配 ST `ButtonUi.renderBar` 逻辑）和 `quickReply.execute`（优先 `api.executeQuickReply(setName,label)`，回退 `executeQuickReplyByName`）。扩展未加载时返回空列表而非报错。
- **ChatStore**：新增 `QuickReplyItem(setName,label,icon,message)` + `quickReplies` 列表 + `setQuickReplies()`。
- **ChatRuntimeBridge**：`loadQuickReplies()`/`executeQuickReply()`；`CommandResult` 按 `pending.name == "quickReply.list"` 路由到 `applyQuickReplyResult()`（`completePendingCommand` 改为返回 `PendingCommand?`）；在 `RuntimeReady` 和 `ChatChanged` 时刷新（避免每次 snapshot 都刷）。
- **NativeChatScreen**：新增 `QuickReplyStrip`，位于 `ChatQuickStrip` 下方、输入栏上方，水平滚动 `AssistChip`，空列表隐藏，生成中/未就绪置灰。

#### B2：Checkpoint / Branch ✅

- **adapter**：新增 `chat.createCheckpoint`（`import('./scripts/bookmarks.js').createNewBookmark(mesId,{forceName})`，写 `extra.bookmark_link`，不导航）、`chat.createBranch`（`branchChat(mesId)`，写 `extra.branches` 并自动导航到新分支）、`chat.openCheckpoint`（按文件名 `openCharacterChat`/`openGroupChat`）。
- **ChatBridgeModels**：新增 `ChatMessage.bookmarkLink`（`extra.bookmark_link`）和 `branches`（`extra.branches`）扩展属性。
- **ChatRuntimeBridge**：`createCheckpoint()`/`createBranch()`/`openCheckpoint()`。
- **NativeChatScreen**：AI 气泡内 `BubbleMeta`（🔖 书签 + 分支数量 badge）；`MessageActionSheet` 新增"创建存档点"/"创建分支"/"查看分支（数量）"；`CheckpointDialog`（命名输入，留空自动命名）；`BranchListSheet`（列出 bookmark_link + branches，点击 `openCheckpoint` 打开）。

#### B3：扩展系统兼容（最小化通道）🔶

- **adapter**：新增 `extensions.list`（`import('./scripts/extensions.js').extensionNames`）。
- **ChatStore**：新增 `loadedExtensions` 列表 + `setLoadedExtensions()`。
- **ChatRuntimeBridge**：`loadExtensions()` 在 `RuntimeReady` 时调用，结果路由到 `applyExtensionsResult()`。
- 数据已就绪，只读展示 UI（Tools/Settings 入口）留待后续；扩展产生的消息内容通过 `extra` 字段在 MessageBubble 中自然展示，扩展提示通过 A1 toastr 通道展示。

#### 单元测试

`ChatBridgeModelsTest` 新增 4 项：`bookmarkLink` 解析与空值、`branches` 解析与缺省空列表。当前共 13 项全绿。

#### 文件变更汇总

| 文件 | 变更 |
|---|---|
| `assets/chat_runtime_adapter.js` | `quickReply.list/execute`、`chat.createCheckpoint/createBranch/openCheckpoint`、`extensions.list` 命令与 handler |
| `chat/ChatBridgeModels.kt` | `bookmarkLink`/`branches` 扩展属性 |
| `chat/ChatStore.kt` | `QuickReplyItem` + `quickReplies`、`loadedExtensions` + setters |
| `chat/ChatRuntimeBridge.kt` | QR/checkpoint/branch/extensions 命令、`CommandResult` 结果路由、`completePendingCommand` 返回值、ready/changed 时刷新 |
| `chat/NativeChatScreen.kt` | `QuickReplyStrip`、`BubbleMeta`、`MessageActionSheet` 新增项、`CheckpointDialog`、`BranchListSheet`/`BranchRow` |
| `test/.../ChatBridgeModelsTest.kt` | bookmarkLink/branches 解析测试 |

### v0.10 P3 阶段 A：Toast 捕获 + Reasoning + Tool Calls（2026-05-31）

完成 P3 阶段 A 三项高感知价值功能，并收口两项 P2 遗留。设计严格对照 `docs/P3-ui-design-spec.md` 与设计稿 JSX。

#### A1：Toastr 通知捕获（P2 遗留 🔶→✅）

- **adapter**：新增 `tryWrapToastr()`，包裹 `window.toastr` 的 `error/warning/info/success` 四个方法，在原调用后发 `runtime.toast` 事件（`{type,title,message}`，含 `stripHtml()` 去标签）；在 `tryBindEvents()` 末尾调用一次。未用 `toastr.subscribe()`（拿不到 type）。
- **ChatBridgeModels**：新增 `BridgeEvent.Toast` 变体及 `runtime.toast` 解析。
- **ChatStore**：新增 `RuntimeToast(seq,type,title,message)` + `latestToast` 状态 + `pushToast()`/`clearToast()`，`seq` 自增确保相同内容也能重新触发。
- **ChatRuntimeBridge**：`handleEvent` 处理 `BridgeEvent.Toast` → `store.pushToast()`。
- **NativeChatScreen**：新增 `RuntimeToastHost`，浮于聊天界面顶部（`Box` 内 `Align.TopCenter`），按类型着色（error=errorContainer / warning=tertiaryContainer / success=secondaryContainer / info=primaryContainer），4 秒自动消失（`LaunchedEffect(seq)+delay`），同时只显示最新一条。

#### A2：文件嵌入到提示词上下文（P2 遗留 ⬜→✅）

无代码改动。ST `appendFileContent()`（`script.js:4424`）在 `Generate()` 期间自动从 `extra.files` 读取文件内容拼入提示词，全程在隐藏 WebView 内完成；v0.9 附件上传已正确写入 `extra.files`。

#### A3：Reasoning/Thinking 展示 ✅

- **ChatBridgeModels**：新增 `ChatMessage.reasoning` 扩展属性（`extra.reasoning`，空白返回 null）。
- **adapter**：新增 `STREAM_REASONING_DONE` 监听 → `throttledSnapshot()`，流式 reasoning 结束后刷新。
- **NativeChatScreen**：新增 `ReasoningSection` 可折叠区（`Psychology` 图标 + "思考过程" + 展开箭头，展开区 `surfaceContainerHighest`），渲染于 AI 文本上方，默认折叠。

#### A4：Tool Calls 渲染 ✅

- **ChatBridgeModels**：新增 `ToolInvocation` 数据类 + `ChatMessage.toolInvocations` 解析（`extra.tool_invocations`，`parameters`/`result` 支持对象/数组/字符串）。
- **NativeChatScreen**：新增 `ToolCallGroup`/`ToolCallCard`（`Build` 图标 + displayName，参数折叠、结果展示、`result` 为空时显示"执行中…" + 进度圈）；系统消息含 `tool_invocations` 时渲染卡片替代 `mes` HTML 文本，且不套用隐藏半透明/「已隐藏」badge（`isToolMessage` 区分）。

#### 单元测试

`ChatBridgeModelsTest` 新增 7 项：reasoning 解析与空值、tool_invocations 对象/字符串参数解析与 displayName 回退、缺省空列表、`runtime.toast` 事件解析。

#### 文件变更汇总

| 文件 | 变更 |
|---|---|
| `assets/chat_runtime_adapter.js` | `tryWrapToastr()` + `stripHtml()` + `STREAM_REASONING_DONE` 监听 |
| `chat/ChatBridgeModels.kt` | `ToolInvocation` 数据类、`reasoning`/`toolInvocations` 扩展属性、`BridgeEvent.Toast` + 解析 |
| `chat/ChatStore.kt` | `RuntimeToast` + `latestToast` + `pushToast`/`clearToast` |
| `chat/ChatRuntimeBridge.kt` | `handleEvent` 处理 `BridgeEvent.Toast` |
| `chat/NativeChatScreen.kt` | `RuntimeToastHost`、`ReasoningSection`、`ToolCallGroup`/`ToolCallCard`、`MessageBubble` 集成 |
| `test/.../ChatBridgeModelsTest.kt` | reasoning/toolInvocations/toast 解析测试 |

### v0.9 P2 附件/CFG/世界书收口（2026-05-30）

完成 P2 阶段最后三个核心功能：附件上传和展示、CFG Scale 对话框、世界书只读入口。

#### 附件上传和展示 ✅

- **Coil 依赖**：`build.gradle.kts` 新增 `io.coil-kt:coil-compose:2.6.0`
- **API**：`TavernCoreClient` 新增 `uploadFile(name, base64Data)` / `deleteFile(path)`，POST `/api/files/upload` 和 `/api/files/delete`
- **Bridge 扩展**：`ChatRuntimeBridge.sendMessage()` 从 `store.pendingAttachments` 取出附件信息，放入 payload `attachments` 数组；JS adapter `normalizePendingAttachments()` 处理注入
- **ChatStore**：新增 `PendingAttachment` 数据类和 `pendingAttachments` 状态列表
- **ChatBridgeModels**：新增 `MediaAttachment`、`FileAttachment` 数据类和 `ChatMessage.mediaAttachments` / `.fileAttachments` 扩展属性（从 `extra` JSONObject 解析）
- **NativeChatScreen UI**：
  - `AttachSheet`："附件"和"图片"按钮接入 Android 文件选择器（`ActivityResultContracts.GetContent`，`*/*` / `image/*`）
  - `PendingAttachmentStrip`：输入栏上方显示待发送附件预览（文件名 + 删除按钮）
  - `MessageBubble` 扩展：媒体附件用 Coil `AsyncImage` 渲染，文件附件用 `MessageFileCard` 卡片样式展示
- **单元测试**：`ChatBridgeModelsTest` 覆盖 `MediaAttachment` / `FileAttachment` 解析

#### CFG Scale 对话框 ✅

- JS adapter 新增 `cfg.get` / `cfg.set` 命令，读写 `chat_metadata` 中的 `cfg_guidance_scale`、`cfg_negative_prompt`、`cfg_positive_prompt`
- `buildSnapshot()` metadata 扩展 `cfgScale`、`cfgNegativePrompt`、`cfgPositivePrompt`
- `ChatStore` 新增对应状态字段，`applySnapshot` 自动同步
- `ChatRuntimeBridge.setCfg()` 封装 Bridge 命令
- `CfgScaleDialog`：AlertDialog 含 Slider（1.0-3.0）+ 负面/正面提示词 TextField
- `ChatHeader` DropdownMenu 新增"CFG 引导"菜单项

#### 世界书入口 ✅

- JS adapter 新增 `worldInfo.get` 命令，返回 `chat_metadata.world_info`
- `buildSnapshot()` metadata 扩展 `worldInfo` 字段
- `ChatStore.worldInfoName` 状态字段
- `WorldInfoSheet`：ModalBottomSheet，通过 API 加载世界书列表 + 当前绑定信息，点击展开查看 entries 概要（只读）
- `ChatHeader` DropdownMenu 新增"世界书"菜单项

#### 文件变更汇总

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `app/build.gradle.kts` | 修改 | 新增 Coil 依赖 |
| `app/.../api/TavernCoreApi.kt` | 修改 | 新增 `uploadFile()`, `deleteFile()` |
| `app/.../chat/ChatBridgeModels.kt` | 修改 | 新增 `MediaAttachment`, `FileAttachment` 数据类和解析扩展 |
| `app/.../chat/ChatStore.kt` | 修改 | 新增 `PendingAttachment`, `pendingAttachments`, CFG 字段, `worldInfoName` |
| `app/.../chat/ChatRuntimeBridge.kt` | 修改 | `sendMessage` 扩展携带附件, 新增 `setCfg()` |
| `app/.../chat/NativeChatScreen.kt` | 修改 | `AttachSheet` 接入文件选择器, `PendingAttachmentStrip`, `MessageBubble` 附件渲染, `CfgScaleDialog`, `WorldInfoSheet` |
| `app/.../assets/chat_runtime_adapter.js` | 修改 | `handleSend` 附件注入, `cfg.get/set`, `worldInfo.get`, snapshot metadata 扩展 |
| `test/.../ChatBridgeModelsTest.kt` | 修改 | 新增 `MediaAttachment` / `FileAttachment` 解析测试 |

### v0.8 文档收口（2026-05-29）

本次更新只调整迁移文档，不改变运行时代码。重点是让总览、优先级、验收和实现记录与当前 `main` 的真实状态一致：

- `runtime.save` 已列入 Bridge 命令，用作 `SaveErrorBanner` 的重试入口。
- 保存错误处理统一标记为 best-effort：adapter 能捕获 `saveChat` 缺失或 wrapper 级异常，但 ST `saveChatConditional()` 内部吞掉的保存失败不会向外抛出。
- P2 状态拆分为“已落地 / 待后续”：Author's Note、slash command 正常结果、隐藏/取消隐藏已落地；附件、CFG、世界书、toastr 错误原生提示仍待做。
- 消息删除记录更新为优先调用 ST 导出的 `deleteMessage()`；ST `MESSAGE_DELETED` 事件参数是删除后的 `chat.length`，adapter 只用它触发 snapshot 同步。
- 消息编辑记录补充已同步 `.mes_text`、`.mes_bias`、媒体和代码块复制按钮，但仍无法完全等价复用未导出的 `messageEditDone(div)`。

### v0.7 阶段 C P2 接续 + adapter 契约修正（2026-05-29）

实现阶段 C 的 6 项 P2 功能（完成 5 项，附件上传待后续），并修正了 adapter 与 ST 运行时之间的多个 API 契约错误。

#### adapter 契约修正（审计发现）

对 adapter 与 SillyTavern 源码的逐行对照审计，修正了以下重要错误：

**修正 1：`saveChatConditional` → `saveChat`**
- `getContext()` 暴露的保存函数名为 `saveChat`（st-context.js:151 `saveChat: saveChatConditional`），adapter 之前调用不存在的 `ctx.saveChatConditional()`，实际为 no-op
- `safeSave()` 修正为 `ctx.saveChat()`，编辑/删除/隐藏/作者注 会进入 ST 保存链路；保存失败检测仍为 best-effort

**修正 2：移除不存在的 `CHAT_SAVE_ERROR` 事件监听 + 保存错误检测降级**
- ST 事件系统没有 `CHAT_SAVE_ERROR` 事件（events.js 无此定义）
- 移除 `tryBindEvents` 中的 `ev.CHAT_SAVE_ERROR` 监听
- `saveChatConditional()` 内部 try/catch 吞掉所有异常（script.js:9325），不向外抛出，因此 adapter 的 try/catch 无法捕获保存失败
- 保存错误检测目前为 best-effort，`SaveErrorBanner` 仅在极端情况（如 `ctx.saveChat` 本身不存在时）触发；P1 "保存 integrity 错误提示"标记为部分完成

**修正 3：消息隐藏使用 `is_system` 而非 `is_hidden`**
- ST 隐藏消息的实际机制是 `hideChatMessageRange()` (chats.js:147)，设置 `message.is_system = true/false`
- 之前实现设置的 `msg.is_hidden` 在 ST 中不存在，AI 上下文不会真正排除该消息
- 修正为通过 `import('./scripts/chats.js')` 动态导入并调用 `hideChatMessageRange()`，确保 DOM 更新、swipe 刷新，并进入 ST 保存链路
- Kotlin 端移除 `isHidden` 字段，改用已有的 `isSystem` 作为隐藏标识
- `visibleChatMessages` 不再过滤系统消息，改为在 UI 中用视觉标识显示

**修正 4：`chat.new` 改用 `doNewChat()` 直接调用**
- 之前通过 `#option_start_new_chat.click()` 触发，ST 会弹出 `Popup.show.confirm()` 确认框
- 隐藏 WebView 中无人确认，新建聊天实际不会执行，但 adapter 立即回 success
- 修正为 `import('./script.js')` 动态导入并直接调用 `doNewChat({ deleteCurrentChat: false })`（注意：`script.js` 在 ST `public/` 根目录，非 `scripts/` 子目录）

**修正 5：群聊 regenerate 改用 `regenerateGroup()`**
- 之前群聊也走 `ctx.generate('regenerate')`，不会删除同一轮 group generation 的旧回复
- ST 原生 UI 在群聊里走 `regenerateGroup()`（group-chats.js:167），先删除当前 generation round 的所有 AI 回复再重新生成
- 修正为检测 `ctx.groupId`，群聊时 `import('./scripts/group-chats.js')` 调用 `regenerateGroup()`

#### C1: 群聊消息发送和停止 ✅

send/stop 复用相同 Bridge 通道（`handleSend` 使用 DOM 元素 `send_textarea` + `send_but`，`handleStop` 使用 `ctx.stopGeneration()`），群聊模式下无需特殊处理。regenerate 分流到 `regenerateGroup()`（见上方修正 5）。

UI 增强：
- `ChatHeader` 新增 `isGroupMode` 参数，群聊模式显示 `PrototypeGroupAvatar` 和"群聊"badge（tertiaryContainer 色）
- 消息列表中每条消息已通过 `message.name` 显示不同角色名（群聊各角色）

#### C2: 群聊历史聊天切换 ✅

新增 `GroupChatHistorySheet`（ModalBottomSheet），通过 API 加载 `GroupSummary.chats` 列表，当前聊天高亮标识。点击切换调用 `bridge.openGroup(groupId, chatId)`。

入口：群聊模式下 ChatHeader "历史对话"菜单项自动路由到内联 sheet（非 PastChatsScreen）。

#### C3: 附件上传和展示 ✅

已在 v0.9 完成，详见 v0.9 段落。

#### C4: Author's Note 基础接入 ✅

- JS adapter 新增 `authorsNote.get` / `authorsNote.set` 命令处理
- `buildSnapshot()` metadata 扩展 `authorsNote` 字段
- `ChatStore.authorsNote` 状态字段，通过 snapshot 自动同步
- `AuthorsNoteDialog`：AlertDialog 显示说明文本 + 多行 OutlinedTextField，保存后通过 Bridge `authorsNote.set` → `ctx.saveChat()` 写回
- AttachSheet "作者注"按钮连接到 `AuthorsNoteDialog`
- CFG 和世界书已在 v0.9 落地（`CfgScaleDialog` + `WorldInfoSheet`）

#### C5: Slash commands ✅

斜杠命令通过 `handleSend` 自然工作（文本注入 textarea → 点击发送 → ST 内部处理）。命令结果以正常消息形式通过 `MESSAGE_SENT` / `MESSAGE_RECEIVED` 事件同步到原生 UI。

已知限制：ST 的 toastr 通知（命令错误、警告）在隐藏 WebView 中显示，原生端暂不可见。

#### C6: 消息隐藏/取消隐藏 ✅

- JS adapter 新增 `message.hide` / `message.unhide` 命令，通过 `import('./scripts/chats.js').hideChatMessageRange()` 调用 ST 原生隐藏机制（`is_system` 标志 + 保存 + DOM 刷新）
- Kotlin 端使用已有的 `isSystem` 字段作为隐藏标识（`is_system: true` = 隐藏于 AI 上下文）
- `visibleChatMessages` 不再过滤系统消息，所有消息均在原生 UI 中显示
- `MessageActionSheet` 新增"从 AI 上下文中隐藏"/"取消隐藏"ListItem
- `MessageBubble` 隐藏消息半透明（`alpha(0.5f)`）+ `HiddenMessageBadge`（VisibilityOff 图标 + "已隐藏"文字）

#### 第二轮契约修正（审计跟进）

审计复核发现第一轮修正中仍存在的问题：

**修正 6：`import('./scripts/script.js')` 路径错误**
- `script.js` 在 ST `public/` 根目录（`<script type="module" src="script.js">`），不在 `scripts/` 子目录
- 所有 `import('./scripts/script.js')` 修正为 `import('./script.js')`
- 影响：`handleNewChat`（`doNewChat`）和 `handleRegenerate`（群聊检测后的回退路径）

**修正 7：保存错误检测降级为 best-effort**
- `saveChatConditional()` 内部 try/catch（script.js:9325）吞掉所有异常，仅 `console.error`
- adapter 的 `safeSave()` 改为直接调用 `ctx.saveChat()`，仅捕获 `saveChat` 不存在或 wrapper 级异常
- 新增 `runtime.save` 命令，`SaveErrorBanner` 的重试按钮会触发保存而不是误用 `chat.reload`
- `SaveErrorBanner` 保留但标记为 best-effort；ST 内部保存失败不会向 adapter 抛出

**修正 8：`handleDeleteMessage` 改用 ST 导出的 `deleteMessage()`**
- 之前直接 `chat.splice()` 跳过 DOM 删除、`deleteItemizedPromptForMessage`、`updateViewMessageIds`、`refreshSwipeButtons` 等运行时维护
- 修正为优先检查 DOM 元素是否存在，存在时调用 `import('./script.js').deleteMessage(id, undefined, false)`
- DOM 元素不存在时（消息在 lazy-render 窗口外）回退到直接 splice + `ctx.saveChat()` + 事件发射
- ST `MESSAGE_DELETED` 事件参数是删除后的 `chat.length`，不是 deleted id；adapter 不再把它转发为 `message.deleted`，改为直接 snapshot 同步

**修正 9：`handleEditMessage` 添加 `MESSAGE_EDITED` 事件 + DOM 更新**
- 之前只发 `MESSAGE_UPDATED`，缺少 `MESSAGE_EDITED`（扩展依赖此事件）
- 新增 `MESSAGE_EDITED` 事件发射（在修改文本后、保存前），扩展可在此回调中变换文本
- 新增 DOM 更新：如果消息 DOM 元素存在，使用 `import('./script.js').messageFormatting()` 重新渲染 `.mes_text` / `.mes_bias`，并调用 `appendMediaToMessage()`、`addCopyToCodeBlocks()`
- 已知限制：`messageEditDone(div)` 未导出，原生编辑仍无法完全复用 ST 内部编辑模式的所有 UI 状态副作用，reasoning 编辑等细节需后续单独补齐

#### 文件变更汇总

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `assets/chat_runtime_adapter.js` | 修改 | `safeSave()` 修正为 `ctx.saveChat()`；新增 `runtime.save`；移除 `CHAT_SAVE_ERROR` 监听；`MESSAGE_DELETED` 改为 snapshot 同步；`message.hide/unhide` 改用 `hideChatMessageRange()`；`handleNewChat` 改用 `import('./script.js').doNewChat()`；`handleRegenerate` 群聊分流 `regenerateGroup()`；`handleDeleteMessage` 优先使用 `deleteMessage()`；`handleEditMessage` 增加 `MESSAGE_EDITED` + DOM 更新；新增 `authorsNote.get/set` 命令；snapshot metadata 增加 `authorsNote` |
| `chat/ChatBridgeModels.kt` | 修改 | 移除 `isHidden` 字段（使用已有 `isSystem` 代替）；保留 `SaveError` 事件解析 |
| `chat/ChatStore.kt` | 修改 | 新增 `authorsNote` 和 `saveError` 状态字段 |
| `chat/ChatRuntimeBridge.kt` | 修改 | 新增 `hideMessage`、`unhideMessage`、`setAuthorsNote`、`runtime.save` 重试保存命令和 COMMAND_LABELS |
| `chat/NativeChatScreen.kt` | 修改 | ChatHeader 群聊标识；MessageActionSheet hide/unhide（基于 `isSystem`）；HiddenMessageBadge；GroupChatHistorySheet；AuthorsNoteDialog；AttachSheet 作者注连接 |
| `chat/ChatUiState.kt` | 修改 | `visibleChatMessages` 不再过滤 `isSystem` 消息 |

### v0.6 历史对话页面（2026-05-29）

实现阶段 B 第一项：聊天文件列表和历史聊天切换 UI（`PrototypePastChatsScreen`）。

#### 功能

- 全屏历史对话列表页，显示角色所有聊天存档
- 当前活动聊天用左侧竖条 + primaryContainer 底色 + "进行中" badge 标识
- 本地搜索过滤（按 lastMessage 和 fileName 匹配）
- 操作 bottom sheet：打开、重命名、导出 JSONL、导出纯文本、删除
- 删除当前活动聊天时自动新建聊天
- 导出通过 FileProvider + Android share sheet 分享
- FAB "新对话" 创建新聊天并进入
- 空状态提示（无历史 / 搜索无结果）
- 从角色详情页"历史对话"卡片入口进入

#### 新增/变更文件汇总

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `ui/screens/PrototypePastChatsScreen.kt` | **新增** | 历史对话列表页面（搜索、操作 sheet、重命名/删除对话框、导出分享） |
| `ui/navigation/STNavGraph.kt` | 修改 | 新增 `PAST_CHATS` 路由和 `pastChats()` 辅助函数 |
| `MainActivity.kt` | 修改 | 注册 PAST_CHATS 路由、角色详情页传入 `onOpenPastChats` 导航 |
| `ui/screens/PrototypeCharacterScreens.kt` | 修改 | 角色详情页新增"历史对话"入口卡片 |
| `res/xml/file_provider_paths.xml` | 修改 | 新增 `cache-path` 用于聊天导出文件分享 |

#### 消息复制、编辑、删除（2026-05-29）

实现阶段 B 第二项：消息级操作（复制/编辑/删除）。

- 长按消息气泡弹出 `MessageActionSheet`（ModalBottomSheet），含图标网格（复制/编辑/重写/翻译）+ 列表项（删除）
- 复制：原生 `ClipboardManager` 复制消息文本
- 编辑：`MessageEditBubble` 内联编辑模式（OutlinedTextField + 保存/取消/删除工具栏），通过 Bridge `message.edit` 命令同步 ST runtime（更新 `chat[].mes` + `swipes[]`，发出 `MESSAGE_EDITED`/`MESSAGE_UPDATED`，并调用 `ctx.saveChat()` 进入保存链路）
- 删除：`DeleteMessageDialog` 确认后通过 Bridge `message.delete` 命令，优先调用 ST 导出的 `deleteMessage(id, undefined, false)`；DOM 不存在时才退回直接 splice + snapshot 同步
- 最后一条 AI 消息的"更多"按钮（⋮）也打开操作 sheet

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `chat/NativeChatScreen.kt` | 修改 | 长按 `combinedClickable`、`MessageActionSheet`、`MessageEditBubble`、`DeleteMessageDialog`、`ActionGridItem` |
| `chat/ChatRuntimeBridge.kt` | 修改 | 新增 `editMessage()`、`deleteMessageFromChat()` Bridge 命令 |
| `assets/chat_runtime_adapter.js` | 修改 | 新增 `handleEditMessage`、`handleDeleteMessage` 命令处理；编辑补发 `MESSAGE_EDITED`/`MESSAGE_UPDATED` 并更新 DOM；删除优先走 ST `deleteMessage()`，`MESSAGE_DELETED` 仅触发 snapshot 同步 |

#### 保存 integrity 错误处理 + Bridge 超时 + 崩溃恢复（2026-05-29）

实现阶段 B 第 3-5 项。

- **保存 integrity**：JS adapter 新增 `safeSave()` 调用 `ctx.saveChat()`（v0.7 修正为正确 API 名称）；由于 ST `saveChatConditional()` 内部吞掉保存异常，`save.error` 只能覆盖 wrapper 级失败；原生端 `SaveErrorBanner` 保留显示和重试入口，重试会调用 `runtime.save`
- **Bridge 超时**：`ChatRuntimeBridge.dispatch()` 注册超时回调（`pendingCommands` map），按命令类型分级：默认 15s / 打开角色 30s / 生成相关 60s；收到 `bridge.result` 或 `bridge.error` 时取消超时
- **崩溃恢复**：`ChatWebViewScreen` 新增 `onRenderProcessGone` 回调，触发 `RENDER_PROCESS_GONE` 错误页面；重试时用 `loadUrl(baseUrl)` 重启渲染进程

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `chat/ChatRuntimeBridge.kt` | 修改 | 超时追踪（`pendingCommands`、`registerTimeout`、`completePendingCommand`、`clearPendingCommands`）、`retrySave()`、`dismissSaveError()` |
| `chat/ChatStore.kt` | 修改 | 新增 `saveError` 状态、`recordSaveError()`、`clearSaveError()` |
| `chat/ChatBridgeModels.kt` | 修改 | 新增 `BridgeEvent.SaveError`、`save.error` 事件解析 |
| `chat/NativeChatScreen.kt` | 修改 | 新增 `SaveErrorBanner` 组件 |
| `assets/chat_runtime_adapter.js` | 修改 | 新增 `safeSave()` / `runtime.save`（v0.7 移除了不存在的 `CHAT_SAVE_ERROR` 事件监听，保存失败检测降级为 best-effort） |
| `ui/webview/ChatWebViewScreen.kt` | 修改 | 新增 `onRenderProcessGone` 回调、`RENDER_PROCESS_GONE` 重试路径 |
| `ui/webview/WebViewErrorPage.kt` | 修改 | 新增 `RENDER_PROCESS_GONE` 枚举值及对应错误页文案 |

### v0.5 界面审计收口（2026-05-29）

对 Chat 相关的所有原生屏幕（NativeChatScreen、PrototypeHomeScreen、PrototypeCharacterScreens、PrototypeGroupChatScreen 及其二级入口）进行了 4 轮系统性审计，从 22 个问题逐轮收敛到 0 个功能性 bug。

#### 第一轮（22 个问题）— 假数据和核心导航

- **清除硬编码假数据**：NativeChatScreen 中"Claude Sonnet · 200k 上下文""今天 14:00"等 demo 占位文本全部替换为从 `ChatStore` 读取的真实数据
- **底部导航修复**：Chat 加入底部 tab bar；进入 Chat 时隐藏底部导航栏（`showNavigationChrome = currentRoute != STRoutes.CHAT`）
- **FAB 行为修正**：首页 FAB 从"打开第一个聊天"改为"新建对话"（`onNewChat`）
- **`pendingWebViewTarget` 可持久化**：使用 `rememberSaveable(stateSaver = webViewTargetSaver())` 跨配置变更恢复，saver 基于 `Uri.encode` 和 `|` 分隔符
- **返回时重置聊天状态**：`navigateMainTab` 和 `onBackToHome` 中调用 `chatStore.reset()`
- **群聊入口接通**：`PrototypeGroupChatScreen` 的 `onOpenGroupChat` 连接到 `WebViewTarget.GroupChat` 导航

#### 第二轮（14 个问题）— 模型映射和 API 一致性

- **删除死代码**：`prototypeFallbackChats()` 和 `prototypeFallbackCharacters()` 从 PrototypeModels 中移除
- **标签从真实数据派生**：新增 `prototypeCharacterTagFilters()` 按频率排序 + 黑名单过滤（排除 `v2`、`not_dead`、`内部:` 前缀、空白标签等）
- **群聊创建增强**：`GroupCreateRequest` 携带 `allowSelfResponses`、`activationStrategy`、`generationMode`；`chatId` 仅在非空时发送
- **删除无数据支撑的 UI**：`PrototypeChatItem` 移除 `streaming` 和 `unread` 字段及其过滤逻辑
- **置顶语义统一**：`isPinned` 映射为 `favorite`，UI 文案从"收藏"改为"置顶"
- **CHAT 目标匹配修正**：`WebViewTarget.CHAT` 的 `targetMatchesStore` 从 `true` 改为 `store.chatFile.isNotBlank() || store.characterName.isNotBlank() || store.messages.isNotEmpty()`
- **GroupChat 目标匹配**：检查 `store.mode == "group"` 且 `identifiersMatch(target.groupId, store.avatarUrl)`

#### 第三轮（8 个问题）— 离线和状态一致性

- **离线角色渲染修复（P1 功能 bug）**：`PrototypeCharacterScreens` 的 `when` 分支重排 — `loading` 优先、然后 `!serverRunning && characters.isEmpty()` 才显示离线提示，有本地数据时正常显示角色网格
- **离线数据保护**：`runCatching { reader.listCharacters() }` 保护文件读取异常
- **标签越界防护**：`LaunchedEffect(filterChips.size, selectedFilter)` 在标签减少时重置 `selectedFilter`
- **群聊排序**：新建群聊置顶 `listOf(created) + groups.filterNot { it.id == created.id }`
- **群聊刷新按钮**：GroupListView 标题栏增加手动刷新入口
- **toggle 互斥集中化**：`setAutoSelectNext` / `setMentionOnly` 在父组件集中管理互斥逻辑

#### 第四轮（2 个问题）— 最终收尾

- **标签过滤质量**：`prototypeCharacterTagFilters` 增加 trim 后 blank 检查和 `hiddenPrototypeTagFilters` 黑名单
- **搜索栏 UI 优化**：角色搜索从弹窗 (`AlertDialog`) 迁移到内联 `PrototypeSearchBar` 组件

#### 回归测试

新增 15 个回归测试覆盖审计修复：

- `ChatInterfaceAuditRegressionTest`（10 个）：源码扫描确保无硬编码假数据、无死代码复活、过滤逻辑正确、群聊行为符合预期、导航状态清理正确、CHAT 目标匹配不再过宽
- `PrototypeModelsTest`（5 个）：逻辑测试覆盖 `ChatSummary`/`CharacterSummary` 映射、零时间戳处理、标签频率排序和黑名单过滤、DrawerState 连接状态

#### 新增/变更文件汇总

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `chat/ChatUiState.kt` | **新增** | Chat UI 辅助函数（消息过滤、日期标签、滚动目标、消息 key、目标命令 key） |
| `chat/NativeChatScreen.kt` | 修改 | 清除假数据、swipe 控件、快捷操作栏（含不可用反馈）、CHAT/GroupChat 目标匹配修正 |
| `chat/ChatStore.kt` | 修改 | 新增 `mode` 字段，`applySnapshot` 设置 mode，`reset()` 重置 mode |
| `chat/ChatRuntimeBridge.kt` | 修改 | 新增 `openGroup(groupId, chatId?)`、`swipePrevious(messageId)`、`swipeNext(messageId)` |
| `ui/screens/PrototypeModels.kt` | 修改 | 删除 fallback 工厂、新增 `prototypeCharacterTagFilters()`、移除 streaming/unread、置顶语义 |
| `ui/screens/PrototypeHomeScreen.kt` | 修改 | 移除 streaming/unread UI、过滤只保留"全部"/"置顶" |
| `ui/screens/PrototypeCharacterScreens.kt` | 修改 | 离线分支重排、真实标签、内联搜索栏、`runCatching` 保护 |
| `ui/screens/PrototypeGroupChatScreen.kt` | 修改 | toggle 互斥、API 参数完整、排序修正、刷新按钮 |
| `ui/screens/PrototypeComponents.kt` | 修改 | 新增 `PrototypeSearchBar` 组件 |
| `ui/webview/WebViewNavigator.kt` | 修改 | `WebViewTarget.GroupChat` sealed variant |
| `MainActivity.kt` | 修改 | `webViewTargetSaver()`、`chatStore.reset()`、`openGroupChat` 导航、动态 drawer badge |
| `test/.../ChatInterfaceAuditRegressionTest.kt` | **新增** | 10 个源码扫描回归测试 |
| `test/.../PrototypeModelsTest.kt` | **新增** | 5 个逻辑回归测试 |

### v0.4b 收口（空屏和 runtime 恢复修复）

基于后续界面改版后的代码回查，P0 又补齐了以下稳定性问题：

1. **Runtime WebView 不再 0dp 挂载**：`NativeChatScreen` 中的 runtime WebView 从 `Box(Modifier.size(0.dp))` 改为 1dp 隐藏宿主，确保 WebView 真正参与测量和窗口挂载，避免只剩背景色的空屏风险。
2. **目标聊天改为 ready 后重放**：从角色页或最近聊天进入 Chat 时，`MainActivity` 只记录 `WebViewTarget.CharacterChat`；等 JS adapter 发出 `runtime.ready` 后，由 `NativeChatScreen` 统一通过 `ChatRuntimeBridge.openCharacter()` 打开目标角色/历史聊天，避免向旧 WebView 或未初始化 runtime 发命令。
3. **WebView reload/dispose 会重置原生 runtime 状态**：`ChatWebViewScreen` 增加 `onRuntimeReset`、`onWebViewDisposed` 回调；页面重载、服务停止、WebView 销毁时，`ChatStore` 会回到 `NOT_READY`，输入栏随之禁用。
4. **Bridge 命令不再静默丢失**：Kotlin 侧 dispatch 会检测 `window.STAndroidChatRuntime` 是否已挂载；未挂载时回到“正在重新连接”状态。JS 侧 `bridge.error` 也会进入 `ChatStore.runtimeError`。
5. **消息同步去重**：`ChatStore.addMessage()` 改为按 message id upsert，避免 `message.added` 与 `chat.loaded` snapshot 交错时重复显示消息。
6. **角色库直达聊天重新接通**：当前界面已迁到 `PrototypeCharacterLibraryScreen` / `PrototypeCharacterProfileScreen`，角色卡和详情页都会进入同一条 `WebViewTarget.CharacterChat` ready 后重放链路。
7. **JS adapter 打开角色/聊天改为 await**：`chat.openCharacter` 会等待 `selectCharacterById()` 和 `openCharacterChat()` 完成后再发 snapshot/result；同时监听 `CHAT_LOADED` 事件并在 send/stop/reload 后主动刷新 snapshot。
8. **真机反馈后的可见性修复**：最近聊天在 API 暂未实现时回落到本地聊天历史；打开聊天不再保留 demo 分支；Chat 页面切目标时隐藏旧消息并显示正在打开的目标/错误；输入法弹出时输入栏使用 IME inset 抬起，消息列表随键盘出现滚到底部。
9. **生成状态误判修复**：不再用 `ctx.streamingProcessor` 或 `#mes_stop` 可见性判断是否生成中，改为读取 ST 自己维护的 `body[data-generating]`；如果 ST 处于 `no_connection`，原生发送/继续/重写会显示“还没有连接模型 API”，不会把原生 UI 卡在生成中。

### v0.4a 修订（代码审查后修复）

基于代码审查反馈，修复了以下 3 个关键问题：

1. **`runtime.ready` 时序修正**：不再在拿到 `eventSource` 时立即发送 `runtime.ready`。改为监听 ST 的 `APP_READY` 事件（对应 `event_types.APP_READY`），该事件在 ST 完成 `firstLoadInit`、角色加载、初始聊天渲染后才触发。ST 的 `EventEmitter` 会对 `APP_READY` 进行 auto-fire，adapter 注入晚于 ready 时也会收到该事件。
2. **函数访问路径统一为 `getContext()`**：
   - `generation.regenerate` / `generation.continue`：从 `window.Generate` 改为 `ctx.generate`（`getContext()` 上的 `generate` 属性，映射自 `Generate`）
   - `generation.stop`：优先使用 `ctx.stopGeneration`，回退到 DOM `#mes_stop`
   - `chat.openCharacter`：从 `ctx.selectCharacterById || window.selectCharacterById` 改为仅 `ctx.selectCharacterById`；`openCharacterChat` 同理
   - `chat.new`：`doNewChat` 是 ES module export 但不在 `getContext()` 上，通过 `import('./script.js').doNewChat()` 动态导入直接调用（v0.7 修正，之前的 DOM 点击方式会触发确认弹窗阻塞）
   - `chat.reload`：**新增实现**，调用 `ctx.reloadCurrentChat()`（返回 Promise，完成后发送 snapshot）
3. **角色列表直接进入 Chat**：角色库入口新增 `onOpenChat` 回调，点击后直接导航到 Chat 页并打开该角色。v0.4b 后对应实现位于 `PrototypeCharacterLibraryScreen` 的角色卡对话按钮，`MainActivity` 将此回调连接到 `openCharacterChatFromCharacterManagement`。

Kotlin 侧对应变更：`ChatRuntimeBridge` 新增 `reloadChat()` 方法。

### 已完成

#### 路线图步骤 1-3 已完成

按照第 14 节推荐路线，一次性完成了步骤 1（Runtime WebView + Bridge adapter）、步骤 2（原生只读镜像）和步骤 3（原生输入栏接管）。

#### 新增文件

| 文件 | 职责 | 对应方案章节 |
|---|---|---|
| `app/src/main/java/io/github/sanitised/st/chat/ChatBridgeModels.kt` | Bridge 消息信封（`BridgeMessage`）、`ChatMessage`、`ChatSnapshot`、`BridgeEvent` sealed class 及 JSON 解析 | §4.2 消息信封、§4.4 事件、§4.5 Snapshot |
| `app/src/main/java/io/github/sanitised/st/chat/ChatStore.kt` | Compose 可观察状态持有：runtime 状态、角色信息、消息列表的增删改和 snapshot 覆盖 | §10 `ChatStore` |
| `app/src/main/java/io/github/sanitised/st/chat/ChatRuntimeBridge.kt` | Kotlin 侧 Bridge：通过 `evaluateJavascript` 发送命令，接收 JS 事件并更新 `ChatStore` | §10 `ChatRuntimeBridge` + `ChatBridgeEventHandler` |
| `app/src/main/java/io/github/sanitised/st/chat/ChatUiState.kt` | Chat UI 辅助函数：消息过滤、日期标签、滚动目标、消息 key、目标命令 key | §10 `ChatUiState` |
| `app/src/main/java/io/github/sanitised/st/chat/NativeChatScreen.kt` | 完整 Compose Chat UI：标题栏（角色名 + 生成状态）、消息气泡列表、输入栏（发送/停止按钮）、swipe 控件、快捷操作栏。内嵌不可见 `ChatWebViewScreen` 作为 runtime 容器 | §10 `NativeChatScreen` + `MessageRenderer`（基础版） |
| `app/src/main/assets/chat_runtime_adapter.js` | 注入 WebView 的 JS 适配器：监听 ST `eventSource` 事件、构建 snapshot、处理 Android 发来的命令 | §4.1 `STAndroidChatRuntime`、§4.3 命令、§4.4 事件 |

#### 修改文件

| 文件 | 变更 |
|---|---|
| `STAndroidBridge.kt` | 新增 `postChatEvent` `@JavascriptInterface` 方法和 `chatEventHandler` 参数 |
| `WebViewNavigator.kt` | 新增 `injectChatRuntimeAdapter()` 从 assets 加载 JS adapter，`resetInjectionState()` 处理页面重载；新增 `WebViewTarget.GroupChat` sealed variant |
| `ChatWebViewScreen.kt` | 新增 `chatEventHandler` 和 `onWebViewReady` 参数；`onPageFinished` 中注入 adapter；页面 start/dispose 时重置注入状态 |
| `MainActivity.kt` | Chat tab 替换为 `NativeChatScreen`；创建共享 `ChatStore` 和 `ChatRuntimeBridge`；角色/群聊进入聊天时记录 `WebViewTarget`，等待 runtime ready 后统一重放；`webViewTargetSaver()` 跨配置恢复；`chatStore.reset()` 导航时清理状态 |
| `PrototypeCharacterScreens.kt` | 角色卡与详情页接入 `onOpenChat`；离线分支重排；真实标签过滤；内联搜索栏 |
| `PrototypeGroupChatScreen.kt` | 群聊列表和创建（`activationStrategy`/`allowSelfResponses`）、`onOpenGroupChat` 导航、toggle 互斥、排序修正 |
| `PrototypeModels.kt` | 删除 fallback 工厂、新增 `prototypeCharacterTagFilters()`、移除 streaming/unread、置顶语义修正 |
| `PrototypeHomeScreen.kt` | 移除 streaming/unread UI 路径、过滤只保留"全部"/"置顶" |
| `PrototypeComponents.kt` | 新增 `PrototypeSearchBar` 组件 |

#### P0 验收清单对照

| # | 验收项 | 状态 | 说明 |
|---|---|---|---|
| 1 | 原生 Chat 页面能等待 Runtime WebView ready | ✅ 已实现 | JS adapter 轮询 ST `eventSource`，就绪后发送 `runtime.ready` |
| 2 | 从角色列表或角色详情进入 Chat，能打开正确角色 | ✅ 已实现 | `openCharacterChatFromCharacterManagement` 记录 `WebViewTarget.CharacterChat`；runtime ready 后由 `NativeChatScreen` 调用 bridge 打开目标角色 |
| 3 | 指定历史聊天时，能打开对应 chat file | ✅ 已实现 | `chat.openCharacter` 命令携带 `chatFile`，JS 侧调用 `openCharacterChat(normalized)` |
| 4 | 原生消息列表显示 ST runtime 的实际消息 | ✅ 已实现 | `chat.loaded` snapshot → `ChatStore.applySnapshot` → `MessageList` composable |
| 5 | 空聊天能显示角色首条消息 | ✅ 已实现 | snapshot 包含 ST runtime 的 `chat[]`，首条消息由 ST 生成 |
| 6 | 原生输入栏能发送一条用户消息 | ✅ 已实现 | `chat.send` 命令 → JS 写入 `#send_textarea` 并 click `#send_but` |
| 7 | 能收到 AI 回复 | ✅ 已实现 | `MESSAGE_RECEIVED` 事件 → `message.added` → `ChatStore.addMessage` |
| 8 | 生成中能停止 | ✅ 已实现 | `generation.stop` 命令 → JS click `#mes_stop` 或调用 `stopGeneration()` |
| 9 | 聊天由 ST runtime 保存，重启 App 后能恢复 | 🔶 设计依赖 | 活动聊天写入由 ST runtime 负责，原生端不写 JSONL；保存失败检测 best-effort |
| 10 | Runtime WebView reload 后能重新同步 snapshot | ✅ 已实现 | `onPageStarted` 重置注入状态 → `onPageFinished` 重新注入 adapter → 重新 `runtime.ready` + snapshot |

#### 额外已实现（超出 P0 最小范围，归属 P1/P2）

- **Streaming token 更新**（P1）：`STREAM_TOKEN_RECEIVED` 事件以 80ms 节流回传 `stream.token`，原生端实时更新最后一条消息文本
- **重生成和继续**（P1）：`ChatRuntimeBridge` 已暴露 `regenerate()` 和 `continueGeneration()`，JS adapter 已实现 `generation.regenerate` 和 `generation.continue` 命令；群聊 regenerate 分流 `regenerateGroup()`
- **新建聊天**（P1）：`ChatRuntimeBridge.newChat()` → `chat.new` → JS 调用 `doNewChat()`
- **`generation.ended` 后自动刷新 snapshot**：确保生成结束后原生端拿到完整的最终消息
- **Swipe 控件**（P1）：`ChatRuntimeBridge.swipePrevious(messageId)` / `swipeNext(messageId)` Bridge 命令已通，NativeChatScreen 已有 swipe 按钮 UI
- **群聊打开**（P2）：`ChatRuntimeBridge.openGroup(groupId, chatId?)` 已实现；`WebViewTarget.GroupChat` 导航和 `webViewTargetSaver` 序列化已通
- **群聊列表和创建**（P2）：`PrototypeGroupChatScreen` 完整的群聊管理 UI（列表、创建、行为选项）
- **ChatStore 模式支持**（P2）：`mode` 字段区分 `"character"` 和 `"group"`，`applySnapshot` 从 runtime 同步模式
- **回归测试**：Kotlin 单元测试覆盖 UI 状态关键路径；JS 契约测试覆盖 adapter 删除事件、保存重试和文档保存语义，防止回退

### 待验证（需真机测试）

1. 从最近聊天或角色卡进入 Chat 时，标题和消息必须切到对应角色/聊天；如果 JS runtime 找不到角色，Chat 页要显示明确错误而不是继续显示上一段聊天。
2. 输入法弹出时，输入栏必须抬到键盘上方，消息列表必须保持最后一条消息可见。
3. 生成回复时，streaming token 更新不能明显卡顿；必要时通过 WebView 日志和诊断导出判断 `postChatEvent` 是否过密。
4. App 切后台再回来后，当前角色标题、消息列表、输入栏可用状态必须保持一致；如果 WebView 重载，需要重新进入“正在连接/正在打开”状态并恢复 snapshot。
5. Runtime WebView 的 1dp 隐藏宿主无法直接肉眼观察，验收以 `runtime.ready` 后能打开真实目标、能发送/停止、reload 后能重新同步为准。

### 与方案的偏差和决策

1. **`ChatController` 和 `ChatBridgeEventHandler` 合并**：方案第 10 节建议分开，实现时合并到 `ChatRuntimeBridge` 中，因为 P0 阶段命令和事件处理紧密耦合，拆分反而增加复杂度。P1 如果逻辑膨胀再拆分。
2. **Runtime WebView 作为 NativeChatScreen 的子组件**：方案建议 `ChatRuntimeWebViewHost` 独立管理生命周期，实现时嵌入 `NativeChatScreen` 的 1dp 隐藏宿主中，复用现有 `ChatWebViewScreen` 的服务启动和健康检查逻辑。
3. **`chat.send` 通过 DOM 操作，`chat.new` 通过动态导入**：`chat.send` 直接设置 `#send_textarea.value` 并 click `#send_but`（`sendTextareaMessage` 不在 `getContext()` 上）；`chat.new` 通过 `import('./script.js').doNewChat()` 动态导入调用（v0.7 修正，避免 DOM 点击触发确认弹窗阻塞隐藏 WebView）。
4. **优先使用 ST 公开 API，必要时动态导入模块 export**：`generation.stop`（`ctx.stopGeneration`）、`generation.continue`（`ctx.generate`）、`chat.reload`（`ctx.reloadCurrentChat`）、`chat.openCharacter`（`ctx.selectCharacterById` + `ctx.openCharacterChat`）走 `SillyTavern.getContext()`；`chat.new` / 单聊删除 / 群聊 regenerate / 隐藏消息等走 `import('./script.js')`、`import('./scripts/group-chats.js')`、`import('./scripts/chats.js')` 中已导出的函数。
5. **Chat UI 字符串暂未完全国际化**：当前原生 Chat 新增文案以中文为主，后续仍需统一抽到 `strings.xml` 和 `values-zh-rCN/strings.xml`。

### 下一步

> **当前进度（v0.12）**：P0/P1/P2 + P3 阶段 A+B+C 全部落地，均通过编译 + 单测（`ChatBridgeModelsTest` 15 项）+ `assembleDebug`。下方阶段 A/B/C 列表保留历史记录；当前最高优先级是**真机端到端验证**与**剩余阻塞/后续项**。

#### 剩余项

1. ⛔ logprobs：ST `logprobs.js` 的 `state` 未 export，需上游改动，保持 submodule 原封不动故不做。
2. ⬜ TTS、翻译、生图：后续专项处理（逻辑复杂，单独立项）。
3. 🔶 扩展系统：数据通道（`extensions.list`）已就绪，只读 UI 待后续。
4. ⬜ P4 完整原生生成链路：契约测试就绪前不启动。

#### 真机验证（最高优先级）

1. P0 全部验收项（见 §13 P0 验收清单）；Streaming token 性能（`postChatEvent` 频率、帧率、长消息渲染）。
2. 群聊打开/发送/历史切换、Swipe 前后翻页、离线角色库回退。
3. P3 新功能依赖 ST 运行时实际状态，单测仅覆盖解析层，需真机验证：Quick Reply 执行、Checkpoint 创建/打开、Branch 创建跳转、Reasoning/Tool Calls 渲染、Itemized Prompt 与 Data Bank sheet 的真实数据。

#### 阶段 B：P1 收口

1. ~~聊天文件列表和历史聊天切换 UI~~ ✅ (`PrototypePastChatsScreen`)
2. ~~消息复制、编辑、删除~~ ✅（长按消息操作 sheet + 内联编辑 + 删除确认）
3. 保存 integrity 错误处理和用户提示 🔶（`runtime.save` + `SaveErrorBanner` 已有入口；ST 内部保存失败不会抛出，`save.error` 只能 best-effort）
4. ~~Bridge 超时处理（命令超时提示和重试机制）~~ ✅（`pendingCommands` 超时追踪，按命令类型分级超时）
5. ~~Runtime 崩溃检测和自动恢复~~ ✅（`onRenderProcessGone` + `RENDER_PROCESS_GONE` 错误页 + `loadUrl` 恢复）

#### 阶段 C：P2 接续

1. ~~群聊消息发送和停止（NativeChatScreen 内验证）~~ ✅ 群聊 send/stop 复用相同 Bridge 通道，regenerate 分流 `regenerateGroup()`，ChatHeader 增加群聊模式标识和群头像
2. ~~群聊历史聊天切换~~ ✅ `GroupChatHistorySheet` 内联 bottom sheet，通过 API 加载群聊列表，`bridge.openGroup(groupId, chatId)` 切换
3. ~~附件上传和展示~~ ✅ Coil `AsyncImage` + `MessageFileCard` + `PendingAttachmentStrip` + `TavernCoreClient.uploadFile()` + JS adapter 附件注入
4. ~~Author's Note、CFG、世界书基础接入~~ ✅ Author's Note `authorsNote.get/set` + `AuthorsNoteDialog`；CFG `cfg.get/set` + `CfgScaleDialog`；世界书 `worldInfo.get` + `WorldInfoSheet`（只读）；全部通过 snapshot metadata 同步
5. ~~Slash commands 结果和错误展示~~ ✅ 斜杠命令通过 `handleSend` 自然工作，正常结果以消息形式展示；toastr 通知（错误/警告/成功）经 adapter 包裹转发 `runtime.toast` → `RuntimeToastHost`（v0.10）
6. ~~消息隐藏/取消隐藏~~ ✅ adapter `message.hide/unhide` 通过 `hideChatMessageRange()` 切换 `is_system` + Kotlin 端用 `isSystem` 标识 + action sheet 切换 + 气泡半透明

#### 阶段 D：P3 高级能力

- **阶段 A（v0.10）✅**：toastr 通知捕获、Reasoning 展示、Tool Calls 渲染。
- **阶段 B（v0.11）✅**：Quick Replies、Checkpoint/Branch、扩展列表（最小化通道）。
- **阶段 C（v0.12）✅**：itemized prompts、Data Bank（聊天内 sheet）。logprobs ⛔ 阻塞（ST 未导出 state）。
- **TTS、翻译、生图**：后续专项处理。

按 §11 P3 列表推进，优先级根据用户需求动态调整。

###  v0.3 审查变更记录（历史）

v0.3 基于对 SillyTavern 源码的逐行审查，修正和补充了以下内容：

### 修正

1. **`messageEditDone` 非 export**：此函数在 `script.js:8288` 定义但未 export，Bridge adapter 无法直接调用，需要在 adapter 注入时包装暴露。
2. **`swipe()` 签名过时**：从 `swipe(..., { swipeId })` 修正为完整签名 `swipe(event, direction, { source, repeated, message, forceMesId, forceSwipeId, forceDuration })`。
3. **`stopGeneration()` 双 AbortController 说明**：`StreamingProcessor.abortController`（流式）和模块级 `abortController`（非流式）是两个不同对象，均会被 abort。
4. **`doNewChat` 签名**：补充 `{ deleteCurrentChat }` 参数说明。

### 补充

1. **事件映射表**：补充了 ST 原生事件名与 Bridge 事件的对应关系，标注了 ST 事件命名不一致问题。
2. **P1+ 额外事件**：补充 `MESSAGE_EDITED`、`MESSAGE_SWIPED`、`MESSAGE_SWIPE_DELETED`、`MESSAGE_REASONING_*`、`CHAT_CREATED`/`CHAT_DELETED`、`STREAM_TOKEN_RECEIVED`、`STREAM_REASONING_DONE`、`TOOL_CALLS_*` 等事件；其中 `MESSAGE_EDITED` 和 `STREAM_TOKEN_RECEIVED` 已在当前 adapter 路径中落地。
3. **代码入口地图大幅扩展**：新增 tool-calling.js、reasoning.js、swipe-picker.js、authors-note.js、cfg-scale.js、slash-commands、constants.js、sse-stream.js、server-main.js、settings.js 等模块。
4. **角色 API 补全**：新增 rename、edit-avatar、edit-attribute、merge-attributes、duplicate、delete 路由。
5. **设置 API 快照路由**：新增 get-snapshots、make-snapshot、load-snapshot、restore-snapshot。
6. **新增风险章节**：12.6 Tool Calling 和 Reasoning、12.7 SWIPE_STATE 和并发保护。
7. **`openCharacterChat` 内部行为**：详细说明了 5 步内部流程和 `this_chid` 前置条件。
8. **`Generate()` 完整签名和流程**：补充了 swipeState 保护、ToolManager 检查、auto_swipe 等关键步骤。
9. **`saveReply()` 新增字段**：reasoning、reasoningSignature、imageUrls。
10. **`extra.tool_invocations`** 对消息过滤的影响说明。

### 验证通过（无需修改）

- 所有 P0 命令和事件的 Bridge 设计
- 单一事实源分工
- API 路由路径和方法
- 核心函数名和所在文件
- 架构分层和迁移路线建议
- JSONL 聊天文件结构
- ChatMessage 核心字段
