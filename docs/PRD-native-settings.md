# PRD：ST Android 移动端客户端改造

版本：v0.7
日期：2026-05-27
状态：Phase 0 已完成，M0 已验收，M1 已验收，进入 M2 阶段
变更记录：v0.1 → v0.2 策略转型；v0.2 → v0.3 更新 M0；v0.3 → v0.4 调整 M1；v0.4 → v0.5 更新 M1 结果；v0.5 → v0.6 调整 M2 方向；v0.6 → v0.7 明确重构 UI 设计来源：摒弃 PRD 历史的臆想视觉设计，以独立工具设计稿 `SillyTavern Mobile.html`（共 26 个 Artboard 页面）为最终视觉还原标准。本 PRD 仅作为功能迁移阶段的参考文档，不再承担 UI 视觉与布局定义的职责。

> [!IMPORTANT]
> **设计规范重要变更通知（2026-05-27）**：
> 本文档原有的具体 UI 布局设计（例如臆想的 5-Tab 规划、世界书详情等）已全面作废。所有原生界面与服务管理界面的视觉实现、间距、圆角与布局架构，应完全以最新工具设计稿 `SillyTavern Mobile.html` 及其背后的 `design-canvas.jsx`、`primitives.jsx` 为最终的“视觉合同”进行高保真组件化还原。

---

## 1. 一句话定义

将 ST-android 从"启动器 + 外部浏览器"改造为"Chat WebView + App 原生功能页"——App 内运行 SillyTavern 服务，聊天页继续由原版 SillyTavern WebView 承载，角色、设置、管理、预设、世界书等非 Chat 功能逐步由 Compose 原生页面承接。

---

## 2. 背景与动机

### 2.1 现状

当前 App（ST-android-setting）功能局限于：

| 功能 | 实现状态 |
|---|---|
| 启动/停止 Node Core Service | 已有 |
| 查看 stdout/stderr/service 日志 | 已有 |
| 编辑 config.yaml | 已有 |
| 应用设置（主题、更新、电池） | 已有 |
| 管理 ST 安装（备份/导入/自定义源/GitHub 安装） | 已有 |
| 法律信息展示 | 已有 |
| App 内聊天 | 已有（M0：内置 WebView） |
| App 自有移动端页面 | 已有（M1：首页工作台、角色入口、工具入口） |
| Android 原生能力桥接 | 基础文件选择/运行信息已接入，M1+ 继续增强 |

M0 之前，用户启动 Core 后通过 `Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:$port/"))` 跳转系统浏览器访问 SillyTavern。M0 已改为内置 WebView，App 不再只是一个启动器。

### 2.2 v0.1 → v0.2 策略调整

v0.1 PRD 计划用 Jetpack Compose 原生重写 SillyTavern 的 22 个屏幕（聊天、角色、世界书、预设等）。经过评估，该方案存在核心问题：

1. **工作量不可控**：SillyTavern 前端功能极度复杂（插件系统、宏语言、多后端适配），完整原生化需数月
2. **上游同步困难**：用 Compose 重写意味着与 SillyTavern 上游完全脱钩，无法受益于社区更新
3. **API 边界需要梳理**：SillyTavern Core 已有本地管理 API，但并非面向第三方 SDK 文档化；Android 端需要把实际使用的本地 API 明确成兼容契约
4. **投入产出比差**：大量 Power User 功能（扩展管理、正则规则、高级提示词）在移动端使用频率低

v0.2 策略：**先用 WebView 承接原版 SillyTavern 前端，再用移动端补丁和原生桥接逐步"App 化"。**

v0.4 策略调整：SillyTavern 原版前端已经具备手机适配界面，M1 不再投入 CSS/JS 去改它的自带 UI。WebView 保持接近上游原样；App 化体验改由 Compose 自有页面承担。

v0.6 策略调整：M2 起不再把非 Chat 复杂功能长期交给原版 WebView。Chat 页仍保持原版 WebView，以保留 SillyTavern 聊天运行时、扩展脚本和生成流程；其他用户可见功能页以 Compose 原生承接为长期方向。M2 当前聚焦角色管理基础承接，后续里程碑再逐步扩展到预设、世界书、Persona、群聊、Prompt Manager、连接档案等页面。M2 角色管理以 SillyTavern 本地 API 为主线，本地文件读取只作为缓存、兜底和迁移保护。详细迁移拆解见 `docs/M2-character-management-migration.md`。

### 2.3 新定位

> SillyTavern Android Native Shell + App Pages

- 保留原版 SillyTavern 的后端、数据结构、角色卡、聊天记录、API 配置、世界书等全部能力
- Chat WebView 内保持 SillyTavern 原版聊天界面，减少对聊天运行时的破坏
- 在 WebView 外提供 App 原生的角色、管理、设置和其他功能页
- 核心聊天、角色、角色设置、预设、世界书、导入导出必须稳定
- 第三方扩展作为实验性能力，不承诺完整兼容

核心优势：
1. 不需要用户装 Termux 或理解 Node.js
2. 聊天运行时不需要重写，降低最核心流程风险
3. 非 Chat 功能可以按 Android 交互重做，逐步摆脱桌面 Web UI 的限制
4. App 自有页面可渐进式增强，同时通过数据格式兼容继续跟随 SillyTavern Core

### 2.4 设计来源

最新设计稿完全来自项目中的 `SillyTavern Mobile.html` 独立工具设计文档（共包含 26 个精心设计的手机 Artboard 屏幕），摒弃了以往非专业设计出的“臆想设计”。该 HTML 设计稿明确定义了：
*   **入口与浏览**：主抽屉（左划呼出）、对话列表、角色库。
*   **聊天屏核心状态**：对话进行中、富文本正在输入、加号附件与工具抽屉、长按消息底部操作表、行内消息编辑等。
*   **角色与扮演**：高保真角色详情/编辑（支持头像照片上传入口）、群聊编排、扮演者 (Persona) 切换与管理。
*   **知识与系统**：真实的 API 连接切换表单（Chat/Text Completion 模式切换与预设切换）、AI 采样设置（温度、抑制、流式、思考链）、世界书/知识库展示、App我的设置。
*   **ST 核心/服务管理（Android 专属）**：服务总览（运行中/已停止/启动失败状态卡）、内核版本管理（支持 GitHub 自定义源及 ZIP 安装）、数据备份恢复、实时日志输出与客户端偏好。

该工具设计稿作为 Compose 原生 UI 与核心管理端高保真还原的唯一视觉合同。

---

## 3. 产品原则

### 3.1 Chat WebView, Native Pages

SillyTavern 聊天前端通过内置 WebView 加载。App 的 Compose Shell 负责：启动页、首页、角色列表、角色设置/编辑、工具、设置、日志、备份/恢复，以及后续预设、世界书、Persona、连接档案等非 Chat 功能页。

### 3.2 尊重上游 Chat UI

不 fork、不大规模覆盖 SillyTavern 聊天前端界面。Chat WebView 内保持接近上游原样。必要的补丁只用于兼容、Bridge 胶水或严重设备问题，不作为非 Chat 原生页面建设的替代方案。

### 3.3 Bridge 做可选增强

`STAndroidBridge`（`window.STAndroid`）提供 Android 原生能力，但 SillyTavern 前端不强依赖它。检测到 `window.STAndroid` 时启用增强，否则回退到标准 Web 行为。

### 3.4 Core 负责数据与运行时，App 负责非 Chat 体验

App 不重写聊天生成链路，不在 Android 端复制提示词最终组装、世界书触发、扩展注入等运行时逻辑。非 Chat 页面优先通过 SillyTavern 本地 API 读写数据；只有在 API 不覆盖、服务未启动展示缓存、导入导出或恢复场景下，才直接读取本地文件作为兜底。

### 3.5 渐进式改造

保留现有功能不受影响。每个 Milestone 交付可用增量。

### 3.6 设计稿为非 Chat 页面视觉合同

Compose 原生页面（如角色库、编辑、内核服务管理、日志等）以及后续阶段的世界书、预设的视觉实现，其颜色、间距、圆角、整体布局以及状态切换，应严格对齐 `SillyTavern Mobile.html` 中的 CSS Tokens (如 `tokens.css` 等系统) 进行还原。

---

## 4. 架构概览

### 4.1 整体架构

```
Android App
├── Compose Shell
│   ├── 启动页（01-startup）
│   ├── 首页 / 管理（02-home）
│   ├── 角色列表（04-characters）
│   ├── 角色设置 / 编辑（05-character-edit）
│   ├── 设置页（10-settings）
│   ├── 日志页（09-diagnostics）
│   ├── 备份/恢复（manage）
│   ├── config.yaml 编辑器
│   ├── 法律信息
│   ├── 后续非 Chat 原生页（预设 / 世界书 / Persona / Prompt Manager 等）
│   └── ChatWebViewScreen ← 原版聊天 WebView 容器
│
├── Node Runtime
│   ├── node binary
│   ├── SillyTavern server
│   ├── config.yaml
│   ├── data/
│   └── logs/
│
├── Android Bridge（window.STAndroid）
│   ├── File Picker
│   ├── Save Document
│   ├── Share Sheet
│   ├── Clipboard
│   ├── Notification
│   ├── TTS/STT
│   └── Theme Sync
│
└── App Native Pages
    ├── Android 首页工作台
    ├── 继续聊天 / 最近聊天 / 最近角色
    ├── 角色列表 / 搜索 / 筛选
    ├── 角色设置 / 编辑 / 字段校验
    ├── 工具入口页
    ├── 管理与备份
    ├── 诊断与日志
    ├── 预设 / 世界书 / Persona / Prompt Manager（后续里程碑）
    └── 设置页外部浏览器入口
```

### 4.2 导航结构

```
MainActivity
  └─ STAppTheme（设计稿 Token → Material3 映射）
      └─ Scaffold
          ├─ STBottomBar（条件显示：WebView 全屏时隐藏）
          └─ NavHost
              ├─ home        → Compose 首页
              ├─ chat        → ChatWebViewScreen（原版 SillyTavern WebView）
              ├─ characters  → Compose 角色列表（M1 起；M2 增强）
              ├─ characterEdit/{id?} → Compose 角色设置 / 编辑（M2 起）
              ├─ tools       → Compose 工具入口页（M1 起）
              ├─ settings    → Compose 设置页
              └─ 子路由       → logs / config / legal / manage 等
```

Chat Tab 进入原版 SillyTavern WebView。Characters / Tools 从 M1 起进入 App 自有 Compose 页面，不提供伪深链到 SillyTavern 内部角色/工具面板。M2 当前范围内，Characters 进一步承接角色管理基础能力；角色数据优先通过 SillyTavern 本地 API 读写。需要排查原版 Web UI 行为时，由设置页统一提供"在网页打开"入口，作为诊断和兼容兜底，不作为常规功能入口。

### 4.3 关键技术选型

| 决策点 | 选择 | 理由 |
|---|---|---|
| 聊天 UI | 内置 WebView | 复用 SillyTavern 聊天运行时，避免重写生成链路 |
| 非 Chat 功能 UI | Jetpack Compose | 启动/设置/日志/备份/角色等页面保持原生体验 |
| 导航 | Navigation Compose 2.7 | 已实现，支持 back stack、deep link、状态恢复 |
| 主题 | STTheme + Material3 | 已实现，用于管理页面和 App 原生页面；Chat WebView 内优先保留 ST 原版主题 |
| 网络 | OkHttp 4.12 | 已引入，用于 TavernCoreApi 健康检查、角色列表和角色编辑 API |
| JS 桥接 | WebView.addJavascriptInterface | 标准 Android WebView Bridge 方案 |
| 前端补丁 | 最小化兼容补丁 | 仅用于 Chat WebView 的 Bridge 胶水、严重设备问题或上游无法覆盖的兼容性问题 |
| 状态管理 | ViewModel + Compose State | 沿用现有模式 |

---

## 5. 设计 Token 系统

从设计稿 `common.css` `:root` 提取，已实现在 `ui/theme/STColors.kt` 和 `ui/theme/STTheme.kt`。用于 Compose Shell 和非 Chat 原生页面；Chat WebView 内优先使用 SillyTavern 原版主题体系。

### 5.1 颜色

| Token | 浅色值 | 深色值 | 用途 |
|---|---|---|---|
| bg | `#F8FAFD` | `#1A1C1E` | 页面背景 |
| surface | `#FFFFFF` | `#2D2F31` | 卡片、底部栏 |
| surfaceWarm | `#E8F0FE` | `#1E2A3A` | 强调背景、选中态 |
| fg | `#202124` | `#E3E3E3` | 主文字 |
| fg2 | `#3C4043` | `#C4C7C5` | 次文字 |
| muted | `#5F6368` | `#9AA0A6` | 辅助文字、图标 |
| border | `#DADCE0` | `#444746` | 分割线、卡片边框 |
| borderSoft | `#EDF0F2` | `#3C3E40` | 列表项分割线 |
| accent | `#1A73E8` | `#8AB4F8` | 主按钮、链接、选中标签 |
| accentOn | `#FFFFFF` | `#1A1C1E` | 主按钮文字 |
| success | `#188038` | `#81C995` | 运行中状态 |
| warn | `#F9AB00` | `#FDD663` | 警告状态 |
| danger | `#D93025` | `#F28B82` | 错误、删除 |

### 5.2 间距

| Token | 值 |
|---|---|
| xs | 4dp |
| sm | 8dp |
| md | 12dp |
| lg | 16dp |
| xl | 20dp |
| xxl | 24dp |
| xxxl | 32dp |
| section | 48dp |

### 5.3 圆角

| Token | 值 | 用途 |
|---|---|---|
| sm | 4dp | 代码块、小标签 |
| md | 12dp | 按钮、输入框、紧凑卡片 |
| lg | 24dp | 卡片、Banner、底部弹窗 |
| pill | 9999dp | 状态标签、头像 |

---

## 6. ChatWebViewScreen 规格

### 6.1 核心行为

ChatWebViewScreen 是 App 的完整 SillyTavern 界面入口，内嵌 WebView 加载 `http://127.0.0.1:{port}/`。

**启动流程：**
1. 用户点击聊天 Tab 或首页"开始聊天"按钮
2. 检测 NodeService 状态
3. 若 RUNNING → 直接加载 WebView
4. 若 STOPPED → 自动启动 NodeService，轮询 healthCheck 直到 ready，然后加载
5. 若 ERROR → 显示本地错误页，提供重试/查看日志入口

**WebView 配置：**
- JavaScript 启用
- DOM Storage 启用
- 文件访问启用（用于角色卡导入等）
- Mixed Content 允许（localhost）
- 注入 `STAndroidBridge` JS Interface
- 加载完成后注入 Android runtime flags
- 不默认注入移动端 CSS 覆盖；只在兼容问题明确时追加最小补丁

**导航控制：**
- 返回键优先处理 WebView history（`webView.canGoBack()`）
- WebView history 耗尽后返回 App 首页
- Chat Tab 进入 WebView；Characters/Tools Tab 从 M1 起进入 Compose 自有页面
- Characters/Tools 不再通过 JS 猜测或点击 SillyTavern 内部面板，避免进入不可预期页面后无法自然回到 App 原生页
- M2 起角色列表和角色设置/编辑由 Compose 原生承接，不再把角色编辑交给原版 Web UI
- 系统浏览器打开完整原版 Web UI 的入口放在设置页

**错误处理：**
- 服务未启动 → 本地错误页 + 启动按钮
- 端口不可达 → 本地错误页 + 重试
- 页面加载失败 → 本地错误页 + 刷新
- Node 崩溃 → 检测 NodeService 状态变化 → 显示本地错误页 + 查看日志

### 6.2 验收标准

M0 验收聚焦"App 内可用，不再依赖外部浏览器"。M1 不再验收 SillyTavern Web UI 的移动端 CSS 改造，而是验收 App 自有页面建设。

1. 用户点击"开始聊天"后，不跳外部浏览器
2. App 内直接进入 SillyTavern 完整 UI
3. 返回键正确处理 WebView history
4. Node 崩溃时，WebView 显示本地错误页
5. 基础文件上传、角色卡导入走 Android 文件选择器
6. 横竖屏/键盘有基础适配；后续只针对明确设备问题做兼容修复

---

## 7. STAndroidBridge 规格

通过 `WebView.addJavascriptInterface` 注入，暴露为 `window.STAndroid`。

### 7.1 接口定义

```
window.STAndroid
 ├── openFilePicker(type: string, multiple: boolean) → Promise<FileResult[]>
 ├── saveFile(filename: string, mime: string, base64: string) → Promise<boolean>
 ├── shareText(text: string) → void
 ├── shareImage(base64: string, mime: string) → void
 ├── copyToClipboard(text: string) → void
 ├── setKeepScreenOn(enabled: boolean) → void
 ├── notify(title: string, body: string) → void
 ├── vibrate(pattern: number[]) → void
 ├── getAppInfo() → AppInfo
 ├── getRuntimeInfo() → RuntimeInfo
 ├── getThemeMode() → "light" | "dark" | "auto"
 ├── ttsSpeak(text: string, lang: string) → void
 ├── ttsStop() → void
 └── sttStart(lang: string) → Promise<string>
```

### 7.2 设计原则

桥接层必须做成**可选增强**：

```js
const isAndroidApp = Boolean(window.STAndroid);
```

SillyTavern 前端不强依赖 Android Bridge。在检测到 `window.STAndroid` 时启用增强路径，否则回退到标准 Web 行为。这样：
- 桌面端逻辑不受影响
- 同步上游时冲突最小化
- 通过 `ST_ANDROID=1` 环境变量 + `window.STAndroid` 双重检测确认 Android 环境

### 7.3 场景映射

| 场景 | 原 Web 逻辑 | Android Bridge 替代 |
|---|---|---|
| 导入角色卡 | `<input type="file">` | `STAndroid.openFilePicker("character")` |
| 导出聊天 | 浏览器下载 | `STAndroid.saveFile(...)` |
| 分享回复 | 复制文本 | `STAndroid.shareText(...)` |
| 图片上传 | Web 文件选择 | `STAndroid.openFilePicker("image")` |
| 通知 | 浏览器通知 API | `STAndroid.notify(...)` |
| TTS | Web Speech API | `STAndroid.ttsSpeak(...)` |
| 主题同步 | 手动设置 | `STAndroid.getThemeMode()` 自动同步 |

---

## 8. App 原生页面建设策略

M1 开始不再以改造 SillyTavern Web UI 为目标。M2 起策略进一步调整为：Chat WebView 负责承载原版聊天运行时；除 Chat 外的功能页逐步由 App 原生页面承接。

### 8.1 M1 页面范围

M1 建设一组 Compose 页面，目标是让用户打开 App 后先看到 Android 客户端自己的工作台，而不是只看到一个网页入口。

```
App 自有页面
 ├── 首页 / 工作台
 │   ├── 服务状态
 │   ├── 启动 / 停止
 │   ├── 继续聊天
 │   ├── 最近聊天
 │   ├── 最近角色
 │   └── 管理入口（日志 / config / 设置 / Manage ST）
 ├── 角色入口页
 │   ├── 最近角色
 │   ├── 角色空态
 │   └── 返回聊天入口
 ├── 工具入口页
 │   ├── 备份 / 恢复
 │   ├── 配置编辑
 │   ├── 日志 / 诊断
 │   └── Manage ST
 └── 设置页优化
     ├── 主题
     ├── 自动打开
     ├── 在网页打开
     ├── 更新
     └── 电池保活提示
```

### 8.2 M2 当前页面范围

M2 当前范围建设角色管理基础承接，不把所有非 Chat 页面一次性做完。角色管理以 SillyTavern 本地 API 为主线，功能覆盖口径以 `docs/M2-character-management-migration.md` 为准。

```
M2 当前范围
 ├── 角色列表
 │   ├── `/api/characters/all` 读取
 │   ├── 搜索
 │   ├── 排序、收藏、最近、标签筛选（优先使用 API 返回字段和 ST tag map）
 │   ├── 头像预览
 │   ├── 新建、导入、导出、重命名、复制、删除等基础角色管理动作
 │   ├── 空态 / 错误态 / 刷新
 │   └── 进入角色设置 / 编辑
 └── 角色设置 / 编辑
     ├── `/api/characters/get` 读取详情
     ├── 新建角色
     ├── 编辑基础字段、Prompt Overrides、Creator Metadata、Advanced Definitions、Character Note、Alternate Greetings
     ├── `/api/characters/create` 新建，`/api/characters/edit` 完整保存，`/api/characters/merge-attributes` 局部更新
     ├── `/api/characters/edit-avatar` 更换头像
     ├── 历史聊天列表入口（打开 Chat WebView 继续聊天）
     └── 保存后回到角色列表或进入 Chat WebView
```

M2 不包含预设、世界书、Persona、群聊、Prompt Manager、连接档案、扩展管理等页面的完整实现；这些页面在 PRD 中作为长期 App 原生方向保留，后续里程碑逐项建设。

### 8.3 WebView 边界

Chat WebView 内保持 SillyTavern 原版移动端聊天界面，不主动覆盖：
- 聊天页布局
- 聊天输入、消息列表、生成状态、聊天内弹窗和抽屉行为
- 与生成链路、扩展脚本和聊天运行时强相关的前端逻辑

仅在以下情况下允许加入最小补丁：
1. Android WebView 与上游前端存在明确兼容问题
2. 文件选择、下载、分享等能力需要 Bridge 胶水
3. 某些机型出现键盘或安全区严重遮挡，且无法通过 Activity / WindowInsets 解决
4. 补丁不改变原版页面信息架构，不长期 fork 上游 UI

### 8.4 数据来源

M1 的自有页面以轻量数据为主；M2 角色管理开始引入 API 数据适配层：
- 服务状态、端口、版本来自 `NodeStatus` 和 `STAndroidBridge.getRuntimeInfo()`
- 最近聊天可继续通过本地 data 目录读取作为轻量入口；角色列表和角色详情改为优先使用 `TavernCoreApi`
- 角色设置/编辑以本地 SillyTavern API 为数据源：`/api/characters/all`、`/api/characters/get`、`/api/characters/create`、`/api/characters/merge-attributes`
- 本地文件读取仅作为服务未启动时的只读缓存、迁移诊断、备份恢复和导入导出兜底
- 缺少稳定 API 的非 Chat 页面不再默认交给原版 Web UI，而是进入后续 App 原生页面建设队列
- M1 不提供 Characters/Tools 到原版 SillyTavern 内部面板的伪深入口
- M2 当前不重写提示词、世界书触发、预设编辑、扩展管理等复杂业务；这些留到后续非 Chat 原生页面里逐项处理

---

## 9. 功能分层

将功能分为 4 层，决定哪些立即做 App 原生页面，哪些作为后续非 Chat 原生页面，哪些只保留 Chat WebView。

### L1：App 自有页面优先

高频、稳定、适合 Android 原生体验的功能，优先用 Compose 做入口或完整页面：

| 功能 | 适配方式 |
|---|---|
| 首页 / 工作台 | Compose 原生 |
| 服务启动/停止/状态 | Compose 原生 |
| 日志/错误提示 | Compose 原生 |
| 备份/恢复/导入导出 | Compose 原生 + Android 文件选择器 |
| 设置（主题、更新、电池、自动打开） | Compose 原生 |
| 最近聊天/最近角色入口 | Compose 原生 + 轻量数据读取 |
| 角色列表 | Compose 原生 + SillyTavern 本地 API |
| 角色设置 / 编辑 | Compose 原生 + SillyTavern 本地 API |
| 在网页打开完整 SillyTavern | 设置页外部浏览器入口 |

### L2：后续 App 原生页面

功能重要，但不纳入 M2 当前范围。后续里程碑按页面逐项原生化，不再把完整能力长期交给原版 WebView：

| 功能 | 适配方式 |
|---|---|
| 预设选择/编辑 | 后续 Compose 原生页面 |
| 世界书 | 后续 Compose 原生页面 |
| Persona | 后续 Compose 原生页面 |
| 群聊 | 后续 Compose 原生页面 |
| Quick Replies | 后续 Compose 原生页面 |
| Regex / 文本规则 | 后续 Compose 原生页面 |
| Prompt Manager | 后续 Compose 原生页面 |
| API 连接设置 | 后续 Compose 原生页面 |
| 图片/附件设置 | 后续 Compose 原生页面 + Bridge 能力 |

### L3：Chat WebView 保留范围

保持 SillyTavern 原版 Chat UI，不做 App 原生化：

- 聊天消息列表
- 聊天输入区
- 生成 / 停止生成 / 继续生成等聊天运行时操作
- 聊天页内由 SillyTavern 前端驱动的扩展脚本行为
- 与生成链路强绑定、无法安全拆出的临时兼容能力

### L4：明确不承诺兼容

早期不承诺支持：

- 第三方 UI 扩展完整兼容
- 所有插件自动更新
- 依赖服务端插件的扩展
- 任意 DOM 注入插件
- 桌面端快捷键体系

产品表述：
> Android 版支持核心聊天功能；扩展系统作为实验性功能，不保证全部兼容。

---

## 10. 内置移动端增强模块

替代"强行兼容插件系统"，用原生能力覆盖用户高频需求。

| 模块 | 实现层 | 替代的插件需求 |
|---|---|---|
| 角色卡导入助手 | Bridge + Compose | 角色卡导入插件 |
| 聊天备份助手 | Compose 原生 | 备份管理插件 |
| API Key 管理器 | Compose 原生 | API 管理插件 |
| 图片发送/压缩/预览 | Bridge | 图片处理插件 |
| TTS/STT 原生桥接 | Bridge | 语音插件 |
| 快捷入口 / 最近项 | Compose 原生 | 常用侧栏/启动页插件 |
| 角色设置 / 编辑 | Compose 原生 | 角色管理插件 |
| 正则模板入口 | 后续 Compose 原生页面 | 正则管理插件 |
| 世界书管理 | 后续 Compose 原生页面 | 世界书管理插件 |
| 消息收藏 | Bridge | 收藏插件 |
| 分享到 SillyTavern | Android Intent | 系统分享集成 |

---

## 11. TavernCoreApi（保留）

已定义在 `api/TavernCoreApi.kt`，当前为骨架。在 v0.6 策略下，其角色调整为：

- **健康检查**：WebView 加载前探测 Core 是否 ready
- **App 首页数据**：Compose 首页展示最近聊天/角色（轻量读取）
- **自有入口页数据**（M1 起）：角色入口、工具入口、最近聊天等页面需要轻量 API 或本地 data 读取
- **角色编辑数据**（M2 起）：角色列表和角色设置/编辑优先通过 SillyTavern 本地 API 读写

TavernCoreApi 不承担聊天生成运行时。Chat 仍由 WebView 内的 SillyTavern 前端 JS 直接处理；非 Chat 页面通过 SillyTavern 本地 API 和必要的本地只读兜底逐步原生承接。

```kotlin
interface TavernCoreApi {
    suspend fun healthCheck(): CoreHealth
    suspend fun listCharacters(): List<CharacterSummary>     // `/api/characters/all`
    suspend fun getCharacter(avatar: String): CharacterDetail // `/api/characters/get`
    suspend fun createCharacter(request: CharacterSaveRequest): String // `/api/characters/create`
    suspend fun updateCharacter(request: CharacterSaveRequest): Unit // `/api/characters/edit`
    suspend fun renameCharacter(avatar: String, newName: String): String // `/api/characters/rename`
    suspend fun duplicateCharacter(avatar: String): String // `/api/characters/duplicate`
    suspend fun deleteCharacter(avatar: String, deleteChats: Boolean = false): Unit // `/api/characters/delete`
    suspend fun importCharacter(fileName: String, bytes: ByteArray, preservedName: String? = null): String // `/api/characters/import`
    suspend fun exportCharacter(avatar: String, format: CharacterExportFormat): CharacterExportFile // `/api/characters/export`
    suspend fun updateCharacterAvatar(avatar: String, fileName: String, bytes: ByteArray): Unit // `/api/characters/edit-avatar`
    suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary> // `/api/characters/chats`
    suspend fun listRecentChats(): List<ChatSummary>         // 首页用
}
```

---

## 12. 里程碑计划

### Phase 0 — 架构基础 ✅ 已完成

| 交付物 | 状态 |
|---|---|
| Navigation Compose + 底部导航栏 | ✅ |
| STTheme（颜色/间距/圆角/字体 Token） | ✅ |
| TavernCoreApi 接口骨架 | ✅ |
| MainActivity 从手动路由迁移到 NavHost | ✅ |
| 聊天/角色/工具 占位页 | ✅ |
| OkHttp 依赖 | ✅ |

Phase 0 的成果在 v0.6 策略下完全保留：
- Compose Shell 继续作为管理页面框架
- STTheme 继续驱动管理页面和非 Chat 原生页面
- 导航结构保留，Chat 进入 ChatWebViewScreen，Characters/Tools 从 M1 起改为 App 自有页面，M2 起增加角色设置/编辑路由
- TavernCoreApi 用于首页数据和健康检查

---

### M0 — 验证版 ✅ 已验收

**目标：** 证明 App 内聊天可用，关闭外部浏览器依赖。

| 任务 | 优先级 | 状态 | 描述 |
|---|---|---|---|
| ChatWebViewScreen | P0 | ✅ 代码完成 | WebView 容器页面，加载 `127.0.0.1:{port}` |
| 服务 ready 检测 | P0 | ✅ 代码完成 | Node 启动后轮询 healthCheck，ready 后自动加载 WebView |
| 返回键处理 | P0 | ✅ 代码完成 | WebView history 优先，耗尽后返回首页 |
| 本地错误页 | P0 | ✅ 代码完成 | 服务未启动/端口不可达/加载失败/Node 错误时显示本地错误页 |
| 替换外部浏览器 | P0 | ✅ 代码完成 | 主入口从 `127.0.0.1:{port}` 外部浏览器跳转改为内部导航；外部文档/站点链接仍使用系统浏览器 |
| 横竖屏/键盘基础适配 | P1 | ✅ 已验收 | `MainActivity` 使用 `windowSoftInputMode="adjustResize"` |
| 文件上传基础接入 | P1 | ✅ 已验收 | WebView `onShowFileChooser` 接入 Android 文件选择器 |

**代码验证：**
- 2026-05-26 本地执行 `./gradlew testDebugUnitTest`：通过
- 2026-05-26 本地执行 `./gradlew assembleDebug`：通过
- Debug APK 已包含 `lib/arm64-v8a/libnode.so`、`assets/node_payload/st_bundle.tar` 和 `assets/node_payload/npm.tar`

**真机验收：**
1. ✅ 首次安装后点击聊天 Tab 或首页"Open SillyTavern"，自动启动 NodeService 并进入 App 内 WebView
2. ✅ WebView 内 SillyTavern 首页/聊天页可正常渲染，不出现空白页或长时间 preloader
3. ✅ 返回键先返回 WebView history，history 耗尽后回到 App 首页
4. ✅ 角色卡导入、图片/文件上传弹出 Android 文件选择器，并能把选择结果交回 WebView
5. ✅ 旋转屏幕、键盘弹起、系统导航栏显示时，聊天输入区至少可用
6. ✅ 强制停止或崩溃 Node 后，WebView 区域显示本地错误页，并可进入日志页

**M0 验收口径：** 用户安装 APK 后，点一次就能在 App 内使用 SillyTavern，不跳外部浏览器。当前状态为已验收，可进入 M1。

---

### M1 — App 自有页面起步版 ✅ 已验收

**目标：** 在不改动 SillyTavern 原版移动端界面的前提下，建立 Android App 自己的首页、角色入口、工具入口和管理体验。

| 任务 | 优先级 | 状态 | 描述 |
|---|---|---|---|
| App 首页 / 工作台 | P0 | ✅ 已验收 | Compose 首页展示 Core Service 卡片、启动/停止、继续聊天、最近聊天、最近角色和管理入口 |
| 角色入口页 | P0 | ✅ 已验收 | Compose 页面展示最近角色和空态；不再提供不可控的 Full Manager 深入口 |
| 工具入口页 | P0 | ✅ 已验收 | Compose 页面整合备份/恢复、config、日志、Manage ST |
| 首页数据读取 | P0 | ✅ 已验收 | 通过本地 data 目录读取最近聊天/角色，失败时优雅降级为空态 |
| 底部导航重分配 | P1 | ✅ 已验收 | Chat 保持 WebView；Characters/Tools 改为 App 自有页面；移除 WebView 伪深入口 |
| 设置页整理 | P1 | ✅ 已验收 | 保留主题、更新、电池、自动打开；新增"在网页打开"外部浏览器入口 |
| 首页底部 dock 去重 | P1 | ✅ 已验收 | 移除旧首页底部固定启动 dock，由 Core Service 卡片承担启动/停止/继续聊天 |
| 文件桥接补齐 | P1 | ✅ 沿用 M0 | 保留 WebView `onShowFileChooser`；App 自有页暂不新增独立角色导入流程 |
| WebView 兼容兜底 | P2 | ✅ 无新增补丁 | 只处理真机发现的明确 WebView/键盘/文件问题，不做 UI 覆盖型 CSS 补丁 |

**代码验证：**
- 2026-05-26 本地执行 `./gradlew testDebugUnitTest`：通过
- 2026-05-26 本地执行 `./gradlew assembleDebug`：通过
- 2026-05-26 通过无线调试安装 Debug APK 到真机 `SM_S9310`：成功

**真机验收：**
1. ✅ 用户打开 App 首屏能看到 Android 自有工作台和 Core Service 卡片
2. ✅ 首页旧底部固定 dock 已移除，启动/停止/继续聊天由 Core Service 卡片承担
3. ✅ Chat Tab 仍进入 App 内原版 SillyTavern WebView
4. ✅ Characters Tab 进入 Compose 角色入口页，不再提供 Full Manager 深入口
5. ✅ Tools Tab 进入 Compose 工具入口页，不再提供 Full Tools 深入口
6. ✅ 设置页提供"在网页打开"入口，用系统浏览器访问 `http://127.0.0.1:{port}/`
7. ✅ 最近聊天/角色读取失败或无数据时显示可理解空态

**M1 验收口径：** 用户打开 App 后先看到 Android 自有工作台；高频管理入口不依赖在 SillyTavern Web UI 中寻找；Chat 保持原版 WebView；Characters/Tools 不做不可控的 WebView 内部深跳转。当前状态为已验收，可进入 M2。

---

### M2 — 角色管理原生承接版（预估 4-8 周）

**目标：** 在 M1 自有页面基础上，把角色管理基础能力从原版 Web UI 中迁出，由 App 原生承接；Chat 仍由原版 SillyTavern WebView 承载。

| 任务 | 优先级 | 描述 |
|---|---|---|
| 角色 API 适配层 | P0 | 封装角色管理迁移文档列出的 P0 API：`/all`、`/get`、`/create`、`/edit`、`/merge-attributes`、`/rename`、`/delete`、`/duplicate`、`/import`、`/export`、`/edit-avatar`，统一错误、超时和服务未就绪处理 |
| 角色列表增强 | P0 | Compose 角色列表、搜索、排序、标签/收藏/最近筛选、头像预览、空态、错误态、刷新 |
| 角色设置 / 编辑 | P0 | Compose 原生页面承接基础字段、Prompt Overrides、Creator Metadata、Advanced Definitions、Character Note、Alternate Greetings |
| 角色基础管理动作 | P0 | 在 App 内完成新建、保存、重命名、复制、删除、导入、导出、头像上传 |
| 保存安全性 | P0 | 完整编辑优先通过 `/api/characters/edit` 保存，局部更新通过 `/api/characters/merge-attributes`，失败时保留编辑草稿并提示原因 |
| 最近聊天增强 | P1 | 最近聊天列表、继续聊天、空态、错误态、数据刷新；点击仍进入 Chat WebView |
| 系统分享导入 | P2 | Android Intent 接收角色卡 / 图片，作为角色管理的后续增强 |

**不在 M2 当前范围：** 预设、世界书、Persona、群聊、聊天附件、Author's Note、RAG、Quick Replies、Regex、媒体、扩展、主题、连接档案、Prompt Manager 的完整页面实现。这些页面的长期方向仍是 App 原生承接，但不阻塞 M2 验收。

**验收：** 用户可以在 App 内完成角色浏览、搜索、排序、筛选、新建、编辑、保存、重命名、复制、删除、导入、导出、头像上传，并继续进入 Chat WebView 使用角色聊天；常规角色管理不再依赖原版 SillyTavern Web UI。

**当前代码验证补充：**
- 2026-05-26 本地执行 `./gradlew testDebugUnitTest`：通过
- 2026-05-26 通过 adb 转发连接真机 `SM_S9310` 上的 ST dev 实例，执行 `ST_CONTRACT_BASE_URL=http://127.0.0.1:18000/ ./gradlew testDebugUnitTest --tests 'io.github.sanitised.st.api.TavernCoreRealContractTest'`：通过
- 真实 ST 契约测试已覆盖角色完整保存、复杂 `json_data` 未知字段保留、`merge-attributes`、`edit-avatar`、ST tag 创建/重命名/删除和 `tag_map` 保存；Chat WebView 指定聊天定位仍归入单独 smoke test

---

### M3 — 稳定版（预估 8-12 周）

**目标：** 变成真正可发布的 Android 客户端。

| 任务 | 优先级 | 描述 |
|---|---|---|
| 数据备份/恢复完善 | P0 | Compose 原生管理页，支持 .tar.gz / .tar / .zip / ST UI 导出 |
| 崩溃恢复 | P0 | Node 崩溃检测 → 自动重启 or 引导用户操作 |
| 端口冲突处理 | P1 | 检测端口占用，提示或自动切换 |
| 后台保活引导 | P1 | 引导用户设置电池优化白名单 |
| 大量机型测试 | P1 | 主流 Android 手机 + 平板适配验证 |
| 上游同步策略 | P1 | 文档化 SillyTavern bundle 更新、真机 smoke test 和兼容补丁流程 |
| 扩展系统标记 | P2 | UI 中明确标记扩展为"实验性" |
| AGPL 合规 | P2 | 开源合规说明完善 |
| App 原生页面扩展 | P2 | 按优先级继续将预设、世界书、Persona、连接档案、Prompt Manager 等非 Chat 页面移入 Compose |

**验收：** 可作为正式版发布到 GitHub Release。崩溃率 < 1%，核心功能稳定。

---

## 13. 风险与应对

| 风险 | 等级 | 应对 |
|---|---|---|
| Chat WebView 与 SillyTavern 前端 JS 兼容问题 | 高 | M0 阶段尽早验证；关注 WebView 版本差异（Android 8.0+ System WebView） |
| App 原生页面 API 调用失败 | 中 | M2 角色 API 调用必须有服务未启动、超时、HTTP 错误和解析失败状态；编辑保存失败时保留草稿 |
| Bridge 安全性（JS Interface 注入攻击） | 中 | 限制 Bridge 只对 localhost 生效；`@JavascriptInterface` 方法严格校验来源 |
| 扩展插件在 WebView 中异常 | 中 | 扩展标记为实验性；提供关闭扩展的快捷入口；捕获 JS 错误不崩溃 |
| 键盘适配在不同机型上表现不一 | 中 | 优先使用 Activity / WindowInsets 层解决；只在明确设备问题时做最小 WebView 兼容补丁 |
| SillyTavern Core 版本更新影响 API 形状 | 中 | 锁定测试过的 Core 版本；更新时先跑 Chat WebView smoke test、`TavernCoreRealContractTest` 真实 ST 契约测试和关键导入/聊天流程 |

---

## 14. 成功标准

| 指标 | M0 | M1 | M2 | M3 |
|---|---|---|---|---|
| App 内聊天可用 | ✅ | ✅ | ✅ | ✅ |
| 主流程不跳外部浏览器 | ✅ | ✅ | ✅ | ✅ |
| Core 启动/停止回归 | 100% | 100% | 100% | 100% |
| App 自有首页/工作台 | — | ✅ | ✅ | ✅ |
| App 原生页面 | — | 角色/工具入口（无伪深跳转） | 角色列表 + 角色设置/编辑闭环 | 非 Chat 页面持续扩展 |
| Bridge 功能覆盖 | 文件选择基础 | 文件选择沿用 + 外部浏览器入口 | 文件选择沿用 + 角色导入预留 | 全部 |
| 崩溃率 | < 5% | < 2% | < 1% | < 1% |
| 扩展兼容 | 不承诺 | 不承诺 | 实验性 | 实验性 |

---

## 15. 文件结构（M2 当前范围后）

```
app/src/main/java/io/github/sanitised/st/
├── api/
│   └── TavernCoreApi.kt          # API 接口 + 存根实现
├── data/
│   ├── LocalTavernLibraryReader.kt # 本地最近聊天/角色读取
│   └── CharacterRepository.kt      # M2：角色 API 聚合、筛选、草稿状态
├── ui/
│   ├── theme/
│   │   ├── STColors.kt           # 颜色 Token（浅色/深色）
│   │   └── STTheme.kt            # 主题系统（间距/圆角/字体 + Material3 映射）
│   ├── navigation/
│   │   ├── STNavGraph.kt          # 路由常量
│   │   ├── STBottomBar.kt         # 底部导航栏
│   │   └── PlaceholderScreens.kt  # 历史占位屏幕（M0 后不再作为主入口）
│   ├── webview/                   # M0 新增，代码已落地
│   │   ├── ChatWebViewScreen.kt   # WebView 容器 Composable
│   │   ├── STAndroidBridge.kt     # JS Bridge 基础实现
│   │   ├── WebViewErrorPage.kt    # 本地错误页
│   │   └── WebViewNavigator.kt    # WebView 导航控制
│   └── screens/
│       ├── M1HubScreens.kt        # 首页工作台组件、工具入口页、空态/列表组件
│       ├── CharacterListScreen.kt # M2：角色列表、搜索、筛选、头像预览
│       └── CharacterEditScreen.kt # M2：角色设置 / 编辑
├── MainActivity.kt                # NavHost + Scaffold + 底部导航
├── MainViewModel.kt               # 现有 ViewModel
├── UiApp.kt                       # 首页（M1 重构）
├── UiSettings.kt                  # 设置页（含外部浏览器入口）
├── UiConfig.kt                    # config.yaml 编辑器
├── UiLogs.kt                      # 日志查看器
├── UiLegal.kt                     # 法律信息
├── UiManageSt.kt                  # ST 安装管理
├── UiTopBar.kt                    # 通用 TopAppBar
├── ... (其他现有文件)
├── NodeService.kt                 # Node 前台服务
├── NodeStatus.kt                  # 状态枚举
└── SillyTavernUrl.kt              # 本地 SillyTavern URL 构造

android-patches/                   # 非 M1 主线；仅在必要兼容问题出现时新增
└── ...
```

---

## 16. 设计稿参考索引

v0.6 中设计稿的角色调整：设计稿主要服务 App 原生页面；Chat 页面继续由原版 SillyTavern WebView 承载，不按设计稿重写。

| 屏幕 | 文件 | v0.6 实现方式 | 里程碑 |
|---|---|---|---|
| 01 启动与初始化 | `01-startup.html` | Compose 原生 | M1 |
| 02 首页 | `02-home.html` | Compose 原生工作台 | M1 |
| 03 聊天页 | `03-chat.html` | 原版 SillyTavern WebView | M0 |
| 04 角色列表 | `04-characters.html` | M2 Compose 原生列表、搜索、筛选、头像预览 | M1 / M2 |
| 05 角色编辑 | `05-character-edit.html` | M2 Compose 原生设置 / 编辑 | M2 |
| 06 世界书 | `06-worldbook.html` | 后续 Compose 原生页面 | M3+ |
| 07 模型预设 | `07-presets.html` | 后续 Compose 原生页面 | M3+ |
| 08 工具中心 | `08-tools.html` | Compose 工具入口页 | M1 |
| 09 诊断与日志 | `09-diagnostics.html` | Compose 原生 | M1 |
| 10 设置 | `10-settings.html` | Compose 原生 | M1 |
| 11 Persona | `11-persona.html` | 后续 Compose 原生页面 | M3+ |
| 12 群聊 | `12-group-chat.html` | 后续 Compose 原生页面 | M3+ |
| 13 聊天附件 | `13-chat-files.html` | 后续 Compose 原生页面 + Chat Bridge | M3+ |
| 14 Author's Note | `14-author-note.html` | 后续 Compose 原生页面 | M3+ |
| 15 RAG / Data Bank | `15-rag.html` | 后续 Compose 原生页面 | M3+ |
| 16 快捷回复 | `16-quick-replies.html` | 后续 Compose 原生页面 | M3+ |
| 17 文本规则 | `17-regex.html` | 后续 Compose 原生页面 | M3+ |
| 18 图片与语音 | `18-media.html` | 后续 Compose 原生页面 + Bridge | M3+ |
| 19 扩展适配 | `19-extensions.html` | 后续 Compose 原生页面；扩展仍标记实验性 | M3+ |
| 20 主题管理 | `20-theme.html` | App 主题走 Compose；Chat WebView 主题保留原版设置 | M1 |
| 21 连接档案 | `21-connection-profiles.html` | 后续 Compose 原生页面 | M3+ |
| 22 Prompt Manager | `22-prompt-manager.html` | 后续 Compose 原生页面 | M3+ |

---

## 17. App 原生页面验收清单

### 首页 / 工作台（M1 完成后）

1. 首屏能看到服务状态、版本、端口和主要操作
2. 服务未运行时可一键启动
3. 服务运行时可继续聊天
4. 最近聊天/角色数据不可用时显示可理解空态
5. 关键操作不需要用户理解 Node.js 或端口
6. 首页不再保留旧底部固定启动 dock，避免与 Core Service 卡片重复

### 角色页面（M1/M2 完成后）

1. M1 提供最近角色入口和空态
2. M1 不提供不可控的 Full Manager / 原版角色管理深入口
3. M2 提供角色列表、搜索、筛选、头像预览
4. M2 提供角色设置 / 编辑页面，可新建和保存角色
5. M2 保存时保留未知字段，失败时不破坏原角色卡
6. M2 角色保存后可回到列表，也可进入 Chat WebView 继续聊天
7. 角色导入可先沿用 Chat WebView 文件选择流程，系统分享导入作为后续增强

### 设置页（M1 完成后）

1. App 主题、更新、电池、自动打开等设置清晰
2. 不把 SillyTavern 的复杂模型/API 设置搬到 App 设置页
3. 配置编辑仍有停止服务保护
4. 电池保活和通知权限提示可理解
5. 设置变更有明确反馈
6. 设置页提供"在网页打开"，用于系统浏览器访问完整原版 SillyTavern

### 工具页 / 数据页（M1/M3 完成后）

1. M1 可进入备份/恢复、日志、config、ST 管理
2. M1 不提供不可控的 Full Tools / 原版工具页深入口
3. 导出完整备份
4. 导入 ST 官方用户备份（.tar.gz / .tar / .zip）
5. 导入失败显示原因
6. 备份前提示会覆盖哪些数据
7. 支持从旧 Termux/PC 迁移数据
