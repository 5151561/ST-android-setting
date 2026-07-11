# 上游 SillyTavern 升级流程

> 状态:当前权威。适用于升级 `SillyTavern/` git submodule(即打包进 APK 的 ST core)。

## 为什么需要这份流程

聊天生成语义(提示词组装、世界书扫描、instruct 模板、正则脚本等)已在 `app/src/main/java/io/github/sanitised/st/chat/prompt/` 用 Kotlin 原生重实现。上游 ST 升级后,这些原生实现可能与新版 core 的行为**悄悄分叉**(语义漂移)。防线是契约测试:fixtures 冻结"原生 vs SillyTavern"的输入输出对照,升级时必须重新核对。

只要 core 的 HTTP API(`/api/backends/*/generate`、角色/聊天/世界书 CRUD、CSRF)不做大改动,升级通常只需走完本清单;一旦 core 改了 prompt 组装或 API 形状,需要同步修改原生实现并更新 goldens。

## 升级步骤

### 1. 升级 submodule

```bash
cd SillyTavern
git fetch && git checkout <目标 tag 或 commit>
cd ..
git add SillyTavern
```

同时阅读上游 release notes,重点关注:server 端 API 变更、prompt 组装逻辑(`public/scripts/` 下生成相关模块)、世界书/instruct 数据格式变更。

### 2. 跑契约测试

```bash
./gradlew test
```

重点测试(位于 `app/src/test/java/io/github/sanitised/st/chat/`):

| 测试 | 覆盖 |
|---|---|
| `contract/ChatCompletionPayloadContractTest` | chat completion 生成 payload 与上游一致 |
| `contract/TextCompletionPromptContractTest` | text completion prompt 组装 |
| `contract/WorldInfoActivationContractTest` | 世界书扫描/激活语义 |
| `contract/ChatContractFixturesTest` | fixtures 自身完整性 |
| `GroupChatMigrationContractTest` | 群聊 REST 与生成路径 |
| `MessageOpsContractTest` | 消息操作(编辑/swipe/分支)JSONL 语义 |

部分契约测试直接读取 `SillyTavern/` submodule 源码,升级 submodule 后它们会自动对照新版行为。

### 3. 核对与更新 goldens

Fixtures 位于 `app/src/test/resources/chat-contract-fixtures/`,加载器是 `chat/contract/ContractFixtures.kt`。`goldens/` 下每个期望产物都带 `provenance` 注记(标明派生自哪段 ST 源码)。

测试红了时,先判断是哪种情况:

- **上游语义变了** → 修改 `chat/prompt/` 或 `chat/engine/` 下的原生实现跟上,再按新行为更新 golden(并更新 provenance 注记)。
- **上游只是重构、语义未变** → 原生实现不动,必要时仅更新 provenance 指向。
- **原生实现本来就有 bug** → 修原生实现,golden 不动。

禁止为了让测试变绿而直接改 golden 却不核对上游源码。

### 4. 真机手动验收

按 [native-chat-runtime-exit-status.md](native-chat-runtime-exit-status.md) 末尾清单执行:单聊、群聊、全部连接页 provider 至少一次生成、停止、继续、重写、附件发送、Quick Replies、提示词分析、Data Bank、checkpoint/branch。

### 5. 打包验证

```bash
./gradlew assembleDebug
```

确认 `NodePayload` 解压新版 ST 后服务能正常启动(首启日志无报错、`/api/ping` 通)。

## 相关文档

- [architecture.md](architecture.md) — 当前架构详版
- [native-chat-runtime-exit-status.md](native-chat-runtime-exit-status.md) — 原生运行时口径与验收清单
- [native-chat-phase0-audit.md](native-chat-phase0-audit.md) — 契约 fixtures 的建立背景
