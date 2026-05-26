# M2 角色管理迁移文档

日期：2026-05-26
检查更新：2026-05-26
状态：执行中（M2 P0 已全覆盖并通过手机真实 ST 契约测试；高频 P1：标签管理、批量操作、分页、聊天导入/重命名、头像格式兜底、token counter、Replace/Update 与跨系统入口 UI 已补齐；本次回查确认角色主入口已从未覆盖/占位状态进入原生列表，剩余为跨系统完整编辑器和少量低频增强）
范围：原版 SillyTavern 角色管理右侧面板到 Android Compose 原生页面的迁移拆解

## 1. 结论

当前 APP 的 M2 实现已从“角色列表 + 基础编辑 + 新建/保存”的最小闭环推进到“P0 基础承接 + 高频 P1 增强”的可验收状态：API 层和 Compose 页面已覆盖角色列表、分页、详情、新建、保存、重命名、复制、删除、导入、URL 导入、导出、真实头像展示、头像随保存上传、独立 `/api/characters/edit-avatar`、快捷收藏、Search score、embedded tags 与 ST tag map 管理、ST folder/drilldown、HotSwaps、列表/网格/批量模式、真批量收藏/取消收藏/复制/删除/标签，以及角色历史聊天查看、打开、导入、输入式重命名、删除确认和导出。Source URL 现在可从 Chub/Pygmalion/GitHub/source_url 等来源推导打开，并可在编辑页保存 `source_url`。角色 API 主链路已用手机 `SM_S9310` 上的真实 ST dev 实例跑过 `TavernCoreRealContractTest`。

当前剩余不再阻塞 M2 P0：Lorebook / Persona / Assistant 已在关联页给出清晰入口与状态，Source 可打开、编辑基础 URL，并可从来源更新；Replace / Update、token counter 和图片格式兜底已补齐到原生编辑页。头像裁剪入口因当前价值不足先撤回，完整 World Info、Persona、Assistant 配置编辑器仍属于跨系统里程碑。

本次未覆盖界面回查结论：`Characters` 主导航现在直接进入 `CharacterListScreen`，不再停留在 M1 的轻量角色 Hub 或历史 `PlaceholderScreen`；新建、详情、编辑也都挂到原生路由。`Tools` / `Settings` / `Manage ST` 已由 App 自有页面承接，不属于本 M2 角色管理未覆盖项。仍未做成原生完整页面的是群聊、完整 World Info、Persona 连接、Welcome Assistant、Chat Lorebook / Author's Note overrides 这类跨系统编辑器；当前处理方式是在角色详情 Links tab 给出可见状态、入口按钮或明确不可用提示，避免用户以为功能消失。

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
| 搜索 | 搜索角色、群组、标签，支持模糊分数排序 | 前端 `FilterHelper` + 本地列表 | 已覆盖：query 非空时按 Search score 排序，名称命中优先，其次 embedded tags、ST tags、Creator Notes；群聊不在本轮 M2 主线 | P0 |
| 排序 | Search、A-Z、Z-A、Newest、Oldest、Favorites、Recent、Most/Least chats、Most/Least tokens、Random | `/api/characters/all` 返回 `create_date`、`date_last_chat`、`chat_size`、`data_size` | 已覆盖：A-Z、Z-A、Newest、Oldest、Favorites、Recent、Most/Least chats、Most/Least tokens、Random；Search score 在搜索时置顶 | P0 |
| 分页 | 每页 10/25/50/100/250/500/1000 | 前端分页 | 已覆盖：本地筛选/排序后分页，支持 10/25/50/100/250/500/1000 每页切换 | P1 |
| 列表/网格视图 | 可切换列表和网格 | 前端偏好 | 已覆盖：列表、网格、批量模式初稿 | P1 |
| HotSwaps | 收藏角色横向快捷头像 | 收藏字段 + 前端 UI | 已覆盖：收藏角色横向头像条，可折叠 | P1 |
| 标签过滤 / 文件夹 | 标签过滤、标签作为 folder、drilldown | `tags.js`、settings tag map | 已覆盖：embedded tag chip、ST tag map 筛选、folder 一级筛选和 drilldown；`tag_map` key 兼容 `id` / `avatarUrl` | P0 |
| 群聊混排 | 角色、群组、标签 folder 混合列表 | characters + groups + tags | 未覆盖，且群聊不在当前 M2 主线 | P2 |

### 3.2 角色卡基础操作

| 功能 | 原版能力 | 原版 API / 数据 | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 新建角色 | 表单提交，支持头像、裁剪、多开场白、extensions | `/api/characters/create` multipart form | 部分覆盖：已改为 multipart，支持字段、多开场白和头像随保存上传；非 png/jpg/jpeg 头像会静默转 PNG，裁剪入口暂不覆盖，extensions 体验仍弱 | P0 |
| 保存角色 | 完整表单保存，保留 chat/create_date/json_data | `/api/characters/edit` multipart form | 已覆盖：已改为 multipart，保留 `chat`、`create_date`、`json_data`，支持头像随保存上传；已通过手机真实 ST 契约测试确认复杂 `json_data` 未知字段不丢 | P0 |
| 局部更新 | 收藏、批量收藏等局部字段 | `/api/characters/merge-attributes` | 已覆盖：列表/详情快捷收藏、批量收藏/取消收藏走 `/merge-attributes`；embedded tag 独立编辑也走 `/merge-attributes` | P0 |
| 重命名 | 改角色名、头像文件名、聊天目录；可选择改历史聊天内角色名 | `/api/characters/rename` + `/api/chats/get/save` | 部分覆盖：已调用 `/rename`，ST 会改头像文件名和聊天目录；无历史聊天内角色名更新选项 | P0 |
| 复制角色 | 复制 PNG 卡，生成新文件名 | `/api/characters/duplicate` | 已覆盖：列表和编辑页均可调用 `/duplicate` | P0 |
| 删除角色 | 可选择是否删除该角色聊天文件夹 | `/api/characters/delete` | 已覆盖：列表和编辑页均有确认，并支持 `delete_chats` | P0 |
| 导入角色 | 支持 `.json`、`.png`、`.yaml`、`.yml`、`.charx`、`.byaf`，支持多选 | `/api/characters/import` multipart form | 已覆盖原生入口：Android 多文件选择后调用 `/import` multipart | P0 |
| 外部 URL 导入 | 从外部 URL 导入角色卡 | 前端 `importFromExternalUrl` + `processDroppedFiles` | 已覆盖：列表页可输入 URL/UUID，先走 `/api/content/importURL` 或 `/api/content/importUUID`，再调用 `/api/characters/import` | P1 |
| 替换 / 更新 | 用文件或在线来源替换当前角色，保留聊天、资产、群组关系 | 复用导入流程并传 preserved avatar | 已覆盖：编辑页支持“文件替换”和“来源更新”，通过 `preserved_name` 导入到当前头像文件名，聊天继续挂接当前角色 | P1 |
| 导出角色 | PNG 或 JSON 导出，导出时清理私有字段 | `/api/characters/export` | 已覆盖：编辑页可导出 JSON / PNG，并写到用户选择的位置 | P0 |

### 3.3 头像与媒体

| 功能 | 原版能力 | 原版 API / 数据 | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 头像预览 | 角色列表和编辑页展示头像 | `/characters/{avatar}` 或 thumbnail | 已覆盖：列表、详情、编辑页统一使用真实头像组件，失败时回退首字母占位；待选头像先显示本地预览 | P0 |
| 头像上传 | 新建/编辑时选择图片 | `/api/characters/create`、`/api/characters/edit` multipart | 已覆盖：新建 / 编辑可选择头像，并随 `/create` 或 `/edit` multipart 保存上传；非 png/jpg/jpeg 头像会静默转 PNG | P0 |
| 单独换头像 | 不改角色数据，仅替换 PNG 封面 | `/api/characters/edit-avatar` | 已覆盖：编辑页选择头像后既可随保存上传，也可对已有角色立即调用 `/edit-avatar` 替换封面 | P1 |
| 图片格式兼容 | 前端会转换不支持格式 | `ensureImageFormatSupported` | 已覆盖：头像上传前可转换为 PNG；非 png/jpg/jpeg 会自动转 PNG 后走 create/edit/edit-avatar | P1 |
| 裁剪 | 带 `crop` query 参数保存裁剪结果 | create/edit/edit-avatar `?crop=` | 暂不覆盖：原生编辑页已撤回头像裁剪和处理模式按钮 | P2 |
| 外部媒体开关 | 允许/禁止角色描述引用外部媒体 | 角色扩展字段 | 未覆盖 | P2 |

### 3.4 编辑字段

| 字段组 | 原版字段 | APP 当前状态 | 迁移优先级 |
|---|---|---|---|
| 基础字段 | name、description、first_mes | 已覆盖 | P0 |
| Creator Metadata | creator、character_version、creator_notes、embedded tags | 已覆盖：creator、version、creator_notes、embedded tags | P0 |
| Prompt Overrides | system_prompt、post_history_instructions | 已覆盖 | P0 |
| Advanced Definitions | personality、scenario、mes_example | 已覆盖 | P0 |
| Character's Note | depth_prompt.prompt、depth_prompt.depth、depth_prompt.role | 已覆盖 | P0 |
| Talkativeness | group chat 发言倾向 0-1 | 已覆盖：数值输入 + 滑块，保存时限制在 `0.0..1.0` | P1 |
| Alternate Greetings | 多开场白增删改排序 | 已覆盖：条目式新增、删除、上移、下移，每条独立输入框，保存仍写入 multipart `alternate_greetings` | P0 |
| Token counters | 各字段 token 计数、总 token、永久 token | 已覆盖：编辑页显示描述、开场白、Prompt/Note、元数据/示例和总量的即时估算；使用 ST byte fallback 算法，非模型精确 tokenizer | P1 |
| Markdown / macros 辅助 | data-macros、编辑器最大化、帮助链接 | 未覆盖 | P2 |
| Unknown fields | `json_data` 里非 ST 字段保留 | 已覆盖：详情读取 `json_data`，保存时通过 multipart 原样回传；已通过手机真实 ST 契约测试确认第三方字段、未知顶层字段和未知 `data.extensions` 字段不丢 | P0 |

### 3.5 标签与收藏

| 功能 | 原版能力 | 原版 API / 数据 | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 收藏 | 角色列表、编辑页、批量右键均可收藏 | `data.extensions.fav` + `fav` | 已覆盖：列表显示、列表行/网格/详情快捷切换、编辑页保存、批量收藏/取消收藏均可用 | P0 |
| Embedded tags | 写入角色卡 `data.tags` | 角色 edit/create/merge | 已覆盖：编辑页随 create/edit 保存；列表标签弹窗可独立编辑并走 `/merge-attributes` | P0 |
| ST 标签系统 | 独立 tag map，用于 folder/filter，可从角色卡导入 | `tags.js` + settings | 已覆盖：读取/保存 `/api/settings/get/save`，支持筛选、创建、重命名、删除、folder/drilldown、从 embedded tags 导入为 ST tags | P0 |
| 标签查看/创建/删除/重命名 | tag view popup | settings tag map | 已覆盖：列表页标签管理入口，保存时保留未知 settings 字段；删除标签时从所有 `tag_map` 引用中移除 | P1 |
| 批量标签 | 多选角色添加/删除 mutual tags | `BulkEditOverlay` + tag map | 已覆盖：批量模式可对所选角色添加或移除 ST 标签 | P1 |

### 3.6 聊天文件管理

原版角色管理里包含 Past Chats 弹窗和聊天文件操作，这部分与 Chat WebView 运行时相关，但角色管理承接时至少要给用户可达入口。

| 功能 | 原版能力 | 原版 API | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 查看角色历史聊天 | 展示文件名、日期、大小、消息数、摘要 | `/api/characters/chats` | 已覆盖：详情页 Chats tab 调用 API 并展示历史聊天摘要 | P1 |
| 打开聊天 | 切到该角色指定 chat | 原版前端状态 + `/api/chats/get` | 已覆盖原生入口：详情页可进入 Chat WebView，并通过 bridge 尝试选择角色和指定 chat；稳定性仍归入 WebView smoke test | P1 |
| 删除聊天文件 | 删除单个 jsonl | `/api/chats/delete` | 已覆盖：详情页聊天行菜单会先确认，成功后移出列表 | P1 |
| 重命名聊天文件 | 改 jsonl 文件名 | `/api/chats/rename` | 已覆盖：详情页弹出输入框，不再硬编码 `-renamed`，成功后刷新当前聊天列表项 | P1 |
| 导出聊天 | JSONL / TXT | `/api/chats/export` | 已覆盖：详情页可导出 JSONL / TXT 到 Android 文档选择器指定位置 | P1 |
| 导入聊天 | 导入 json/jsonl | `/api/chats/import` | 已覆盖：详情页 Chats tab 可通过 Android 文件选择器导入 json/jsonl，并刷新聊天列表 | P2 |

### 3.7 Lorebook、Persona、来源、助手

这些在原版角色管理中是角色操作的一部分，但跨到世界书、Persona、欢迎页等子系统。M2 可以先放入口和状态，不建议和基础角色管理一次做完。

| 功能 | 原版入口 | 数据 / API | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 角色主 Lorebook | globe / Link to World Info | `data.extensions.world` + worldinfo | 部分覆盖：编辑页可维护 `world` 文本字段，详情页 Links tab 显示当前世界书状态；打开动作目前给出不可用提示，无 World Info 选择器、校验或完整编辑 | P1 |
| Additional Lorebooks | character extra world info | settings `world_info.charLore` | 未覆盖：无原生列表或关联编辑器；归入 World Info 子系统 | P2 |
| Chat Lorebook | passport 按钮 | chat metadata / worldinfo | 未覆盖：Chat 运行时仍交给 WebView，原生侧无 Chat Lorebook 编辑器 | P2 |
| Import Card Lore | 从角色卡导入内嵌世界书 | world-info 前端逻辑 | 未覆盖：无内嵌世界书抽取 / 导入流程 | P2 |
| Connected Personas | Persona 连接弹窗 | persona settings | 部分覆盖：详情页 Links tab 显示 Persona 行和管理入口，点击给出不可用提示；完整 Persona 连接仍等 Persona 子系统 | P2 |
| Link to Source | Chub/Pygmalion/GitHub/source_url 等 | `data.extensions.*` | 已覆盖：详情页可推导并打开 Chub / Pygmalion / GitHub / `source_url` 等来源，编辑页可保存 `source_url`，并支持从来源更新当前角色 | P1 |
| Set as assistant | 欢迎页助手角色 | welcome settings | 部分覆盖：详情页 Links tab 显示助手角色行和设置入口，点击给出不可用提示；实际 welcome settings 写入仍等欢迎页/助手子系统 | P2 |

### 3.8 批量操作

| 功能 | 原版能力 | 原版实现 | APP 当前状态 | 迁移优先级 |
|---|---|---|---|---|
| 多选角色 | 点击切换，Shift 范围选择，右键菜单 | `BulkEditOverlay` | 已覆盖：批量模式、复选框、全选当前筛选结果、清空选择；Shift 范围选择和右键菜单暂不做 | P1 |
| 批量收藏 | 右键 favorite | `/merge-attributes` | 已覆盖：批量模式可对所选角色调用 `/merge-attributes` 收藏或取消收藏 | P1 |
| 批量复制 | 右键 duplicate | `/duplicate` | 已覆盖：批量模式逐个调用 `/duplicate` 并报告成功/失败计数 | P1 |
| 批量删除 | 可选择删聊天 | `/delete` | 已覆盖：批量确认后逐个调用 `/api/characters/delete`，复用 `delete_chats` 选项并报告成功/失败计数 | P1 |
| 批量转 Persona | persona 逻辑 | `convertCharacterToPersona` | 未覆盖 | P2 |
| 批量标签 | mutual tags popup | tag map | 已覆盖：批量添加 / 移除 ST tags 并保存 `tag_map` | P1 |

## 4. 当前 APP 承接差距

截至本次检查，当前代码已完成：

| APP 文件 | 已覆盖 |
|---|---|
| `TavernCoreApi.kt` | `/api/characters/all`、`/get`、`/create`、`/edit`、`/merge-attributes`、`/rename`、`/delete`、`/duplicate`、`/import`、`/export`、`/edit-avatar`、`/characters/chats`、`/api/chats/import`、`/api/chats/rename`、`/api/chats/delete`、`/api/chats/export`、`/api/settings/get`、`/api/settings/save`、`/api/content/importURL`、`/api/content/importUUID`、CSRF token 和 session cookie；其中 `/create`、`/edit`、`/import`、`/edit-avatar`、`/api/chats/import` 均已走 multipart |
| `CharacterListScreen.kt` | 列表、分页、Search score、All/Favorites/Recent、embedded tag 和 ST tag map 筛选、ST folder/drilldown、标签管理、排序（含 Random）、导入、URL 导入、头像预览、HotSwaps、列表/网格/批量模式、批量收藏/取消收藏/复制/删除/标签、刷新、空态、错误态；仍缺 Shift 范围选择 |
| `CharacterDetailScreen.kt` | 角色详情、真实头像、快捷收藏、概要/聊天/关联 tab、历史聊天列表、打开指定聊天、导入聊天、输入式聊天重命名、删除确认、导出 JSONL/TXT、Source URL 打开、Lorebook / Persona / Assistant 关联入口和状态；仍缺跨系统完整编辑器 |
| `CharacterEditScreen.kt` | 新建、读取详情、真实头像、本地待选头像预览、非支持格式头像静默转 PNG、字段分组、Source URL 编辑、条目式 Alternate Greetings、Talkativeness 数值/滑块、token counter、保存、头像随保存上传、独立 `/edit-avatar`、重命名、复制、删除、文件替换、来源更新、导出 JSON/PNG |
| `DocumentFileHelpers.kt` | 仅用于 Android 文件选择器读取导入文件 / 头像文件，以及写出导出结果，不直接覆盖 SillyTavern 角色数据目录 |
| `STNavGraph.kt` / `MainActivity.kt` | Characters tab 进入原生列表，新建/详情/编辑进入原生页面；从详情页聊天入口进入 Chat WebView 并保留返回栈 |
| `ui/components/STCards.kt` / `CharacterSharedComponents.kt` | 角色列表、详情、编辑和全局确认/进度弹窗已复用 `STInfoCard`、`STSectionCard`、`STConfirmDialog`、`FavoriteIconButton`、`CharacterTagCheckboxList`，避免角色页面各自保留重复占位组件 |

未覆盖界面完成情况：

| 界面 / 入口 | 当前处理 | 后续判断 |
|---|---|---|
| Characters 主入口 | 已完成：底部 Characters tab 直接进入 `CharacterListScreen`，不是 M1 轻量 Hub，也不是原版 Full Manager 深跳 | 属于 M2 已覆盖 |
| 角色新建 / 详情 / 编辑 | 已完成：`CHARACTER_NEW`、`CHARACTER_DETAIL`、`CHARACTER_EDIT` 均为 Compose 原生路由，并共用主底部导航选中态 | 属于 M2 已覆盖 |
| 历史 `PlaceholderScreen` | 仍保留文件，但当前主 `NavHost` 未把它作为角色管理入口使用 | 可作为历史兜底清理项，不影响 M2 |
| Tools / Settings / Manage ST | 已有 App 自有页面承接，且不再提供不可控的 `WEBVIEW_CHARACTERS` / `WEBVIEW_TOOLS` 伪深入口 | 不计入 M2 角色未覆盖项 |
| Lorebook / Persona / Assistant | 详情 Links tab 已显示状态和入口；非 Source 的动作目前提示不可用，未写入对应系统配置 | 跨系统里程碑 |
| 群聊 / 完整 World Info / Chat Lorebook | 未做原生完整页面；Chat 运行时和群聊逻辑仍留在 WebView 或后续子系统 | P2 / 后续里程碑 |

剩余边界：

| 边界 | 影响 |
|---|---|
| 批量操作剩余项 | 真批量收藏/取消收藏/复制/删除/标签已覆盖；Shift 范围选择和右键菜单未覆盖 |
| ST 标签系统剩余项 | 管理、folder/drilldown、从卡内导入、批量标签已覆盖；更复杂的颜色/排序 UI 可后续增强 |
| 历史聊天管理剩余项 | 聊天列表、打开、导入、输入式重命名、删除确认、导出已覆盖；后续可继续补更完整的 Chat WebView 指定角色/聊天定位 smoke test |
| Lorebook / Persona / Assistant 仍不完整 | 关联页已有状态、入口和不可用提示；完整编辑/连接需要对应子系统落地 |
| Source 校验仍不完整 | Source 已可打开、编辑基础 URL，并可执行来源更新；仍缺原版级来源校验和差异预览 |

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
| 头像增强 | 单独换头像、图片格式兼容；裁剪暂不做 |
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
| Markdown / editor macro helpers | data-macros、编辑器最大化、帮助链接有体验价值，但不阻塞基础管理闭环；token counter 已在 P1 完成 |

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
| 外部 URL / UUID 导入 | `POST /api/content/importURL` / `POST /api/content/importUUID` 后接 `/api/characters/import` | JSON + multipart form | 先下载角色卡内容，再按文件导入 |
| 导出 | `POST /api/characters/export` | JSON `{ avatar_url, format }` | format 为 `png` 或 `json` |
| 聊天列表 | `POST /api/characters/chats` | JSON `{ avatar_url, metadata }` | 角色历史聊天列表 |
| 聊天读取 | `POST /api/chats/get` | JSON | 打开指定聊天或重命名历史聊天时需要 |
| 聊天保存 | `POST /api/chats/save` | JSON / compressed | 批量改历史聊天角色名时需要 |
| 聊天重命名 | `POST /api/chats/rename` | JSON | 改单个角色聊天文件名 |
| 聊天删除 | `POST /api/chats/delete` | JSON | 删除单个聊天文件 |
| 聊天导出 | `POST /api/chats/export` | JSON | 导出 JSONL/TXT |
| 聊天导入 | `POST /api/chats/import` | multipart form | P2 |

所有 POST 都必须经过 `/csrf-token` 获取 token，并携带同一个 session cookie 和 `x-csrf-token` header。

当前实现检查：

| API | 当前实现状态 |
|---|---|
| `/api/characters/all`、`/get`、`/rename`、`/duplicate`、`/delete`、`/export`、`/characters/chats` | 已通过 JSON POST 接入 |
| `/api/characters/create`、`/edit`、`/import`、`/edit-avatar` | 已通过 multipart form 接入；`/create`、`/edit` 支持头像文件随保存上传，编辑页也可单独调用 `/edit-avatar`；crop query 尚未接入 UI |
| `/api/characters/merge-attributes` | 已暴露为 `TavernCoreApi` 方法，详情页和列表快捷收藏、批量收藏/取消收藏、embedded tags 独立编辑均已使用 |
| `/api/settings/get`、`/api/settings/save` | 已封装 ST `tags` / `tag_map` 读写，UI 已覆盖筛选、标签管理、folder/drilldown、从 embedded tags 导入和批量 ST 标签 |
| `/api/content/importURL`、`/api/content/importUUID` | 已封装外部角色卡下载，并串接 `/api/characters/import`；列表页已有 URL/UUID 输入入口 |
| `/api/chats/import`、`/rename`、`/delete`、`/export` | 已封装并在详情页聊天列表使用；导入走 Android 文件选择器 + multipart，重命名使用输入框，删除有确认，成功后刷新当前列表状态 |
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
| Prompt Overrides | system_prompt、post_history_instructions |
| Creator Metadata | creator、character_version、creator_notes、embedded tags |
| Advanced Definitions | personality、scenario、mes_example |
| Character Note | depth_prompt.prompt、depth_prompt.depth、depth_prompt.role |
| Group Behavior | talkativeness |
| Links | source URL、primary Lorebook、chat Lorebook 入口 |
| Danger Zone | rename、duplicate、delete、export、replace/update |

### 7.3 CharacterChatsScreen

当前已作为 `CharacterDetailScreen` 的 Chats tab 初步落地，后续可以继续独立成 bottom sheet 或子页面：

| 能力 | 说明 |
|---|---|
| 聊天列表 | 文件名、日期、大小、消息数、最后消息摘要 |
| 打开 | 进入 Chat WebView，并尽量定位到该角色和该聊天 |
| 管理 | 重命名、删除、导出 |
| 导入 | 已覆盖：使用 Android 文件选择器导入 json/jsonl |

### 7.4 未覆盖界面的 UI 口径

角色管理范围内不能再出现“点进去只是占位”的主路径。当前检查结果：

| 路径 | UI 口径 |
|---|---|
| `Characters` tab | 直接进入原生角色列表，服务未启动时显示可启动服务的错误/空态卡片 |
| 角色详情 Links tab | Source 可打开；Lorebook / Persona / Assistant 显示当前状态或后续承接提示，不伪装成已完成编辑器 |
| 跨系统页面 | 群聊、完整 World Info、Persona、Assistant、Chat Lorebook 不在 M2 内强塞；后续独立页面落地前，角色管理只给可见入口和明确提示 |

## 8. 保存策略

| 场景 | 推荐策略 |
|---|---|
| 新建角色，无头像 | 使用 `/api/characters/create` multipart form，头像字段为空，让 ST 写默认头像 |
| 新建角色，有头像 | 使用 `/api/characters/create` multipart form，附带 avatar 文件 |
| 编辑完整角色 | 使用 `/api/characters/edit` multipart form，传 `json_data`、`avatar_url`、`chat`、`create_date` |
| 快捷收藏 | 使用 `/api/characters/merge-attributes` JSON patch |
| 快捷标签 | embedded tags 使用 `/merge-attributes`；ST tags 使用 settings `tag_map` 读写 |
| 换头像 | 使用 `/api/characters/edit-avatar` multipart form |
| 保存失败 | 草稿留在 APP 状态，不刷新详情，不覆盖本地 UI |
| 未知字段 | 必须从 `/get` 的 `json_data` 基础上合并，避免丢扩展字段 |

当前偏差：

- 新建和完整编辑已改为 multipart，并可随保存上传头像；编辑页已有真实头像预览、独立 `/edit-avatar` 即时动作和非支持格式头像静默 PNG 转换。头像裁剪与处理模式按钮已先撤回。
- 未知字段目前依赖 `/get` 的 `json_data` 原样回传；已补复杂 `json_data` multipart 单元契约测试，并已用手机真实 ST 契约测试回归第三方扩展字段、未知顶层字段和未知 `data.extensions` 字段。
- 快捷收藏已走 `/merge-attributes` 局部 patch；embedded tags 独立编辑已接 `/merge-attributes`，ST tags 和批量标签已接 `/api/settings/get/save`。

## 9. 验收口径修订

M2 不能只验“能编辑描述和开场白”。建议改为：

1. 用户可以在 APP 内浏览完整角色库，搜索、排序、筛选结果与原版主要行为一致。
2. 用户可以在 APP 内新建、编辑、保存、重命名、复制、删除、导入、导出角色。
3. 用户可以编辑原版角色管理核心字段：description、first_mes、alternate greetings、creator notes、system prompt、post-history instructions、creator、version、tags、personality、scenario、character note、example dialogue。
4. 用户可以更换头像；非支持格式会静默转 PNG，保存失败不会破坏原角色卡。
5. 用户可以查看某个角色的历史聊天列表，并至少能进入 Chat WebView 继续聊天。
6. 收藏、标签、最近、聊天数、token/data size 等列表信息从 API 返回字段或 ST 设置中读取，不再依赖原版角色管理 drawer。
7. 对群聊、Persona、完整世界书、Chat Lorebook 等跨系统能力，M2 必须给出清晰入口、状态或“后续承接”提示，不能静默消失，也不能用不可控的原版 Full Manager 深跳伪装成完成。

## 10. 实施顺序建议

| 顺序 | 目标 | 原因 |
|---|---|---|
| 1 | 头像增强剩余项 | 已完成：真实头像、待选预览、独立 `/edit-avatar`、非支持格式 PNG 转换；头像裁剪暂不做 |
| 2 | 批量操作增强剩余项 | 全选、清空、真批量收藏/取消收藏/复制/删除/标签已完成；后续可补 Shift 范围选择和右键菜单 |
| 3 | 标签管理增强剩余项 | 创建/重命名/删除、folder/drilldown、从角色卡导入、批量标签已完成；后续可补颜色、排序等细节 |
| 4 | 历史聊天增强剩余项 | 已能看、开、导入、输入式重命名、确认删除、导出；后续继续验证不同 ST 前端状态下的指定聊天定位 |
| 5 | 补 token counter | 已完成：编辑页即时估算各字段与总量；后续可接模型精确 tokenizer |
| 6 | Lorebook、Persona、Source、Replace/Update | 已完成：关联页入口和状态、Source 打开/编辑/来源更新、文件替换；Lorebook / Persona / Assistant 入口会明确提示后续承接，完整编辑器另立子系统 |

## 11. 风险与边界

| 风险 | 处理 |
|---|---|
| multipart 保存与 ST 原版字段兼容 | 已改为 form/multipart helper；后续新增字段时继续对照 `charaFormatData`，保留 `/merge-attributes` 用于局部更新 |
| 未知扩展字段丢失 | 所有完整保存必须基于 `/get` 返回的 `json_data` 合并；已补可选真实 ST 契约测试，并已在手机调试实例通过 |
| 标签系统不只在角色卡内 | 区分 embedded tags 和 ST tag map，不能把二者混为一谈 |
| 重命名影响聊天、群组、标签、世界书关联 | 重命名单独做任务，必须有契约测试和回滚提示 |
| Chat WebView 状态无法从原生精确定位 | 当前 bridge 会尝试选择角色和指定聊天；这属于 WebView 运行时 smoke test，不属于本轮角色 API 契约测试，仍需单独验证不同 ST 前端状态下是否稳定 |
| 未覆盖入口造成误解 | 角色主路径不再使用占位页；跨系统入口必须显示当前状态或不可用提示，避免用户误认为已完整支持 |
| 群聊和世界书过大 | M2 只保留入口、字段和后续承接说明，不把完整 group/world editor 塞进角色管理任务 |

## 12. 真实 ST 契约测试

`TavernCoreClientTest` 使用 `MockWebServer` 检查 Android 端请求形态；`TavernCoreRealContractTest` 则连接一个真实运行的 SillyTavern 服务，验证 ST 实际接受并保存这些请求。

本次验证记录：

| 日期 | 目标 | 命令 | 结果 |
|---|---|---|---|
| 2026-05-26 | 手机 `SM_S9310` 上的 ST dev 实例，adb 转发 `tcp:18000 -> tcp:8000` | `ST_CONTRACT_BASE_URL=http://127.0.0.1:18000/ ./gradlew testDebugUnitTest --tests 'io.github.sanitised.st.api.TavernCoreRealContractTest'` | 通过；未留下 `STContract*` 临时角色 |
| 2026-05-26 | 默认日常单测行为，不设置真实 ST 地址 | `./gradlew testDebugUnitTest --tests 'io.github.sanitised.st.api.TavernCoreRealContractTest' --rerun-tasks` | 通过；测试安全跳过 |
| 2026-05-26 | 常规单元测试套件 | `./gradlew testDebugUnitTest` | 通过 |

默认不设置 `ST_CONTRACT_BASE_URL` 时，真实契约测试会跳过，避免日常单测误改真实数据。连接手机调试实例时：

```bash
/Users/changlepan/android-sdk/platform-tools/adb -s 'adb-RFCY41BD54H-jx4XIL._adb-tls-connect._tcp' forward tcp:18000 tcp:8000
ST_CONTRACT_BASE_URL=http://127.0.0.1:18000/ ./gradlew testDebugUnitTest --tests 'io.github.sanitised.st.api.TavernCoreRealContractTest'
```

测试会创建一个 `STContract_*` 临时角色，覆盖完整保存、复杂 `json_data` 保留、`merge-attributes` 收藏和 embedded tags、`edit-avatar`、ST tags 创建/重命名/删除，以及 `tag_map` 保存；结束时会恢复原始 tag settings 并删除临时角色。
