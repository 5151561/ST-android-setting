# Chat 原生化 Phase 1 进度

日期：2026-06-10
分支：`codex/native-chat-phase1`

## 1. 当前状态

Phase 1 本轮完成到 v0.6.1 验收口径：单聊打开、编辑、删除、隐藏/取消隐藏、swipe 和原生生成成功路径都可以由原生侧完成，不要求隐藏 WebView runtime 作为事实源；仍走 Bridge 的 regenerate / continue / fallback send / fallback message ops 会在写操作前强制 reload 对齐，adapter 会等待 reload 完成后再执行后续写命令。单聊和群聊生成启动后都会释放命令队列，`generation.stop` 不会被长生成阻塞。checkpoint / branch 已有原生实现，但 v0.6.1 仍明确不计入 Phase 1 验收。

## 2. TDD 记录

### 切口 A：原生生成成功路径不 reload WebView，并替换源码 grep 测试

新增测试：

```text
NativeChatEnginePhase1ContractTest.nativeSuccessPathSavesJsonlWithoutReloadingCompatibilityRuntime
```

失败原因：

```text
Type mismatch: RecordingBridgeActions but ChatRuntimeBridge was expected
Unresolved reference: NativeChatLogger
```

说明：旧测试通过读取 `.kt` 源码子串判断 `reloadChat()`，不符合 v0.5 测试红线。本轮改为 fake `ChatRuntimeBridgeActions` 记录运行调用，断言原生成功保存后没有 `reloadChat()`。

### 绿

最小实现：

1. 抽出 `ChatRuntimeBridgeActions` 和 `DefaultChatRuntimeBridgeActions`，让测试可用 fake 记录真实调用。
2. 为 `NativeChatEngine` 增加可替换 `NativeChatLogger`，避免 JVM 单元测试依赖 Android `Log`。
3. 保持原生生成成功保存 JSONL 后不调用 `bridge.reloadChat()`。

重构：小清理。

用 bridge actions 接口替换直接依赖具体 `ChatRuntimeBridge`，是为了把契约测试从源码文本转为行为断言。

### 切口 A2：Bridge 写操作前强制对齐

新增测试：

```text
NativeChatEnginePhase1ContractTest.unsupportedNativeSendAlignsWebViewBeforeBridgeSend
NativeChatEnginePhase1ContractTest.bridgeGenerationWritesAlignWebViewBeforeDispatch
```

红：

```text
expected:<[reloadChat, sendMessage:hello]> but was:<[sendMessage:hello]>
expected:<[reloadChat, regenerate, reloadChat, continueGeneration]> but was:<[regenerate, continueGeneration]>
```

绿：

1. 新增 `NativeChatEngine.runBridgeWrite`。
2. 群聊/附件/unsupported API 的 fallback send、`regenerate()`、`continueGeneration()` 都先调用 `reloadChat()`，再派发 Bridge 写命令。
3. `stop()` 不是写操作，保持原逻辑。

重构：`no op`。

对齐规则集中在一个小函数里，当前没有进一步拆分必要。

### 切口 B：原生 JSONL MessageOps / Swipe / Checkpoint / Branch

新增测试：

```text
NativeChatJsonOpsTest
```

红：

```text
Unresolved reference: NativeChatJsonOps
Unresolved reference: NativeAttachmentKind
Unresolved reference: NativeMediaDisplay
```

绿：

1. 新增 `NativeChatJsonOps`。
2. 实现编辑、删除、隐藏/取消隐藏、上移/下移、reasoning 写入/删除、附件/媒体删除、`media_display=list/gallery`。
3. 实现 swipe 切换、创建、删除，并同步 `mes` / `swipes` / `swipe_id` / `swipe_info`。
4. 实现 checkpoint/branch 的 JSONL 前缀复制，并在当前消息 `extra.bookmark_link` / `extra.branches` 写回链接。

重构：`no op`。

本轮实现集中在一个纯 JSONL 操作对象里，没有额外拆分。

### 切口 C：原生 ChatRepository / Runtime

新增测试：

```text
NativeChatRuntimeTest
```

红：

```text
Unresolved reference: NativeChatRuntime
Unresolved reference: NativeChatDataSource
```

绿：

1. 新增 `NativeChatDataSource` 和 `TavernNativeChatDataSource`，把 `TavernCoreApi` 的角色聊天读写、列表、改名、删除、导入、导出收敛到原生聊天数据源。
2. 新增 `NativeChatRepository`，封装 load/save/list/rename/delete/import/export。
3. 新增 `NativeChatRuntime`，每次单聊操作从 API 读取 JSONL、调用 `NativeChatJsonOps`、保存 JSONL、再用同一份 JSONL 刷新 `ChatStore`。
4. checkpoint 创建后留在当前聊天；branch 创建后保存当前聊天和分支聊天，并打开新分支。

重构：`no op`。

### 切口 C2：Repository 数据安全护栏

新增测试：

```text
NativeChatRepositorySafetyTest
```

红：

```text
Cannot find a parameter with this name: backupNameProvider
Unresolved reference: NativeChatIntegrityConflict
```

绿：

1. `NativeChatRepository.save` 保存前重新读取当前磁盘 JSONL。
2. 用 header `chat_metadata.integrity` 做写前一致性校验；检测到磁盘被第三方改动时抛出 `NativeChatIntegrityConflict`，不写 backup、不写目标。
3. 保存目标前先把当前磁盘 JSONL 写入退避备份。
4. 保存目标时刷新 `chat_metadata.integrity`。
5. 同一 avatar/chatFile 通过 `Mutex` 串行化写操作。
6. `NativeChatEngine` 成功生成落盘也改为复用 `NativeChatRepository.save`，不再绕过安全护栏。

重构：小清理。

安全保存逻辑集中在 `NativeChatRepository.save`，生成路径和消息操作共用这一入口。

### 切口 D：NativeChatScreen / MainActivity 接线

新增测试：

```text
NativeChatUiRoutingTest
```

红：

```text
Unresolved reference: NativeChatUiRouting
```

绿：

1. `MainActivity` 创建 `NativeChatRuntime`。
2. 新增 `NativeChatUiRouting.shouldActivateHiddenWebViewForChatEntry`；原生生成开启时，进入 Chat 或打开角色聊天不再把 `chatRuntimeActivated` 置为 true。
3. `NativeChatScreen` 新增 `nativeChatRuntime` 参数。
4. 新增 `NativeChatUiRouting.selectNativeSingleChatRuntime`；单聊且原生 runtime 可用时，编辑、删除、隐藏/取消隐藏、swipe previous/next、checkpoint、branch、打开 checkpoint/branch 都优先调用 `NativeChatRuntime`。
5. 群聊或原生 runtime 不可用时，保留原 Bridge 路径。

重构：小清理。

旧的源码子串断言测试已删除，改为纯逻辑行为测试。

### 切口 E：审计反馈修复 - 对齐完成顺序、fallback 对齐、备份修剪

新增测试：

```text
chat_runtime_adapter_contract.test.mjs
  queued bridge writes wait for chat reload to finish before dispatching the next command
  generation stop is not blocked behind a long running queued generation
NativeBridgeAlignmentTest.bridgeFallbackWriteReloadsRuntimeBeforeWriting
NativeChatRepositorySafetyTest.listChatNamesSkipsNativeBackupFiles
NativeChatRepositorySafetyTest.savePrunesOldNativeBackupsForTheSameChat
NativeChatRepositorySafetyTest.backupPruningUsesTimestampAcrossLegacyAndPrefixedBackupNames
```

红：

```text
JS: expected ['reload:start'] but was ['reload:start', 'generate:regenerate']
JS: expected ['generate:regenerate', 'stop'] but was ['generate:regenerate']
Kotlin: Unresolved reference: runAlignedBridgeWrite
Kotlin: listChatNamesSkipsNativeBackupFiles / savePrunesOldNativeBackupsForTheSameChat assertion failed
```

绿：

1. `chat_runtime_adapter.js` 增加命令队列；`chat.reload`、message ops、swipe、send 等 handler 返回真实异步完成点，确保 reload 完成后才执行后续写命令。
2. `generation.regenerate` / `generation.continue` 只占用队列到生成启动完成，避免长生成阻塞 `generation.stop`。
3. `NativeChatScreen.launchNativeAction` 的 Bridge fallback 统一通过 `runAlignedBridgeWrite`，在群聊、target 不匹配或实验开关关闭时也会先 `reloadChat()`。
4. `NativeChatRepository` 备份改为 `__native-backup__<chat>__<timestamp>` 前缀；`listChatNames` 过滤新旧备份名；保存后按时间戳排序新旧备份格式，默认保留最近 5 份同聊天备份并 best-effort 删除更早备份。

重构：`no op`。

本切口是护栏补洞和数据安全收口，没有发现值得单独抽象的新复杂度。

### 切口 F：复核反馈修复 - 群聊 stop 队列与过往聊天备份过滤

新增测试：

```text
chat_runtime_adapter_contract.test.mjs
  group regenerate stop is not blocked behind a long running queued group generation
PrototypePastChatsScreenTest.filtersNativeBackupsFromVisiblePastChats
```

红：

```text
JS: expected ['group:regenerate', 'stop'] but was ['group:regenerate']
Kotlin: Unresolved reference: filterVisibleCharacterChats
```

绿：

1. `handleRegenerate` 的群聊分支改为和单聊分支一致：`regenerateGroup()` 启动后用 Promise 回调上报结果，不再 `await` 整轮群聊生成，因此不会占住命令队列。
2. `NativeChatRuntime` 的备份名判断从 private 放宽为 module-internal，供 UI 复用。
3. `PrototypePastChatsScreen.refreshList` 通过 `filterVisibleCharacterChats` 过滤新旧原生备份名，避免备份副本出现在用户可见的历史对话列表。

重构：`no op`。

本轮只补两个明确缺口，没有新增跨模块抽象；共享的是已经存在的备份命名规则。

## 3. Phase 1 交付对照

| Phase 1 交付 | 当前实现 | 证据 |
|---|---|---|
| 原生 ChatSession / Repository 边界 | `NativeChatRuntime` + `NativeChatRepository` + `NativeChatDataSource` | `NativeChatRuntimeTest` |
| `/api/chats` 读写封装 | `TavernNativeChatDataSource` 包装 `TavernCoreApi` get/save/rename/delete/import/export | `NativeChatRuntime.kt` |
| 单一写者护栏 | 仍走 Bridge 的写操作前 `reloadChat()` 对齐；adapter 等 reload 完成后再处理后续写命令；fallback message ops 也对齐；单聊/群聊 stop 不被长生成阻塞 | `NativeChatEnginePhase1ContractTest`, `NativeBridgeAlignmentTest`, `chat_runtime_adapter_contract.test.mjs` |
| 写前 integrity + 退避备份 + 串行化 | `NativeChatRepository.save` 统一负责；生成路径也复用 Repository；备份固定前缀、Repository 命名和过往聊天 UI 过滤、默认保留最近 5 份 | `NativeChatRepositorySafetyTest`, `NativeChatEnginePhase1ContractTest`, `PrototypePastChatsScreenTest` |
| MessageOps | `NativeChatJsonOps` 编辑、删除、隐藏、移动、reasoning、附件/媒体 | `NativeChatJsonOpsTest` |
| SwipeManager | `NativeChatJsonOps` swipe previous/next/create/delete | `NativeChatJsonOpsTest` |
| UI 优先原生 runtime | `NativeChatScreen.nativeChatRuntime` 单聊优先，Bridge 仅兜底 | `NativeChatUiRoutingTest`, `NativeChatRuntimeTest` |
| 原生生成不 reload WebView | 成功保存后不调用 `bridge.reloadChat()`；保存前走 Repository 安全护栏 | `NativeChatEnginePhase1ContractTest` |
| checkpoint / branch | 已实现原生 JSONL 分支/存档点；v0.5 不计入 Phase 1 验收 | `NativeChatJsonOpsTest`, `NativeChatRuntimeTest` |

## 4. 验证命令

```bash
./gradlew testDebugUnitTest --tests "io.github.sanitised.st.chat.NativeChatJsonOpsTest" --tests "io.github.sanitised.st.chat.NativeChatRuntimeTest" --tests "io.github.sanitised.st.chat.NativeChatRepositorySafetyTest" --tests "io.github.sanitised.st.chat.NativeChatUiRoutingTest" --tests "io.github.sanitised.st.chat.engine.NativeChatEnginePhase1ContractTest" --tests "io.github.sanitised.st.ui.prototype.PrototypePastChatsScreenTest"
/Users/changlepan/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node --test app/src/test/js/chat_runtime_adapter_contract.test.mjs
```

结果：

1. Phase 1 目标测试：`BUILD SUCCESSFUL`。
2. 完整 Kotlin 单元测试：`./gradlew testDebugUnitTest`，`BUILD SUCCESSFUL`。
3. JS adapter 合同测试：14/14 通过。
