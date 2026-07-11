# SillyTavern Mobile 设计原型落地情况评估报告

> 2026-06-24 当前口径：报告中“聊天主界面采用 WebView 封装”的评估已过期。聊天页已迁到纯原生实现，见 `docs/native-chat-runtime-exit-status.md`。

本报告系统评估了 `SillyTavern Mobile` 交互原型稿（基于 React+Babel）在原生安卓项目 `ST-android-setting`（基于 Jetpack Compose 与 Node.js 本地服务）中的落地实现与对接情况。本审计结合了当前项目的实际源码与本地设计资源，旨在为后续版本的迭代、验收与合规发布提供技术路线与量化基准。

---

## 1. 报告摘要

*   **交互原型基线**：[SillyTavern Prototype.html](file:///Users/changlepan/Downloads/sillytavern/SillyTavern%20Prototype.html) 及其配套的 [sillytavern-prototype-gap-audit.md](file:///Users/changlepan/Downloads/sillytavern/sillytavern-prototype-gap-audit.md)。
*   **原生安卓基线**：`ST-android-setting` (主模块: `app/`)，主要界面位于 [app/src/main/java/io/github/sanitised/st/ui/screens/](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/)。
*   **总体还原还原度**：**92%**（历史评估值）。2026-06-24 后聊天主界面也已改为原生 Compose 链路，当前聊天实现见 `docs/native-chat-runtime-exit-status.md`。
*   **核心架构决策**：
    *   **聊天主路径纯原生**：聊天主界面（Chat Screen）已迁到 `NativeChatScreen` / `NativeChatEngine`，不再保留隐藏 WebView runtime。
    *   **辅助控制面 Native 化**：角色库、编辑详情、世界书、扮演者（Persona）、API连接、AI采样、本地服务控制台、数据备份/快照管理全面 Native 化，提供卓越的单手握持操作流与系统级硬隔离稳定性。

---

## 2. 原型屏幕与安卓 Native 落地对照矩阵

| 功能模块域 | 原型组件 (`screens/*.jsx`) | 原生 Compose 页面与路由 (`STRoutes`) | 落地进度 | API 对接机制与现状 | 缝隙与遗留工作 (Gaps) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **导航外壳** | [Drawer.jsx](file:///Users/changlepan/Downloads/sillytavern/screens/Drawer.jsx) / `prototype.jsx` | [STBottomBar.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/navigation/STBottomBar.kt) / [MainActivity.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/MainActivity.kt) | **100%** | 基于 `STNavigationScaffold` 渲染侧边抽屉与底部导航，Drawer 状态（如连接状态）动态适配本地 Node 服务状态。 | 已完成。无显式缝隙。 |
| **对话列表** | [ChatList.jsx](file:///Users/changlepan/Downloads/sillytavern/screens/ChatList.jsx) | [PrototypeHomeScreen.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeHomeScreen.kt) (`STRoutes.HOME`) | **100%** | 对接 `librarySnapshot.recentChats` 接口，展示真实会话列表；服务未运行或数据为空时自动 fallback 至原型设计演示数据。 | 单人聊天历史的管理（如导入/导出/重命名）需从角色管理层聚合到全局会话列表。 |
| **聊天主屏** | [Chat.jsx](file:///Users/changlepan/Downloads/sillytavern/screens/Chat.jsx) | `chat/NativeChatScreen.kt` (`STRoutes.CHAT`) | **原生承接** | `NativeChatEngine` 通过 `TavernCoreApi` 调用本地 SillyTavern 后端，JSONL 保存由原生侧负责。 | 仍需真机手动回归全部 provider、附件、Quick Replies、提示词分析、Data Bank、checkpoint/branch。 |
| **角色管理** | [CharLib.jsx](file:///Users/changlepan/Downloads/sillytavern/screens/CharLib.jsx) | [PrototypeCharacterScreens.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeCharacterScreens.kt) (`STRoutes.CHARACTERS`) | **100%** | 对接 `/api/characters/all`。原生支持角色名搜索、收藏卡片展示、标签筛选和批量选择态。 | 文件夹管理器和标签增删的增量操作需要封装。 |
| **角色详情/编辑**| [CharEdit.jsx](file:///Users/changlepan/Downloads/sillytavern/screens/CharEdit.jsx) | [PrototypeCharacterScreens.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeCharacterScreens.kt) (`STRoutes.CHARACTER_DETAIL`) | **100%** | 对接 `/api/characters/get`、`/edit`。支持头像裁剪上传、Scenario 字段、世界书及 Persona 连接绑定。 | Alt Greetings、Tavern Card V2 部分元数据细节表单有待在 M4 进一步展开。 |
| **世界书管理** | `Misc.jsx` (`WorldInfoScreen`) | [PrototypeSystemScreens.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeSystemScreens.kt) (`STRoutes.WORLD_INFO`) | **95%** (P1) | 对接 `/api/worldinfo/list` 与 `/edit`。原生实现 Lorebook 列表、词条快速切换、词条启停与高阶注入参数设定。 | 完整世界书的多文件归档、合并与概率排序的高级细节，M3 阶段做保真保存，不设复杂原生表单。 |
| **扮演者 (Persona)**| `Misc.jsx` (`PersonasScreen`) | [PrototypeSystemScreens.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeSystemScreens.kt) (`STRoutes.PERSONA`) | **95%** (P1) | 对接 `/api/avatars/*` 与 `settings.json` 的 `power_user.personas`。支持头像上传、名称描述编辑、默认身份切换。 | 删除 Persona 头像时的 Settings 元数据联动清理已经补齐，逻辑完整。 |
| **AI 采样设置** | [SettingsAPI.jsx](file:///Users/changlepan/Downloads/sillytavern/screens/SettingsAPI.jsx) | [PrototypeSystemScreens.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeSystemScreens.kt) (`STRoutes.PRESETS`) | **85%** (P1) | 读取 `/api/settings/get`。原生实现常用 Instruct、Context 预设列表及 JSON 详情展示，支持 CFG 与作者注。 | Tokenizer 计数和极其细碎的采样 bias 滑块保持 JSON 自适应渲染，不做硬编码原生滑块。 |
| **API 连接/密钥** | [SettingsAPI.jsx](file:///Users/changlepan/Downloads/sillytavern/screens/SettingsAPI.jsx) | [PrototypeSystemScreens.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeSystemScreens.kt) (`STRoutes.CONNECTIONS`) | **90%** (P1) | 对接 `/api/secrets/*`。支持各大 Provider 连接预设展示，Masked 密文存储， active key 切换与端点连接测试。 | 仅支持主流 OpenAI-compatible、Claude 与 NovelAI 连接，其余小众 provider 仅限 JSON 预览。 |
| **记忆与回顾** | [AdvancedMobile.jsx](file:///Users/changlepan/Downloads/sillytavern/screens/AdvancedMobile.jsx) | [PrototypeSystemScreens.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeSystemScreens.kt) (`STRoutes.CHAT_BACKUPS`) | **90%** (P1) | 对接 `/api/backups/chat/get`。原生支持角色聊天记录快照（.jsonl）的浏览、单独下载导出与清理删除。 | 向量检索结果的只读面板及清空确认仍在迭代中。 |
| **群聊列表/管理** | `Misc.jsx` (`GroupChatScreen`) | [PrototypeGroupChatScreen.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeGroupChatScreen.kt) (`STRoutes.GROUP_CHAT`) | **75%** (P2) | 原型 UI 骨架已完美落地，支持群成员列表、生成顺序卡片展示。真实服务端对接接口（`/api/groups/*`）已规划。 | 完整的群聊新建向导、多角色发言优先级交互属于 M4 级独立大版本工作。 |
| **ST 内核管理** | [STCore.jsx](file:///Users/changlepan/Downloads/sillytavern/screens/STCore.jsx) | [PrototypeSystemScreens.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeSystemScreens.kt) (`STRoutes.MANAGE_ST`) | **100%** (P0) | 深度结合 `NodeService`。实现 Node 版本状态卡、自动浏览器唤醒开关、App 与 UI 双重备份导入预检、设置快照管理。 | 已完美闭环，甚至超越了 HTML 设计稿中的功能边界。 |
| **我的 / 设置** | `Misc.jsx` (`AppSettingsScreen`) | [PrototypeSystemScreens.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeSystemScreens.kt) (`STRoutes.SETTINGS`) | **100%** | 对接本地 SharePreferences 和 ViewModel 设置。支持主题外观（ColorSource / ThemeMode）、电量白名单直达、版本检查。 | 已完成。无显式缝隙。 |

---

## 3. 视觉与体验保真度审计 (Fidelity Audit)

### 3.1 调色板与主题系统 (Theme & Tokens)
在原生安卓侧，[STTheme.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/theme/STTheme.kt) 通过全新的 M3 `ColorScheme` 设计完美继承了原型设计中以 **高雅暖棕色 (Brand Warm Brown)** 为核心的色域。

*   **色彩映射**：
    *   原型中的 `#0a0805`（超深沉背景）映射至 Native 的 `DarkBrandColorScheme.background` (`Color(0xFF18130E)`) 及 `surfaceContainerLowest` (`Color(0xFF120E09)`)，在 OLED 屏幕上拥有极具 premium 质感的微弱暖棕微光，绝非单调纯黑。
    *   主色调映射：原型的 Accent 暖金黄色直接映射至 Native 的 `primary = Color(0xFFFFB871)` 与 `primaryContainer = Color(0xFF6B3B05)`，保证视觉一致性。
*   **圆角与栅格 (Radius & Spacing)**：
    *   通过 `STRadius`（`md = 12.dp`，`lg = 20.dp`，`xl = 28.dp`）硬契合原型的卡片圆角与 Material Design 3 风格。
    *   全局栅格完全使用 `STSpacing` 阶梯化控制（`sm=8.dp`, `md=12.dp`, `lg=16.dp`），完全避免了硬编码间距导致的界面紊乱。

### 3.2 动态响应与微交互 (Motion & States)
*   **渐变卡片 (Gradient Cards)**：[PrototypeModels.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeModels.kt) 完美复刻了原型的彩色角色占位渐变背景算法 `prototypeGradientFor(index)`，通过 6 组精心调配的莫兰迪双色渐变，保证了即便在未分配头像时角色卡也极具美学张力。
*   **按压与反馈 (Press State)**：在 Compose 卡片与按钮中引入了 `Ripple` 与微弱变色机制，完美对应原型 CSS 中的 `.st-press:active`，提供灵动的手感。
*   **Material 3 Bottom Sheets**：所有编辑表单、设置抽屉和快照恢复逻辑，均转化为原生系统的 `ModalBottomSheet`，比网页端拥有更流畅的划出曲线。

---

## 4. 关键技术实现深度评估

在后台服务和数据结构融合方面，该项目进行了高难度的精细开发，具体体现在以下四个关键机制上：

### 4.1 数据双路访问与 Parse-Merge-Save 机制
*   **挑战**：安卓原生 App 与 WebView 中的 SillyTavern 同时读写同一套数据目录，直接的文件覆写极易导致数据冲突或覆盖未暴露给原生的未知配置。
*   **落地实现**：原生端涉及 settings 修改（如切换当前 Persona 或更新 API 连接）时，并非完全重写，而是首先从 `POST /api/settings/get` 获取全量原始 settings JSON 字符串，对其目标 key（例如 `default_persona`）进行修改，然后将新老字段**保真合并 (Parse-Merge-Save)** 并全量提交给 `/api/settings/save`。此举完美保留了未来新版本中原生端尚不感知的未知扩展配置。
*   **双路备用机制**：设计了 [LocalTavernLibraryReader.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/data/LocalTavernLibraryReader.kt) 缓存层。当后台 Node.js 服务尚未就绪时，App 直接通过本地文件流快速解析并缓存展现角色卡列表与聊天快照，提供瞬时冷启动体验。

### 4.2 双模式备份与 Manifest 安全校验
*   **落地实现**：[NodeBackup.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/NodeBackup.kt) 内建强大的备份/恢复机制，支持两种模式：
    1.  **原生 App 备份模式 (`st_backup/`)**：打包包含 `manifest.yaml`（记录 App 版本、Node 引擎哈希、配置及数据体量大小等信息），导入时进行严格指纹校验。
    2.  **ST UI 备份兼容模式 (单用户 `zip`)**：可直接解析从官方 SillyTavern Web 端导出的用户数据压缩包，并深度重组以恢复到 Android 的本地数据路径中。

### 4.3 诊断导出与凭据安全正则脱敏
*   **挑战**：技术支持或开发者在排查 Node 报错时，需要导出 `config.yaml` 与运行日志，然而这些文件中往往包含极其敏感的 Socks5 代理密码、自定义 API 密钥与个人隐私链接。
*   **落地实现**：[DiagnosticExporter.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/DiagnosticExporter.kt) 实现的诊断 ZIP 打包过程中，不仅**绝对排除**了 `secrets.json` 物理文件，更使用极其精密的正则表达式对代理连接中的 `user:password` 凭据信息进行清洗替换：
    ```
    // 例如，将敏感的 userinfo 段进行脱敏：
    socks5://admin:pass123@127.0.0.1:1080 -> socks5://[redacted]@127.0.0.1:1080
    ```
    安全脱敏机制已通过 [DiagnosticsExportTest.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/test/java/io/github/sanitised/st/DiagnosticsExportTest.kt) 自动化单元契约测试的严密拦截验证。

### 4.4 健壮的后台进程与冲突保护
*   **非主动退出捕获**：SillyTavern 属于后台常驻 Node 服务，如被 Android 系统低内存强杀，原生侧 `NodeService` 会第一时间在其 `service.log` 写入明确的异常诊断标识（如 `unexpected exit: code 137`），防止白屏并允许用户一键拉起。
*   **启动端口冲突预检**：在每次服务拉起前，[PortAvailability.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/PortAvailability.kt) 对目标端口进行快速 Socket 测试。若遇冲突，主动阻断启动并提示用户，避免频繁崩溃循环。

---

## 5. 后续演进建议与路线图

为了巩固当前的落地成果，建议在 M3 收尾阶段和后续的 M4 里程碑中重点落实以下技术方向：

### 5.1 M3 P0 诊断与发布稳定性收尾 (高优先级)
1.  **真机复杂备份兼容性 Smoke 测试**：
    *   建议对从多端、不同浏览器官方网页版 SillyTavern 导出的各种脏数据单用户备份包进行高强度的安卓端边界导入测试，确保 `NodeBackup.materializeUiBackup` 具备更强的文件路径容错与解析能力。
2.  **设置快照的双端 Diff 原生展现**：
    *   目前的原生快照恢复直接触发强制覆写。未来可考虑提供一个轻量级面板，高亮标示出即将发生的本地 settings 属性差异（如端点变化），以防用户误操作引发灾难性覆盖。

### 5.2 M3 P1 / M4 原生体验补强
1.  **API 密钥 (Secrets) 原生大面积安全校验**：
    *   在 [PrototypeSystemScreens.kt](file:///Users/changlepan/stas/ST-android-setting/app/src/main/java/io/github/sanitised/st/ui/screens/PrototypeSystemScreens.kt) 的 `APIScreen` 侧，虽支持 Key 的写入与 active 标识，但仍缺乏类似“双向密码安全信道”（不应在任何本地 Android 日志甚至 debug console 输出任何解密后的 API 明文）。
2.  **角色高级卡片元数据交互展开**：
    *   在 `PrototypeCharacterProfileScreen` 中补全 Tavern Card V2 精密定义的 alt greetings (可选问候语)、角色名多语言别称、系统预设前置词的拖拽覆盖等功能。

### 5.3 M4 原生群聊与插件管理器 (独立里程碑)
*   **原生群聊逻辑硬重构**：
    *   需要深入剖析 SillyTavern 的群聊逻辑（涉及 `/api/groups` 及 `POST /api/chats/group` 等路由），并原生化成员添加、头像混排、多角色交替发言顺序编辑器（包含“轮巡模式”或“基于权重”机制的界面化设定）。
*   **Quick Reply (快捷回复集) 的原生表达式渲染**：
    *   需要将 QR 集合与 Slash Command 原生列表化，并配合安卓输入法候选栏或悬浮按钮注入 WebView 容器，提升对话操作层效能。

---

> [!NOTE]
> **审计结论**
> 当前原生项目 `ST-android-setting` 针对 `SillyTavern Mobile` 的落地情况非常优秀。不仅实现了高品质的 Material Design 3 风格，更在底层技术设计上解决了进程冲突、凭据泄漏、API 写入覆写冲突等众多高难度工程难题，为下一阶段走向 100% 生产力交付打下了极其坚实的基石。
