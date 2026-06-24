# Native Chat Runtime Exit Status

日期：2026-06-24

当前口径：App 内聊天不再嵌入隐藏 WebView runtime。`NativeChatScreen` 是唯一聊天界面，`NativeChatEngine` 是唯一 `ChatEngine` 实现；本地 Node.js + SillyTavern HTTP 服务仍保留，原生侧通过 `TavernCoreApi` 调用后端 endpoint。

已删除的旧 runtime 资产：

- `ChatWebViewScreen`
- `STAndroidBridge`
- `WebViewNavigator` / `WebViewTarget`
- `ChatRuntimeBridge`
- `BridgeChatEngine`
- `BridgeMessage` / `BridgeEvent` 旧事件信封
- `chat_runtime_adapter.js`

当前原生服务边界：

- `NativeGenerationRouter`：覆盖连接页 provider 到原生 chat/text completion route。
- `QuickReplyRuntime`：读取本地 QuickReplies JSON，渲染可见按钮，普通文本可发送，disable-send 可注入草稿，slash command 明确报 unsupported。
- `DataBankRepository`：聚合 settings、角色 raw data、chat metadata 的 Data Bank 附件。
- `ItemizedPromptStore`：生成时记录 prompt 组件，消息菜单按 messageId 查询。
- `ChatModels`：保留原生消息、附件、提示词和 Data Bank 数据结构，不再包含 Bridge 命令/事件模型。

TDD 记录：

- runtime 删除架构守卫：红 -> 绿；重构：`NativeChatLogger` 从旧 actions 文件拆出。
- provider 覆盖：红 -> 绿；重构：新增 `NativeGenerationRouter`。
- `send/regenerate/continue` JSONL 保存：红 -> 绿；重构 no op。
- Quick Replies：红 -> 绿；重构 no op。
- Data Bank：红 -> 绿；重构 no op。
- Itemized Prompt：红 -> 绿；重构 no op。
- Bridge 模型死代码清理：架构守卫补充旧事件信封禁用项；重构：`ChatBridgeModels` -> `ChatModels`。

最终验收仍需要真机手动清单：单聊、群聊、全部连接页 provider 至少一次生成、停止、继续、重写、附件发送、Quick Replies、提示词分析、Data Bank、checkpoint/branch。
