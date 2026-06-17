package io.github.sanitised.st.ui.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.api.WorldInfoEntry
import io.github.sanitised.st.api.WorldInfoSummary
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// P0 · 世界书完整编辑（设计稿 screens/WorldBook.jsx，画板 10–15）
// 接真实后端：listWorldInfos / getWorldInfo / saveWorldInfo / deleteWorldInfo
// + settings.world_info_settings（全局激活 globalSelect + world_info_* 扫描规则）。
// 条目读写依赖 WorldInfoEntry.raw 兜底未知字段，避免丢数据。
// ─────────────────────────────────────────────────────────────────────────────

enum class P0WiMode(val label: String, val icon: ImageVector) {
    Constant("常驻", Icons.Filled.PushPin),
    Normal("触发", Icons.Filled.Key),
    Vectorized("向量", Icons.Filled.Hub),
}

private fun WorldInfoEntry.mode(): P0WiMode = when {
    constant -> P0WiMode.Constant
    (raw["vectorized"] as? Boolean) == true -> P0WiMode.Vectorized
    else -> P0WiMode.Normal
}

@Composable
private fun P0WiMode.tint(): Color = when (this) {
    P0WiMode.Constant -> MaterialTheme.colorScheme.primary
    P0WiMode.Normal -> MaterialTheme.colorScheme.tertiary
    P0WiMode.Vectorized -> MaterialTheme.colorScheme.secondary
}

private val WI_POSITIONS = listOf(
    "角色定义之前", "角色定义之后", "作者注顶部", "作者注底部", "@D — 按深度插入对话历史", "示例消息之前", "示例消息之后"
)
private val WI_ROLES = listOf("系统", "用户", "AI")

private fun WorldInfoEntry.positionLabel(): String {
    val role = WI_ROLES.getOrElse((raw["role"] as? Number)?.toInt() ?: 0) { "系统" }
    return when (position) {
        0 -> "角色定义之前"; 1 -> "角色定义之后"; 2 -> "作者注顶部"; 3 -> "作者注底部"
        4 -> "@D 深度 $depth · $role"; 5 -> "示例消息之前"; 6 -> "示例消息之后"
        else -> "位置 $position"
    }
}

private fun WorldInfoEntry.probability(): Int = (raw["probability"] as? Number)?.toInt() ?: 100

private fun anyMap(value: Any?): Map<String, Any?> =
    (value as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()

@Suppress("UNCHECKED_CAST")
private fun globalSelectOf(settings: Map<String, Any?>): List<String> {
    val wis = anyMap(settings["world_info_settings"])
    val wi = anyMap(wis["world_info"])
    return (wi["globalSelect"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
}

/** 返回写入了新 globalSelect 的 settings 副本。 */
private fun settingsWithGlobalSelect(settings: Map<String, Any?>, books: List<String>): Map<String, Any?> {
    val out = settings.toMutableMap()
    val wis = anyMap(out["world_info_settings"]).toMutableMap()
    val wi = anyMap(wis["world_info"]).toMutableMap()
    wi["globalSelect"] = books
    wis["world_info"] = wi
    out["world_info_settings"] = wis
    return out
}

// ── 10/11 世界书管理 + 长按操作表 ───────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeWorldBookManageScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenGlobalSettings: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val running = status.state == NodeState.RUNNING
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var books by remember { mutableStateOf<List<WorldInfoSummary>>(emptyList()) }
    var globalSelect by remember { mutableStateOf<List<String>>(emptyList()) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var actionsForBook by remember { mutableStateOf<WorldInfoSummary?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(running, baseUrl, reloadKey) {
        if (!running) return@LaunchedEffect
        loading = true
        runCatching {
            val client = TavernCoreClient(baseUrl)
            books = client.listWorldInfos().sortedBy { it.name.lowercase() }
            globalSelect = globalSelectOf(client.getSettings())
        }.onFailure { onShowMessage(it.message ?: "世界书加载失败") }
        loading = false
    }

    fun toggleGlobal(name: String, on: Boolean) {
        if (!running) return
        scope.launch {
            runCatching {
                val client = TavernCoreClient(baseUrl)
                val current = globalSelectOf(client.getSettings())
                val next = if (on) (current + name).distinct() else current - name
                client.saveSettings(settingsWithGlobalSelect(client.getSettings(), next))
            }.onSuccess { reloadKey++ }.onFailure { onShowMessage(it.message ?: "保存失败") }
        }
    }

    P0Scaffold(
        title = "世界书",
        onBack = onBack,
        actions = {
            PrototypeIconButton(Icons.Filled.Search, "搜索", { onShowMessage("搜索世界书") })
            PrototypeIconButton(Icons.Filled.Settings, "全局设置", onOpenGlobalSettings)
        }
    ) {
        if (!running) {
            ServiceOffline()
            return@P0Scaffold
        }
        if (loading) {
            LoadingRow("正在读取世界书…")
            return@P0Scaffold
        }
        P0SectionHeader("全局激活")
        Text(
            "全局激活的世界书对所有聊天生效；角色绑定的世界书只在对应聊天生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
        )
        if (globalSelect.isEmpty()) {
            Text("（暂无全局激活的世界书）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                globalSelect.forEach { id ->
                    val display = books.firstOrNull { it.id == id }?.name ?: id
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer).clickable { toggleGlobal(id, false) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(5.dp))
                        Text(display, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
                    }
                }
            }
        }

        P0SectionHeader("全部世界书", trailing = {
            Text("${books.size} 本", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        })
        if (books.isEmpty()) {
            Text("尚未创建任何世界书。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        books.forEachIndexed { i, b ->
            val active = globalSelect.contains(b.id)
            PrototypeListItem(
                headline = b.name,
                supporting = if (active) "全局激活中" else "点击查看条目",
                leading = {
                    PrototypeTileIcon(
                        icon = Icons.Filled.AutoStories,
                        tint = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailing = {
                    if (active) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("全局", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        PrototypeIconButton(Icons.Filled.MoreVert, "更多", { actionsForBook = b })
                    }
                },
                divider = i < books.lastIndex,
                onClick = { onOpenBook(b.id) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        runCatching {
                            val name = "新世界书 ${System.currentTimeMillis() % 100000}"
                            TavernCoreClient(baseUrl).saveWorldInfo(WorldInfoBook(name = name, entries = emptyList(), rawData = mapOf("entries" to emptyMap<String, Any?>()), fileId = name))
                            name
                        }.onSuccess { onShowMessage("已创建「$it」"); reloadKey++ }.onFailure { onShowMessage(it.message ?: "新建失败") }
                    }
                },
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("新建世界书")
            }
            OutlinedButton(onClick = { onShowMessage("导入：请在文件管理器中选择 .json/.png") }, modifier = Modifier.weight(1f).height(44.dp)) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("导入")
            }
        }
        Text(
            "支持 .json / .lorebook / 嵌入世界书的角色卡 .png",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            textAlign = TextAlign.Center
        )
    }

    val book = actionsForBook
    if (book != null) {
        val active = globalSelect.contains(book.id)
        ModalBottomSheet(
            onDismissRequest = { actionsForBook = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrototypeTileIcon(Icons.Filled.AutoStories, tint = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Text(book.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            P0SheetItem(Icons.AutoMirrored.Filled.OpenInNew, "打开条目列表") { actionsForBook = null; onOpenBook(book.id) }
            P0SheetItem(Icons.Filled.Public, if (active) "取消全局激活" else "全局激活") { actionsForBook = null; toggleGlobal(book.id, !active) }
            P0SheetItem(Icons.Filled.Delete, "删除世界书", danger = true) {
                actionsForBook = null
                scope.launch {
                    runCatching { TavernCoreClient(baseUrl).deleteWorldInfo(book.id) }
                        .onSuccess { onShowMessage("已删除「${book.name}」"); reloadKey++ }
                        .onFailure { onShowMessage(it.message ?: "删除失败") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── 12 条目列表 ──────────────────────────────────────────────────────────────
@Composable
fun PrototypeLorebookDetailScreen(
    status: NodeStatus,
    baseUrl: String,
    bookName: String,
    onBack: () -> Unit,
    onOpenEntry: (Int) -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val running = status.state == NodeState.RUNNING
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var book by remember { mutableStateOf<WorldInfoBook?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    val pageSize = 20

    LaunchedEffect(running, baseUrl, bookName, reloadKey) {
        if (!running) return@LaunchedEffect
        loading = true
        runCatching { book = TavernCoreClient(baseUrl).getWorldInfo(bookName) }
            .onFailure { onShowMessage(it.message ?: "条目加载失败") }
        loading = false
    }

    fun toggleEntry(entry: WorldInfoEntry, on: Boolean) {
        val b = book ?: return
        scope.launch {
            runCatching {
                val updated = b.copy(entries = b.entries.map { if (it.uid == entry.uid) it.copy(disabled = !on) else it })
                TavernCoreClient(baseUrl).saveWorldInfo(updated)
            }.onSuccess { reloadKey++ }.onFailure { onShowMessage(it.message ?: "保存失败") }
        }
    }

    Box(Modifier.fillMaxSize()) {
        P0Scaffold(
            title = book?.name?.ifBlank { bookName } ?: bookName,
            subtitle = book?.let { "${it.entries.size} 条" } ?: "",
            onBack = onBack,
            actions = {
                PrototypeIconButton(Icons.Filled.Search, "搜索", { onShowMessage("搜索条目") })
                PrototypeIconButton(Icons.Filled.MoreVert, "更多", { onShowMessage("更多操作") })
            }
        ) {
            if (!running) { ServiceOffline(); return@P0Scaffold }
            if (loading) { LoadingRow("正在读取条目…"); return@P0Scaffold }
            val entries = book?.entries.orEmpty()
            if (entries.isEmpty()) {
                Text("此世界书暂无条目。点击右下「新条目」添加。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                return@P0Scaffold
            }
            val pageCount = (entries.size + pageSize - 1) / pageSize
            val pageEntries = entries.drop(page * pageSize).take(pageSize)
            pageEntries.forEachIndexed { i, e ->
                WiEntryRow(e, onClick = { onOpenEntry(e.uid) }, onToggle = { toggleEntry(e, it) })
                if (i < pageEntries.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 90.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrototypeIconButton(Icons.Filled.ChevronLeft, "上一页", { if (page > 0) page-- })
                Text("第 ${page + 1} / $pageCount 页", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
                PrototypeIconButton(Icons.Filled.ChevronRight, "下一页", { if (page < pageCount - 1) page++ })
            }
        }
        if (running) {
            ExtendedFloatingActionButton(
                onClick = { onOpenEntry(-1) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新条目") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            )
        }
    }
}

@Composable
private fun WiEntryRow(e: WorldInfoEntry, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val mode = e.mode()
    val tint = mode.tint()
    val on = !e.disabled
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(mode.icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.comment.ifBlank { e.keys.joinToString(", ").ifBlank { "未命名条目" } },
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(mode.label, style = MaterialTheme.typography.labelSmall, color = tint)
            }
            if (e.keys.isNotEmpty()) {
                Text(e.keys.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            Text(
                buildString {
                    append(e.positionLabel()); append(" · 顺序 "); append(e.order)
                    if (e.probability() < 100) { append(" · "); append(e.probability()); append("%") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = on, onCheckedChange = onToggle)
    }
}

// ── 13/14 条目编辑器（内容 / 插入与触发）─────────────────────────────────────
@Composable
fun PrototypeWorldEntryEditScreen(
    status: NodeStatus,
    baseUrl: String,
    bookName: String,
    entryUid: Int,
    onClose: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    BackHandler(onBack = onClose)
    val running = status.state == NodeState.RUNNING
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var book by remember { mutableStateOf<WorldInfoBook?>(null) }
    // 编辑草稿：从 entry.raw 起步，键名对齐上游条目字段，保存时整体写回（保留未知字段）。
    val draft = remember { mutableStateMapOf<String, Any?>() }
    var draftUid by remember { mutableIntStateOf(entryUid) }

    LaunchedEffect(running, baseUrl, bookName, entryUid) {
        if (!running) { loading = false; return@LaunchedEffect }
        loading = true
        runCatching {
            val b = TavernCoreClient(baseUrl).getWorldInfo(bookName)
            book = b
            val entry = b.entries.firstOrNull { it.uid == entryUid }
            draft.clear()
            if (entry != null) {
                draft.putAll(entry.raw)
                draftUid = entry.uid
            } else {
                // 新条目
                draftUid = (b.entries.maxOfOrNull { it.uid } ?: -1) + 1
                draft["uid"] = draftUid
                draft["key"] = emptyList<String>()
                draft["content"] = ""
                draft["comment"] = ""
                draft["order"] = 100
                draft["position"] = 1
                draft["constant"] = false
            }
        }.onFailure { onShowMessage(it.message ?: "条目加载失败") }
        loading = false
    }

    fun s(key: String): String = (draft[key])?.toString().orEmpty()
    fun i(key: String, def: Int): Int = (draft[key] as? Number)?.toInt() ?: def
    fun b(key: String): Boolean = (draft[key] as? Boolean) ?: false
    fun keysText(): String = (draft["key"] as? List<*>)?.joinToString(", ") { it.toString() } ?: ""
    fun keysSecondaryText(): String = (draft["keysecondary"] as? List<*>)?.joinToString(", ") { it.toString() } ?: ""

    fun modeIndex(): Int = when {
        b("constant") -> 0
        b("vectorized") -> 2
        else -> 1
    }

    fun save() {
        val bk = book ?: return
        scope.launch {
            runCatching {
                val entry = WorldInfoEntry(
                    uid = draftUid,
                    keys = keysFrom(keysText()),
                    secondaryKeys = keysFrom(keysSecondaryText()),
                    comment = s("comment"),
                    content = s("content"),
                    order = i("order", 100),
                    depth = i("depth", 4),
                    position = i("position", 1),
                    constant = b("constant"),
                    selective = b("selective"),
                    disabled = b("disable") || b("disabled"),
                    raw = draft.toMap()
                )
                val exists = bk.entries.any { it.uid == draftUid }
                val nextEntries = if (exists) bk.entries.map { if (it.uid == draftUid) entry else it } else bk.entries + entry
                TavernCoreClient(baseUrl).saveWorldInfo(bk.copy(entries = nextEntries))
            }.onSuccess { onShowMessage("条目已保存"); onClose() }.onFailure { onShowMessage(it.message ?: "保存失败") }
        }
    }

    P0Scaffold(
        title = if (entryUid < 0) "新条目" else "编辑条目",
        subtitle = book?.name?.ifBlank { bookName } ?: bookName,
        onBack = onClose,
        closeIcon = true,
        actions = {
            PrototypeIconButton(Icons.Filled.Check, "保存", { save() })
        }
    ) {
        if (!running) { ServiceOffline(); return@P0Scaffold }
        if (loading) { LoadingRow("正在读取条目…"); return@P0Scaffold }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            P0Seg(
                options = listOf("常驻", "触发", "向量"),
                selectedIndex = modeIndex(),
                onSelect = { idx ->
                    draft["constant"] = idx == 0
                    draft["vectorized"] = idx == 2
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text("启用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Switch(checked = !(b("disable") || b("disabled")), onCheckedChange = { draft["disable"] = !it })
        }
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("内容") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("插入与触发") })
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) {
            P0Field(label = "标题 / 备注", value = s("comment"), onValueChange = { draft["comment"] = it }, hint = "仅用于整理，不进入提示词")
            P0Field(label = "主关键字（逗号分隔）", value = keysText(), onValueChange = { draft["key"] = keysFrom(it) })
            P0Field(label = "可选过滤（逗号分隔，任一命中）", value = keysSecondaryText(), onValueChange = { draft["keysecondary"] = keysFrom(it) }, placeholder = "无 — 仅主关键字")
            P0BoltBanner("常驻条目无视关键字，始终插入提示词；触发条目在扫描窗口里命中关键字才插入。")
            P0Field(
                label = "内容", value = s("content"), onValueChange = { draft["content"] = it },
                multiline = true, minLines = 7, hint = "支持 {{user}} / {{char}} 宏"
            )
            Spacer(Modifier.height(24.dp))
        } else {
            P0SectionHeader("插入位置")
            P0Dropdown(label = "位置", options = WI_POSITIONS, selectedIndex = i("position", 1).coerceIn(0, WI_POSITIONS.lastIndex), onSelect = { draft["position"] = it })
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                P0Stepper(label = "深度", value = i("depth", 4), onValueChange = { draft["depth"] = it }, modifier = Modifier.weight(1f), min = 0)
                Column(Modifier.weight(2f)) {
                    Text("插入角色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    P0Seg(options = WI_ROLES, selectedIndex = i("role", 0).coerceIn(0, 2), onSelect = { draft["role"] = it })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                P0Stepper(label = "顺序", value = i("order", 100), onValueChange = { draft["order"] = it }, modifier = Modifier.weight(1f), hint = "数值大的更靠近末尾", step = 10, min = 0)
                P0Stepper(label = "触发概率", value = i("probability", 100), onValueChange = { draft["probability"] = it; draft["useProbability"] = true }, modifier = Modifier.weight(1f), suffix = "%", step = 5, min = 0, max = 100)
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            P0SectionHeader("递归")
            P0ToggleRow(title = "不可被递归触发", sub = "其它条目的内容不会激活本条", checked = b("excludeRecursion"), onCheckedChange = { draft["excludeRecursion"] = it })
            P0ToggleRow(title = "阻止进一步递归", sub = "本条内容不会再激活其它条目", checked = b("preventRecursion"), onCheckedChange = { draft["preventRecursion"] = it })
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                P0Stepper(label = "延迟到第 N 级递归", value = i("delayUntilRecursion", 0), onValueChange = { draft["delayUntilRecursion"] = it }, hint = "0 = 不延迟", min = 0)
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            P0SectionHeader("时效")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                P0Stepper(label = "黏性", value = i("sticky", 0), onValueChange = { draft["sticky"] = it }, modifier = Modifier.weight(1f), suffix = "轮", min = 0)
                P0Stepper(label = "冷却", value = i("cooldown", 0), onValueChange = { draft["cooldown"] = it }, modifier = Modifier.weight(1f), suffix = "轮", min = 0)
                P0Stepper(label = "延迟", value = i("delay", 0), onValueChange = { draft["delay"] = it }, modifier = Modifier.weight(1f), suffix = "楼", min = 0)
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            P0SectionHeader("分组与匹配")
            Row(Modifier.fillMaxWidth()) {
                P0Field(label = "包含组", value = s("group"), onValueChange = { draft["group"] = it }, modifier = Modifier.weight(1f), hint = "同组条目每次只激活一条")
                P0Field(label = "组权重", value = i("groupWeight", 100).toString(), onValueChange = { draft["groupWeight"] = it.toIntOrNull() ?: 100 }, modifier = Modifier.width(120.dp))
            }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                P0Stepper(label = "扫描深度覆盖", value = i("scanDepth", 0), onValueChange = { draft["scanDepth"] = it }, hint = "0 = 用全局值", min = 0)
            }
            P0Field(label = "自动化 ID", value = s("automationId"), onValueChange = { draft["automationId"] = it }, placeholder = "供 Quick Reply / STscript 引用")
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun keysFrom(text: String): List<String> =
    text.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }

// ── 15 全局激活设置 ──────────────────────────────────────────────────────────
@Composable
fun PrototypeWIGlobalSettingsScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val running = status.state == NodeState.RUNNING
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var rawSettings by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    val wi = remember { mutableStateMapOf<String, Any?>() }

    LaunchedEffect(running, baseUrl) {
        if (!running) { loading = false; return@LaunchedEffect }
        loading = true
        runCatching {
            val settings = TavernCoreClient(baseUrl).getSettings()
            rawSettings = settings
            wi.clear()
            wi.putAll(anyMap(anyMap(settings["world_info_settings"]).filterKeys { it != "world_info" }))
        }.onFailure { onShowMessage(it.message ?: "设置加载失败") }
        loading = false
    }

    fun i(key: String, def: Int): Int = (wi[key] as? Number)?.toInt() ?: def
    fun bl(key: String, def: Boolean): Boolean = (wi[key] as? Boolean) ?: def

    fun save() {
        scope.launch {
            runCatching {
                val out = rawSettings.toMutableMap()
                val wis = anyMap(out["world_info_settings"]).toMutableMap()
                val globalSelect = wis["world_info"] // preserve globalSelect
                wi.forEach { (k, v) -> wis[k] = v }
                if (globalSelect != null) wis["world_info"] = globalSelect
                out["world_info_settings"] = wis
                TavernCoreClient(baseUrl).saveSettings(out)
            }.onSuccess { onShowMessage("全局设置已保存"); onBack() }.onFailure { onShowMessage(it.message ?: "保存失败") }
        }
    }

    P0Scaffold(
        title = "世界书 · 全局设置",
        subtitle = "作用于所有世界书的激活规则",
        onBack = onBack,
        actions = { PrototypeIconButton(Icons.Filled.Check, "保存", { save() }) }
    ) {
        if (!running) { ServiceOffline(); return@P0Scaffold }
        if (loading) { LoadingRow("正在读取设置…"); return@P0Scaffold }
        P0SectionHeader("扫描与预算")
        P0Slider("扫描深度（最近 N 条消息）", value = i("world_info_depth", 2).toFloat(), onValueChange = { wi["world_info_depth"] = it.toInt() }, valueRange = 0f..10f, steps = 9)
        P0Slider("上下文占比上限 (%)", value = i("world_info_budget", 25).toFloat(), onValueChange = { wi["world_info_budget"] = it.toInt() }, valueRange = 1f..100f)
        P0Slider("Token 预算硬上限（0 = 不限）", value = i("world_info_budget_cap", 0).toFloat(), onValueChange = { wi["world_info_budget_cap"] = it.toInt() }, valueRange = 0f..8192f)
        P0Slider("最少激活条数", value = i("world_info_min_activations", 0).toFloat(), onValueChange = { wi["world_info_min_activations"] = it.toInt() }, valueRange = 0f..100f)
        Text(
            "超出预算时，按顺序值低的条目优先丢弃。最少激活条数会突破扫描深度继续向上搜索。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        P0SectionHeader("递归")
        P0ToggleRow(title = "递归扫描", sub = "已激活条目的内容可以再激活其它条目", checked = bl("world_info_recursive", true), onCheckedChange = { wi["world_info_recursive"] = it })
        P0Slider("最大递归层级", value = i("world_info_max_recursion_steps", 2).toFloat(), onValueChange = { wi["world_info_max_recursion_steps"] = it.toInt() }, valueRange = 0f..10f, steps = 9)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        P0SectionHeader("匹配")
        P0ToggleRow(title = "大小写敏感", sub = "默认值，可被条目覆盖", checked = bl("world_info_case_sensitive", false), onCheckedChange = { wi["world_info_case_sensitive"] = it })
        P0ToggleRow(title = "全词匹配", sub = "key 必须是完整词，不做子串匹配", checked = bl("world_info_match_whole_words", true), onCheckedChange = { wi["world_info_match_whole_words"] = it })
        P0ToggleRow(title = "组权重计分", sub = "包含组内按权重随机，而非顺序优先", checked = bl("world_info_use_group_scoring", false), onCheckedChange = { wi["world_info_use_group_scoring"] = it })
        P0ToggleRow(title = "预算溢出警告", sub = "条目因预算被丢弃时弹出提示", checked = bl("world_info_overflow_alert", false), onCheckedChange = { wi["world_info_overflow_alert"] = it })
        Spacer(Modifier.height(24.dp))
    }
}

// ── 共用小件 ─────────────────────────────────────────────────────────────────
@Composable
private fun ServiceOffline() {
    Text(
        "本地服务未启动。启动服务后可读写真实世界书。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun LoadingRow(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
