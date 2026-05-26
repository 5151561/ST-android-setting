# SillyTavern Chat 原生界面迁移方案

版本：0.2  
日期：2026-05-26  
状态：草案  
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
| `runtime.ready` | JS -> Android | ST 前端初始化完成，可以接收命令 |
| `runtime.error` | JS -> Android | runtime 初始化或命令执行失败 |
| `chat.loaded` | JS -> Android | 聊天打开完成，携带 snapshot |
| `chat.changed` | JS -> Android | 当前聊天文件或角色变化 |
| `message.added` | JS -> Android | 用户消息或 AI 消息新增 |
| `message.updated` | JS -> Android | streaming delta、编辑、swipe 切换等 |
| `message.deleted` | JS -> Android | 消息删除 |
| `generation.started` | JS -> Android | 生成开始 |
| `generation.ended` | JS -> Android | 生成完成 |
| `generation.stopped` | JS -> Android | 用户停止生成 |
| `generation.error` | JS -> Android | 生成失败 |
| `bridge.result` | JS -> Android | 命令成功结果 |
| `bridge.error` | JS -> Android | 命令失败结果 |

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
| 主聊天逻辑 | `SillyTavern/public/script.js` | 初始化、角色选择、聊天读取/保存、消息渲染、发送、生成、编辑、删除、swipe |
| 单聊辅助 | `SillyTavern/public/scripts/chats.js` | 附件、文件嵌入、媒体、隐藏消息、Data Bank、聊天工具初始化 |
| 群聊逻辑 | `SillyTavern/public/scripts/group-chats.js` | 群聊读取/保存、群成员、群聊生成、新建/删除/导入群聊 |
| Chat Completion | `SillyTavern/public/scripts/openai.js` | OpenAI/Claude/OpenRouter 等 Chat Completion 提示词组装和请求 |
| 事件总线 | `SillyTavern/public/scripts/events.js` | 消息、聊天、生成、角色、群聊等生命周期事件 |
| API 注册 | `SillyTavern/src/server-startup.js` | `/api/chats`、`/api/characters`、`/api/groups`、`/api/backends/*` 路由挂载 |
| 聊天 API | `SillyTavern/src/endpoints/chats.js` | 聊天文件读写、导入导出、搜索、群聊聊天文件 |
| 角色 API | `SillyTavern/src/endpoints/characters.js` | 角色列表、角色详情、角色聊天列表、角色增删改导入导出 |
| 群聊 API | `SillyTavern/src/endpoints/groups.js` | 群聊元数据增删改查 |

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

1. `CharacterListScreen`：继续复用当前原生角色列表。
2. `CharacterDetailScreen`：历史聊天列表可从这里进入 Chat。
3. `NativeChatScreen`：当前角色聊天。
4. `ChatRuntimeWebViewHost`：不可作为主要视觉 UI，只负责保持 ST runtime。

从原生角色页进入 Chat 时，先通过 API 确定 `avatar_url` 和目标 `chatFile`，再通过 Bridge 发送 `chat.openCharacter`，由 ST runtime 选择角色并打开聊天。

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
| 新建聊天 | `doNewChat()` | P0 | Bridge |
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
| 编辑 | `messageEditDone()` 后保存 | P1 | Bridge |
| 删除 | `deleteMessage()` | P1 | Bridge |
| 隐藏/取消隐藏 | 设置 `message.is_system` | P1/P2 | Bridge |
| swipe 切换 | `syncSwipeToMes()` | P1/P2 | Bridge |
| swipe 生成 | `swipe(..., { swipeId })` | P2 | Bridge |
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
4. `swipes` + `swipe_id` 是候选回复机制，切换 swipe 时要同步 `mes`。
5. 原生端必须保留未知字段，不要在镜像转换时丢字段。

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
  -> JS runtime 复用 sendTextareaMessage / Generate('normal')
      -> processCommands()
      -> sendMessageAsUser()
      -> saveChatConditional()
      -> 组装提示词、世界书、Author's Note、CFG、扩展注入
      -> sendGenerationRequest() 或 sendStreamingRequest()
      -> saveReply()
      -> saveChatConditional()
  -> JS runtime 将 message/generation 事件回传原生
  -> 原生 ChatStore 增量更新 UI
```

P0 可以采用“发送后等待 snapshot 刷新”的保守方式；P1 再做 streaming delta 级别更新。

### 8.3 停止生成

```text
用户点击停止
  -> Android 发送 generation.stop
  -> JS runtime 调用 stopGeneration()
      -> streamingProcessor.onStopStreaming()
      -> abortController.abort()
      -> emit GENERATION_STOPPED
  -> JS runtime 回传 generation.stopped + 最新 snapshot
```

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
| `/version` | GET | 版本信息 | P0/P1 |
| `/api/settings/get` | POST | 获取全局设置 | P0 |
| `/api/settings/save` | POST | 保存全局设置 | P1 |

### 9.2 角色 API

| API | 方法 | 用途 | 推荐通道 |
|---|---|---|---|
| `/api/characters/all` | POST | 角色列表 | API |
| `/api/characters/get` | POST | 单个角色完整数据 | API |
| `/api/characters/chats` | POST | 角色聊天文件列表 | API |
| `/api/characters/create` | POST FormData | 新建角色 | API，已由角色原生页承接 |
| `/api/characters/edit` | POST FormData | 保存角色 | API，已由角色原生页承接 |
| `/api/characters/import` | POST FormData | 导入角色 | API，已由角色原生页承接 |
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

注意：这些接口不应作为 Chat 原生迁移 P0 的直接入口。P0/P1 由 Bridge 调用 ST runtime，后续若要做完整原生生成，需要单独设计 Prompt Builder、World Info、Extensions、Streaming Processor 和兼容测试。

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

阶段性目标应该是“原生 UI 体验明显改善，但聊天语义仍和原版 ST 一致”。在没有契约测试前，不建议重写提示词组装和生成请求。
