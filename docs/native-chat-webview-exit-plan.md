# Chat 全面原生化与隐藏 WebView 退出计划

版本：0.5
日期：2026-06-03
方向：从“原生 UI + 隐藏 WebView runtime 兜底”逐步迁移到“原生 Chat runtime 为主，WebView 仅作为临时兼容壳，最终默认关闭并移除”。

> v0.5 变更：补上 Phase 1 的“单一写者”安全护栏与并发/integrity 保护，修正 Phase 1 验收范围与阶段优先级矛盾，明确群聊双入口写者归属，纠正测试基础设施与契约测试方法（见各阶段及 §5、§9）。

## 1. 目标

现有 chat 迁移已经完成可见 UI 原生化、Bridge 兜底、实验性原生生成，以及群聊 REST + 原生生成 MVP 的一部分。这个计划不是从零重建，而是沿着“原生生成（实验）”这条已经打通的路径继续外扩：先让现有原生生成成为稳定事实源，再逐步迁走消息操作、提示词语义、群聊、附件和扩展。

下一阶段目标是把 chat 的事实源迁到 Android 原生侧：

1. 原生侧负责当前聊天会话状态、消息操作、保存和生成。
2. 原生侧完整承接提示词组装、世界书、作者注、CFG、模板、扩展注入、流式处理、reasoning、tool calls、swipe 和 logprobs 等核心语义。
3. WebView 逐步降级为“兼容壳”：只用于未迁移的扩展 UI 或调试对照。
4. 当主要聊天路径、常用扩展和设置均原生可用后，默认不再启动隐藏 WebView。

一句话原则：

> 不再把 WebView 当运行时，而是把它当历史实现的对照样本和临时兼容层；每迁出一块，都用契约测试证明原生语义能独立工作。

## 1.1 当前已有原生化基础

当前已经落地的“原生生成（实验）”不是空壳，后续计划应复用这些接缝：

| 已有能力 | 当前代码/文档 | 价值 |
|---|---|---|
| 实验开关 | `UpdateManager` / `MainViewModel` / `PrototypeSystemScreens` / `MainActivity` | “原生生成（实验）”默认关闭，打开后注入原生生成路径 |
| 生成引擎接缝 | `ChatEngine` / `BridgeChatEngine` / `NativeChatEngine` | UI 已经不直接绑定 WebView 发送链路，可按能力选择原生或兜底 |
| 单聊原生打开 | `NativeChatLoader` | 实验开关下，角色聊天可先经 API 读取角色卡和 JSONL，Compose 不必等 WebView 快照 |
| 原生 JSONL 与生成 API | `TavernCoreApi.getChatJsonl` / `saveChatJsonl` / `generateChatCompletion*` / `generateTextCompletion*` | 原生侧已经能无损读写聊天文件，并直接调用后端生成 |
| Chat Completion 提示词 | `PromptBuilder` | 已覆盖角色卡、persona、示例对话、世界书简化扫描、作者注深度、上下文裁剪 |
| Text Completion 提示词 | `TextPromptBuilder` / `InstructTemplate` / `StopStringBuilder` | 已覆盖首批 Text Completion 后端和保守模板语义 |
| 流式 delta 解析 | `GenerationDeltaParser` | Chat Completion / Text Completion 共用，兼容多种 SSE delta 形态 |
| 世界书基础扫描 | `WorldInfoScanner` | 已覆盖 constant、关键词、selective、position、order 的最小可测子集 |
| 群聊原生生成 MVP | `NativeGroupGenerator` / `GroupChatScreen` / `docs/group_chat_migration_plan.md` | 群聊数据已走 REST，AI 回复可复用单聊 prompt/stream 管线，不经隐藏 WebView |
| 单测基线 | `NativeEngineModeTest`、`NativeChatLoaderTest`、`PromptBuilderTest`、`TextPromptBuilderTest`、`WorldInfoScannerTest`、`GenerationDeltaParserTest`、`NativeGroupGeneratorTest` | 已有红-绿基础，后续应扩展而不是绕开 |

## 1.2 当前实验边界

打开“原生生成（实验）”后，当前可走原生的路径是保守子集：

1. 单聊角色聊天，且当前没有待发送附件。
2. `main_api = openai` 的 Chat Completion 路径。
3. `main_api = textgenerationwebui` 且 `api_type` 属于首批支持后端：`ooba` / `koboldcpp` / `llamacpp` / `ollama`。
4. Text Completion 必须是 `TextPromptBuilder.supports(...)` 接受的简单模板语义。
5. 原生生成成功后会写回真实 JSONL，并更新 Compose 里的占位消息。
6. 群聊已有 `NativeGroupGenerator` MVP，但它还不是和单聊统一的完整 `ChatEngine` runtime。

仍然回退或依赖 WebView 的部分：

1. 单聊附件发送、复杂 Handlebars / instruct disabled / story prefix-suffix 等未覆盖模板。
2. 不受支持的 `main_api` / `api_type`，例如 NovelAI、Horde 等尚未接入的生成入口。
3. `regenerate()`、`continueGeneration()` 当前仍主动走 Bridge，因为 native swipe/历史语义未补齐。
4. 编辑、删除、隐藏、移动、checkpoint、branch、quick reply、swipe picker 等消息操作仍主要走 `ChatRuntimeBridge`。
5. reasoning/tool calls/logprobs、Regex、extension prompt injection、完整世界书、Data Bank、媒体、TTS、翻译、生图等语义尚未完整迁移。
6. `NativeChatEngine` 落盘后仍调用 `bridge.reloadChat()` 让隐藏 runtime 对齐；这说明 WebView 还在承担“历史运行时同步器”的角色。

## 2. 总体架构

目标架构：

```text
Compose Native Chat UI
  -> Native Chat Runtime
      -> ChatSessionStore / ChatRepository
      -> MessageOps
      -> PromptAssembly
      -> GenerationEngine
      -> ExtensionRuntime
      -> MediaAndAttachmentService
      -> GroupChatEngine
  -> TavernCoreClient
      -> ST local APIs
      -> backend generate APIs
      -> files/images/speech/worldinfo/settings endpoints

Optional WebView Compatibility Host
  -> only enabled for unported extension UI, diagnostics, or explicit fallback
```

关键变化：

| 当前 | 目标 |
|---|---|
| WebView 内 ST 前端是活动聊天事实源 | `ChatSessionStore` 是活动聊天事实源 |
| Bridge 负责发送、编辑、删除、swipe、扩展命令 | 原生命令直接修改会话并保存，Bridge 仅保留兼容入口 |
| 原生生成只覆盖保守子集 | `GenerationEngine` 覆盖主要后端和完整提示词语义 |
| 扩展提示和运行时依赖 ST 前端事件 | 原生 `ExtensionRuntime` 提供可测试的 hooks/events |
| WebView 常驻保活 | 默认不启动；仅兼容模式启动 |

## 3. 迁移边界

### 必须原生化

这些能力决定聊天是否能摆脱隐藏 WebView：

1. Chat JSONL 无损读写、保存 integrity、并发写保护。
2. 消息新增、编辑、删除、隐藏、上移/下移、reasoning 编辑/删除。
3. swipe 创建、切换、删除、自动 swipe、swipe picker。
4. checkpoint、branch、打开分支。
5. 角色单聊和群聊状态机。
6. Chat Completion、Text Completion、NovelAI、Kobold/KoboldCpp、Horde 等生成入口。
7. 流式处理、停止、继续、重写、代笔。
8. Prompt Manager、Context Template、Instruct Template、System Prompt、Author's Note、CFG。
9. 世界书完整扫描：深度、递归、概率、分组、whole word、case sensitive、角色/聊天/全局绑定。
10. Regex、extension prompt injection、generation interceptor。
11. tool calls、reasoning/thinking、logprobs。
12. 附件、Data Bank、媒体 gallery、文件嵌入提示词。
13. TTS/STT、翻译、生图、caption、memory、vectors、token counter 等高频扩展。

### 可以后置但需标注

1. 少数复杂扩展的完整配置 UI。
2. 低频 provider 的高级参数页。
3. 旧版 Web 前端特殊交互和拖拽布局。
4. 自定义 CSS 对 chat 气泡的影响。

后置能力必须有明确 UI 提示，不能伪装成已经完整原生化。

## 4. 阶段计划

### Phase 0：冻结现有原生生成基线与依赖地图

目标：先把当前已经能原生跑的路径固定成测试、能力矩阵和回退原因，避免后续扩展时把已打通的实验路径弄回 WebView。

状态：Phase 0 基线检查已记录在 `docs/native-chat-phase0-audit.md`。本阶段只做检查和冻结，不做 Phase 1 迁移。

交付：

1. 建立 `native-generation-route-matrix`：列出 `NativeEngineMode` 对 `main_api` / `api_type` / 附件 / 群聊 / authors note / 模板复杂度的路由结果。
2. 把现有 `NativeEngineModeTest`、`PromptBuilderTest`、`TextPromptBuilderTest`、`NativeChatLoaderTest`、`NativeGroupGeneratorTest` 纳入迁移基线。
3. 建立 `chat-contract-fixtures`：角色卡、用户 persona、世界书、设置、聊天 JSONL、群聊、附件、扩展设置的样本集。
4. 增加原生 vs ST 前端的 payload 对照测试：同一输入下比较最终 generate payload、stop strings、世界书激活、消息保存结果。
5. 增加消息操作契约测试：编辑、删除、隐藏、swipe、checkpoint、branch、reasoning、附件。
6. 增加 WebView 依赖地图：列出当前每个 Bridge 命令对应的 ST 函数、事件、迁出状态和 fallback 原因。

验收：

1. 能用测试精确说明哪些语义已经可由原生侧独立复现，哪些仍必须 fallback。
2. 所有后续阶段都必须先写失败测试，再按红-绿-重构推进；如果不需要重构，在记录中说明 `no op`。
3. “原生生成（实验）”当前单聊 Chat Completion / Text Completion 路径被保护成稳定基线。

### Phase 1：原生 ChatSession 从生成路径扩展为单聊事实源

目标：单聊下，当前 chat 的读写和消息操作不再依赖 WebView runtime。

状态：Phase 1 本轮已按 v0.5 口径补齐，进度记录在 `docs/native-chat-phase1-progress.md`。单聊原生 runtime 已覆盖打开、编辑、删除、隐藏/取消隐藏、移动、reasoning、附件/媒体数据操作和 swipe；`NativeChatRepository.save` 负责写前 integrity 校验、退避备份和同会话写串行化；原生生成成功保存 JSONL 后不自动调用 `bridge.reloadChat()`，但仍走 Bridge 的写操作会先 reload 对齐。checkpoint/branch 已有原生实现，但不计入本阶段验收。群聊、完整 prompt 语义和扩展能力属于后续 Phase。

> **重要前置（v0.5 新增）：第一刀引入了 split-brain 风险，必须先补护栏再继续扩面。**
> 现状：`NativeChatEngine.send()` 成功后已不再 `reloadChat()`，但 `regenerate()` / `continueGeneration()` / 编辑 / 删除 / swipe 仍走 Bridge＝WebView 写盘。也就是说当前写者分裂为“原生（send）+ WebView（其余）”。去掉同步 reload 后，隐藏 WebView 的内存快照可能是旧的；此时执行一次 bridge 操作（如 regenerate）就可能用旧快照覆盖磁盘，**让原生刚发送的消息丢失**。`send()` 自身也是无锁 read-modify-write（`getChatJsonl` → append → `saveChatJsonl`），read 与 save 之间若 WebView 写入会被静默覆盖。
> 因此 Phase 1 必须先满足“单一写者或显式对齐”这一前提，再扩消息操作。

交付：

0. **单一写者护栏（最高优先，先做）**：在 native 尚未接管全部写操作前，任何仍走 Bridge 的写操作（regenerate / continue / 编辑 / 删除 / swipe）执行前，必须先把隐藏 WebView 的 runtime 与磁盘对齐——即“bridge 写操作前强制 reload native session 到 WebView”，或保留这些路径上的 `reloadChat()`，直到对应操作完成原生化为止。明确记录：**只有当某操作的写盘已完全由原生承担后，才允许移除该操作前后的对齐。**
1. 在现有 `NativeChatLoader`、`ChatStore`、`TavernCoreApi.getChatJsonl/saveChatJsonl` 基础上抽出或补齐 `ChatSessionStore`，保存当前角色、chat file、header metadata、messages、dirty state、generation state。
2. 把现有无损 JSONL 读写收敛成 `ChatRepository`：封装 `/api/chats/get/save/rename/delete/export/import`，保持未知字段无损。**并承担并发写保护与数据安全：保存前对原 JSONL 做退避备份；用 header 的 `chat_metadata.integrity` 做写前一致性校验，检测到磁盘被第三方（WebView/ST API）改动时拒绝盲写并重新读取；同一会话写操作串行化。**
3. 新建 `MessageOps`：实现编辑、删除、隐藏、上移/下移、reasoning 编辑/删除、附件删除、媒体显示切换。每迁完一项，按交付0 的规则解除该项的 bridge 对齐。
4. 新建 `SwipeManager`：实现 swipe 切换、创建、删除、同步 `mes/swipes/swipe_id/swipe_info`。
5. 让 `NativeChatScreen` 的消息操作优先调用原生 runtime；只有未迁移操作才进入兼容模式（且遵守交付0 的对齐规则）。
6. 单聊原生生成落盘后不再依赖 `bridge.reloadChat()` 对齐；隐藏 WebView 如果打开，只能观察或显式 reload 原生 session。

> 说明：checkpoint / branch 依赖 `scripts/bookmarks.js` 的 native 化，不在本阶段交付，放到 Phase 3 的历史/分支语义一并处理；本阶段验收不含 checkpoint/branch。

验收：

1. 单聊打开、编辑、删除、隐藏、swipe 全部可在不启动 WebView 时完成（checkpoint/branch 不在本阶段）。
2. 保存后用 ST API 重新读取 JSONL，字段和预期一致；并验证“写前 integrity 校验 + 退避备份”在并发改动场景下不丢数据。
3. 隐藏 WebView 关闭时，单聊消息操作不降级。
4. “原生生成（实验）”在单聊成功生成后不需要 `ChatRuntimeBridge.reloadChat()` 才能保持 UI 和磁盘一致。
5. **仍走 Bridge 的写操作（regenerate/continue 等）在执行前会先对齐 WebView 与磁盘，send 后立刻 regenerate 不会丢失刚发送的消息。**（用 behavioral 测试覆盖，见下）

> **测试方法纠正（v0.5）**：Phase 1 第一刀曾出现过“读取 `.kt` 源码文本、grep `reloadChat(` 是否出现”的字符串断言，只验证源码排版、不验证运行行为，且对函数抽取等重构脆弱。当前已替换为 behavioral 契约测试：用 fake `ChatRuntimeBridgeActions` 记录调用，断言成功生成路径不触发 `reloadChat()`、而 bridge 写操作前会触发对齐。后续阶段的契约测试一律以行为/产物为断言对象，不得以源码子串为断言对象。

### Phase 2：原生 PromptAssembly 替代 ST 前端提示词组装

目标：在现有 `PromptBuilder` / `TextPromptBuilder` 基础上补齐 ST 提示词语义，让原生侧可独立构造后端 payload。

交付：

1. 扩展现有 `PromptBuilder`，覆盖 Chat Completion 的 prompt manager、系统提示、角色卡、示例对话、persona、作者注深度、世界书前后插入、bias、stop strings。
2. 扩展现有 `TextPromptBuilder` / `InstructTemplate` / `StopStringBuilder`，覆盖 instruct disabled、复杂 context story string、story prefix/suffix、example `<START>` 块、activation regex、sampler order/priority。
3. 将 `WorldInfoScanner` 演进为 `WorldInfoEngine`，覆盖递归、概率、分组、深度、whole word、case sensitive、角色/聊天/全局世界书组合。
4. 新建 `RegexEngine`，覆盖输入、输出、reasoning 和显示层正则。
5. 新建 `ExtensionPromptRegistry`，提供 extension prompt 注入点和 generation interceptor。

验收：

1. 契约样本下，原生组装结果与 ST 前端逐项一致——而不是模糊的“语义等价”。具体可测口径：对固定 fixture 集（见 §4 切口、§8），比较 **final generate payload 字段、stop strings 集合、世界书激活条目与顺序、最终消息保存结果**，逐项 diff 通过。无法做到逐项一致的项，必须在矩阵里标为 known-diff 并说明原因。
2. 对不支持的复杂扩展，生成前明确 fallback 或提示，而不是静默丢语义。
3. Chat Completion 和 Text Completion 的常见模板不再需要 WebView。

### Phase 3：原生 GenerationEngine 覆盖主要生成链路

目标：以现有 `NativeChatEngine` 为核心，把发送、停止、继续、重写、代笔、流式、非流式都收敛到原生生成引擎。

> 阶段依赖（v0.5）：新增 backend（NovelAI / Kobold / Horde）的生成必须等其 prompt 语义在 Phase 2 完成后再做，否则会为“提示词组装还没做的后端”先写生成器，造成返工。因此 Phase 3 先只深挖已有的 Chat Completion / 首批 Text Completion（continue/regenerate/stop/reasoning/logprobs），新 backend adapter 排在 Phase 2 之后接入。checkpoint / branch 的历史语义（依赖 `scripts/bookmarks.js`）在本阶段一并原生化。

交付：

1. 保留当前 Chat Completion / 首批 Text Completion 原生路径作为绿色基线。
2. 将 `NativeChatEngine` 和 `NativeGroupGenerator` 里的共用流式、回滚、payload、错误处理逻辑收敛为 `GenerationEngine` / backend adapter（**先 CC/TC，新 backend 待 Phase 2 后**）：
   - Chat Completion
   - Text Completion
   - NovelAI（Phase 2 后）
   - Kobold/KoboldCpp（Phase 2 后）
   - Horde（Phase 2 后）
3. 实现统一 `StreamingProcessor`：token delta、reasoning delta、tool call delta、logprobs、结束保存、失败回滚。
8. checkpoint、branch、打开分支的原生历史语义（替代 `scripts/bookmarks.js`）。
4. 强化原生 stop：能取消 OkHttp call，并保存或丢弃部分回复的策略可配置。
5. 实现原生 continue、regenerate、impersonate，并补齐对应 swipe / 历史保存语义。
6. 实现 tool calling 递归生成和 tool result 系统消息写入。
7. 实现 logprobs 采集、保存和原生展示。**注意真正的阻塞点是 backend 是否在流式响应里返回 logprobs（见 CLAUDE.md“上游阻塞”），原生自存只解决“不依赖 ST `logprobs.js` 私有 state”，不解决 provider 不返回的问题；对不返回的 provider 必须在 UI 标注不可用，而不是假装已支持。**

验收：

1. 单聊文本生成在默认路径不启动 WebView，也不需要生成后 reload 隐藏 runtime。
2. 流式、停止、继续、重写、代笔均通过原生测试和真机测试。
3. reasoning、tool calls、logprobs 在消息 `extra` 中保存并可重新打开展示。

### Phase 4：群聊原生 runtime 收拢与产品化

目标：在现有 `NativeGroupGenerator` 和 `GroupChatScreen` 的 REST + 原生生成 MVP 基础上，补齐群聊打开、成员激活、历史切换和保存语义，让群聊 runtime 与单聊统一。

> 双入口问题（v0.5）：当前群聊存在两条路径——`NativeChatScreen` 的群聊 send 走 Bridge fallback，而独立 `GroupChatScreen` 走 `NativeGroupGenerator`。同一个群聊从不同入口进入会有不同写者和行为。在 Phase 4 统一之前，**Phase 1–3 期间群聊的唯一写者应明确为独立 `GroupChatScreen`/`NativeGroupGenerator`，`NativeChatScreen` 群聊入口要么禁用、要么仅作 Bridge 兼容且遵守 Phase 1 交付0 的对齐规则**，避免两条路径互相覆盖。

交付：

1. 从现有 `GroupChatScreen` 状态中抽出 `GroupChatSessionStore`。
2. 将 `NativeGroupGenerator` 收敛为统一 `GroupChatEngine`：细化 natural/list/pooled/manual 激活策略、allow self responses、generation mode。
3. 原生实现群聊 regenerate：删除同一轮 group generation 后重新生成。
4. 原生实现群聊历史、新建、导入、删除、重命名。
5. 群聊接入 Phase 2 后的 `PromptAssembly`、`WorldInfoEngine`、`ExtensionPromptRegistry`。

验收：

1. 群聊发送、点名、自动接龙、continue、regenerate 在不启动 WebView 时完成。
2. 多成员回复顺序、激活策略和保存结果与 ST 契约样本一致。

### Phase 5：附件、媒体与 Data Bank 原生化

目标：文件、图片、音频、视频、Data Bank 和媒体 gallery 由原生管理。

交付：

1. 新建 `AttachmentService`：上传、删除、打开、重命名、禁用、移动、verify。
2. 新建 `MediaService`：图片/视频/音频渲染、gallery/list 显示切换、media swipe、media delete。
3. 新建 `DataBankRepository`：全局/角色/聊天附件列表、上传、删除、移动、禁用。
4. 文件嵌入提示词由 `PromptAssembly` 读取 `extra.files`，不依赖 ST `appendFileContent()`。
5. Caption 对图片附件的处理迁入原生或原生扩展。

验收：

1. 附件和媒体消息在不启动 WebView 时完整可用。
2. 删除媒体或文件后，服务器文件和 JSONL 状态一致。

### Phase 6：高频扩展原生 runtime

目标：把 `public/scripts/extensions` 中对 chat 影响最大的扩展迁出 WebView。

优先级：

1. Quick Replies：列表、执行、编辑器、自动触发。
2. Regex：脚本列表、角色/预设绑定、执行引擎。
3. Translation：消息翻译、reasoning 翻译、provider 设置。
4. TTS/STT：朗读、自动朗读、语音输入、TTS job 状态。
5. Stable Diffusion / Image Generation：消息生成图、图片 swipe、工具调用生成图。
6. Caption：图片 caption，写入消息或提示词。
7. Memory/Summarize：自动摘要、手动摘要、summary 消息写入。
8. Token Counter：当前聊天、消息、prompt token 统计。
9. Expressions/Gallery：角色表情和图库展示。
10. Vectors：RAG/向量存储入口和检索注入。

验收：

1. 高频扩展的 chat 可见能力不用 WebView。
2. 未迁移扩展在 UI 中显示“需要兼容模式”，并可按需启动 WebView 壳。

### Phase 7：兼容模式降级与隐藏 WebView 默认关闭

目标：WebView 从常驻 runtime 改为按需 compatibility host。

交付：

1. 移除默认 `chatRuntimeActivated` 常驻启动路径。
2. 新增“兼容模式”开关：只在用户打开未迁移扩展或调试时启动 WebView。
3. `ChatRuntimeBridge` 改名或拆分为 `WebCompatibilityBridge`。
4. Chat 默认路径不注入 `chat_runtime_adapter.js`。
5. 增加启动性能、内存和 WebView 进程指标对比。

验收：

1. 默认进入 Chat 不创建 WebView。
2. 主聊天、生成、群聊、附件、常用扩展均可用。
3. 兼容模式启动和关闭不会污染原生 session。

### Phase 8：移除隐藏 WebView runtime

目标：删除 chat 运行时 WebView 依赖，仅保留可选显式打开原版 Web UI 的入口。

交付：

1. 删除 chat 中的隐藏 WebView 宿主和自动 Bridge 兜底。
2. 删除 `chat_runtime_adapter.js` 的默认打包路径，或移入调试/兼容模块。
3. 原版 ST Web UI 作为“打开网页版”显式入口，而不是 App runtime 组成部分。
4. 更新文档、测试和用户说明。

验收：

1. 所有 release 默认路径不需要隐藏 WebView。
2. 真机回归通过：单聊、群聊、生成、停止、继续、重写、附件、世界书、扩展。

## 5. 测试策略

所有开发任务采用严格 TDD：

1. 红：先写失败测试，确认测试确实失败。
2. 绿：写最小实现让测试通过。
3. 重构：只在有重复或结构问题时重构；不需要重构时记录 `no op`。

测试层级：

| 层级 | 目的 | 现状 |
|---|---|---|
| Kotlin unit tests | Prompt、World Info、Regex、MessageOps、Swipe、Generation payload | 已有，主力 |
| JS/ST contract fixtures | 与 `SillyTavern/public` 现有语义对照 | 已有 `chat_runtime_adapter_contract.test.mjs` 等 |
| API contract tests | `/api/chats`、`/api/groups`、`/api/files`、`/api/backends/*` 请求/响应 | 部分 |
| Instrumentation tests | Compose chat UI 操作、附件选择、长列表、键盘 | **本仓库当前无 instrumented test 基础设施（见 CLAUDE.md）。引入前需先把它列为某阶段交付的一项，否则相关验收只能降级为手动回归清单。** |
| 真机 E2E | 多 provider、流式停止、群聊、多扩展 | **无自动化，只能手动；凡验收写“真机测试”的，必须配套一份可勾选的手动回归清单，不能当作 CI 门禁。** |
| Regression snapshot | JSONL 保存前后未知字段不丢 | 已有部分 |

测试方法红线：

- **断言对象必须是运行行为或产物（调用、payload、保存结果、UI 状态），不得断言源码子串**。Phase 1 第一刀的源码 grep 式 `NativeChatEnginePhase1ContractTest` 已替换为 fake-bridge 的 behavioral 测试；后续不得重新引入同类测试。

每个阶段都必须至少包含：

1. 失败测试记录。
2. 通过测试记录。
3. 与 WebView 兜底路径的差异说明。
4. 是否仍需兼容模式。

## 6. 风险与处理

| 风险 | 处理 |
|---|---|
| ST 前端语义复杂，原生复刻容易漏 | 先建契约测试，不靠记忆重写 |
| 生成 payload 与 ST 不一致 | 每个 provider 建 golden fixture |
| 世界书/Regex/扩展注入互相影响 | 把 PromptAssembly 拆成可组合 pipeline |
| 原生保存与 ST API 保存冲突 | 原生成为单一写者，WebView 兼容模式只读或显式锁定。**在单一写者真正达成前（Phase 1–3 过渡期），仍走 Bridge 的写操作执行前必须强制对齐 WebView 与磁盘（Phase 1 交付0），并由 `ChatRepository` 做写前 integrity 校验 + 退避备份。** |
| 去掉生成后 reload 导致 split-brain | 这是 Phase 1 第一刀已引入的真实风险：send 已不 reload，但 regen/编辑仍走 WebView 旧快照。处理见 Phase 1 交付0 与上一行，禁止在单一写者达成前进一步移除对齐 |
| 扩展太多，一次迁不完 | 高频扩展优先，低频扩展显示兼容模式 |
| logprobs 当前 ST 未 export | 原生生成路径自行保存 logprobs，不再依赖 ST `logprobs.js` 私有 state；**但 provider 不在流式响应里返回 logprobs 时无法采集，需在 UI 标注不可用** |
| 用户打开旧 Web UI 修改聊天 | 兼容模式进入前后做 session reload 和 dirty 检查 |
| 原生覆盖造成数据丢失 | 保存前对原 JSONL 退避备份，保留可回滚副本；按阶段用 feature flag 灰度，发现乱写可一键退回 WebView 兜底（见 §9） |

## 7. 优先级建议

最高优先级不是马上删除 WebView，而是先把已经打通的原生生成路径稳定成“事实源”，再从它往外扩：

1. Phase 0：冻结现有“原生生成（实验）”基线、路由矩阵和 WebView 依赖地图。
2. Phase 1：让单聊原生 session 成为事实源。**先补单一写者护栏（交付0）再扩面**，去掉生成成功后的 `bridge.reloadChat()` 依赖的同时，不让 regen/编辑路径出现 split-brain。
3. Phase 3（仅 CC/TC 部分）：在 `NativeChatEngine` 上补齐 continue / regenerate / stop / reasoning / logprobs，但**只针对已有的 Chat Completion / 首批 Text Completion**。
4. Phase 2：补全 PromptAssembly。**新增 backend（NovelAI/Kobold/Horde）的 Phase 3 生成器必须排在这之后**，避免为没有提示词组装的后端先写生成器而返工。
5. Phase 4：收拢现有 `NativeGroupGenerator` MVP，让群聊 runtime 与单聊统一；过渡期群聊唯一写者见 Phase 4 双入口说明。

注意上面 3 在 2 之前只对“深挖已有 CC/TC 链路”成立；任何新 backend 的生成都依赖 Phase 2，不能提前。完成这些步骤后，隐藏 WebView 才能从“运行时同步器 + 兜底执行器”降级为“兼容工具”。再继续做媒体和扩展，最终默认关闭。

## 8. 近期可执行切口

建议第一轮只做四个小切口：

1. **原生生成基线冻结**：把单聊 Chat Completion、首批 Text Completion、群聊 `NativeGroupGenerator` 的已支持/未支持能力整理成 route matrix，并补测试保护。
2. **已完成：去掉单聊生成后的 WebView 对齐依赖 + 补单一写者护栏**：当前用 fake `ChatRuntimeBridgeActions` 写 behavioral 测试（不是源码 grep），证明：(a) 生成成功落盘后不触发 `reloadChat()`；(b) 仍走 Bridge 的写操作前会先对齐磁盘。同时已把源码字符串断言的 `NativeChatEnginePhase1ContractTest` 替换掉。
3. **MessageOps 原生化**：先从编辑、删除、隐藏、reasoning 编辑开始，因为它们不需要 provider；每迁完一项再按交付0 解除该项的 bridge 对齐。
4. **PromptAssembly 契约夹具**：选 3 个角色 + 2 个世界书 + 2 套模板，逐项 diff native 产物与 ST 产物（payload 字段 / stop strings / WI 激活条目与顺序 / 保存结果），known-diff 入矩阵。

这四个切口完成后，再决定是优先补生成完整性，还是优先补媒体/扩展。

## 9. 回滚、灰度与数据安全（v0.5 新增）

原生侧已经成为 `send` 的实际写者，一旦原生写盘有 bug 会直接损坏用户聊天文件，因此前进式迁移必须配套退路：

1. **写前退避备份**：`ChatRepository` 每次写 JSONL 前，先把原文件备份到可恢复位置（如 `*.bak` 或带时间戳副本），保留最近 N 份，便于一键回滚。
2. **写前 integrity 校验**：用 header `chat_metadata.integrity` 检测磁盘是否被第三方（WebView / ST API）改动；不一致时拒绝盲写、重新读取后再合并，避免静默覆盖。
3. **同会话写串行化**：同一聊天的并发写操作排队，杜绝 read-modify-write 竞态。
4. **按阶段灰度**：沿用“原生生成（实验）”开关思路，每个 Phase 的原生写路径都能独立开关；线上发现乱写时可一键退回 WebView 兜底，不必回滚整版本。
5. **过渡期单一写者纪律**：单一写者未完全达成前，任何仍走 Bridge 的写操作执行前强制对齐（Phase 1 交付0），是上述机制的前提，不能省略。
