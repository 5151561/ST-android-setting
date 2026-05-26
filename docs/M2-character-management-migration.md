# M2 角色管理迁移文档

日期：2026-05-26
检查更新：2026-05-26
状态：执行中（P0 主链路大半落地，保存请求形态和若干入口待补）
范围：原版 SillyTavern 角色管理右侧面板到 Android Compose 原生页面的迁移拆解

## 1. 结论

当前 APP 的 M2 实现已从“角色列表 + 基础编辑 + 新建/保存”的最小闭环推进到“角色管理基础承接”的大半主链路：API 层和 Compose 页面已覆盖角色列表、详情、新建、保存、重命名、复制、删除、导入、导出和单独换头像。

但当前状态仍不能算 M2 完整验收：`/api/characters/create` 和 `/api/characters/edit` 现在由 APP 以 JSON body 调用，尚未改成文档契约要求的 multipart form；快捷收藏/标签尚未通过 `/api/characters/merge-attributes` 暴露为独立操作；编辑页没有头像预览，新建角色不能随表单上传头像；历史聊天列表 API 已封装但没有 UI；ST 独立标签系统、批量操作、Lorebook / Persona / Source 入口仍缺。

核心原则仍然是 API 优先：优先调用 SillyTavern 本地 API，只有文件选择、分享导入、缓存展示、备份恢复和诊断场景允许走本地文件或 Android 文件选择器。本次检查没有发现角色管理代码直接覆盖 `data/default-user/characters` 下的角色卡；当前文件读写主要用于 Android 文件选择器读取导入文件 / 头像文件、把导出结果写到用户选择的位置，以及 M2 之外的备份恢复、缓存或诊断场景。

## 2. 原版入口来源

原版角色管理 UI 主要在右侧 drawer：

| 原版区域 | 源码位置 | 说明 |
|---|---|---|
| Character Management drawer | `SillyTavern/public/index.html` `rightNavHolder` | 右侧角色管理总入口 |
| Character List Panel | `SillyTavern/public/index.html` `rm_characters_block` | 角色列表、搜索、排序、导入、新建、群聊入口、批量编辑 |
| Solo Char Create/Edit Panel | `SillyTavern/public/index.html` `rm_ch_create_block` | 单角色新建/编辑表单 |
| Advanced Definitions popup | `SillyTavern/public/index.html` advanced definitions | Prompt 覆盖、元数据、性格、场景、角色 Note、示例对话 |
| Alternate Greetings popup | `SillyTavern/public/index.html` `alternate_greetings_template` | 多开场白维护 |
| Character API endpoints | `SillyTavern/src/endpoints/characters.js` | 角色读写、导入导出、重命名、删除、复制、头像 |
| Chat file endpoints | `SillyTavern/src/endpoints/chats.js` | 角色聊天文件获取、保存、重命名、删除、导入、导出 |

## 3. 原版功能盘点

### 3.1 角色库列表

| 功能 | 原版能力 | 原版 API / 数据 | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 读取角色列表 | 展示所有角色、头像、名称、Creator Notes、版本、内联标签、收藏标记 | `/api/characters/all` | 已覆盖：名称、头像、Creator Notes、版本、embedded tags、收藏、最近字段 | P0 |
| 搜索 | 搜索角色、群组、标签，支持模糊分数排序 | 前端 `FilterHelper` + 本地列表 | 部分覆盖：名称、embedded tags、Creator Notes；无群组 / ST tag map / 模糊分数排序 | P0 |
| 排序 | Search、A-Z、Z-A、Newest、Oldest、Favorites、Recent、Most/Least chats、Most/Least tokens、Random | `/api/characters/all` 返回 `create_date`、`date_last_chat`、`chat_size`、`data_size` | 部分覆盖：A-Z、Z-A、Newest、Oldest、Favorites、Recent、Most/Least chats、Most/Least tokens；未覆盖 Random / Search score | P0 |
| 分页 | 每页 10/25/50/100/250/500/1000 | 前端分页 | 未覆盖 | P1 |
| 列表/网格视图 | 可切换列表和网格 | 前端偏好 | 未覆盖 | P1 |
| HotSwaps | 收藏角色横向快捷头像 | 收藏字段 + 前端 UI | 未覆盖 | P1 |
| 标签过滤 / 文件夹 | 标签过滤、标签作为 folder、drilldown | `tags.js`、settings tag map | 部分覆盖：embedded tag chip 筛选；未接 ST tag map、folder、drilldown | P0 |
| 群聊混排 | 角色、群组、标签 folder 混合列表 | characters + groups + tags | 未覆盖，且群聊不在当前 M2 主线 | P2 |

### 3.2 角色卡基础操作

| 功能 | 原版能力 | 原版 API / 数据 | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 新建角色 | 表单提交，支持头像、裁剪、多开场白、extensions | `/api/characters/create` multipart form | 部分覆盖：当前以 JSON 请求新建，字段含多开场白 / extensions 相关值；无头像 / 裁剪，未按 multipart 契约 | P0 |
| 保存角色 | 完整表单保存，保留 chat/create_date/json_data | `/api/characters/edit` multipart form | 部分覆盖：当前以 JSON 请求 `/edit`，保留 `chat`、`create_date`、`json_data`；未按 multipart 契约，不能随保存换头像 | P0 |
| 局部更新 | 收藏、批量收藏等局部字段 | `/api/characters/merge-attributes` | 未接入 UI / API 接口；代码里有未使用的 merge payload helper | P0 |
| 重命名 | 改角色名、头像文件名、聊天目录；可选择改历史聊天内角色名 | `/api/characters/rename` + `/api/chats/get/save` | 部分覆盖：已调用 `/rename`，ST 会改头像文件名和聊天目录；无历史聊天内角色名更新选项 | P0 |
| 复制角色 | 复制 PNG 卡，生成新文件名 | `/api/characters/duplicate` | 已覆盖：列表和编辑页均可调用 `/duplicate` | P0 |
| 删除角色 | 可选择是否删除该角色聊天文件夹 | `/api/characters/delete` | 已覆盖：列表和编辑页均有确认，并支持 `delete_chats` | P0 |
| 导入角色 | 支持 `.json`、`.png`、`.yaml`、`.yml`、`.charx`、`.byaf`，支持多选 | `/api/characters/import` multipart form | 已覆盖原生入口：Android 多文件选择后调用 `/import` multipart | P0 |
| 外部 URL 导入 | 从外部 URL 导入角色卡 | 前端 `importFromExternalUrl` + `processDroppedFiles` | 未覆盖 | P1 |
| 替换 / 更新 | 用文件或在线来源替换当前角色，保留聊天、资产、群组关系 | 复用导入流程并传 preserved avatar | 未覆盖 | P1 |
| 导出角色 | PNG 或 JSON 导出，导出时清理私有字段 | `/api/characters/export` | 已覆盖：编辑页可导出 JSON / PNG，并写到用户选择的位置 | P0 |

### 3.3 头像与媒体

| 功能 | 原版能力 | 原版 API / 数据 | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 头像预览 | 角色列表和编辑页展示头像 | `/characters/{avatar}` 或 thumbnail | 部分覆盖：列表已覆盖，编辑页未覆盖 | P0 |
| 头像上传 | 新建/编辑时选择图片 | `/api/characters/create`、`/api/characters/edit` multipart | 部分覆盖：编辑页可单独换头像；新建 / 完整保存随表单上传头像未覆盖 | P0 |
| 单独换头像 | 不改角色数据，仅替换 PNG 封面 | `/api/characters/edit-avatar` | 已覆盖：编辑页通过文件选择器调用 `/edit-avatar` multipart | P1 |
| 图片格式兼容 | 前端会转换不支持格式 | `ensureImageFormatSupported` | 未覆盖 | P1 |
| 裁剪 | 带 `crop` query 参数保存裁剪结果 | create/edit/edit-avatar `?crop=` | 未覆盖 | P1 |
| 外部媒体开关 | 允许/禁止角色描述引用外部媒体 | 角色扩展字段 | 未覆盖 | P2 |

### 3.4 编辑字段

| 字段组 | 原版字段 | APP 当前状态 | 迁移优先级 |
|---|---|---|---|
| 基础字段 | name、description、first_mes | 已覆盖 | P0 |
| Creator Metadata | creator、character_version、creator_notes、embedded tags | 已覆盖：creator、version、creator_notes、embedded tags | P0 |
| Prompt Overrides | system_prompt、post_history_instructions | 已覆盖 | P0 |
| Advanced Definitions | personality、scenario、mes_example | 已覆盖 | P0 |
| Character's Note | depth_prompt.prompt、depth_prompt.depth、depth_prompt.role | 已覆盖 | P0 |
| Talkativeness | group chat 发言倾向 0-1 | 已覆盖为文本输入，需后续优化为数值控件 | P1 |
| Alternate Greetings | 多开场白增删改排序 | 部分覆盖：按行编辑；未提供独立增删、排序控件 | P0 |
| Token counters | 各字段 token 计数、总 token、永久 token | 未覆盖 | P1 |
| Markdown / macros 辅助 | data-macros、编辑器最大化、帮助链接 | 未覆盖 | P2 |
| Unknown fields | `json_data` 里非 ST 字段保留 | 部分覆盖：详情读取 `json_data`，保存时回传；仍需真实 ST 契约测试确认未知字段不丢，且保存请求需改 multipart | P0 |

### 3.5 标签与收藏

| 功能 | 原版能力 | 原版 API / 数据 | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 收藏 | 角色列表、编辑页、批量右键均可收藏 | `data.extensions.fav` + `fav` | 部分覆盖：列表显示、编辑页保存；无列表快捷切换、无批量收藏、未走 `/merge-attributes` | P0 |
| Embedded tags | 写入角色卡 `data.tags` | 角色 edit/create/merge | 部分覆盖：逗号输入，随 create/edit 保存；未提供独立标签编辑体验 | P0 |
| ST 标签系统 | 独立 tag map，用于 folder/filter，可从角色卡导入 | `tags.js` + settings | 未覆盖 | P0 |
| 标签查看/创建/删除/重命名 | tag view popup | settings tag map | 未覆盖 | P1 |
| 批量标签 | 多选角色添加/删除 mutual tags | `BulkEditOverlay` + tag map | 未覆盖 | P1 |

### 3.6 聊天文件管理

原版角色管理里包含 Past Chats 弹窗和聊天文件操作，这部分与 Chat WebView 运行时相关，但角色管理承接时至少要给用户可达入口。

| 功能 | 原版能力 | 原版 API | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 查看角色历史聊天 | 展示文件名、日期、大小、消息数、摘要 | `/api/characters/chats` | API 已封装，UI 未覆盖 | P1 |
| 打开聊天 | 切到该角色指定 chat | 原版前端状态 + `/api/chats/get` | 当前只进入 Chat WebView，不指定角色/聊天 | P1 |
| 删除聊天文件 | 删除单个 jsonl | `/api/chats/delete` | 未覆盖 | P1 |
| 重命名聊天文件 | 改 jsonl 文件名 | `/api/chats/rename` | 未覆盖 | P1 |
| 导出聊天 | JSONL / TXT | `/api/chats/export` | 未覆盖 | P1 |
| 导入聊天 | 导入 jsonl | `/api/chats/import` | 未覆盖 | P2 |

### 3.7 Lorebook、Persona、来源、助手

这些在原版角色管理中是角色操作的一部分，但跨到世界书、Persona、欢迎页等子系统。M2 可以先放入口和状态，不建议和基础角色管理一次做完。

| 功能 | 原版入口 | 数据 / API | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 角色主 Lorebook | globe / Link to World Info | `data.extensions.world` + worldinfo | 部分覆盖：编辑 `world` 文本字段；无 World Info 入口、选择器或校验 | P1 |
| Additional Lorebooks | character extra world info | settings `world_info.charLore` | 未覆盖 | P2 |
| Chat Lorebook | passport 按钮 | chat metadata / worldinfo | 未覆盖 | P2 |
| Import Card Lore | 从角色卡导入内嵌世界书 | world-info 前端逻辑 | 未覆盖 | P2 |
| Connected Personas | Persona 连接弹窗 | persona settings | 未覆盖 | P2 |
| Convert to Persona | 角色转 Persona | persona 前端逻辑 | 未覆盖 | P2 |
| Link to Source | Chub/Pygmalion/GitHub/source_url 等 | `data.extensions.*` | 未覆盖 | P1 |
| Set as assistant | 欢迎页助手角色 | welcome settings | 未覆盖 | P2 |

### 3.8 批量操作

| 功能 | 原版能力 | 原版实现 | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 多选角色 | 点击切换，Shift 范围选择，右键菜单 | `BulkEditOverlay` | 未覆盖 | P1 |
| 批量收藏 | 右键 favorite | `/merge-attributes` | 未覆盖 | P1 |
| 批量复制 | 右键 duplicate | `/duplicate` | 未覆盖 | P1 |
| 批量删除 | 可选择删聊天 | `/delete` | 未覆盖 | P1 |
| 批量转 Persona | persona 逻辑 | `convertCharacterToPersona` | 未覆盖 | P2 |
| 批量标签 | mutual tags popup | tag map | 未覆盖 | P1 |

## 4. 当前 APP 承接差距

截至本次检查，当前代码已完成：

| APP 文件 | 已覆盖 |
|---|---|
| `TavernCoreApi.kt` | `/api/characters/all`、`/get`、`/create`、`/edit`、`/rename`、`/delete`、`/duplicate`、`/import`、`/export`、`/edit-avatar`、`/characters/chats`、CSRF token 和 session cookie；其中 `/import`、`/edit-avatar` 为 multipart，`/create`、`/edit` 当前仍是 JSON body |
| `CharacterListScreen.kt` | 列表、搜索、All/Favorites/Recent、embedded tag 筛选、排序、导入、头像预览、复制、删除、刷新、空态、错误态；排序缺 Random / Search score，标签只覆盖 embedded tags |
| `CharacterEditScreen.kt` | 新建、读取详情、字段分组、保存、重命名、复制、删除、单独换头像、导出 JSON/PNG；缺编辑页头像预览、新建头像上传、完整保存 multipart |
| `DocumentFileHelpers.kt` | 仅用于 Android 文件选择器读取导入文件 / 头像文件，以及写出导出结果，不直接覆盖 SillyTavern 角色数据目录 |
| `STNavGraph.kt` / `MainActivity.kt` | Characters tab 进入原生列表，新建/编辑进入原生页面 |

主要缺口：

| 缺口 | 影响 |
|---|---|
| `create/edit` 请求形态未达契约 | 当前走 JSON body；虽然 SillyTavern 后端可解析 JSON，但和原版表单/multipart 行为不完全一致，头像、裁剪、字段兼容和未知字段保护风险更高 |
| `/merge-attributes` 未作为公开 API / UI 使用 | 快捷收藏、快捷标签、批量局部更新还不能低风险 patch |
| ST 独立标签系统未接入 | 当前已支持 embedded tags 展示/筛选/编辑，但还没有接入原版 settings tag map、folder 和批量标签 |
| 无历史聊天管理 UI | API 层已有 `/characters/chats`，但用户无法从角色管理页选择、导出、删除、重命名角色聊天 |
| 头像体验不完整 | 列表头像和单独换头像已有；编辑页头像预览、新建头像上传、裁剪、格式转换未完成 |
| 无 Lorebook / Persona / Source 入口 | 原版“More...”菜单里的重要管理动作不可达 |
| 无批量操作 | 大角色库用户迁移后效率下降明显 |

## 5. M2 迁移范围建议

### 5.1 M2 P0：角色管理基础承接

P0 的目标是让用户在 APP 内完成日常角色管理，不再频繁回原版角色管理 drawer。

| 模块 | 必做能力 |
|---|---|
| API 层 | 补齐 `/edit`、`/rename`、`/delete`、`/duplicate`、`/import`、`/export`、`/edit-avatar`、`/characters/chats` |
| 列表 | 排序补齐：A-Z、Z-A、Newest、Oldest、Favorites、Recent、Most/Least chats、Most/Least tokens、Random |
| 列表 | 标签过滤接入 ST tag map；保留搜索；显示 creator notes、version、embedded tags、收藏 |
| 编辑 | 完整字段分组：基础、Prompt Overrides、Creator Metadata、Advanced Definitions、Character Note、Alternate Greetings |
| 保存 | 采用 API 优先策略：完整编辑优先 `/api/characters/edit` multipart；局部收藏/标签可继续 `/merge-attributes` |
| 生命周期 | 新建、保存、重命名、复制、删除、导入、导出 |
| 头像 | 新建/编辑头像上传，编辑页头像预览；裁剪可先不做，但文件选择必须有 |
| 错误处理 | 服务未启动、CSRF 失败、HTTP 错误、解析失败、保存失败保留草稿 |

### 5.2 M2 P1：高频增强

| 模块 | 能力 |
|---|---|
| 历史聊天 | 查看角色聊天列表，打开指定聊天进入 Chat WebView，删除/重命名/导出聊天 |
| 批量操作 | 多选、全选、批量删除、批量复制、批量收藏、批量标签 |
| 标签管理 | 查看全部标签、创建/重命名/删除标签、从角色卡导入标签 |
| 头像增强 | 单独换头像、图片格式兼容、裁剪 |
| 来源与替换 | Link to Source、Replace / Update、外部 URL 导入 |
| HotSwaps | 收藏角色快捷头像条 |

### 5.3 M2 P2 / 后续里程碑

| 模块 | 原因 |
|---|---|
| 群聊管理 | 与 group-chats 子系统耦合，适合单独作为非 Chat 原生页面 |
| World Info 完整管理 | 与世界书编辑器耦合，M2 只保留角色主 Lorebook 字段或入口 |
| Connected Personas / Convert to Persona | 与 Persona 管理耦合，建议 Persona 页面建立后再做 |
| Welcome Assistant | 与欢迎页配置耦合，低频 |
| Chat Lorebook / Author's Note overrides | 与聊天上下文和世界书触发链路耦合，先保留 Chat WebView 处理 |
| Token counter / editor macro helpers | 有体验价值，但不阻塞基础管理闭环 |

## 6. API 迁移契约

| APP 动作 | 首选 API | 请求形态 | 备注 |
|---|---|---|---|
| 列表 | `POST /api/characters/all` | JSON `{}` | 返回浅/全量角色对象，含 `date_last_chat`、`chat_size`、`data_size` |
| 详情 | `POST /api/characters/get` | JSON `{ "avatar_url": "xxx.png" }` | 返回完整角色对象和 `json_data` |
| 新建 | `POST /api/characters/create` | multipart form | 原版支持头像、alternate_greetings、extensions、crop |
| 完整保存 | `POST /api/characters/edit` | multipart form | 最接近原版编辑行为，保留 `chat`、`create_date` |
| 局部保存 | `POST /api/characters/merge-attributes` | JSON | 适合收藏、标签、少量字段 patch |
| 单独换头像 | `POST /api/characters/edit-avatar` | multipart form | 不改角色 JSON |
| 重命名 | `POST /api/characters/rename` | JSON `{ avatar_url, new_name }` | 返回新 avatar；APP 需同步标签、active character、历史聊天可选更新 |
| 复制 | `POST /api/characters/duplicate` | JSON `{ avatar_url }` | 返回新文件名 |
| 删除 | `POST /api/characters/delete` | JSON `{ avatar_url, delete_chats }` | 需要二次确认 |
| 导入 | `POST /api/characters/import` | multipart form | 支持 json/png/yaml/yml/charx/byaf |
| 导出 | `POST /api/characters/export` | JSON `{ avatar_url, format }` | format 为 `png` 或 `json` |
| 聊天列表 | `POST /api/characters/chats` | JSON `{ avatar_url, metadata }` | 角色历史聊天列表 |
| 聊天读取 | `POST /api/chats/get` | JSON | 打开指定聊天或重命名历史聊天时需要 |
| 聊天保存 | `POST /api/chats/save` | JSON / compressed | 批量改历史聊天角色名时需要 |
| 聊天删除 | `POST /api/chats/delete` | JSON | 删除单个聊天文件 |
| 聊天导出 | `POST /api/chats/export` | JSON | 导出 JSONL/TXT |
| 聊天导入 | `POST /api/chats/import` | multipart form | P2 |

所有 POST 都必须经过 `/csrf-token` 获取 token，并携带同一个 session cookie 和 `x-csrf-token` header。

当前实现检查：

| API | 当前实现状态 |
|---|---|
| `/api/characters/all`、`/get`、`/rename`、`/duplicate`、`/delete`、`/export`、`/characters/chats` | 已通过 JSON POST 接入 |
| `/api/characters/import`、`/edit-avatar` | 已通过 multipart form 接入 |
| `/api/characters/create`、`/edit` | 已接入，但当前是 JSON POST；需要迁移到 multipart form，才能和原版编辑/头像/裁剪行为完全对齐 |
| `/api/characters/merge-attributes` | 尚未暴露为 `TavernCoreApi` 方法，UI 未使用；仅有未调用的 payload helper |
| 直接文件覆盖角色卡 | 未发现 M2 角色管理路径直接写 `data/default-user/characters`；本地文件访问仅用于文件选择器导入/导出和非 M2 的备份恢复等场景 |

## 7. 原生页面结构建议

### 7.1 CharacterListScreen

应从单列表升级为角色库工具页：

| 区域 | 内容 |
|---|---|
| 顶栏 | 返回/标题、刷新、新建、导入 |
| 搜索排序 | 搜索框、排序菜单、列表/网格切换 |
| 筛选 | All、Favorites、Recent、Tags、Folders |
| HotSwaps | 收藏角色横向头像，可折叠 |
| 列表项 | 头像、名称、Creator Notes、version、tags、最近聊天、聊天数、收藏 |
| 行动作 | 打开编辑、打开聊天、更多菜单 |
| 批量模式 | 多选、全选、批量删除、批量收藏、批量标签 |
| 空态/错误态 | 无角色、搜索无结果、API 错误、服务未启动 |

### 7.2 CharacterEditScreen

应分为多个 section，而不是单张长表单：

| Section | 字段 / 动作 |
|---|---|
| Header | 头像、名称、收藏、保存、更多菜单 |
| Basic | description、first_mes、alternate greetings |
| Prompt Overrides | system_prompt、post_history_instructions |
| Creator Metadata | creator、character_version、creator_notes、embedded tags |
| Advanced Definitions | personality、scenario、mes_example |
| Character Note | depth_prompt.prompt、depth_prompt.depth、depth_prompt.role |
| Group Behavior | talkativeness |
| Links | source URL、primary Lorebook、chat Lorebook 入口 |
| Danger Zone | rename、duplicate、delete、export、replace/update |

### 7.3 CharacterChatsScreen

可以作为编辑页的一个 tab 或 bottom sheet：

| 能力 | 说明 |
|---|---|
| 聊天列表 | 文件名、日期、大小、消息数、最后消息摘要 |
| 打开 | 进入 Chat WebView，并尽量定位到该角色和该聊天 |
| 管理 | 重命名、删除、导出 |
| 导入 | P2，使用 Android 文件选择器 |

## 8. 保存策略

| 场景 | 推荐策略 |
|---|---|
| 新建角色，无头像 | 使用 `/api/characters/create` multipart form，头像字段为空，让 ST 写默认头像 |
| 新建角色，有头像 | 使用 `/api/characters/create` multipart form，附带 avatar 文件 |
| 编辑完整角色 | 使用 `/api/characters/edit` multipart form，传 `json_data`、`avatar_url`、`chat`、`create_date` |
| 快捷收藏 | 使用 `/api/characters/merge-attributes` JSON patch |
| 快捷标签 | embedded tags 可用 `/merge-attributes`；ST tag map 需要 settings/tag 逻辑 |
| 换头像 | 使用 `/api/characters/edit-avatar` multipart form |
| 保存失败 | 草稿留在 APP 状态，不刷新详情，不覆盖本地 UI |
| 未知字段 | 必须从 `/get` 的 `json_data` 基础上合并，避免丢扩展字段 |

当前偏差：

- 新建和完整编辑已经走 SillyTavern API，但仍是 JSON body，不是原版表单/multipart 形态。
- 未知字段目前依赖 `/get` 的 `json_data` 原样回传；需要补真实 ST 契约测试，确认保存后第三方扩展字段、未知顶层字段和未知 `data.extensions` 字段不会丢。
- 快捷收藏和快捷标签还没有改成 `/merge-attributes` 局部 patch。

## 9. 验收口径修订

M2 不能只验“能编辑描述和开场白”。建议改为：

1. 用户可以在 APP 内浏览完整角色库，搜索、排序、筛选结果与原版主要行为一致。
2. 用户可以在 APP 内新建、编辑、保存、重命名、复制、删除、导入、导出角色。
3. 用户可以编辑原版角色管理核心字段：description、first_mes、alternate greetings、creator notes、system prompt、post-history instructions、creator、version、tags、personality、scenario、character note、example dialogue。
4. 用户可以更换头像，保存失败不会破坏原角色卡。
5. 用户可以查看某个角色的历史聊天列表，并至少能进入 Chat WebView 继续聊天。
6. 收藏、标签、最近、聊天数、token/data size 等列表信息从 API 返回字段或 ST 设置中读取，不再依赖原版角色管理 drawer。
7. 对群聊、Persona、完整世界书、Chat Lorebook 等跨系统能力，M2 必须给出清晰入口或“后续承接”说明，不能静默消失。

## 10. 实施顺序建议

| 顺序 | 目标 | 原因 |
|---|---|---|
| 1 | API 层补齐 character edit/import/export/rename/delete/duplicate/chats | 先稳住数据契约 |
| 2 | 编辑页字段补齐和 section 化 | 当前承接感最弱，用户马上能感知 |
| 3 | 列表排序、标签过滤、更多菜单 | 角色库可用性提升最大 |
| 4 | 导入/导出/头像 | 角色管理基础操作闭环 |
| 5 | 重命名/复制/删除/危险操作确认 | 需要更严格错误处理和确认弹窗 |
| 6 | 历史聊天管理入口 | 连接角色管理与 Chat WebView |
| 7 | 批量操作和 HotSwaps | 大库用户效率增强 |
| 8 | Lorebook、Persona、Source、Replace/Update | 跨系统能力逐步接入 |

## 11. 风险与边界

| 风险 | 处理 |
|---|---|
| `/api/characters/edit` multipart 与当前 JSON 客户端差异大 | API 层新增 form/multipart helper，保留 `/merge-attributes` 用于局部更新 |
| 未知扩展字段丢失 | 所有完整保存必须基于 `/get` 返回的 `json_data` 合并 |
| 标签系统不只在角色卡内 | 区分 embedded tags 和 ST tag map，不能把二者混为一谈 |
| 重命名影响聊天、群组、标签、世界书关联 | 重命名单独做任务，必须有契约测试和回滚提示 |
| Chat WebView 状态无法从原生精确定位 | M2 可先打开 Chat WebView，指定角色/聊天定位作为后续 bridge 增强 |
| 群聊和世界书过大 | M2 只保留入口和字段，不把完整 group/world editor 塞进角色管理任务 |
