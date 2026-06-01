# P3 新增 UI 设计规格

## 设计上下文

ST-android 是 SillyTavern 的 Android 原生客户端。聊天界面使用 Jetpack Compose + Material 3 构建。以下是当前聊天界面的布局结构，新增组件需要在此基础上集成。

### 当前聊天界面结构（自上而下）

```
┌─────────────────────────────────────┐
│  ChatHeader                         │  标题栏：角色头像+名称、生成状态、菜单
├─────────────────────────────────────┤
│  SaveErrorBanner (可选)             │  保存错误横幅
├─────────────────────────────────────┤
│                                     │
│  MessageList                        │  消息列表（LazyColumn）
│    ├─ MessageBubble (用户)          │    右对齐蓝色气泡
│    ├─ MessageBubble (AI)            │    左对齐灰色气泡 + 头像
│    └─ AssistantMessageControls      │    最后一条 AI 消息底部：swipe 翻页、重写、继续
│                                     │
├─────────────────────────────────────┤
│  ChatQuickStrip                     │  快捷操作条（继续、代笔、新建等按钮）
├─────────────────────────────────────┤
│  AttachSheet (可选，向上展开)       │  附件面板（2×4 图标网格）
│  PendingAttachmentStrip (可选)      │  待发送附件预览横条
├─────────────────────────────────────┤
│  ChatInputBar                       │  输入栏：+ 按钮 | 文本框 | 发送/停止/语音
└─────────────────────────────────────┘
```

### 当前设计语言

- **配色**：Material 3 Dynamic Color，支持 light/dark/dynamic
- **用户消息气泡**：`primaryContainer` 背景，右对齐，左上圆角 18dp 右上 4dp 底部 18dp
- **AI 消息气泡**：`surfaceContainer` 背景，左对齐，左上 4dp 右上 18dp 底部 18dp，左侧有 36dp 圆形头像
- **隐藏消息**：整个气泡 50% 透明度 + "已隐藏" badge
- **底部面板**：`ModalBottomSheet`，`surfaceContainerLow` 背景
- **操作网格**：`ActionGridItem`（图标+文字，等分排列）
- **列表项**：Material 3 `ListItem`（headline + supporting + leading icon）
- **输入栏**：`OutlinedTextField` + `extraLarge` shape，`surfaceContainerLow` 背景
- **Chip**：Material 3 `AssistChip` / `FilterChip`

---

## 组件 1：ReasoningSection（思考过程折叠区）

### 位置
AI 消息气泡内部，在消息正文**上方**。只有 `extra.reasoning` 不为空时才出现。

### 布局

```
折叠态：
┌─────────────────────────────────────┐
│ 💭  思考过程                    ▾   │  单行，可点击区域
├─────────────────────────────────────┤
│ 这是 AI 的正式回复文本...           │  正常消息内容
└─────────────────────────────────────┘

展开态：
┌─────────────────────────────────────┐
│ 💭  思考过程                    ▴   │  可点击收起
│ ┌─────────────────────────────────┐ │
│ │ Let me think about this...      │ │  reasoning 文本区
│ │ The user is asking about...     │ │  浅色背景，bodySmall 字号
│ │ I should consider...            │ │  可能很长，不限高度
│ └─────────────────────────────────┘ │
├─────────────────────────────────────┤
│ 这是 AI 的正式回复文本...           │  正常消息内容
└─────────────────────────────────────┘
```

### 视觉规格

| 属性 | 值 |
|------|-----|
| 折叠行高度 | 36dp |
| 折叠行背景 | 无额外背景，继承气泡色 |
| 折叠行文字 | `bodySmall`，`onSurfaceVariant` 色 |
| 折叠行图标 | 💭 或 `Icons.Filled.Psychology`，16dp |
| 展开箭头 | `Icons.Filled.ExpandMore` / `ExpandLess`，20dp |
| 展开区背景 | `surfaceContainerHighest` 圆角 8dp |
| 展开区文字 | `bodySmall`，`onSurfaceVariant` 色 |
| 展开区内边距 | 12dp |
| 与正文间距 | 8dp |

### 交互
- 点击折叠行切换展开/折叠
- 默认折叠
- 展开区文本可选中复制（长按触发系统复制）
- 无最大高度限制，长文本自然撑开

### 数据
- `reasoning: String` — 从 `message.extra.reasoning` 解析，可能是几个词到几千字
- 只出现在 AI 消息中（`is_user = false`）
- 用户消息没有 reasoning

### 边界情况
- reasoning 为空字符串或 null → 不显示
- reasoning 非常长（5000+ 字） → 正常展开，依赖 LazyColumn 的虚拟化
- 正在流式生成中 → reasoning 可能先到达然后正文再开始，展开区内容动态更新

---

## 组件 2：ToolCallCard（工具调用卡片）

### 位置
系统消息（`is_system = true` 且 `extra.tool_invocations` 非空）的气泡内部，**替代**正常的 `mes` 文本渲染。

### 布局

一条消息可能包含多个工具调用，纵向排列。

```
┌─ 系统消息气泡 ──────────────────────┐
│ 🔧  搜索网页                    ▾   │  工具调用 #1
│ ┌─────────────────────────────────┐ │
│ │ 参数: {"query": "天气预报"}     │ │  折叠时隐藏
│ │ ─────────────────               │ │
│ │ 结果: 今天晴，25°C，东风3级     │ │  结果始终显示
│ └─────────────────────────────────┘ │
│                                     │
│ 🔧  计算器                      ▾   │  工具调用 #2
│ ┌─────────────────────────────────┐ │
│ │ 结果: 42                        │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

### 视觉规格

| 属性 | 值 |
|------|-----|
| 卡片容器 | `OutlinedCard`，圆角 12dp |
| 卡片背景 | `surfaceContainerLow` |
| 卡片边框 | `outlineVariant`，1dp |
| 工具图标 | `Icons.Filled.Build` 或 `Construction`，18dp，`primary` 色 |
| 工具名称 | `titleSmall`，`onSurface` 色 |
| 参数标签 | `labelSmall`，`onSurfaceVariant` 色 |
| 参数内容 | `bodySmall`，monospace 字体，`onSurfaceVariant` 色 |
| 结果标签 | `labelSmall`，`onSurfaceVariant` 色 |
| 结果内容 | `bodySmall`，`onSurface` 色 |
| 卡片间距 | 8dp |
| 卡片内边距 | 12dp |

### 交互
- 点击卡片头部（工具名称行）展开/折叠参数区域
- 结果始终可见（折叠时只显示结果）
- 长按卡片可复制结果文本

### 数据
```
ToolInvocation:
  id: String         — 调用 ID
  displayName: String — 显示名称（如"搜索网页"）
  name: String       — 内部名称（如"web_search"）
  parameters: String — JSON 字符串，调用参数
  result: String     — 执行结果文本
```

### 边界情况
- 只有一个工具调用 → 只渲染一张卡片
- parameters 是长 JSON → 截断显示前 3 行，展开后显示全部
- result 很长 → 截断显示前 5 行 + "展开全部"
- result 为空 → 显示"执行中…"或"无结果"

---

## 组件 3：QuickReplyStrip（快捷回复按钮条）

### 位置
输入栏区域上方，`PendingAttachmentStrip` 下方（或同一位置，两者不会同时大量出现）。

### 布局

```
┌─────────────────────────────────────┐
│  [打招呼] [继续剧情] [切换场景] [角色扮演] ← 水平滚动
├─────────────────────────────────────┤
│  + │  发条消息...              │ 🎤  │  输入栏
└─────────────────────────────────────┘
```

### 视觉规格

| 属性 | 值 |
|------|-----|
| 整条高度 | wrap_content，大约 48dp（含上下边距） |
| 整条背景 | `surfaceContainerLow`（与输入栏一致） |
| 水平内边距 | 12dp |
| 按钮间距 | 8dp |
| 按钮样式 | Material 3 `AssistChip` 或 `SuggestionChip` |
| 按钮文字 | `labelMedium` |
| 按钮图标 | 可选，QR 有 icon 时显示，16dp |
| 按钮颜色 | 默认 chip 色（`surface` + `outline` 边框） |
| 滚动 | `LazyRow`，水平滚动，无滚动指示器 |
| 分隔线 | 上方有 0.5dp `outlineVariant` 分隔线 |

### 交互
- 点击按钮 → 执行对应的 Quick Reply（通过 Bridge 发送）
- 按钮可能很多（10+），水平滚动浏览
- QR 列表为空时**整条隐藏**
- 运行时未就绪时按钮置灰不可点
- 生成中时按钮置灰不可点

### 数据
```
QuickReplyItem:
  setName: String  — 所属集合名
  label: String    — 按钮文字（如"打招呼"）
  icon: String?    — 可选 emoji 图标
  message: String  — 点击后要执行的文本/命令（不显示在 UI 中）
```

### 边界情况
- 0 个 QR → 整条不渲染
- 1-3 个 QR → 不滚动，按钮居左
- 很多 QR（10+） → 水平滚动
- 按钮文字很长 → 单行截断 ellipsis，max 120dp 宽
- 切换角色/聊天后 QR 列表可能变化 → 重新加载

---

## 组件 4：RuntimeToastHost（运行时通知）

### 位置
浮动在聊天界面上方，居顶或居底。推荐使用 Material 3 `SnackbarHost` 机制，置于 `Scaffold` 或 `Box` 的叠加层。

### 布局

```
┌─────────────────────────────────────┐
│                                     │
│  ┌────────────────────────────────┐ │
│  │ ⚠️  Slash 命令执行失败         │ │  浮动 Snackbar
│  │ /invalidcmd: 未知的命令        │ │
│  └────────────────────────────────┘ │
│                                     │
│  消息列表...                        │
│                                     │
└─────────────────────────────────────┘
```

### 视觉规格

按类型着色：

| type | 容器色 | 内容色 | 图标 |
|------|--------|--------|------|
| error | `errorContainer` | `onErrorContainer` | `Icons.Filled.Error` |
| warning | `tertiaryContainer` | `onTertiaryContainer` | `Icons.Filled.Warning` |
| info | `primaryContainer` | `onPrimaryContainer` | `Icons.Filled.Info` |
| success | 自定义绿色或 `tertiaryContainer` | 对应 on 色 | `Icons.Filled.CheckCircle` |

| 属性 | 值 |
|------|-----|
| 形状 | 圆角 12dp |
| 内边距 | 16dp |
| 水平边距 | 16dp（距屏幕边） |
| 标题 | `titleSmall`，可选（有 title 时显示） |
| 消息 | `bodySmall` |
| 自动消失 | 4 秒 |
| 最大同时 | 1 条（新的替换旧的） |
| 动画 | 从上方滑入，淡出 |

### 交互
- 点击关闭
- 自动 4 秒后消失
- 同时只显示最新一条

### 数据
```
Toast:
  type: String    — "error" | "warning" | "info" | "success"
  title: String   — 标题（可能为空）
  message: String — 内容
```

### 边界情况
- 消息内容含 HTML 标签 → 需要 strip HTML，只显示纯文本
- 消息很长 → 最多显示 3 行，截断
- 短时间内多个 toast → 只显示最新的

---

## 组件 5：Checkpoint/Branch 消息标识和操作

### 5a. 消息气泡上的标识

在消息气泡内或旁边显示 checkpoint/branch 标识。

```
AI 消息有 checkpoint：
┌───────────────────────────────────┐
│ Alice                             │
│ ┌───────────────────────────────┐ │
│ │ 这是消息内容...               │ │
│ │                          🔖   │ │  右下角书签图标
│ └───────────────────────────────┘ │
│ ◀ 1/3 ▶  🔄  ⏩  ⋯             │
└───────────────────────────────────┘

AI 消息有分支：
┌───────────────────────────────────┐
│ Alice                             │
│ ┌───────────────────────────────┐ │
│ │ 这是消息内容...               │ │
│ │                      🔖  ⑵   │ │  书签 + 分支数量
│ └───────────────────────────────┘ │
└───────────────────────────────────┘
```

### 视觉规格

| 属性 | 值 |
|------|-----|
| 书签图标 | `Icons.Filled.Bookmark`，14dp，`primary` 色 |
| 分支数量 | `labelSmall`，圆形 badge，`tertiaryContainer` 色 |
| 位置 | 消息气泡内右下角，与文本底部对齐 |

### 5b. MessageActionSheet 新增操作项

在现有 `MessageActionSheet` 的列表区域（分隔线下方，与"隐藏"/"删除"同级）新增：

```
┌─────────────────────────────────────┐
│ 消息操作                            │
│ [复制] [编辑] [重写] [翻译]         │  图标网格
│ ────────────────────                │
│ 📌 从 AI 上下文中隐藏               │  已有
│ 🔖 创建存档点                       │  新增
│ 🌿 创建分支                        │  新增
│ 📂 查看分支 (2)                     │  新增，仅有分支时显示
│ ────────────────────                │
│ 🗑️ 删除消息                        │  已有
└─────────────────────────────────────┘
```

### 5c. CheckpointDialog

创建存档点时弹出的命名对话框。

```
┌─────────────────────────────────────┐
│  创建存档点                          │
│                                     │
│  为当前消息创建一个聊天存档快照       │
│                                     │
│  ┌─────────────────────────────────┐│
│  │ 存档点名称                      ││  OutlinedTextField
│  └─────────────────────────────────┘│
│                                     │
│              [取消]     [创建]       │
└─────────────────────────────────────┘
```

### 5d. BranchListSheet

查看分支列表的 BottomSheet。

```
┌─────────────────────────────────────┐
│  分支列表                            │
│                                     │
│  🌿 Branch #1 — 2026-05-30 14:00   │  点击打开该分支聊天
│  🌿 Branch #2 — 2026-05-30 15:30   │
│  🔖 Checkpoint: 剧情转折点          │  如有 bookmark_link
│                                     │
└─────────────────────────────────────┘
```

### 数据
```
ChatMessage 扩展属性：
  bookmarkLink: String?     — checkpoint 名称（extra.bookmark_link）
  branches: List<String>    — 分支聊天文件名列表（extra.branches）
```

---

## 组件 6：LogprobsSheet（token 概率面板）— 低优先级

### 位置
从 MessageActionSheet 的"查看 logprobs"进入，ModalBottomSheet。

### 布局

```
┌─────────────────────────────────────┐
│  Token 概率分析                      │
│                                     │
│  [Hello][ ][world][,][ ][how]       │  token 流，水平 wrap
│  [ ][are][ ][you][?]                │  每个 token 是可点击色块
│                                     │
│  ── 选中 token: "world" ──          │  点击某 token 后展开
│                                     │
│  world      -0.12  (88.7%)          │  当前 token
│  earth      -2.34  (9.6%)           │  候选 token
│  planet     -4.56  (1.0%)           │  候选 token
│  universe   -6.78  (0.1%)           │  候选 token
│                                     │
└─────────────────────────────────────┘
```

### 视觉规格

| 属性 | 值 |
|------|-----|
| token 色块 | 交替色调区分相邻 token（4 色循环） |
| token 文字 | `bodySmall`，monospace |
| 选中 token | `primaryContainer` 高亮 |
| 候选列表 | `ListItem` 风格，logprob 值 + 百分比 |
| 概率条 | 可选，每个候选后面加进度条表示概率 |

### 数据
```
TokenLogprobs:
  token: String                        — token 文本
  topLogprobs: List<Pair<String, Float>> — 候选 token + logprob 值
```

---

## 组件 7：ItemizedPromptSheet（提示词分析面板）— 低优先级

### 位置
从 MessageActionSheet 进入，ModalBottomSheet。

### 布局

```
┌─────────────────────────────────────┐
│  提示词分析  ·  消息 #12             │
│                                     │
│  总 token 数: 4096                   │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │  总量进度条
│                                     │
│  角色描述          800 tokens  ████  │  各组件 token 数
│  角色性格          200 tokens  ██    │
│  世界书            600 tokens  ███   │
│  作者注            100 tokens  █     │
│  聊天历史         1800 tokens  ████████
│  系统提示          400 tokens  ███   │
│  其他              196 tokens  █     │
│                                     │
│  预设: Default  ·  模型: claude-4    │  元信息
│  API: openai  ·  分词器: cl100k     │
│                                     │
└─────────────────────────────────────┘
```

### 视觉规格

| 属性 | 值 |
|------|-----|
| 组件行 | `ListItem`：名称 + token 数 + 微型进度条 |
| 进度条色 | 按组件用不同色调（primary, secondary, tertiary 交替） |
| 元信息 | `bodySmall`，`onSurfaceVariant` 色 |

---

## 组件 8：DataBankScreen（数据银行页面）— 低优先级

### 位置
独立全屏页面，从 Tools 标签页进入。

### 布局

```
┌─────────────────────────────────────┐
│  ← 数据银行                    🔄   │  顶栏
├─────────────────────────────────────┤
│  [全局] [角色: Alice] [当前聊天]     │  三级 Tab 切换
├─────────────────────────────────────┤
│                                     │
│  📄 world_lore.txt         12.5 KB  │  文件列表
│     2026-05-28                      │
│                                     │
│  📄 character_bio.md        3.2 KB  │
│     2026-05-29                      │
│                                     │
│  📷 reference.png          245 KB   │
│     2026-05-30                      │
│                                     │
│  ── 空状态 ──                       │
│  还没有文件，点击下方按钮添加        │
│                                     │
├─────────────────────────────────────┤
│                              [ + ]  │  FAB 上传文件
└─────────────────────────────────────┘
```

### 交互
- Tab 切换：全局 / 角色级 / 聊天级
- 文件项点击 → 查看文件内容（文本类型展开，图片类型预览）
- 文件项长按或侧滑 → 删除操作
- FAB → 文件选择器上传

---

## 设计注意事项

1. **深色模式**：所有组件必须在 light 和 dark 模式下都正常显示。使用 Material 3 的语义色（`primaryContainer`、`surfaceContainer` 等），不要硬编码颜色值。

2. **字体大小无障碍**：考虑系统字体放大场景，避免固定高度容器导致文字裁切。

3. **横屏/平板**：当前聊天界面未专门适配横屏，新组件也不需要。但 `maxWidth` 约束要继承（消息气泡有 `widthIn(max = maxWidth)` 约束）。

4. **性能**：MessageBubble 内的 ReasoningSection 和 ToolCallCard 在 LazyColumn 中渲染，需要避免过度重组。折叠态应尽量轻量。

5. **中文 UI**：所有界面文字使用中文。

6. **一致性**：新增的 BottomSheet 应与现有的 `MessageActionSheet`、`WorldInfoSheet`、`GroupChatHistorySheet` 风格一致（`surfaceContainerLow` 背景，Material 3 `ListItem`）。
