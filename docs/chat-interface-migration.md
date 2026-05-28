# SillyTavern Chat 原生界面迁移方案

版本：0.4  
日期：2026-05-28
状态：**P0 代码已收口，待真机验证**（已对照 SillyTavern 源码审查）
适用范围：ST-android 下一阶段 Chat 原生化、JS Bridge、SillyTavern 运行时复用、API 对接

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
| 当前打开聊天切换、新建、保存 | Runtime WebView 优先 | 通过 Bridge 命令触发，保持 ST 前端事件一致 |
| 附件选择、分享、复制、通知、TTS/STT | Android 原生能力 | 通过 `STAndroidBridge` 提供给 Web runtime 或直接由原生 UI 调用 |

换句话说：**活动聊天由 JS runtime 负责写，原生 UI 负责显示和发命令；非活动数据管理优先走本地 API。**

### 3.3 Bridge 不是替代所有 API

下一阶段“通过 JS Bridge”主要指 Chat 运行时控制和事件同步，不代表所有数据请求都走 Bridge。

| 场景 | 推荐通道 | 原因 |
|---|---|---|
| 发送消息、停止、重生成、继续 | Bridge | 必须复用 `Generate()`、streaming、扩展和保存语义 |
| 当前活动聊天加载、切换 | Bridge 优先 | 保持 `this_chid`、`chat`、事件总线和 UI runtime 一致 |
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
| `message.deleted` | JS -> Android | 消息删除（对应 ST `MESSAGE_DELETED`） |
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
| 导航入口 | `app/src/main/java/io/github/sanitised/st/MainActivity.kt` | Chat tab 从 WebView 页面切到原生 Chat screen |

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
| 编辑 | `messageEditDone(div)` 后保存（注意：此函数非 export，Bridge 需要包装调用） | P1 | Bridge |
| 删除 | `deleteMessage()` | P1 | Bridge |
| 隐藏/取消隐藏 | 设置 `message.is_system` | P1/P2 | Bridge |
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
| `ChatRuntimeBridge` | 封装 `evaluateJavascript`、命令 ID、结果等待、超时、日志 |
| `ChatRuntimeWebViewHost` | 管理 runtime WebView 生命周期、ready 状态、注入 adapter |
| `ChatBridgeEventHandler` | 解析 `postChatEvent`，更新 `ChatStore` |
| `ChatApiService` | 聊天文件列表、导入导出、搜索、最近聊天等 API |
| `MessageRenderer` | Markdown、reasoning、media、files、swipe、tool calls 展示 |
| `AttachmentService` | Android 文件选择、上传、预览、删除 |

## 11. 迁移优先级

### P0：原生 Chat 镜像可用

1. 保留 Runtime WebView 加载 ST 原版前端。
2. 注入 Chat runtime adapter。
3. 新增 Bridge 事件：`runtime.ready`、`chat.loaded`、`message.added`、`message.updated`、`generation.started`、`generation.ended`、`generation.error`。
4. 原生 `NativeChatScreen` 展示 snapshot 消息。
5. 从角色页进入 Chat 时通过 `chat.openCharacter` 打开指定角色/聊天。
6. 原生输入栏通过 `chat.send` 发送普通文本。
7. 原生停止按钮通过 `generation.stop` 停止生成。
8. 退出重进后仍能恢复当前聊天。

### P1：常规聊天体验

1. Streaming delta 增量更新。
2. 新建聊天、聊天文件列表、切换历史聊天。
3. 重生成、继续生成。
4. 消息复制、编辑、删除。
5. 保存 integrity 错误处理和用户提示。
6. 基础 swipe 展示与切换。
7. bridge 超时、runtime 崩溃、reload 恢复。

### P2：接近 SillyTavern 核心体验

1. 群聊打开、发送、停止、历史切换。
2. 附件上传和展示，保留 `extra.files`、`extra.media`。
3. Author's Note、CFG、世界书基础接入。
4. Slash commands 的结果和错误展示。
5. 消息隐藏/取消隐藏。
6. 文件嵌入到提示词上下文。

### P3：高级能力

1. 扩展系统兼容策略。
2. Quick Replies。
3. TTS、翻译、生图。
4. logprobs。
5. checkpoint、branch。
6. tool calls 渲染。
7. itemized prompts。
8. Data Bank。

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
8. 保存 integrity 错误不会静默覆盖。
9. Bridge 命令超时会提示，并允许重试。

### P2 验收

1. 可以打开群聊。
2. 可以发送群聊消息并触发群成员回复。
3. 可以保存和切换群聊历史。
4. 可以上传并展示图片或文件附件。
5. Author's Note、CFG、世界书至少有基础接入。
6. Slash command 不会破坏普通发送。

## 14. 推荐迁移路线

推荐路线：

1. 先做 Runtime WebView 管理和 Bridge adapter，不改可见 Chat UI。
2. 再做原生 Chat 只读镜像，确保 snapshot 和事件能稳定同步。
3. 接管原生输入栏：发送、停止、生成状态。
4. 补重生成、继续、新建、历史聊天切换。
5. 补编辑、删除、swipe、附件。
6. 补群聊、Author's Note、世界书和扩展相关能力。
7. 最后再评估是否抽离部分生成链路到原生端。

阶段性目标应该是”原生 UI 体验明显改善，但聊天语义仍和原版 ST 一致”。在没有契约测试前，不建议重写提示词组装和生成请求。

## 15. P0 实现进度

日期：2026-05-28（v0.4b 收口）
状态：**P0 代码已收口，待真机验证**

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

### v0.4a 修订（代码审查后修复）

基于代码审查反馈，修复了以下 3 个关键问题：

1. **`runtime.ready` 时序修正**：不再在拿到 `eventSource` 时立即发送 `runtime.ready`。改为监听 ST 的 `APP_READY` 事件（对应 `event_types.APP_READY`），该事件在 ST 完成 `firstLoadInit`、角色加载、初始聊天渲染后才触发。ST 的 `EventEmitter` 会对 `APP_READY` 进行 auto-fire，adapter 注入晚于 ready 时也会收到该事件。
2. **函数访问路径统一为 `getContext()`**：
   - `generation.regenerate` / `generation.continue`：从 `window.Generate` 改为 `ctx.generate`（`getContext()` 上的 `generate` 属性，映射自 `Generate`）
   - `generation.stop`：优先使用 `ctx.stopGeneration`，回退到 DOM `#mes_stop`
   - `chat.openCharacter`：从 `ctx.selectCharacterById || window.selectCharacterById` 改为仅 `ctx.selectCharacterById`；`openCharacterChat` 同理
   - `chat.new`：由于 `doNewChat` 是 ES module export 但不在 `getContext()` 上，改为 DOM 点击 `#option_start_new_chat`（ST 自身的新建聊天按钮）
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
| `app/src/main/java/io/github/sanitised/st/chat/NativeChatScreen.kt` | 完整 Compose Chat UI：标题栏（角色名 + 生成状态）、消息气泡列表、输入栏（发送/停止按钮）。内嵌不可见 `ChatWebViewScreen` 作为 runtime 容器 | §10 `NativeChatScreen` + `MessageRenderer`（基础版） |
| `app/src/main/assets/chat_runtime_adapter.js` | 注入 WebView 的 JS 适配器：监听 ST `eventSource` 事件、构建 snapshot、处理 Android 发来的命令 | §4.1 `STAndroidChatRuntime`、§4.3 命令、§4.4 事件 |

#### 修改文件

| 文件 | 变更 |
|---|---|
| `STAndroidBridge.kt` | 新增 `postChatEvent` `@JavascriptInterface` 方法和 `chatEventHandler` 参数 |
| `WebViewNavigator.kt` | 新增 `injectChatRuntimeAdapter()` 从 assets 加载 JS adapter，`resetInjectionState()` 处理页面重载 |
| `ChatWebViewScreen.kt` | 新增 `chatEventHandler` 和 `onWebViewReady` 参数；`onPageFinished` 中注入 adapter；页面 start/dispose 时重置注入状态 |
| `MainActivity.kt` | Chat tab 替换为 `NativeChatScreen`；创建共享 `ChatStore` 和 `ChatRuntimeBridge`；角色详情页和角色列表进入聊天时记录 `WebViewTarget.CharacterChat`，等待 runtime ready 后统一重放 |
| `PrototypeCharacterScreens.kt` | `PrototypeCharacterLibraryScreen` 和 `PrototypeCharacterProfileScreen` 接入 `onOpenChat`，角色卡与详情页都能进入原生 Chat |

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
| 9 | 聊天由 ST runtime 保存，重启 App 后能恢复 | ✅ 设计保证 | 活动聊天写入由 ST runtime 负责，原生端不写 JSONL |
| 10 | Runtime WebView reload 后能重新同步 snapshot | ✅ 已实现 | `onPageStarted` 重置注入状态 → `onPageFinished` 重新注入 adapter → 重新 `runtime.ready` + snapshot |

#### 额外已实现（超出 P0 最小范围）

- **Streaming token 更新**：`STREAM_TOKEN_RECEIVED` 事件以 80ms 节流回传 `stream.token`，原生端实时更新最后一条消息文本
- **重生成和继续**：`ChatRuntimeBridge` 已暴露 `regenerate()` 和 `continueGeneration()`，JS adapter 已实现 `generation.regenerate` 和 `generation.continue` 命令
- **新建聊天**：`ChatRuntimeBridge.newChat()` → `chat.new` → JS 调用 `doNewChat()`
- **`generation.ended` 后自动刷新 snapshot**：确保生成结束后原生端拿到完整的最终消息

### 待验证（需真机测试）

1. 从最近聊天或角色卡进入 Chat 时，标题和消息必须切到对应角色/聊天；如果 JS runtime 找不到角色，Chat 页要显示明确错误而不是继续显示上一段聊天。
2. 输入法弹出时，输入栏必须抬到键盘上方，消息列表必须保持最后一条消息可见。
3. 生成回复时，streaming token 更新不能明显卡顿；必要时通过 WebView 日志和诊断导出判断 `postChatEvent` 是否过密。
4. App 切后台再回来后，当前角色标题、消息列表、输入栏可用状态必须保持一致；如果 WebView 重载，需要重新进入“正在连接/正在打开”状态并恢复 snapshot。
5. Runtime WebView 的 1dp 隐藏宿主无法直接肉眼观察，验收以 `runtime.ready` 后能打开真实目标、能发送/停止、reload 后能重新同步为准。

### 与方案的偏差和决策

1. **`ChatController` 和 `ChatBridgeEventHandler` 合并**：方案第 10 节建议分开，实现时合并到 `ChatRuntimeBridge` 中，因为 P0 阶段命令和事件处理紧密耦合，拆分反而增加复杂度。P1 如果逻辑膨胀再拆分。
2. **Runtime WebView 作为 NativeChatScreen 的子组件**：方案建议 `ChatRuntimeWebViewHost` 独立管理生命周期，实现时嵌入 `NativeChatScreen` 的 1dp 隐藏宿主中，复用现有 `ChatWebViewScreen` 的服务启动和健康检查逻辑。
3. **`chat.send` 和 `chat.new` 通过 DOM 操作**：`chat.send` 直接设置 `#send_textarea.value` 并 click `#send_but`；`chat.new` 点击 `#option_start_new_chat`。两者的目标函数（`sendTextareaMessage`、`doNewChat`）是 ES module export 但不在 `getContext()` 上，DOM 操作是唯一可靠的调用路径，但依赖 ST 前端 DOM 结构不变。
4. **其他命令统一走 `getContext()`**：`generation.stop`（`ctx.stopGeneration`）、`generation.regenerate` / `generation.continue`（`ctx.generate`）、`chat.reload`（`ctx.reloadCurrentChat`）、`chat.openCharacter`（`ctx.selectCharacterById` + `ctx.openCharacterChat`）全部通过 `SillyTavern.getContext()` 暴露的公开 API 调用，不依赖 `window` 全局变量。
5. **Chat UI 字符串暂未国际化**：P0 使用英文硬编码字符串，后续统一添加到 `strings.xml` 和 `values-zh-rCN/strings.xml`。

### 下一步（P1）

1. 真机验证 P0 全部验收项
2. Streaming delta 增量更新优化（当前已有基础 token 回传，需测试性能）
3. 新建聊天 UI 入口
4. 聊天文件列表和历史聊天切换
5. 重生成、继续生成 UI 按钮
6. 消息复制、编辑、删除
7. Bridge 超时处理和 runtime 崩溃恢复

## 16. v0.3 审查变更记录（历史）

v0.3 基于对 SillyTavern 源码的逐行审查，修正和补充了以下内容：

### 修正

1. **`messageEditDone` 非 export**：此函数在 `script.js:8288` 定义但未 export，Bridge adapter 无法直接调用，需要在 adapter 注入时包装暴露。
2. **`swipe()` 签名过时**：从 `swipe(..., { swipeId })` 修正为完整签名 `swipe(event, direction, { source, repeated, message, forceMesId, forceSwipeId, forceDuration })`。
3. **`stopGeneration()` 双 AbortController 说明**：`StreamingProcessor.abortController`（流式）和模块级 `abortController`（非流式）是两个不同对象，均会被 abort。
4. **`doNewChat` 签名**：补充 `{ deleteCurrentChat }` 参数说明。

### 补充

1. **事件映射表**：补充了 ST 原生事件名与 Bridge 事件的对应关系，标注了 ST 事件命名不一致问题。
2. **P1+ 额外事件**：补充 `MESSAGE_EDITED`、`MESSAGE_SWIPED`、`MESSAGE_SWIPE_DELETED`、`MESSAGE_REASONING_*`、`CHAT_CREATED`/`CHAT_DELETED`、`STREAM_TOKEN_RECEIVED`、`STREAM_REASONING_DONE`、`TOOL_CALLS_*` 等事件。
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
