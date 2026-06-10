# Chat 原生化 Phase 0 基线检查

日期：2026-06-03
范围：只做 Phase 0 检查和基线冻结；不做 Phase 1 迁移，不改生成/消息操作实现。

> 2026-06-10 复核：本文件完成的是 Phase 0 交付 1、2、6。交付 3（`chat-contract-fixtures` 样本集）、交付 4（native vs ST payload 对照测试）、交付 5（消息操作 vs ST 契约测试）尚未落地，仍是进入 Phase 2 前的欠账。

## 1. 检查目标

Phase 0 的目标是先回答三个问题：

1. 当前“原生生成（实验）”到底哪些路径已经可用。
2. 哪些 chat 行为仍依赖隐藏 WebView / Bridge / ST 前端状态。
3. 现有测试能否作为后续迁移的保护网。

## 2. 当前原生生成路由矩阵

| 场景 | 当前路由 | 依据 | Phase 0 结论 |
|---|---|---|---|
| 实验开关关闭 | `BridgeChatEngine` | `MainActivity` 按 `nativeGeneration` 注入 engine | 全部走隐藏 WebView runtime |
| 实验开关打开 + 单聊 + 无附件 + `main_api=openai` | `NativeChatEngine` / `CHAT_COMPLETION` | `engineMode()` 返回 `CHAT_COMPLETION` | 原生 prompt + 原生流式/非流式生成 + 原生 JSONL 保存 |
| 实验开关打开 + 单聊 + 无附件 + `textgenerationwebui` + `ooba/koboldcpp/llamacpp/ollama` + 简单模板 | `NativeChatEngine` / `TEXT_COMPLETION` | `TextPromptBuilder.supports()` 通过 | 原生 Text Completion 已接入首批后端 |
| 实验开关打开 + 单聊 + 待发送附件 | Bridge fallback | `NativeChatEngine.send()` 先检查 `pendingAttachments` | 附件语义仍依赖 ST 前端 |
| 实验开关打开 + Text Completion 复杂模板/作者注/unsupported api_type | Bridge fallback | `TextPromptBuilder.supports()` 拒绝 | fallback 是显式保守策略 |
| `regenerate()` | Bridge fallback | `NativeChatEngine.regenerate()` 直接调用 Bridge | 原生 swipe/history 语义未冻结前不能迁 |
| `continueGeneration()` | Bridge fallback | `NativeChatEngine.continueGeneration()` 直接调用 Bridge | 原生 continue 还未做 |
| 单聊原生生成停止 | 原生 stop | `stopTargetForGeneration(character, NATIVE)` | 下一 token 截止，保留已生成部分 |
| NativeChatScreen 群聊发送 | Bridge fallback | `NativeChatEngine.send()` 对 `store.mode == "group"` 走 Bridge | 该入口仍依赖 WebView |
| 独立群聊页面 AI 回复 | `NativeGroupGenerator` | `GroupChatScreen` 直接创建 generator | 群聊 REST + 原生生成 MVP 已存在，但尚未统一到单聊 runtime |

历史重要发现：Phase 0 时单聊原生生成成功后仍调用 `bridge.reloadChat()`，让隐藏 WebView runtime 从磁盘重新对齐。这说明当时原生生成已经能写事实数据，但 WebView 仍承担“历史运行时同步器”的职责。该点已在 Phase 1 中改为“原生成功路径不自动 reload；仍走 Bridge 的写操作执行前显式 reload 并等待完成”。

## 3. WebView / Bridge 依赖地图

| Bridge 命令 | Android 入口 | Adapter handler | ST public 依赖 | 当前状态 |
|---|---|---|---|---|
| `chat.send` | `BridgeChatEngine.send()` / native fallback | `handleSend` | ST send pipeline、附件、扩展、生成状态 | 单聊文本部分已原生；附件/群聊/unsupported 仍依赖 |
| `generation.stop` | `BridgeChatEngine.stop()` / bridge route | `handleStop` | ST generation controller | 原生单聊 native route 可本地 stop；bridge route 仍依赖 |
| `generation.regenerate` | `NativeChatScreen` / `NativeChatEngine.regenerate()` | `handleRegenerate` | ST swipe/regenerate 语义 | 未迁移 |
| `generation.continue` | `NativeChatScreen` / `NativeChatEngine.continueGeneration()` | `handleContinue` | ST continue 语义 | 未迁移 |
| `message.edit` | `NativeChatScreen` edit save | `handleEditMessage` | `chat[]`、`MESSAGE_EDITED`、DOM formatting、`saveChat` | 未迁移 |
| `message.delete` | delete dialog | `handleDeleteMessage` | `deleteMessage`、itemized prompt cleanup、mesid 更新 | 未迁移 |
| `message.hide/unhide` | action sheet | `handleHideMessage` / `handleUnhideMessage` | `scripts/chats.js` hide/unhide semantics | 未迁移 |
| `message.swipePrevious/Next` | message swipe controls | `handleSwipe` | `swipe()`、swipe button refresh、save | 未迁移 |
| `authorsNote.set` | 作者注 dialog | `handleSetAuthorsNote` | `chat_metadata` + `saveChat` | 未迁移 |
| `cfg.set` | CFG dialog | `handleSetCfg` | `chat_metadata` + `saveChat` | 未迁移 |
| `quickReply.list/execute` | quick reply strip | `handleListQuickReplies` / `handleExecuteQuickReply` | `globalThis.quickReplyApi` | 未迁移 |
| `itemizedPrompt.get` | prompt analysis sheet | `handleGetItemizedPrompt` | `script.js.itemizedPrompts` / `scripts/itemized-prompts.js` | 未迁移 |
| `dataBank.list` | Data Bank sheet | `handleListDataBank` | attachments extension / vectors Data Bank | 未迁移 |
| `chat.createCheckpoint` | checkpoint dialog | `handleCreateCheckpoint` | `scripts/bookmarks.js.createNewBookmark` | 未迁移 |
| `chat.createBranch` | action sheet | `handleCreateBranch` | `scripts/bookmarks.js.branchChat` | 未迁移 |
| `chat.openCheckpoint` | branch/checkpoint sheet | `handleOpenCheckpoint` | ST open chat functions | 未迁移 |
| `runtime.getSnapshot` | initial sync / refresh | `buildSnapshot` | ST frontend globals | 仍是 WebView snapshot 来源 |
| `chat.reload` | header reload + native generation success sync | `handleReload` | ST frontend reload current chat | 仍承担原生落盘后的同步职责 |

## 4. `public` 侧高风险语义

这批语义在 `SillyTavern/public` 中仍深度绑定前端运行时，后续迁移前需要单独契约测试：

1. `script.js` 的 `extension_prompts`、Prompt Manager、itemized prompt、saveChat。
2. `scripts/world-info.js` 的完整世界书扫描、递归、概率、分组、depth、automation。
3. `scripts/extensions/quick-reply` 的快捷回复列表、自动触发和 slash command。
4. `scripts/extensions/regex` 的输入/输出/reasoning/display 正则。
5. `scripts/extensions/attachments`、`vectors`、Data Bank 的附件与检索注入。
6. `scripts/extensions/tts`、`translate`、`stable-diffusion`、`caption`、`memory`、`token-counter` 的 chat 可见能力。
7. `scripts/reasoning.js`、`scripts/logprobs.js`、`scripts/tool-calling.js` 的 extra 字段和生成中增量语义。

## 5. 已有测试基线

本次 Phase 0 把以下现有测试纳入基线：

| 测试 | 覆盖 |
|---|---|
| `NativeEngineModeTest` | Chat/Text Completion 原生路由与 fallback 条件 |
| `NativeGenerationRouteTest` | native/bridge stop target |
| `NativeChatEngineRollbackTest` | 原生生成失败时乐观消息回滚 |
| `NativeChatLoaderTest` | 原生 API 打开角色聊天并构造 snapshot |
| `PromptBuilderTest` | Chat Completion prompt、persona、世界书、作者注、裁剪 |
| `TextPromptBuilderTest` | Text Completion prompt、模板支持/拒绝、stop strings、上下文裁剪 |
| `WorldInfoScannerTest` | 最小世界书扫描子集 |
| `GenerationDeltaParserTest` | 多 provider SSE delta 解析 |
| `NativeGroupGeneratorTest` | 群聊 speaker 选择策略 |
| `ChatBridgeModelsTest` | Bridge event/model 解析 |
| `GroupChatMigrationContractTest` | 群聊迁移文档/代码契约 |
| `chat_runtime_adapter_contract.test.mjs` | JS adapter 合同与回归 |

验证命令：

```bash
./gradlew testDebugUnitTest --tests "io.github.sanitised.st.chat.engine.*" --tests "io.github.sanitised.st.chat.prompt.*" --tests "io.github.sanitised.st.chat.NativeChatLoaderTest" --tests "io.github.sanitised.st.chat.ChatBridgeModelsTest" --tests "io.github.sanitised.st.chat.GroupChatMigrationContractTest"
node --test app/src/test/js/chat_runtime_adapter_contract.test.mjs
```

验证结果：

1. Gradle 目标测试：`BUILD SUCCESSFUL`。
2. JS adapter 合同测试：Phase 0 记录时为 11 个测试全部通过；2026-06-10 复核后为 13 个测试全部通过。

## 5.1 Phase 0 未完成交付

| 欠账 | 当前状态 | 影响 |
|---|---|---|
| `chat-contract-fixtures` 样本集 | `app/src/test/resources` 尚未建立角色卡、persona、世界书、设置、聊天 JSONL、群聊、附件、扩展设置组合夹具 | Phase 2 无法直接做逐项 diff |
| native vs ST payload 对照测试 | 尚未有同一输入下比较 final generate payload、stop strings、世界书激活和保存结果的测试 | PromptAssembly 迁移缺少验收基础 |
| 消息操作 vs ST 契约测试 | 尚未对编辑、删除、隐藏、swipe、checkpoint、branch、reasoning、附件建立 ST 对照产物 | 后续迁移只能靠原生内部测试，不能证明和 ST 一致 |

## 6. Phase 0 结论

1. “原生生成（实验）”已有真实可用基线：单聊 Chat Completion、首批 Text Completion、JSONL 读写、流式解析、保守世界书扫描和群聊生成 MVP。
2. 隐藏 WebView 仍未退出运行时角色：消息操作、regenerate/continue、附件、扩展、itemized prompt、Data Bank、checkpoint/branch、完整 prompt 语义都还依赖 Bridge。
3. 第一条必须在 Phase 1 处理的迁移点很明确：单聊原生生成成功后不应再调用 `bridge.reloadChat()` 才能保持 UI/JSONL 一致；仍走 Bridge 的写命令必须在执行前完成显式对齐。
4. Phase 1 开始前，不应改 UI 行为；先补“原生 session 成为事实源”的失败测试，再迁移。

## 7. 下一步边界

下一轮才进入 Phase 1。建议只做一个红-绿-重构切口：

1. 红：写测试证明单聊原生生成成功后不需要 `ChatRuntimeBridge.reloadChat()`。
2. 绿：让 `NativeChatEngine` 成功路径只更新原生 store + JSONL，不再主动 reload WebView。
3. 重构：如果只需要移除同步调用并整理注释，记录 `no op` 或最小重构。
