# 里程碑 M2：原生角色库管理

在 M2 阶段，ST-android-setting 完成了最核心的一步：**将角色管理的完整生命周期和表单编辑从原版 Web WebView 中迁出，完全由基于 Compose 与 M3 规范的安卓原生页面承接。**

---

## 🎯 迁移设计与治理现状

根据 `docs/M2-character-management-migration.md`，本项目彻底摒弃了以占位符为主的轻量 Hub，真正实现了生产级别的管理闭环。

### 📊 M2 角色管理覆盖现状

| 模块分区 | Web 原版对应入口 | 安卓原生承接实现 | 优先级级别 |
|---|---|---|---|
| **原生角色列表** | 右侧 `rm_characters_block` 面板 | `CharacterListScreen.kt`<br>- 完美支持模糊搜索（Search score 排序）<br>- A-Z / 最近 / 收藏等 10 种排序方式<br>- Embedded tag 与 ST tag map 折叠及 Folder drilldown 嵌套筛选。 | **P0** (已上线) |
| **原生卡片操作** | 新建、删除、重命名、克隆、局部属性合并 | `POST` 接口对接<br>- 新建克隆完全原生化<br>- 重命名自动联动修改物理头像文件与历史聊天目录<br>- 批量收藏/取消收藏与批量批量添加标签（Bulk edit）。 | **P0** (已上线) |
| **头像与媒体** | 头像上传与裁剪预览 | 媒体增强流<br>- 自动对待上传头像进行格式兼容过滤（非 png/jpg/jpeg 静默在本地转化为标准 PNG）<br>- 独立封面瞬间替换（`/edit-avatar`）<br>- 随保存整体 Multipart 上传。 | **P0** (已上线) |
| **完整原生编辑器** | 单个角色高级属性修改 | `CharacterEditScreen.kt`<br>- 分组式表单卡片设计：Prompt Overrides、Creator Metadata、Advanced Definitions 等一应俱全<br>- 动态 token 计数器即时估算。<br>- 多开场白（Alternate Greetings）增删改排序。 | **P0** (已上线) |
| **历史聊天管理** | Past Chats 弹窗 | 原生 Chats Tab 承接<br>- 展现角色的历史聊天摘要与时间大小<br>- 聊天备份文件在原生界面下重命名、删除确认和多端导出。 | **P1** (已上线) |

---

## 🔌 API 迁移契约映射

为了保持与上游 SillyTavern 内核无缝同步，`TavernCoreClient` 封装了一套严格的 HTTP 映射契约，绝不绕过 API 私自写入数据目录，保障数据原子性：

### 🧬 TavernCoreApi 关键方法与端点

```kotlin
interface TavernCoreApi {
    // 1. 获取浅量角色简要列表 (POST /api/characters/all)
    suspend fun listCharacters(): List<CharacterSummary>

    // 2. 获取单个角色全量 JSON 实体 (POST /api/characters/get)
    suspend fun getCharacter(avatar: String): CharacterDetail

    // 3. 原生新建角色，随表单上传可选头像 (POST /api/characters/create)
    suspend fun createCharacter(request: CharacterSaveRequest): String

    // 4. 原生完整保存，保留已知/未知的所有字段结构 (POST /api/characters/edit)
    suspend fun updateCharacter(request: CharacterSaveRequest): Unit

    // 5. 局部字段补丁，如快捷切换收藏 (POST /api/characters/merge-attributes)
    suspend fun mergeAttributes(avatar: String, attributes: Map<String, Any>): Unit

    // 6. 重命名，联动清理头像和聊天树 (POST /api/characters/rename)
    suspend fun renameCharacter(avatar: String, newName: String): String

    // 7. 单独强换头像，自动更新缓存 (POST /api/characters/edit-avatar)
    suspend fun updateCharacterAvatar(avatar: String, fileName: String, bytes: ByteArray): Unit
}
```

---

## 🔒 核心设计红线：第三方字段与未识别数据保真（Preservation Strategy）

在 SillyTavern 的开源生态中，用户经常会导入来自 Chub.ai、Pygmalion 等社区的高级定制卡片。这些角色卡片内部包含各种自定义的扩展（如第三方 `extensions` 属性，或者其他 AI 平台专用的元数据字段）。

为了保证这部分数据在安卓端原生保存时不被“洗掉”，M2 引入了**高保真合并策略（Parse-Merge-Save）**：

```
+-------------------------------------------------------------+
|              TavernCoreClient.getCharacter()                |
|  1. 从 API 获取完整的 json_data (包含我们不认识的第三方属性)   |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|                     Compose 编辑界面                         |
|  2. 用户在原生表单中仅修改 "description" 或 "personality"     |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
|              TavernCoreClient.updateCharacter()             |
|  3. 将用户编辑的字段覆盖回原有的 json_data 对应节点            |
|  4. 保留所有“未识别字段”，组装为 multipart 请求回传给 Node    |
+-------------------------------------------------------------+
```

1. **绝对不作局部序列化过滤**：当通过 `/api/characters/get` 得到全量数据后，App 不会将其裁剪为只含基础类属性的对象，而是将原始 `json_data` 完整托底保存。
2. **读写合并**：修改表单数据在写回时，会先取得完整的原始树，仅精准覆写被用户修改的原生配置分支，未知顶层字段和未知 `data.extensions` 属性将**原封不动地原路带回**。
3. **高标准自动化回归**：编写了 `TavernCoreRealContractTest`（基于真机调试环境测试），专门挑选了大量夹带私货的 Chub 角色卡执行“读 -> 改 -> 传 -> 重读”循环，通过自动化比对字节流，100% 确认无任一第三方扩展数据丢失。
