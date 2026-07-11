# 技术架构与设计规范

> [!NOTE]
> 本页是 Wiki 摘要版，帮助快速理解当前架构。实现细节、文件索引和最新口径以主仓库的 [docs/architecture.md](https://github.com/5151561/ST-android-setting/blob/main/docs/architecture.md) 为准。

ST-android-setting 在 Android 设备本地运行嵌入式 Node.js + SillyTavern 服务端，并用 Jetpack Compose 构建原生 UI。当前聊天路径已经完成 Native runtime exit：App 内聊天不再嵌入隐藏 WebView runtime，也不再维护 Bridge fallback。

---

## 1. 架构总览

```text
+-----------------------------------------------------------+
|                       Android App                         |
|                                                           |
|  +-----------------------+     +-----------------------+  |
|  |     Compose UI        | <-> |   MainViewModel       |  |
|  |  Home/Chat/Tools/...  |     |   App state           |  |
|  +-----------+-----------+     +-----------+-----------+  |
|              |                             |              |
|              v                             v              |
|  +-----------------------+     +-----------------------+  |
|  | Native Chat Runtime   | <-> |  TavernCoreClient     |  |
|  | NativeChatEngine      |     |  HTTP API + CSRF      |  |
|  +-----------+-----------+     +-----------+-----------+  |
|              |                             |              |
|              +-------------+---------------+              |
|                            v                              |
|                 SillyTavern Server                        |
|              NodeService foreground process               |
+-----------------------------------------------------------+
```

核心分层：

| 层级 | 职责 |
|---|---|
| Compose UI | 首页、聊天、角色、工具、设置等原生界面 |
| Native Chat Runtime | 原生聊天状态、生成、消息操作、提示词组件和扩展数据承接 |
| TavernCoreClient | 调用本地 SillyTavern HTTP API，处理 CSRF、Cookie 和 JSON 序列化 |
| NodeService | 以前台服务方式启动 Node.js 进程，运行 SillyTavern 服务端 |
| AppPaths / NodePayload | 管理文件路径、首次解包、自定义 SillyTavern 安装和运行时资产 |

---

## 2. 内核运行时

### 2.1 NodeService

`NodeService` 是应用的运行时守护进程：

* 以前台服务形式启动和停止 Node.js 进程。
* 捕获 stdout/stderr 日志并向 UI 暴露运行状态。
* 通过通知维持后台运行，降低系统回收概率。
* 在进程异常退出时进入错误态，供首页和日志页面展示诊断入口。

### 2.2 NodePayload

`NodePayload` 负责部署运行时资产：

* 首次运行时从 APK assets 解压 Node.js 二进制和 SillyTavern 源码。
* 支持自定义 SillyTavern 仓库、分支、Tag 或 ZIP 包安装。
* 与备份、恢复和更新流程协作，保护用户数据目录。

### 2.3 AppPaths

`AppPaths` 是路径单一事实源，集中管理：

| 路径 | 用途 |
|---|---|
| `stDir` | SillyTavern 安装目录 |
| `dataDir` | 用户数据目录 |
| `configDir` | 配置文件目录 |
| `logsDir` | 日志目录 |
| `tmpDir` | 临时文件目录 |

---

## 3. 原生 UI 与导航

应用使用单模块 Gradle 项目和 Jetpack Compose。`MainActivity` 托管 `NavHost`，底部导航主要覆盖：

| 页面 | 说明 |
|---|---|
| Home | 状态卡片、最近聊天、最近角色和快捷入口 |
| Chat | 原生聊天界面，当前由 `NativeChatScreen` 承接 |
| Characters | 原生角色列表、详情、编辑和历史聊天管理 |
| Tools | 世界书、预设、API 连接、备份恢复和管理工具入口 |
| Settings | 主题、运行时、日志、系统能力和应用设置 |

设计系统由 `STAppTheme`、`STColors`、`STSpacing`、`STRadius` 和 `STTypography` 提供，支持浅色、深色和动态配色。

---

## 4. 数据访问双路径

原生页面采用 API 优先、本地文件兜底的策略。

### 4.1 API 优先路径

当 SillyTavern 服务运行时，原生页面优先通过 `TavernCoreClient` 调用本地 HTTP API：

* 角色、聊天、设置、世界书等常规读写都走 API。
* OkHttp 负责 Cookie 与 CSRF token。
* JSON 序列化遵循项目内手写工具函数，避免引入额外 JSON 库。

### 4.2 本地文件路径

当服务尚未运行或处于错误态时，原生端可以读取本地文件作为只读兜底：

* 角色列表和聊天摘要可离线展示。
* 常规页面不绕过 API 直接写 SillyTavern 数据文件。
* 导入、导出、诊断和恢复等特种流程才允许直接处理文件。

---

## 5. Native Chat Runtime

当前聊天路径以原生 runtime 为主：

| 组件 | 责任 |
|---|---|
| `NativeChatScreen` | 聊天页面 UI |
| `NativeChatEngine` | 当前唯一 `ChatEngine` 实现 |
| `NativeGenerationRouter` | 将连接页 provider 映射到原生 chat/text completion route |
| `ChatModels` | 原生消息、附件、提示词和 Data Bank 数据结构 |
| `QuickReplyRuntime` | 读取 Quick Replies 配置并渲染可用按钮 |
| `DataBankRepository` | 聚合 settings、角色 raw data 和 chat metadata 附件 |
| `ItemizedPromptStore` | 记录生成时 prompt 组件，供消息菜单查询 |

已移除的旧运行时资产包括 `ChatWebViewScreen`、`STAndroidBridge`、`WebViewNavigator`、`ChatRuntimeBridge`、`BridgeChatEngine` 和 `chat_runtime_adapter.js`。历史 Bridge 方案保留在迁移文档中，当前状态以 [Native Chat Runtime Exit Status](https://github.com/5151561/ST-android-setting/blob/main/docs/native-chat-runtime-exit-status.md) 为准。

---

## 6. 文档地图

| 想查什么 | 文档 |
|---|---|
| 当前详细架构 | [docs/architecture.md](https://github.com/5151561/ST-android-setting/blob/main/docs/architecture.md) |
| Chat runtime 当前状态 | [docs/native-chat-runtime-exit-status.md](https://github.com/5151561/ST-android-setting/blob/main/docs/native-chat-runtime-exit-status.md) |
| Chat 退出 WebView 历史计划 | [docs/native-chat-webview-exit-plan.md](https://github.com/5151561/ST-android-setting/blob/main/docs/native-chat-webview-exit-plan.md) |
| 角色管理迁移 | [docs/M2-character-management-migration.md](https://github.com/5151561/ST-android-setting/blob/main/docs/M2-character-management-migration.md) |
| M3 源码迁移规划 | [docs/M3-sillytavern-source-migration-plan.md](https://github.com/5151561/ST-android-setting/blob/main/docs/M3-sillytavern-source-migration-plan.md) |
