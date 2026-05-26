# M3 SillyTavern 源码迁移规划

日期：2026-05-26
源码基线：`SillyTavern` 子仓库 `e3f41666c`
参考文档：`docs/PRD-native-settings.md` v0.6、`docs/M2-character-management-migration.md`
状态：规划稿（已按 `e3f41666c` 源码复核并修正 API 形态）

## 1. 结论

M2 已把角色管理主链路迁到原生页面。M3 不建议把 SillyTavern 的所有非 Chat 面板一次性迁完，而应按源码依赖关系收敛为三条主线：

1. **发布稳定性主线 P0**：备份/恢复兼容、设置快照、崩溃恢复、端口冲突、上游同步和合规发布。
2. **跨系统原生迁移主线 P1**：优先补 M2 留下的跨系统缺口，即世界书、Persona、预设/Prompt 模板、连接/API Key 基础管理。
3. **实验性能力标记 P2**：群聊、Quick Replies、Regex、Data Bank、RAG、媒体/TTS、扩展管理先列入迁移清单，但 M3 只做入口、状态说明或 WebView/实验性标记。

Chat 消息列表、输入区、生成链路、扩展运行时仍保留在原版 WebView。Android 原生页只接 SillyTavern 已有本地 API 和稳定数据结构，不复制提示词最终组装、世界书触发、Regex 执行、扩展注入等运行时逻辑。

本轮源码审计修正的关键点：

- ST UI 的用户备份入口是 `POST /api/users/backup`，请求体为 `{ "handle": "..." }`，返回的是用户目录根内容的 `.zip`；是否包含 `secrets.json` 取决于 `allowKeysExposure`。
- `/api/settings/get` 返回的 `settings` 是原始 JSON 字符串；`/api/settings/save` 会用请求体整体覆盖 `settings.json`，所以 App 必须 parse-merge-save，不能只提交局部 patch。
- World Info 没有单独 create API；新建世界书或新建条目都走 `POST /api/worldinfo/edit` 保存完整 `{ name, data }`，`entries` 与 `uid` 必须保真。
- `/api/avatars/delete` 只删除 Persona 头像文件和缩略图，不会同步清理 `power_user.personas` / `persona_descriptions`；App 删除 Persona 时要额外合并保存 settings。
- Data Maid 的 `/api/data-maid/view` 是 GET，靠 `token` + `hash` 查看文件；向量接口还包括 `/query-multi` 和 `/purge-all`。

## 2. 源码迁移总表

| 功能域 | 原版源码入口 | 主要 API / 数据 | 当前 App 状态 | M3 处置 | 优先级 |
|---|---|---|---|---|---|
| 数据备份/恢复 | `public/scripts/user.js`、`src/users.js`、`src/endpoints/users-private.js`、`public/scripts/templates/masterExport.html`、`public/scripts/templates/masterImport.html` | ST UI 用户备份：`POST /api/users/backup` 返回 `.zip`；App 自有备份：`st_backup/config.yaml` + `st_backup/data/*`；`data/default-user/*` | 已有 `NodeBackup`，支持 App 自有 `st_backup`、`.tar.gz` / `.tar` / `.zip` 识别和 ST UI 单用户 `.zip` 基础导入 | 做成发布级：补 manifest、导入预检查、覆盖清单、secrets 是否存在提示；继续兼容 App 全量备份与 ST UI 单用户备份 | P0 |
| 设置快照 | `src/endpoints/settings.js` | `/api/settings/get-snapshots`、`/load-snapshot`、`/make-snapshot`、`/restore-snapshot` | 未做原生入口 | 管理页新增“设置快照”：创建、查看、恢复；作为导入/升级前保护 | P0 |
| Chat 自动备份 | `src/endpoints/backups.js`、`public/scripts/chat-backups.js` | `/api/backups/chat/get/delete/download` | 角色详情已覆盖单角色聊天导入/导出；未覆盖全局自动备份列表 | 工具页新增“聊天备份”：浏览、查看、恢复到当前角色、删除 | P1 |
| 数据清理 | `src/endpoints/data-maid.js`、`public/scripts/data-maid.js` | `POST /api/data-maid/report`、`/finalize`、`/delete`；`GET /api/data-maid/view?token&hash` | 未覆盖 | M3 只做诊断入口和只读报告；删除动作可放 M4 | P2 |
| 世界书 | `public/scripts/world-info.js`、`public/css/world-info.css`、`src/endpoints/worldinfo.js` | `/api/worldinfo/list/get/edit/import/delete`；`worlds/*.json` | M2 只在角色详情/编辑显示主世界书字段和不可用入口 | 做原生 World Info 管理：列表、搜索、创建、编辑条目、导入、删除；支持角色主 Lorebook 选择 | P1 |
| 角色 Additional / Chat Lorebooks | `public/scripts/world-info.js`、`public/script.js`、角色 `data.extensions.world`、settings `world_info.charLore`、chat metadata | `/api/settings/get/save` + `/api/worldinfo/*` | 未覆盖 | M3 先支持角色主 Lorebook；Additional / Chat Lorebook 只显示状态和后续提示 | P2 |
| Persona | `public/scripts/personas.js`、`src/endpoints/avatars.js` | `/api/avatars/get/upload/delete`、`/api/settings/get/save`；`User Avatars/*`、settings `power_user.personas`、`persona_descriptions`、`default_persona` | M2 仅在角色详情显示“未建立原生 Persona 连接” | 做 Persona 基础管理：列表、搜索、新建、头像上传、重命名、标题/描述、默认 Persona、删除；删除头像后必须额外清 settings 元数据；角色连接先做只读状态/后续入口 | P1 |
| API Key / Secrets | `public/scripts/secrets.js`、`src/endpoints/secrets.js` | `/api/secrets/read/write/find/delete/rename/rotate/settings`；`secrets.json`；源码 `SECRET_KEYS` 是 provider 白名单 | App 设置页未管理 ST API Key | 做基础 Key 管理：列出源码支持 key、显示 masked value / label / active、写入、重命名、删除、切换 active；默认不展示明文 | P1 |
| 连接历史 / 连接档案 | `public/scripts/server-history.js`、`public/scripts/extensions/connection-manager/*`、`public/scripts/openai.js`、`public/scripts/textgen-settings.js` | settings `power_user.servers`、extension settings、OpenAI/TextGen settings | 未覆盖 | M3 做“连接基础页”：OpenAI-compatible / OpenRouter / KoboldCpp 常用连接与 Key；完整 Connection Manager 放 M4 | P1 |
| 预设 / Prompt 模板 | `public/scripts/preset-manager.js`、`public/scripts/instruct-mode.js`、`public/scripts/power-user.js`、`public/scripts/sysprompt.js`、`public/scripts/reasoning.js` | `/api/settings/get` 返回 `instruct/context/sysprompt/reasoning/openai_settings/textgenerationwebui_presets`；`/api/presets/save/delete/restore`；选择当前预设需合并保存 `settings.json` | 未覆盖 | 做 Preset Lite：列表、选择、新建/复制/删除、JSON 导入导出；优先 Instruct、Context、System Prompt、Reasoning、Chat Completion | P1 |
| Prompt Manager | `public/scripts/PromptManager.js`、`public/css/promptmanager.css`、`public/index.html` completion prompt popup | 主要存在前端状态和 OpenAI preset extensions | 未覆盖 | M3 不迁完整 Prompt Manager；只在 Preset Lite 中保留 prompt 条目可见性和 JSON 保真 | P2 |
| 群聊 | `public/scripts/group-chats.js`、`public/css/rm-groups.css`、`src/endpoints/groups.js`、`src/endpoints/chats.js` group routes | `/api/groups/all/create/edit/delete`、`/api/chats/group/get/info/save/delete/import`；`groups/*`、`group chats/*` | 未覆盖；Chat WebView 仍可用原版群聊 | M3 做迁移设计和只读列表入口；完整群聊创建/成员/聊天管理建议 M4 | P2 |
| Quick Replies | `public/scripts/extensions/quick-reply/*`、`src/endpoints/quick-replies.js` | `/api/quick-replies/save/delete`、`/api/settings/get` `quickReplyPresets`；`QuickReplies/*.json` | 未覆盖 | M3 只列入工具入口并标实验性；完整编辑器 M4 | P2 |
| Regex / 文本规则 | `public/scripts/extensions/regex/*` | settings `extension_settings.regex*`、角色 `data.extensions.regex_scripts`、preset extensions | 未覆盖 | 不在 M3 原生化；保留 WebView/实验性标记，避免复制运行时规则引擎 | P2 |
| Data Bank / 附件 | `public/scripts/chats.js` Data Bank 区域、`public/scripts/extensions/attachments/*`、`src/endpoints/files.js`、`src/endpoints/images.js` | `/api/files/upload/delete/verify`、`/api/images/upload/list/folders/delete`；`files/upload` 和 `images/upload` 都是 base64 JSON；`user/files`、`user/images` | WebView 文件选择已可用；原生未覆盖附件库 | M3 只完善 Bridge 文件选择/分享；原生附件库 M4 | P2 |
| RAG / 向量 | `public/scripts/extensions/vectors/*`、`src/endpoints/vectors.js` | `/api/vector/query/query-multi/insert/list/delete/purge/purge-all`；`vectors/*` | 未覆盖 | 不进 M3；标实验性 | P2 |
| 背景 / 主题 | `public/scripts/backgrounds.js`、`src/endpoints/backgrounds.js`、`src/endpoints/themes.js` | `/api/themes/save/delete`、background endpoints；`backgrounds/*`、`themes/*` | App 主题已独立；Chat WebView 主题保留原版 | M3 不做完整原版主题迁移；只确保备份/恢复保留数据 | P2 |
| 扩展管理 | `public/scripts/extensions.js`、`src/endpoints/extensions.js` | `/api/extensions/install/update/branches/switch/move/version/delete/discover` | 未覆盖 | M3 只在 UI 和文档标“实验性”；不做原生安装/更新 | P2 |
| 图片/语音/媒体生成 | `src/endpoints/openai.js`、`src/endpoints/novelai.js`、`src/endpoints/stable-diffusion.js`、`src/endpoints/speech.js`、`public/scripts/extensions/tts/*` | 多提供商媒体、TTS/STT endpoint | WebView 中可用；Bridge 规划未完全落地 | M3 只补 Android Bridge 的分享、保存、TTS/STT 可选增强；不迁移完整媒体设置 | P2 |
| Chat 运行时 | `public/script.js`、`public/scripts/chats.js`、`public/scripts/openai.js`、`public/scripts/textgen-settings.js`、后端 generate endpoints | `/api/backends/*/generate`、`/api/chats/*` | Chat WebView 承接 | 明确不迁；只做 WebView smoke test、崩溃恢复、文件桥接 | 保留 |

## 3. M3 推荐范围

### 3.1 M3 P0：正式版稳定性

| 模块 | 必做能力 | 源码依据 | 验收 |
|---|---|---|---|
| 备份导出 | 导出 `config.yaml` + `data/default-user`，并生成 manifest：App 版本、ST commit、时间、数据大小 | `USER_DIRECTORY_TEMPLATE`、`NodeBackup.kt` | 导出的 `.tar.gz` 可被当前 App 重新导入；manifest 可被导入预检查读取 |
| ST UI 备份导入 | 识别 ST UI user backup：`POST /api/users/backup` 返回 zip，根目录含 `settings.json` 和 `chats/`；导入到 `data/default-user` | `users-private.js` `/backup`、`createBackupArchive`、`NodeBackup.materializeUiBackup` | 从原版 ST 导出的单用户备份可导入，角色/聊天/世界书/预设可见；若 UI zip 缺 `secrets.json` 要明确说明 |
| 导入预检查 | 解压到临时目录后扫描 `settings.json`、`characters/`、`chats/`、`worlds/`、`groups/`、`User Avatars/`、`QuickReplies/` | `USER_DIRECTORY_TEMPLATE` | 导入前显示覆盖清单；不识别或多用户备份给出明确错误 |
| 设置快照 | 创建/列出/恢复 settings snapshot | `src/endpoints/settings.js` | 导入或上游升级前可一键生成快照；恢复后 WebView 和原生页读取一致 |
| 崩溃恢复 | Node 非主动退出后进入 ERROR，提供重启、查看日志、导出诊断 | `NodeService.waitForExitAsync` | 手动 kill Node 后 App 不白屏，能重启并保留日志 |
| 端口冲突 | 启动前检测目标端口；冲突时提示占用并允许换端口 | `NodeService` `PORT` env、`SillyTavernUrl.kt` | 8000 被占用时不会反复失败；用户可改用新端口 |
| 上游同步流程 | 固化 ST bundle 更新、契约测试、真机 smoke test、合规检查清单 | `SillyTavern` 子仓库、`TavernCoreRealContractTest` | 新 ST commit 更新有固定步骤和失败回滚策略 |

### 3.2 M3 P1：原生跨系统第一批

| 模块 | M3 交付口径 | 不做内容 |
|---|---|---|
| World Info | 原生列表、搜索、详情、条目编辑、`disable` 启停、key/keysecondary、comment/content、order/depth/position、constant/selective、导入/删除；角色编辑页可选择主世界书 | Chat Lorebook、Additional Lorebooks、运行时触发调试 |
| Persona | 原生 Persona 列表、新建、头像上传、名称/标题/描述、默认 Persona、删除；角色详情只显示连接状态和后续入口 | Persona 与每个 chat/character/group 的完整 lock / connection 行为 |
| Preset Lite | Instruct、Context、System Prompt、Reasoning、Chat Completion / Text Completion 预设列表与 JSON 保真编辑；导入/导出/复制/删除；当前预设选择通过 settings merge 保存 | 完整 Prompt Manager 拖拽排序、所有 provider 的高级采样 UI |
| API Key / 连接基础 | 常用 provider 的 key 状态、masked value、label、active secret 切换、写入/删除/重命名；OpenAI-compatible / OpenRouter / KoboldCpp URL 历史和连接测试入口 | 全量 Connection Manager 扩展、OAuth、所有 provider 深度配置 |
| 聊天备份 | 全局 chat backup 浏览、查看、删除；从备份恢复到当前角色时走已有聊天导入链路 | 跨角色批量恢复和自动清理策略 |

### 3.3 M3 P2：只列清单，不承诺完整迁移

| 模块 | M3 动作 | 后续拆分 |
|---|---|---|
| 群聊 | 只读列表 + 打开 WebView 群聊；完成 M4 设计文档 | M4 群聊原生创建、成员排序、群聊文件管理 |
| Quick Replies | 工具页标记实验性，展示是否存在预设 | M4 Quick Replies 原生编辑器 |
| Regex | 角色/预设相关处显示“有 Regex 脚本”状态，不编辑 | M4 或更后独立 Regex 管理 |
| Data Bank / 附件 | 保持 Chat WebView，补 Android 文件选择、分享、保存 | M4 附件库 |
| RAG / Vectors | 标实验性，不做入口或只链接 WebView | M4+ |
| 扩展管理 | 设置页说明扩展实验性 | M4+ |

## 4. API 迁移契约表

| App 动作 | 首选 API | 请求形态 | 数据保真要求 |
|---|---|---|---|
| 读取全量设置 | `POST /api/settings/get` | JSON `{}` | 保留 `settings` 原始 JSON 字符串，用于合并保存 |
| 保存设置 | `POST /api/settings/save` | 原 settings JSON | 只改目标 key，其余未知字段原样保留 |
| 创建设置快照 | `POST /api/settings/make-snapshot` | JSON `{}` | 成功后刷新 snapshot 列表 |
| 列出设置快照 | `POST /api/settings/get-snapshots` | JSON `{}` | 显示 name/date/size |
| 预览设置快照 | `POST /api/settings/load-snapshot` | JSON `{ "name": "settings_default-user_..." }` | 返回快照原始 JSON 字符串，只做只读预览 / diff |
| 恢复设置快照 | `POST /api/settings/restore-snapshot` | JSON `{ "name": "..." }` | 恢复前二次确认，恢复后刷新 App 状态 |
| 列出世界书 | `POST /api/worldinfo/list` | JSON `{}` | 显示 `file_id/name/extensions`，不要直接猜文件路径 |
| 读取世界书 | `POST /api/worldinfo/get` | JSON `{ "name": "..." }` | `entries` 对象必须完整保留 |
| 保存 / 新建世界书 | `POST /api/worldinfo/edit` | JSON `{ "name": "...", "data": { ... } }` | 无单独 create API；未编辑字段、entry `uid` 和未知字段原样保留 |
| 导入世界书 | `POST /api/worldinfo/import` | multipart | 只接受含 `entries` 的 JSON |
| 删除世界书 | `POST /api/worldinfo/delete` | JSON `{ "name": "..." }` | 删除前提示角色可能仍引用 |
| 列出 Persona 头像 | `POST /api/avatars/get` | JSON `{}` | 与 settings 中 `power_user.personas` 合并显示 |
| 上传 Persona 头像 | `POST /api/avatars/upload` | multipart `avatar` + optional `overwrite_name` | 上传后刷新 thumbnail/cache |
| 删除 Persona 头像 | `POST /api/avatars/delete` | JSON `{ "avatar": "..." }` | 源码只删文件和 thumbnail；App 随后必须 merge-save settings 清理 Persona 元数据 |
| 写入 API Key | `POST /api/secrets/write` | JSON `{ key, value, label? }` | 不在 App 日志中输出明文 |
| 读取 API Key 状态 | `POST /api/secrets/read` | JSON `{}` | 返回 `SECRET_KEYS` 全量 map：每个 key 为 `null` 或 masked `id/value/label/active[]` |
| 删除 / 重命名 / 切换 Key | `/api/secrets/delete`、`/rename`、`/rotate` | JSON `{ key, id?, label? }` | `delete` 可不传 id 删除 active；变更后刷新 secret state |
| 明文读取 Key | `POST /api/secrets/find` | JSON `{ key, id? }` | 默认只允许可导出 URL 类 key；其它 key 需要 `allowKeysExposure=true`，原生页默认不用 |
| 保存预设 | `POST /api/presets/save` | JSON `{ apiId, name, preset }` | `apiId` 必须限定在源码支持集合 |
| 删除预设 | `POST /api/presets/delete` | JSON `{ apiId, name }` | 删除前确认，不删除默认 fallback |
| 恢复默认预设 | `POST /api/presets/restore` | JSON `{ apiId, name }` | 区分 `isDefault` |
| 选择当前预设 | `POST /api/settings/save` | merge 后的完整 settings JSON | `/api/presets/save` 只保存文件；当前选中项仍在 settings 中 |
| 聊天备份列表 | `POST /api/backups/chat/get` | JSON `{}` | 展示文件名、消息数、大小、时间 |
| 下载 / 删除聊天备份 | `/api/backups/chat/download`、`/delete` | JSON `{ "name": "..." }` | 只允许 `chat_` 前缀备份 |

所有 SillyTavern POST API 继续走现有 `TavernCoreClient` 的 CSRF/session cookie 逻辑。M3 新 API 不直接写 `data/default-user`，除非处在 App 备份/恢复、导入预检查或诊断只读扫描场景。

## 5. 数据目录覆盖清单

| 目录 / 文件 | SillyTavern 含义 | M3 备份恢复 | M3 原生页面 | 备注 |
|---|---|---|---|---|
| `settings.json` | 全局用户设置、tags、power_user、extension settings | 必须保留 | Settings snapshot、Persona、Preset、连接基础均会局部合并 | 保存时必须保留未知字段 |
| `characters/` | 角色卡与角色资源子目录 | 必须保留 | M2 已覆盖 | M3 只读取世界书/Persona/Regex 关联状态 |
| `chats/` | 单角色聊天 JSONL | 必须保留 | M2 已覆盖单角色历史聊天 | M3 增全局聊天备份 |
| `worlds/` | World Info JSON | 必须保留 | M3 P1 | 主世界书进入 M3 |
| `groups/` | 群组 JSON | 必须保留 | M3 P2 只读 | 完整管理 M4 |
| `group chats/` | 群聊 JSONL | 必须保留 | M3 P2 只读 | 完整管理 M4 |
| `User Avatars/` | Persona 头像 | 必须保留 | M3 P1 | 与 settings Persona 元数据合并 |
| `OpenAI Settings/` | Chat Completion preset 文件 | 必须保留 | M3 P1 Preset Lite | 通过 `/api/settings/get` 和 `/api/presets/*` |
| `TextGen Settings/` | Text Completion preset 文件 | 必须保留 | M3 P1 Preset Lite | 先 JSON 保真，不做全部采样 UI |
| `KoboldAI Settings/`、`NovelAI Settings/` | 其他后端 preset | 必须保留 | M3 可只读/JSON | 全量高级 UI 后移 |
| `instruct/` | Instruct 模板 | 必须保留 | M3 P1 | Preset Lite |
| `context/` | Context 模板 | 必须保留 | M3 P1 | Preset Lite |
| `sysprompt/` | System Prompt | 必须保留 | M3 P1 | Preset Lite |
| `reasoning/` | Reasoning Formatting | 必须保留 | M3 P1 | Preset Lite |
| `QuickReplies/` | Quick Reply preset | 必须保留 | M3 P2 状态 | 编辑器后移 |
| `backups/` | settings/chat 自动备份 | 必须保留 | M3 P0/P1 | Settings snapshot、Chat backup |
| `user/files`、`user/images` | Data Bank / 附件 | 必须保留 | M3 P2 | 原生附件库后移 |
| `vectors/` | RAG / 向量数据 | 必须保留 | 不迁 | 标实验性 |
| `extensions/` | 第三方扩展 | 必须保留 | 不迁 | 发布说明标实验性 |
| `secrets.json` | API keys / secrets | App 备份必须保留；ST UI zip 可能因 `allowKeysExposure=false` 缺失 | M3 P1 | 不导出明文展示；导入 UI zip 时提示 secrets 可能未包含 |

## 6. 实施顺序

| 顺序 | 工作包 | 输出物 | 依赖 |
|---|---|---|---|
| 1 | 备份/恢复预检查与 manifest | `NodeBackup` 增强、导入清单 UI、单测 | 无 |
| 2 | 设置快照与聊天备份 API 适配 | `TavernCoreApi` 新增 settings/backups 方法、工具页入口 | 现有 CSRF 客户端 |
| 3 | 崩溃恢复、端口冲突、诊断导出 | `NodeService` 状态策略、设置/日志页入口 | 现有 NodeService |
| 4 | World Info 原生页面 | `WorldInfoApi`/models、列表页、编辑页、角色主 Lorebook 选择 | settings/worldinfo API |
| 5 | Persona 原生页面 | `PersonaApi`/models、列表/编辑、头像上传、默认 Persona | settings + avatars API |
| 6 | Preset Lite | Preset models、列表、JSON 编辑、导入/导出、删除/恢复 | settings + presets API |
| 7 | API Key / 连接基础页 | Secrets models、常用 provider 管理、连接测试入口 | secrets API |
| 8 | P2 入口和实验性标记 | 群聊/Quick Replies/Regex/Data Bank 状态卡、发布说明 | 前面页面稳定后 |
| 9 | 上游同步和发布验收 | 更新流程文档、契约测试、真机 smoke test、AGPL/许可证清单 | 全部功能冻结 |

## 7. 验收标准

1. 用户可以从 App 导出完整备份，并在干净安装后导入恢复角色、聊天、世界书、Persona、预设和设置。
2. 用户可以导入原版 SillyTavern UI 单用户备份；多用户备份会被明确拒绝并解释原因。
3. Node 崩溃、端口冲突、服务未就绪不会导致空白页；用户能重启、换端口、查看日志或导出诊断。
4. 角色详情中的世界书和 Persona 入口不再只是不可用提示：主世界书和 Persona 基础管理可在原生页完成。
5. 常用 Prompt/预设/API Key 不需要回原版左侧抽屉完成基础管理；高级 provider 参数仍可回 WebView。
6. 群聊、Regex、Quick Replies、Data Bank、RAG、扩展管理在 UI 中明确标注支持边界，不伪装成已原生完成。
7. 上游 SillyTavern bundle 更新有固定检查表：常规单测、真实 ST 契约测试、Chat WebView smoke test、备份导入导出、许可证检查。

## 8. M3 不做事项

| 不做项 | 原因 | 替代方案 |
|---|---|---|
| 原生 Chat 消息列表和输入区 | 与生成链路、扩展、宏、世界书触发高度耦合 | 保留 WebView |
| 完整 Prompt Manager | 前端状态复杂，和 provider preset extensions 强耦合 | Preset Lite 先保 JSON 保真 |
| 完整群聊编辑器 | 涉及群组、群聊文件、自动发言策略、Chat WebView 定位 | M3 只读入口，M4 独立计划 |
| Regex 运行时 | 会复制扩展执行语义，风险高 | WebView/实验性 |
| 第三方扩展安装/更新原生页 | 安全、兼容和上游同步风险高 | 设置页说明实验性 |
| 全量媒体/TTS/STT provider 设置 | provider 数量多，且 WebView 已承载 | M3 只补 Android Bridge 高频能力 |

## 9. 自检

| 检查项 | 结果 |
|---|---|
| 是否逐项对照 SillyTavern 源码入口 | 已复核主要 `src/endpoints`、`public/scripts`、`USER_DIRECTORY_TEMPLATE`、`NodeBackup.kt` 和 `TavernCoreApi.kt` |
| 是否区分 M3 与 M4+ | 已用 P0/P1/P2 和“不做事项”区分 |
| 是否避免直接写 ST 数据目录 | 已规定新功能优先 API，只有备份/恢复/诊断可碰文件 |
| 是否覆盖 M2 留下的跨系统缺口 | 已把 World Info、Persona、Preset/Connection 列入 M3 P1 |
| 是否保留 Chat WebView 边界 | 已明确保留范围 |
