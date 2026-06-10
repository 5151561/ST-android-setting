# Chat 原生化 Phase 2 进度

日期：2026-06-10
分支：`main`

## 1. 当前状态

**Phase 2 第一轮已落地，但未完全关闭（v0.8.1 口径，2026-06-10）。** 原生 PromptAssembly 已覆盖当前契约样本要求的 Chat Completion / Text Completion 常见模板、世界书激活基础、Regex 和扩展 prompt 注入库层能力：

- `PromptBuilder`：按 Chat Completion prompt manager 默认顺序拆分 system messages，补齐 ST `generate_data` 字段，并提供 `ExtensionPromptRegistry` 的 prompt 注入点；OpenAI 示例对话结构和自定义 prompt order 尚未逐项一致。
- `TextPromptBuilder` / `InstructTemplate` / `StopStringBuilder`：支持 instruct disabled、simple `{{#if}}` story string、ChatML story prefix/suffix、example `<START>` 独立注入、sampler order/priority、作者注 in-chat depth 插入；`activation_regex` 不再错误关闭当前已选 instruct preset。
- `WorldInfoScanner` / `WorldInfoEngine`：保留旧入口，并覆盖 case sensitive、whole word、regex key、selective AND/NOT/ALL、`scanDepth`、递归激活基础、inclusion group 基础；概率随机、完整 group 规则、position 2-6 注入仍登记为 known-diff。
- `RegexEngine`：覆盖输入、输出、reasoning 和显示层正则。
- `ExtensionPromptRegistry`：库层覆盖 extension prompt 注册、触发过滤、position/order 排序和 generation interceptor；生产扩展接线仍未完成。
- `chat-contract-fixtures/known-diffs.json`：恢复未逐项一致的 Phase 2 差异登记，矩阵继续双向裁决。

不在本轮关闭范围、留待后续阶段或 Phase 2 follow-up：CC 示例消息结构、自定义 prompt manager、WI 概率随机源、完整 inclusion group、position 2-6 注入、Regex/Extension 生产接线、continue/regenerate/impersonate 生成历史语义（Phase 3）、群聊 runtime 统一（Phase 4）、附件/Data Bank/媒体/TTS/翻译/生图等扩展接线（Phase 5/6）。

## 2. TDD 记录

### 切口 A：收敛 Phase 0 known-diff

红：

```text
compileDebugUnitTestKotlin FAILED
Unresolved reference: ExtensionPromptRegistry
Unresolved reference: RegexEngine
Cannot find a parameter with this name: extensionPrompts
```

同时将 Phase 2 相关 known-diff 从 `known-diffs.json` 移除，契约测试改为直接要求 CC payload 字段集、system prompt 顺序、TC turn join、ChatML story affix、示例对话注入、世界书高级 key matching 与 ST 对齐。

绿：

1. 新增 `RegexEngine`。
2. 新增 `ExtensionPromptRegistry` 和 `ExtensionPrompt`。
3. `PromptBuilder` 拆分 prompt manager system messages、补 ST generate_data 字段，并接入 extension prompts。
4. `TextPromptBuilder` 支持 simple `{{#if}}`、story prefix/suffix、instruct disabled、作者注和示例对话独立注入。
5. `WorldInfoScanner` 委托到 `WorldInfoEngine`，补 case sensitive、whole word、regex key、selective logic 和概率。

重构：清理未使用参数和 prompt 拼接标记；无行为变化。其余为 `no op`。

### 切口 B：补齐剩余 Phase 2 语义

红：

```text
compileDebugUnitTestKotlin FAILED
Unresolved reference: registerInterceptor
Unresolved reference: ExtensionPromptInterceptor
Cannot find a parameter with this name: history
Cannot find a parameter with this name: recursive
```

新增测试覆盖 activation regex、sampler order/priority、世界书 depth/递归/inclusion group、generation interceptor。

绿：

1. `TextPromptBuilder` 透传 `sampler_order` / `sampler_priority`；本切口曾尝试按模型名应用 `activation_regex`，该语义在切口 C 审计修正中撤回。
2. `WorldInfoEngine.scan(entries, history, recursive)` 按条目 `depth` 取最近历史，并用已激活内容做递归扫描；同组 entry 默认保留 order 最高者。
3. `ExtensionPromptRegistry` 新增 `ExtensionPromptInterceptor`，按 order 串行改写 payload。

重构：`no op`。本切口实现已经保持在小对象和纯函数内，暂不需要进一步拆分。

### 切口 C：审计反馈修正

红：

```text
compileDebugUnitTestKotlin FAILED
Cannot find a parameter with this name: defaultScanDepth
```

新增/修正测试覆盖：世界书匹配使用 `scanDepth` 而非插入 `depth`；`activation_regex` 不关闭当前 preset；story prefix 在 wrap=true 时补换行，instruct disabled 时不应用 prefix/suffix；TC 作者注按 in-chat depth 放入历史轮次中。

绿：

1. `WorldInfoEngine.scan(entries, history, defaultScanDepth)` 使用 `entry.scanDepth ?? extensions.scan_depth ?? world_info_depth`，并把生产 `NativeChatEngine` / `NativeGroupGenerator` 从旧单文本入口改为 history 入口，默认 `world_info_depth=2`。
2. `TextPromptBuilder` 移除 `activation_regex` 禁用逻辑；prefix/suffix 只在 instruct enabled 时应用，并按 `wrap` 插入 separator。
3. `TextPromptBuilder` 将作者注插入到最近 4 轮之前，而不是 story string 与 examples 之间。
4. 重新登记仍未逐项一致的 known-diff：`cc.messages.structure`、`wi.probability.randomness`、`wi.inclusion-group.selection`、`wi.position.non-prompt-injections`。

重构：`no op`。本切口是字段语义和文档诚实性修正，没有额外结构调整。

## 3. 验收

验证命令：

```text
./gradlew testDebugUnitTest --tests 'io.github.sanitised.st.chat.prompt.*' --tests 'io.github.sanitised.st.chat.contract.*'
```

结果：通过。

完整回归在本阶段收尾时执行：

```text
./gradlew testDebugUnitTest
```

结果：通过。
