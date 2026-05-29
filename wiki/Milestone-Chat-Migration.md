# 里程碑：Chat 原生界面迁移方案与技术规划

为了实现更流畅的移动端交互，项目规划了下一阶段的核心战场：**将 Chat 聊天页面彻底原生化**。本篇作为官方技术指南，系统阐述如何安全拆解 SillyTavern 复杂的聊天生成逻辑。

---

## 1. 核心架构：单一事实源 (Single Fact Source)

SillyTavern 的核心聊天并不是简单的 POST 请求，它的 Web 前端内部承载了极端复杂的提示词模板渲染、世界书触发扫描、历史消息截断、CFG 参数混合以及第三方插件扩展注入。

如果用 Compose 原生逻辑全量重写这套规则，不仅工作量巨大，还会彻底丢失与上游 SillyTavern AI 生态库的兼容性。

为此，我们设计了**“单一事实源”**的混合容器模式：

```
+-------------------------------------------------------------+
|                     原生 Compose UI 界面                     |
|  - 仅负责展现与排版                                           |
|  - 用户在输入框打字发送、点击重生成或滑动消息                    |
+-------------------------------------------------------------+
                               ^
                               | (双向信封 Bridge 实时流)
                               v
+-------------------------------------------------------------+
|                 后台运行的 WebView (JS 运行时)               |
|  - 作为“无头运行时”，隐密运行在后台                             |
|  - 维持完整的 SillyTavern 内存上下文与 Generate() 逻辑         |
|  - 执行世界书扫描、SSE 流式解析及与大模型后端的网络交互           |
+-------------------------------------------------------------+
```

* **单一写入权**：活动消息的实际读写和更新仍由后台 WebView 内的 JS 运行时掌握，确保 `chats[]` 数据与本地 JSONL 保存文件格式的天然统一。
* **原生只读镜像**：外层的 Compose 原生 UI 不直接操纵文件，它通过 Bridge 实时镜像展示 JS 传出的 Snapshot 快照，并将用户的输入抽象成命令信封，投喂给后台运行时去执行发送。

---

## 2. Bridge 消息信封协议与事件规范

后台运行时与原生 Compose 之间通过单通道、结构化的 JSON 信封包进行异步通信，极大地避免了传统 Android 桥接开发中“桥接方法爆炸”的问题。

### 2.1 消息信封 (Envelope) 结构
```json
{
  "id": "c044-6ab0-4a81",
  "kind": "command",
  "name": "chat.send",
  "payload": {
    "text": "大模型，请问什么是热力学第二定律？"
  },
  "timestamp": 1790000000000
}
```

* `id`：全局唯一 UUID，原生端可通过此 ID 注册并异步等待 JS 执行的回包（Result/Error）。
* `kind`：包括四种类型：`command` (命令)、`event` (事件)、`result` (成功响应)、`error` (失败响应)。
* `name`：具体的方法或事件标示。

### 2.2 核心命令与事件映射表

| 命令/事件名称 | 传递方向 | 详细含义与对应 ST 上游函数 |
|---|---|---|
| `runtime.ready` | JS -> Android | ST 前端首屏彻底初始化完成（映射 ST 的 `APP_READY`）。 |
| `chat.openCharacter` | Android -> JS | 指令：选中角色并加载特定历史聊天（调用 `selectCharacterById` 及 `openCharacterChat`）。 |
| `chat.send` | Android -> JS | 指令：让 ST 前端把文本塞入输入框并提交（调用 `sendTextareaMessage`）。 |
| `generation.stop` | Android -> JS | 指令：中止流式输出（调用 `stopGeneration`，自动 abort 后台请求）。 |
| `message.added` | JS -> Android | 事件：新增了用户或 AI 的气泡（映射 `MESSAGE_SENT` / `MESSAGE_RECEIVED`）。 |
| `stream.token` | JS -> Android | 事件：流式 token 生成 delta。原生端对其进行 **50ms 节流合并**，防止渲染过频。 |

---

## 3. 高级复杂场景的原生承接设计

在聊天原生化的后期阶段，有三个重度依赖上游底层状态的流程需要重点承载：

### 3.1 工具调用 (Tool Calling) 状态机协同
当接入的模型决定调用本地或三方工具时，SillyTavern 在流式期间不会生成普通的文字，而是由 `StreamingProcessor` 在内存中持续收集 `toolCalls` 信息。
* **递归生成**：工具执行完毕后，上游前端会自动携带结果并递归调用 `Generate(..., {depth: depth + 1})`，直到输出最终文字。
* **原生适配**：Bridge 会派发 `TOOL_CALLS_PERFORMED` 和 `TOOL_CALLS_RENDERED` 事件。原生 UI 需要能够渲染出非文字状态的气泡（如“工具箱：正在调用网络搜索...”），并优雅展示返回的结构化卡片，绝不能把工具调用状态当作普通空文本消息隐藏掉。

### 3.2 推理模型思考链 (Reasoning / Thinking) 展示
随着推理模型（如 DeepSeek-R1、Reasoning 预设模型）的普及，生成的消息中经常会夹带独立的 `reasoning` 内容。
* **数据结构**：SillyTavern 会通过 `saveReply({ reasoning, reasoningSignature, ... })` 在消息实体的 `extra.reasoning` 分支中保存完整的思维链。
* **流式生成**：在流式生成期间，adapter 必须捕获 `STREAM_REASONING_DONE` 并在原生气泡上开辟专属的“思考折叠区域”，支持实时展示思考状态。

### 3.3 并发与 SWIPE_STATE 保护
SillyTavern 内部使用全局 `swipeState`（`NONE`、`SWIPING`、`EDITING`）来防止用户在前一次生成还未保存完成、或者正在滑动生成候选（Swipe）时乱打字破坏 integrity。
* **原生拦截**：`sendTextareaMessage` 在 `swipeState != NONE` 时会无情返回。
* **原生配合**：原生 Compose UI 的输入框发送按钮必须监听 `SWIPE_STATE` 的扭转。当处于 `SWIPING`（左右滑动生成中）或 `EDITING`（行内编辑未提交）状态时，**输入栏按钮自动进入禁用（Disabled）状态并显示对应提示**，防止发生双向并发冲突与文件损坏。
