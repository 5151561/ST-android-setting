# 技术架构与设计规范

本页面深入探讨 **ST-android-setting** 的技术架构、核心组件设计、UI 导航逻辑以及底层的 JS Bridge 桥接模式。

---

## 1. 内核运行时与核心组件

ST-android-setting 巧妙地在 Android 进程中直接寄宿了一个完整的 Node.js 环境，并通过一系列服务类对这个微型后端实例进行管理：

```
+-----------------------------------------------------------+
|                       Android App                         |
|                                                           |
|  +-----------------------+     +-----------------------+  |
|  |     Compose UI        | <-> |     TavernCoreClient  |  |
|  +-----------------------+     +-----------------------+  |
|              ^                             |              |
|              v                             v (OkHttp)     |
|  +-----------------------+     +-----------------------+  |
|  |  ChatWebViewScreen    |     |  SillyTavern Server   |  |
|  |  (Embedded WebView)   |     |  (Node.js Local Port) |  |
|  +-----------------------+     +-----------------------+  |
|              ^                             ^              |
|              | (JS Bridge)                 | (Spawn)      |
|              +-----------------------------+              |
|              |        NodeService          |              |
|              +-----------------------------+              |
|                                                           |
+-----------------------------------------------------------+
```

### 1.1 NodeService — 安卓前台守护服务
`NodeService` 是应用的核心运行时守护进程。
* **生命周期管理**：以 Android 前台服务（Foreground Service）形式运行，伴随系统栏通知，防止在后台被系统直接强杀。
* **进程拉起**：在服务内部直接 spawn 一个嵌入式的 `libnode.so` 二进制程序，带入必要的环境变量（如 `PORT`、`IP`、`ST_ANDROID=1` 等），启动 SillyTavern server 入口。
* **状态总线**：对外公开 `NodeStatusListener` 状态回调，定义了四种主要状态：`STOPPED` (停止)、`STARTING` (启动中)、`RUNNING` (已就绪并可通过 HTTP 访问)、`ERROR` (启动出错/端口冲突/崩溃退出)。

### 1.2 NodePayload — 内核解包与更新管理器
由于 Android 应用包（APK）内部结构对 Node.js 读写存在限制，必须在首次启动前将 SillyTavern 源码及 npm 依赖提取到内部文件目录中。
* **首屏提取**：提取打包在 APK assets 下的 `st_bundle.tar` 和 `npm.tar`，解压至外部安全的文件系统。
* **自定义安装管理器**：实现 `CustomInstallManager`。允许用户配置 GitHub 任意分支、Tag 甚至是自定义的 ZIP 压缩包作为 SillyTavern 的数据内核，支持无缝下载、解压、提取覆盖。

### 1.3 AppPaths — 文件路径单一事实源
为了避免路径硬编码带来的灾难性混乱，项目定义了全局唯一的 `AppPaths` 类作为文件存储事实源：
* `stDir`：SillyTavern 源码和运行时依赖所在根目录。
* `dataDir`：SillyTavern 用户数据区根目录，包含 characters、chats 等（SillyTavern 在多用户前默认为 `data/default-user`）。
* `configDir`：SillyTavern 的核心配置文件所在路径。

---

## 2. UI 路由与导航结构

应用采用全 Compose 原生路由方案，由 `MainActivity` 控制全局的宿主骨架。

### 2.1 NavHost 路由控制
`STNavGraph.kt` 中定义了 5 个核心底部导航 Tabs，它们在同一个 `Scaffold` 内共用底部导航条（`STBottomBar`）：

| 导航路由 `STRoutes` | 代表页面 | 实现形式 | 是否显示底部栏 |
|---|---|---|---|
| `HOME` | 仪表盘首页 | Compose 原生 | 显示 |
| `CHAT` | 聊天区 | WebView 宿主 Composable | 隐藏 (全屏沉浸) |
| `CHARACTERS` | 原生角色库列表 | Compose 原生 | 显示 |
| `TOOLS` | 工具中心入口 | Compose 原生 | 显示 |
| `SETTINGS` | 偏好设置页 | Compose 原生 | 显示 |

此外，对于“服务日志 (logs)”、“内核配置编辑 (config)”、“软件备份与数据迁移 (manage)”等子系统路由，均注册为次级原生导航页面。

### 2.2 沉浸式 WebView 与自然回退
* **WebView 沉浸**：当切入 `CHAT` 路由时，`STBottomBar` 会自动判定当前路径并隐藏，从而给 SillyTavern 桌面 Web 界面留出整块手机屏幕空间。
* **回退保护**：当用户按下手机系统返回键时，`WebViewNavigator` 会优先拦截此动作并向上判定 `webView.canGoBack()`。当且仅当 WebView 历史栈回退完毕后，才会自然退回到 App 首页（`HOME` 路由）。

---

## 3. 数据交互：双路径设计

在非 Chat 页面全部原生 Compose 化的演进过程中，App 采用了成熟的**双路径数据读取**设计，既确保了本地数据高保真、又满足了服务离线时的可用性需求。

```
                       +-----------------------+
                       |    Android Compose    |
                       |       UI Screen       |
                       +-----------------------+
                                   |
                     +-------------+-------------+
                     |                           |
             (Server Running)              (Server Offline)
                     v                           v
         +-----------------------+   +-----------------------+
         |     API Path          |   |     Local File Path   |
         |  (TavernCoreClient)   |   | (LocalTavernReader)   |
         +-----------------------+   +-----------------------+
                     |                           |
                     +-------------+-------------+
                                   v
                       +-----------------------+
                       |    SillyTavern Data   |
                       |     (JSON / PNG)      |
                       +-----------------------+
```

### 3.1 API 优先路径 — TavernCoreClient
* 当 NodeService 处于 `RUNNING` 状态时，App 的原生角色页、世界书、Persona 等操作将**百分之百**首选通过 `TavernCoreClient` 调用本地的 SillyTavern HTTP 服务端 API。
* **会话与 CSRF 安全**：`TavernCoreClient` 基于 OkHttp 实现，它在初次握手时自动请求 `/csrf-token`，获取 CSRF token 并配合共享 Cookie 罐，在每一个 POST 请求中自动拼接 `x-csrf-token` 头和会话凭据。
* ** multipart 表单**：对于新建角色、更新完整角色卡、以及独立头像替换（`/api/characters/edit-avatar`）等上传文件操作，客户端完全通过 multipart 封装，将头像的原始字节文件以保真方式传递给 Node 端，避免通过 Base64 解码可能产生的内存压力或格式损毁。

### 3.2 本地文件路径 — LocalTavernLibraryReader (离线缓存与兜底)
* 当本地 NodeService 尚未启动或处于停止、崩溃阶段时，为了避免界面显示大白页或报错，原生侧会降级调用 `LocalTavernLibraryReader`。
* 它直接读取 Android 文件系统中 `/data/` 目录下的用户卡片元数据和缓存头像，提供纯只读的静态展示，并优雅呈现“服务未就绪，可一键在首页启动”的占位卡片。
* **安全红线**：原生页面仅在诊断、导入、导出、及快照备份恢复等特种模块下才被允许通过文件选择器或直接写接口修改 `/data/` 文件；常规日常卡片管理严禁越过 HTTP API 直接覆写 SillyTavern 的数据文件，确保数据格式的完整与一致。

---

## 4. JS Bridge 原生能力增强

`STAndroidBridge`（注入为 `window.STAndroid`）是 WebView 运行时与外层壳通信的桥梁。我们坚守**渐进式增强（Optional Progressive Enhancement）**的原则——当 JS 检测到 `window.STAndroid` 时启用系统原生增强，若不处于安卓容器中，则顺畅回退至标准 Web 逻辑，避免污染上游 SillyTavern。

### 4.1 JS 桥接接口规范
```javascript
window.STAndroid
 ├── openFilePicker(type, multiple) → Promise<FileResult[]> // 桥接系统文件选择器
 ├── saveFile(filename, mime, base64) → Promise<boolean>    // 导出文件到系统 Download/特定路径
 ├── shareText(text) → void                                 // 调出原生系统分享文字
 ├── shareImage(base64, mime) → void                        // 分享生成的 AI 绘图
 ├── copyToClipboard(text) → void                           // 系统剪贴板写入
 ├── setKeepScreenOn(enabled) → void                        // 屏幕常亮控制（防止进入梦境）
 ├── notify(title, body) → void                             // 发送原生前台通知
 ├── vibrate(pattern) → void                                // 震动触觉反馈
 ├── getAppInfo() → AppInfo                                 // 读取当前 App 版本、构建号
 ├── getRuntimeInfo() → RuntimeInfo                         // 探测服务端口、运行环境
 ├── getThemeMode() → "light" | "dark" | "auto"             // 同步 App 的配色偏好
 ├── ttsSpeak(text, lang) → void                            // 调用系统原生 TTS 朗读文本
 ├── ttsStop() → void                                       // 停止当前朗读
 └── sttStart(lang) → Promise<string>                       // 唤起原生语音输入并回传转译文本
```

### 4.2 双向消息传递机制
目前核心聊天界面处于 WebView 内部。为了让外层的 Compose 原生 Chat 页面或状态卡片能实时感知消息，项目设计了一套统一的信封包格式：

* **Android -> JS 注入**：
  通过 `WebView.evaluateJavascript(...)` 传入命令：
  ```javascript
  window.STAndroidChatRuntime.dispatch({
    "id": "c044-6ab0",
    "kind": "command",
    "name": "chat.send",
    "payload": { "text": "你好！" }
  });
  ```
* **JS -> Android 回调**：
  通过桥接事件回传最新的消息快照：
  ```javascript
  window.STAndroid.postChatEvent(JSON.stringify({
    "id": "e099-ef82",
    "kind": "event",
    "name": "message.added",
    "payload": { "mes": "在的，有什么可以帮您？", "is_user": false }
  }));
  ```
这种无耦合的事件信封设计为后续完全原生化的 Chat UI 提供了极度稳健的底层通信支持。
