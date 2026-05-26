# PRD：ST Android 移动端客户端改造

版本：v0.2
日期：2026-05-26
状态：Phase 0 已完成，M0 待启动
变更记录：v0.1 → v0.2 策略转型，从"全量 Compose Native 重写"调整为"内置 WebView + Android Bridge + 移动端前端补丁"

---

## 1. 一句话定义

将 ST-android 从"启动器 + 外部浏览器"改造为"内置移动端容器 + 移动端优化版 SillyTavern 前端"——App 内运行 SillyTavern 服务，通过内置 WebView、Android Bridge 和移动端 CSS/JS 补丁，提供接近原生 App 的聊天体验。

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
| App 内聊天 | **无**（跳外部浏览器） |
| 移动端 UI 适配 | **无** |
| Android 原生能力桥接 | **无** |

用户启动 Core 后，通过 `Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:$port/"))` 跳转系统浏览器访问 SillyTavern。App 本身只是一个启动器。

### 2.2 v0.1 → v0.2 策略调整

v0.1 PRD 计划用 Jetpack Compose 原生重写 SillyTavern 的 22 个屏幕（聊天、角色、世界书、预设等）。经过评估，该方案存在核心问题：

1. **工作量不可控**：SillyTavern 前端功能极度复杂（插件系统、宏语言、多后端适配），完整原生化需数月
2. **上游同步困难**：用 Compose 重写意味着与 SillyTavern 上游完全脱钩，无法受益于社区更新
3. **API 不稳定**：SillyTavern Core 没有公开稳定的 REST API，用 HTTP 接口驱动原生 UI 脆弱且容易断
4. **投入产出比差**：大量 Power User 功能（扩展管理、正则规则、高级提示词）在移动端使用频率低

v0.2 策略：**先用 WebView 承接原版 SillyTavern 前端，再用移动端补丁和原生桥接逐步"App 化"。**

### 2.3 新定位

> SillyTavern Android Native Shell + Mobile Frontend Patch

- 保留原版 SillyTavern 的后端、数据结构、角色卡、聊天记录、API 配置、世界书等全部能力
- 在 Android 上提供更像 App 的使用体验
- 核心聊天、角色、预设、世界书、导入导出必须稳定
- 第三方扩展作为实验性能力，不承诺完整兼容

核心优势：
1. 不需要用户装 Termux 或理解 Node.js
2. 不需要完全重写 SillyTavern 前端
3. 可以持续跟随上游版本更新
4. 前端可渐进式移动端优化

### 2.4 设计来源

Open Design 原型位于项目外部，包含 22 个屏幕的完整设计稿。设计稿定义了：

- 5-tab 底部导航（首页 / 聊天 / 角色 / 工具 / 设置）
- 统一的颜色、间距、圆角、字体 Token 系统
- 每个屏幕的布局结构和交互状态

v0.2 中，设计稿仍作为 Compose Shell 管理页面（首页、设置、管理）和 android-mobile.css 移动端适配的视觉参考。

---

## 3. 产品原则

### 3.1 WebView First, Native Shell

SillyTavern 完整前端通过内置 WebView 加载。App 的 Compose Shell 负责：启动页、管理页、设置页、日志页、备份/恢复。聊天/角色/工具等复杂 UI 由 WebView 承载。

### 3.2 补丁而非重写

不 fork SillyTavern 前端代码。通过 `android-patches/` 目录管理 CSS/JS 补丁，构建时注入，保持与上游同步能力。

### 3.3 Bridge 做可选增强

`STAndroidBridge`（`window.STAndroid`）提供 Android 原生能力，但 SillyTavern 前端不强依赖它。检测到 `window.STAndroid` 时启用增强，否则回退到标准 Web 行为。

### 3.4 Core 负责业务，App 负责体验

App 不重写提示词构建、世界书触发等业务逻辑。这些继续由 SillyTavern Core + 前端 JS 处理。App 只负责进程管理、原生桥接和移动端体验优化。

### 3.5 渐进式改造

保留现有功能不受影响。每个 Milestone 交付可用增量。

### 3.6 设计稿为管理页视觉合同

Compose Shell 管理页面（首页、设置、日志、备份）的颜色、间距、圆角、布局以设计稿 `common.css` 中的 token 为准。

---

## 4. 架构概览

### 4.1 整体架构

```
Android App
├── Compose Shell
│   ├── 启动页（01-startup）
│   ├── 首页 / 管理（02-home）
│   ├── 设置页（10-settings）
│   ├── 日志页（09-diagnostics）
│   ├── 备份/恢复（manage）
│   ├── config.yaml 编辑器
│   ├── 法律信息
│   └── ChatWebViewScreen ← WebView 容器
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
└── SillyTavern Mobile Patch
    ├── android-mobile.css
    ├── android-bridge.js
    ├── mobile-home.js
    ├── keyboard-fix.js
    └── feature-flags.js
```

### 4.2 导航结构

```
MainActivity
  └─ STAppTheme（设计稿 Token → Material3 映射）
      └─ Scaffold
          ├─ STBottomBar（条件显示：WebView 全屏时隐藏）
          └─ NavHost
              ├─ home        → Compose 首页
              ├─ chat        → ChatWebViewScreen（内置 WebView）
              ├─ characters  → ChatWebViewScreen（导航到角色页）
              ├─ tools       → ChatWebViewScreen（导航到工具页）
              ├─ settings    → Compose 设置页
              └─ 子路由       → logs / config / legal / manage 等
```

底部导航栏的 Chat / Characters / Tools 三个 Tab 点击时，导航到 `ChatWebViewScreen` 并通过 JS 调用切换到 SillyTavern 对应的面板。

### 4.3 关键技术选型

| 决策点 | 选择 | 理由 |
|---|---|---|
| 聊天 UI | 内置 WebView | 复用 SillyTavern 完整前端，避免重写 |
| 管理 UI | Jetpack Compose | 启动/设置/日志/备份等管理页面保持原生体验 |
| 导航 | Navigation Compose 2.7 | 已实现，支持 back stack、deep link、状态恢复 |
| 主题 | STTheme + Material3 | 已实现，管理页面用；同时通过 Bridge 同步到 WebView |
| 网络 | OkHttp 4.12 | 已引入，用于 TavernCoreApi 健康检查等 |
| JS 桥接 | WebView.addJavascriptInterface | 标准 Android WebView Bridge 方案 |
| 前端补丁 | CSS/JS 注入 | 构建时打包到 assets，WebView 加载时注入 |
| 状态管理 | ViewModel + Compose State | 沿用现有模式 |

---

## 5. 设计 Token 系统

从设计稿 `common.css` `:root` 提取，已实现在 `ui/theme/STColors.kt` 和 `ui/theme/STTheme.kt`。用于 Compose Shell 管理页面，并通过 Bridge 同步到 WebView 内的 `android-mobile.css`。

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

ChatWebViewScreen 是 App 的主交互界面，内嵌 WebView 加载 `http://127.0.0.1:{port}/`。

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
- 加载完成后注入 `android-mobile.css` 和 `android-bridge.js`

**导航控制：**
- 返回键优先处理 WebView history（`webView.canGoBack()`）
- WebView history 耗尽后返回 App 首页
- 底部导航栏点击 Chat/Characters/Tools 时，通过 JS 调用 SillyTavern 内部导航

**错误处理：**
- 服务未启动 → 本地错误页 + 启动按钮
- 端口不可达 → 本地错误页 + 重试
- 页面加载失败 → 本地错误页 + 刷新
- Node 崩溃 → 检测 NodeService 状态变化 → 显示本地错误页 + 查看日志

### 6.2 验收标准

1. 用户点击"开始聊天"后，不跳外部浏览器
2. App 内直接进入 SillyTavern 完整 UI
3. 返回键正确处理 WebView history
4. Node 崩溃时，WebView 显示本地错误页
5. 文件上传、角色卡导入走 Android 文件选择器
6. 横竖屏、刘海屏、导航栏、键盘弹起不遮挡输入框

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

## 8. 移动端前端补丁系统

### 8.1 android-mobile.css

只在 Android App WebView 内加载的 CSS 补丁，解决 SillyTavern 桌面优先布局在手机上的体验问题。

**核心适配方向：**

```
android-mobile.css
 ├── 全局 safe-area 适配（刘海屏、导航栏）
 ├── 输入区 keyboard inset 适配
 ├── 触控尺寸最小 44dp
 ├── 顶部栏按钮折叠（桌面版按钮太密）
 ├── 左右抽屉全屏化（桌面版太挤）
 ├── 弹窗 bottom-sheet 化（桌面版弹窗高度过大）
 ├── 聊天气泡宽度优化
 ├── 设置页单列布局
 ├── 角色列表卡片化
 └── 图片/附件预览移动端优化
```

**验收标准：**
1. 单手可完成聊天、切角色、切预设
2. 主要按钮触控面积 ≥ 44dp
3. 键盘弹出时输入框始终可见
4. 设置页不横向溢出
5. 弹窗不超出屏幕
6. Android 深色/浅色模式同步到 SillyTavern 主题

### 8.2 补丁目录结构

```
android-patches/
├── public/
│   ├── android-mobile.css       # 移动端 CSS 补丁
│   ├── android-bridge.js        # Bridge 胶水代码
│   ├── mobile-home.js           # 移动端首页逻辑（M2）
│   ├── keyboard-fix.js          # 键盘适配
│   └── feature-flags.js         # 功能开关
├── patch-loader.js              # 补丁加载入口
└── patches/
    ├── inject-android-assets.patch  # 注入 CSS/JS 引用
    ├── mobile-keyboard-fix.patch    # 键盘相关修改
    └── mobile-file-picker.patch     # 文件选择器替换
```

**构建流程：**
```
拉取 SillyTavern 上游
→ 应用 android-patches/patches/*.patch
→ 复制 android-patches/public/* 到 SillyTavern public/
→ 打包进 APK assets
```

这比直接 fork SillyTavern 前端更容易维护，合并上游更新时只需 rebase patches。

---

## 9. 功能分层

将 SillyTavern 前端功能分为 4 层，决定移动端投入程度。

### L1：必须移动端优化

直接影响可用性，必须做 CSS/Bridge 适配：

| 功能 | 适配方式 |
|---|---|
| 聊天页 | CSS 气泡宽度 + 键盘适配 + 触控优化 |
| 角色选择 | CSS 卡片化 + 搜索优化 |
| 输入框 | keyboard-fix.js + safe-area |
| 消息操作菜单 | CSS bottom-sheet 化 |
| API 连接设置 | CSS 单列布局 |
| 预设选择 | CSS 折叠 + 触控优化 |
| 导入导出 | Bridge 文件选择器 |
| 图片上传 | Bridge 相册/文件管理器 |
| 日志/错误提示 | Compose 原生页面（已有） |

### L2：响应式适配，保留 Web UI

功能重要但不值得原生化，做 CSS 响应式补丁：

| 功能 | 适配方式 |
|---|---|
| 世界书 | CSS 单列 + 折叠 |
| 群聊 | CSS 适配 |
| 用户 Persona | CSS 适配 |
| 正则 Regex | CSS 单列 |
| 文本补全参数 | CSS 折叠 |
| 聊天记录管理 | CSS 适配 |
| 角色编辑器 | CSS 表单适配 |

### L3：高级功能，"高级模式"中使用

暂时保持原版 Web UI，不做额外适配：

- 高级提示词管理（Prompt Manager）
- 复杂调试面板
- 模型细节参数
- 扩展管理界面
- 开发者相关设置

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
| 快捷回复栏 | Bridge + CSS | 快捷回复插件 |
| 正则模板管理 | Bridge + CSS | 正则管理插件 |
| 世界书快速开关 | Bridge + CSS | 世界书管理插件 |
| 消息收藏 | Bridge | 收藏插件 |
| 分享到 SillyTavern | Android Intent | 系统分享集成 |

---

## 11. TavernCoreApi（保留）

已定义在 `api/TavernCoreApi.kt`，当前为骨架。在 v0.2 策略下，其角色调整为：

- **健康检查**：WebView 加载前探测 Core 是否 ready
- **首页数据**：Compose 首页展示最近聊天/角色（轻量读取）
- **移动端首页**（M2）：Compose 原生首页需要 API 数据

不再承担驱动全部 UI 的职责。聊天、角色编辑、预设管理等复杂操作由 WebView 内的 SillyTavern 前端 JS 直接处理。

```kotlin
interface TavernCoreApi {
    suspend fun healthCheck(): CoreHealth
    suspend fun listCharacters(): List<CharacterSummary>     // 首页用
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

Phase 0 的成果在 v0.2 策略下完全保留：
- Compose Shell 继续作为管理页面框架
- STTheme 继续驱动管理页面 + 通过 Bridge 同步到 WebView
- 导航结构保留，Chat/Characters/Tools Tab 改为导航到 ChatWebViewScreen
- TavernCoreApi 用于首页数据和健康检查

---

### M0 — 验证版（预估 1-2 周）

**目标：** 证明 App 内聊天可用，关闭外部浏览器依赖。

| 任务 | 优先级 | 描述 |
|---|---|---|
| ChatWebViewScreen | P0 | WebView 容器页面，加载 `127.0.0.1:{port}` |
| 服务 ready 检测 | P0 | Node 启动后轮询 healthCheck，ready 后自动加载 WebView |
| 返回键处理 | P0 | WebView history 优先，耗尽后返回首页 |
| 本地错误页 | P0 | 服务未启动/端口不可达/加载失败 三种错误页 |
| 替换外部浏览器 | P0 | 移除 `Intent(ACTION_VIEW)` 跳转，改为内部导航 |
| 横竖屏/键盘基础适配 | P1 | WebView 的 `windowSoftInputMode` 配置 |

**验收：** 用户安装 APK 后，点一次就能在 App 内使用 SillyTavern，不跳外部浏览器。

---

### M1 — 移动端可用版（预估 2-4 周）

**目标：** 让原版 UI 在手机上不难用。

| 任务 | 优先级 | 描述 |
|---|---|---|
| android-mobile.css | P0 | 全局 safe-area、触控尺寸、抽屉全屏化、弹窗 bottom-sheet 化 |
| 键盘适配 | P0 | keyboard-fix.js，输入框始终可见 |
| 文件桥接 | P0 | Bridge: openFilePicker / saveFile，替代 Web 文件选择 |
| 主题同步 | P1 | App 深浅色模式同步到 SillyTavern CSS 变量 |
| 顶部按钮折叠 | P1 | CSS 补丁收纳桌面版密集按钮 |
| 设置页适配 | P1 | SillyTavern 设置页单列化 |
| 下载桥接 | P2 | 备份下载走系统文件保存而非浏览器下载 |

**验收：** 用户不再觉得这是"网页塞进 App"。单手可完成聊天、切角色、切预设。

---

### M2 — 移动端体验版（预估 4-8 周）

**目标：** 做出 App 感，与浏览器版拉开体验差距。

| 任务 | 优先级 | 描述 |
|---|---|---|
| 移动端首页 | P0 | Compose 或 Web 实现的 Android 专属首页：继续聊天 / 角色 / 预设 / 世界书 / 高级模式入口 |
| 首页数据 | P0 | TavernCoreApi 实现 listRecentChats / listCharacters |
| 角色卡片优化 | P1 | CSS 卡片化 + 搜索触控优化 |
| 长按消息菜单 | P1 | CSS bottom-sheet + 触控优化 |
| 快捷预设切换 | P1 | Bridge + CSS 快捷面板 |
| 系统分享导入 | P1 | Android Intent 接收角色卡 / 图片 |
| 图片发送/预览/保存 | P2 | Bridge: shareImage / saveFile |
| Android 通知 | P2 | Bridge: notify，生成完成/服务状态通知 |
| TTS/STT 初步接入 | P2 | Bridge: ttsSpeak / sttStart |

**验收：** App 有独立首页，高频操作比浏览器版更顺手。

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
| 上游同步策略 | P1 | 文档化 patch rebase 流程，CI 自动检测上游更新 |
| 扩展系统标记 | P2 | UI 中明确标记扩展为"实验性" |
| AGPL 合规 | P2 | 开源合规说明完善 |
| L2 功能 CSS 适配 | P2 | 世界书/群聊/Persona/正则等响应式补丁 |

**验收：** 可作为正式版发布到 GitHub Release。崩溃率 < 1%，核心功能稳定。

---

## 13. 风险与应对

| 风险 | 等级 | 应对 |
|---|---|---|
| WebView 与 SillyTavern 前端 JS 兼容问题 | 高 | M0 阶段尽早验证；关注 WebView 版本差异（Android 8.0+ System WebView） |
| android-patches 与上游更新冲突 | 中 | 补丁尽量只增不改；用 CSS 覆盖而非修改原文件；CI 自动检测 patch 是否可 apply |
| Bridge 安全性（JS Interface 注入攻击） | 中 | 限制 Bridge 只对 localhost 生效；`@JavascriptInterface` 方法严格校验来源 |
| 扩展插件在 WebView 中异常 | 中 | 扩展标记为实验性；提供关闭扩展的快捷入口；捕获 JS 错误不崩溃 |
| 键盘适配在不同机型上表现不一 | 中 | M1 阶段重点测试；使用 `WindowInsets` API；keyboard-fix.js 兜底 |
| SillyTavern Core 版本更新打破 patch | 中 | 锁定测试过的 Core 版本；更新时先 CI 验证 patch 兼容性 |

---

## 14. 成功标准

| 指标 | M0 | M1 | M2 | M3 |
|---|---|---|---|---|
| App 内聊天可用 | ✅ | ✅ | ✅ | ✅ |
| 不跳外部浏览器 | ✅ | ✅ | ✅ | ✅ |
| Core 启动/停止回归 | 100% | 100% | 100% | 100% |
| 移动端触控适配 | — | L1 完成 | L1+L2 | L1+L2 |
| Bridge 功能覆盖 | — | 文件 | 文件+分享+TTS | 全部 |
| 移动端首页 | — | — | ✅ | ✅ |
| 崩溃率 | < 5% | < 2% | < 1% | < 1% |
| 扩展兼容 | 不承诺 | 不承诺 | 实验性 | 实验性 |

---

## 15. 文件结构（Phase 0 后 + M0 规划）

```
app/src/main/java/io/github/sanitised/st/
├── api/
│   └── TavernCoreApi.kt          # API 接口 + 存根实现
├── ui/
│   ├── theme/
│   │   ├── STColors.kt           # 颜色 Token（浅色/深色）
│   │   └── STTheme.kt            # 主题系统（间距/圆角/字体 + Material3 映射）
│   ├── navigation/
│   │   ├── STNavGraph.kt          # 路由常量
│   │   ├── STBottomBar.kt         # 底部导航栏
│   │   └── PlaceholderScreens.kt  # 占位屏幕（M0 后替换为 WebView）
│   ├── webview/                   # M0 新增
│   │   ├── ChatWebViewScreen.kt   # WebView 容器 Composable
│   │   ├── STAndroidBridge.kt     # JS Bridge 实现
│   │   ├── WebViewErrorPage.kt    # 本地错误页
│   │   └── WebViewNavigator.kt    # WebView 导航控制
│   └── screens/                   # M2 新增（可选）
│       └── MobileHomeScreen.kt    # 移动端首页
├── MainActivity.kt                # NavHost + Scaffold + 底部导航
├── MainViewModel.kt               # 现有 ViewModel
├── UiApp.kt                       # 首页（M1 重构）
├── UiSettings.kt                  # 设置页
├── UiConfig.kt                    # config.yaml 编辑器
├── UiLogs.kt                      # 日志查看器
├── UiLegal.kt                     # 法律信息
├── UiManageSt.kt                  # ST 安装管理
├── UiTopBar.kt                    # 通用 TopAppBar
├── ... (其他现有文件)
├── NodeService.kt                 # Node 前台服务
└── NodeStatus.kt                  # 状态枚举

android-patches/                   # M1 新增，项目根目录
├── public/
│   ├── android-mobile.css
│   ├── android-bridge.js
│   └── keyboard-fix.js
├── patch-loader.js
└── patches/
    └── inject-android-assets.patch
```

---

## 16. 设计稿参考索引

v0.2 中设计稿的角色调整——管理页面（Compose）仍以设计稿为视觉合同；WebView 页面以 android-mobile.css 补丁为适配手段，设计稿作为适配方向参考。

| 屏幕 | 文件 | v0.2 实现方式 | 里程碑 |
|---|---|---|---|
| 01 启动与初始化 | `01-startup.html` | Compose 原生 | M2 |
| 02 首页 | `02-home.html` | Compose 原生 | M2 |
| 03 聊天页 | `03-chat.html` | WebView + CSS 补丁 | M0(WebView) / M1(CSS) |
| 04 角色列表 | `04-characters.html` | WebView + CSS 补丁 | M1 |
| 05 角色编辑 | `05-character-edit.html` | WebView + CSS 补丁 | M1 |
| 06 世界书 | `06-worldbook.html` | WebView + CSS 补丁（L2） | M3 |
| 07 模型预设 | `07-presets.html` | WebView + CSS 补丁 | M1 |
| 08 工具中心 | `08-tools.html` | WebView + CSS 补丁（L2） | M3 |
| 09 诊断与日志 | `09-diagnostics.html` | Compose 原生 | M2 |
| 10 设置 | `10-settings.html` | Compose 原生 | M1 |
| 11 Persona | `11-persona.html` | WebView + CSS 补丁（L2） | M3 |
| 12 群聊 | `12-group-chat.html` | WebView + CSS 补丁（L2） | M3 |
| 13 聊天附件 | `13-chat-files.html` | WebView（L3） | — |
| 14 Author's Note | `14-author-note.html` | WebView（L3） | — |
| 15 RAG / Data Bank | `15-rag.html` | WebView（L3） | — |
| 16 快捷回复 | `16-quick-replies.html` | WebView + Bridge（L2） | M3 |
| 17 文本规则 | `17-regex.html` | WebView（L3） | — |
| 18 图片与语音 | `18-media.html` | Bridge 替代（L1） | M2 |
| 19 扩展适配 | `19-extensions.html` | WebView（L4 实验性） | — |
| 20 主题管理 | `20-theme.html` | Bridge 主题同步 | M1 |
| 21 连接档案 | `21-connection-profiles.html` | WebView + CSS 补丁（L2） | M3 |
| 22 Prompt Manager | `22-prompt-manager.html` | WebView（L3） | — |

---

## 17. 前端验收清单

### 聊天页（M1 完成后）

1. 首屏直接进入聊天
2. 输入框不被键盘遮挡
3. 发送、停止、重生成、继续生成按钮容易点击
4. 消息长按出现菜单
5. 图片消息可预览、保存、分享
6. 滚动到底部逻辑稳定
7. 大聊天记录不卡死

### 角色页（M1 完成后）

1. 角色卡网格/列表切换
2. 搜索角色
3. 导入 PNG / JSON 角色卡（通过 Bridge）
4. 从系统分享菜单导入
5. 编辑角色基础信息
6. 删除前确认

### 设置页（M1 完成后）

1. API Key 输入安全
2. 预设切换方便
3. 常用设置优先
4. 高级设置折叠
5. 配置错误能给出可读提示

### 数据页（M3 完成后）

1. 导出完整备份
2. 导入 ST 官方用户备份（.tar.gz / .tar / .zip）
3. 导入失败显示原因
4. 备份前提示会覆盖哪些数据
5. 支持从旧 Termux/PC 迁移数据
