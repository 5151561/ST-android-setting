package io.github.sanitised.st.ui.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.TavernCoreClient
import kotlinx.coroutines.launch
import java.net.URLEncoder

// ─────────────────────────────────────────────────────────────────────────────
// P0 · 背景 / 主题 / 聊天与消息（设计稿 screens/Appearance.jsx，画板 20–23）
// 接真实后端：背景走新增的 /api/backgrounds/*；主题/CSS/聊天行为走 getSettings/
// saveSettings 的 power_user。颜色细调显示真实当前颜色（编辑需色板，后续）。
// ─────────────────────────────────────────────────────────────────────────────

private fun pmap(value: Any?): MutableMap<String, Any?> =
    (value as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v }?.toMutableMap() ?: mutableMapOf()

private fun bgThumbUrl(baseUrl: String, file: String): String =
    "${baseUrl.trimEnd('/')}/thumbnail?type=bg&file=" + URLEncoder.encode(file, "UTF-8").replace("+", "%20")

private fun gradientFor(seed: Int): List<Color> {
    val grads = listOf(
        listOf(0xFF3A2D1D, 0xFF0E0B07), listOf(0xFF22354A, 0xFF0A0F16), listOf(0xFF46371F, 0xFF14100A),
        listOf(0xFF2C4226, 0xFF0B110A), listOf(0xFF3A2F4A, 0xFF110D16), listOf(0xFF3D3A30, 0xFF121110),
    )
    return grads[(seed % grads.size + grads.size) % grads.size].map { Color(it) }
}

/** 解析 CSS 颜色（#rrggbb / #rgb / rgba(...) / rgb(...)）为 Compose Color，失败返回 null。 */
private fun parseCssColor(s: String): Color? {
    val t = s.trim()
    try {
        if (t.startsWith("#")) {
            val hex = t.substring(1)
            return when (hex.length) {
                6 -> Color(("FF$hex").toLong(16))
                8 -> Color(hex.toLong(16))
                3 -> Color(("FF" + hex.map { "$it$it" }.joinToString("")).toLong(16))
                else -> null
            }
        }
        if (t.startsWith("rgb")) {
            val nums = t.substringAfter("(").substringBefore(")").split(",").map { it.trim() }
            if (nums.size >= 3) {
                val r = nums[0].toFloat() / 255f; val g = nums[1].toFloat() / 255f; val b = nums[2].toFloat() / 255f
                val a = if (nums.size >= 4) nums[3].toFloat() else 1f
                return Color(r, g, b, a)
            }
        }
    } catch (_: Exception) {}
    return null
}

private val THEME_COLOR_FIELDS = listOf(
    "主色 / 强调" to "main_text_color",
    "引用文本" to "quote_text_color",
    "斜体 / 动作" to "italics_text_color",
    "AI 气泡" to "bot_mes_blur_tint_color",
    "用户气泡" to "user_mes_blur_tint_color",
    "背景叠加" to "blur_tint_color",
)

// ── 20/21 背景管理 ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PrototypeBackgroundsScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val running = status.state == NodeState.RUNNING
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var backgrounds by remember { mutableStateOf<List<String>>(emptyList()) }
    var reloadKey by remember { mutableStateOf(0) }
    var actionsFor by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(running, baseUrl, reloadKey) {
        if (!running) { loading = false; return@LaunchedEffect }
        loading = true
        runCatching { TavernCoreClient(baseUrl).listBackgrounds() }
            .onSuccess { backgrounds = it }
            .onFailure { onShowMessage(it.message ?: "背景加载失败") }
        loading = false
    }

    P0Scaffold(
        title = "聊天背景",
        onBack = onBack,
        actions = {
            PrototypeIconButton(Icons.Filled.Search, "搜索", { onShowMessage("搜索背景") })
            PrototypeIconButton(Icons.Filled.AddPhotoAlternate, "上传", { onShowMessage("上传背景：在文件管理器中选择图片") })
        }
    ) {
        if (!running) { Offline("启动服务后可读写真实背景。"); return@P0Scaffold }
        if (loading) { Loading("正在读取背景…"); return@P0Scaffold }
        P0ToggleRow(icon = Icons.Filled.Wallpaper, title = "锁定当前聊天背景", sub = "锁定后，切换全局背景不影响当前聊天", initialOn = false)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        P0SectionHeader("全部背景", trailing = {
            Text("${backgrounds.size} 张", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        })
        if (backgrounds.isEmpty()) {
            Text("暂无背景图。点击右上「上传」添加。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            backgrounds.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { bg ->
                        BackgroundTile(bg, baseUrl, modifier = Modifier.weight(1f), onClick = { onShowMessage("应用背景「$bg」（下次进入聊天生效）") }, onLongClick = { actionsFor = bg })
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Text(
            "长按背景可重命名或删除。应用背景在进入聊天时生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }

    val bg = actionsFor
    if (bg != null) {
        ModalBottomSheet(onDismissRequest = { actionsFor = null }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(gradientFor(bg.hashCode())))) {
                    AsyncImage(model = bgThumbUrl(baseUrl, bg), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)))
                }
                Spacer(Modifier.size(14.dp))
                Text(bg, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            P0SheetItem(Icons.Filled.Wallpaper, "设为全局背景") { actionsFor = null; onShowMessage("已选择「$bg」，进入聊天生效") }
            P0SheetItem(Icons.Filled.DriveFileRenameOutline, "重命名（追加 _1）") {
                actionsFor = null
                scope.launch {
                    runCatching {
                        val dot = bg.lastIndexOf('.')
                        val newName = if (dot > 0) bg.substring(0, dot) + "_1" + bg.substring(dot) else bg + "_1"
                        TavernCoreClient(baseUrl).renameBackground(bg, newName)
                    }.onSuccess { onShowMessage("已重命名"); reloadKey++ }.onFailure { onShowMessage(it.message ?: "重命名失败") }
                }
            }
            P0SheetItem(Icons.Filled.Delete, "删除", danger = true) {
                actionsFor = null
                scope.launch {
                    runCatching { TavernCoreClient(baseUrl).deleteBackground(bg) }
                        .onSuccess { onShowMessage("已删除「$bg」"); reloadKey++ }
                        .onFailure { onShowMessage(it.message ?: "删除失败") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackgroundTile(bg: String, baseUrl: String, modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = modifier.aspectRatio(16f / 10f).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(gradientFor(bg.hashCode())))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(model = bgThumbUrl(baseUrl, bg), contentDescription = bg, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x8C000000))))
                .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(bg.substringBeforeLast('.'), style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── 22 界面主题 ──────────────────────────────────────────────────────────────
@Composable
fun PrototypeThemeScreen(
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
    val pu = remember { mutableStateMapOf<String, Any?>() }

    LaunchedEffect(running, baseUrl) {
        if (!running) { loading = false; return@LaunchedEffect }
        loading = true
        runCatching {
            val s = TavernCoreClient(baseUrl).getSettings()
            rawSettings = s; pu.clear(); pu.putAll(pmap(s["power_user"]))
        }.onFailure { onShowMessage(it.message ?: "设置加载失败") }
        loading = false
    }

    fun save(extra: (MutableMap<String, Any?>) -> Unit = {}) {
        scope.launch {
            runCatching {
                val out = rawSettings.toMutableMap()
                val puOut = pmap(rawSettings["power_user"])
                pu.forEach { (k, v) -> puOut[k] = v }
                extra(puOut)
                out["power_user"] = puOut
                TavernCoreClient(baseUrl).saveSettings(out)
                rawSettings = out
            }.onFailure { onShowMessage(it.message ?: "保存失败") }
        }
    }

    @Suppress("UNCHECKED_CAST")
    val themes = (pu["themes"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("name")?.toString() } ?: emptyList()
    val currentTheme = pu["theme"]?.toString() ?: ""

    P0Scaffold(
        title = "界面主题",
        onBack = onBack,
        actions = { PrototypeIconButton(Icons.Filled.Check, "保存", { save(); onShowMessage("主题设置已保存") }) }
    ) {
        if (!running) { Offline("启动服务后可读写真实主题。"); return@P0Scaffold }
        if (loading) { Loading("正在读取设置…"); return@P0Scaffold }

        P0SectionHeader("已安装主题", trailing = { Text("${themes.size} 个", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) })
        if (themes.isEmpty()) {
            Text("未读取到主题列表。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        themes.forEachIndexed { i, name ->
            val isCurrent = name == currentTheme
            PrototypeListItem(
                headline = name,
                supporting = if (isCurrent) "正在使用" else "点击应用",
                leading = {
                    Box(modifier = Modifier.size(width = 56.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                },
                trailing = {
                    if (isCurrent) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("当前", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                divider = i < themes.lastIndex,
                onClick = { if (!isCurrent) { pu["theme"] = name; save(); onShowMessage("已应用「$name」") } }
            )
        }

        P0SectionHeader("颜色细调")
        THEME_COLOR_FIELDS.forEachIndexed { i, (label, key) ->
            val value = pu[key]?.toString().orEmpty()
            val color = parseCssColor(value)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onShowMessage("「$label」当前值：${value.ifBlank { "未设置" }}（色板编辑后续）") }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9.dp)))
                Spacer(Modifier.size(14.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.size(8.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (i < THEME_COLOR_FIELDS.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        }

        P0SectionHeader("外观")
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                Text("头像样式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                P0Seg(options = listOf("圆形", "圆角", "方形"), selectedIndex = ((pu["avatar_style"] as? Number)?.toInt() ?: 0).coerceIn(0, 2), onSelect = { pu["avatar_style"] = it })
            }
            Column(Modifier.weight(1f)) {
                Text("聊天样式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                P0Seg(options = listOf("气泡", "扁平", "文档"), selectedIndex = ((pu["chat_display"] as? Number)?.toInt() ?: 0).coerceIn(0, 2), onSelect = { pu["chat_display"] = it })
            }
        }
        P0Slider("背景模糊强度", value = ((pu["blur_strength"] as? Number)?.toFloat() ?: 10f), onValueChange = { pu["blur_strength"] = it.toInt() }, valueRange = 0f..20f)
        P0Slider("界面字号缩放 (%)", value = (((pu["font_scale"] as? Number)?.toFloat() ?: 1f) * 100f), onValueChange = { pu["font_scale"] = (it / 100f) }, valueRange = 80f..150f)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        P0Field(label = "自定义 CSS", value = pu["custom_css"]?.toString().orEmpty(), onValueChange = { pu["custom_css"] = it }, multiline = true, minLines = 4, mono = true, hint = "应用于整个界面")
        Spacer(Modifier.height(24.dp))
    }
}

// ── 23 聊天与消息 ────────────────────────────────────────────────────────────
@Composable
fun PrototypeChatBehaviorScreen(
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
    val pu = remember { mutableStateMapOf<String, Any?>() }
    val autoCont = remember { mutableStateMapOf<String, Any?>() }

    LaunchedEffect(running, baseUrl) {
        if (!running) { loading = false; return@LaunchedEffect }
        loading = true
        runCatching {
            val s = TavernCoreClient(baseUrl).getSettings()
            rawSettings = s; pu.clear(); pu.putAll(pmap(s["power_user"]))
            autoCont.clear(); autoCont.putAll(pmap(pu["auto_continue"]))
        }.onFailure { onShowMessage(it.message ?: "设置加载失败") }
        loading = false
    }

    fun bl(key: String, def: Boolean): Boolean = (pu[key] as? Boolean) ?: def
    fun num(key: String, def: Float): Float = (pu[key] as? Number)?.toFloat() ?: def

    fun save() {
        scope.launch {
            runCatching {
                val out = rawSettings.toMutableMap()
                val puOut = pmap(rawSettings["power_user"])
                pu.forEach { (k, v) -> puOut[k] = v }
                puOut["auto_continue"] = autoCont.toMap()
                out["power_user"] = puOut
                TavernCoreClient(baseUrl).saveSettings(out)
                rawSettings = out
            }.onSuccess { onShowMessage("聊天设置已保存"); onBack() }.onFailure { onShowMessage(it.message ?: "保存失败") }
        }
    }

    P0Scaffold(
        title = "聊天与消息",
        subtitle = "设置 · 行为",
        onBack = onBack,
        actions = { PrototypeIconButton(Icons.Filled.Check, "保存", { save() }) }
    ) {
        if (!running) { Offline("启动服务后可读写真实聊天设置。"); return@P0Scaffold }
        if (loading) { Loading("正在读取设置…"); return@P0Scaffold }

        P0SectionHeader("流式输出")
        P0ToggleRow(title = "平滑流式", sub = "逐字渲染，而不是按网络分块跳动", checked = bl("smooth_streaming", false), onCheckedChange = { pu["smooth_streaming"] = it })
        P0Slider("渲染速度", value = num("smooth_streaming_speed", 50f), onValueChange = { pu["smooth_streaming_speed"] = it.toInt() }, valueRange = 0f..100f)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        P0SectionHeader("自动化")
        P0ToggleRow(title = "自动继续", sub = "回复疑似被截断时自动追加生成", checked = (autoCont["enabled"] as? Boolean) ?: false, onCheckedChange = { autoCont["enabled"] = it })
        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            P0Stepper(label = "目标长度", value = (autoCont["target_length"] as? Number)?.toInt() ?: 400, onValueChange = { autoCont["target_length"] = it }, suffix = "t", hint = "自动继续到至少这么长", step = 50, min = 0)
        }
        P0ToggleRow(title = "自动滚动到新消息", checked = bl("auto_scroll_chat_to_bottom", true), onCheckedChange = { pu["auto_scroll_chat_to_bottom"] = it })
        P0ToggleRow(title = "自动加载上次聊天", sub = "启动后直接回到最近的对话", checked = bl("auto_load_chat", false), onCheckedChange = { pu["auto_load_chat"] = it })
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        P0SectionHeader("消息显示")
        P0ToggleRow(title = "显示时间戳", checked = bl("timestamps_enabled", true), onCheckedChange = { pu["timestamps_enabled"] = it })
        P0ToggleRow(title = "显示消息 token 数", checked = bl("message_token_count_enabled", false), onCheckedChange = { pu["message_token_count_enabled"] = it })
        P0ToggleRow(title = "显示模型图标", sub = "在消息时间戳旁标注生成模型", checked = bl("timestamp_model_icon", false), onCheckedChange = { pu["timestamp_model_icon"] = it })
        P0ToggleRow(title = "自动修正 Markdown", sub = "修复生成内容里不规范的 Markdown", checked = bl("auto_fix_generated_markdown", false), onCheckedChange = { pu["auto_fix_generated_markdown"] = it })
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        P0SectionHeader("输入")
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(Modifier.fillMaxWidth(0.6f)) {
                Text("回车键行为", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                P0Seg(options = listOf("换行", "发送"), selectedIndex = if (((pu["send_on_enter"] as? Number)?.toInt() ?: 0) > 0) 1 else 0, onSelect = { pu["send_on_enter"] = it })
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        P0SectionHeader("上下文处理")
        P0ToggleRow(title = "始终保留示例消息", sub = "上下文吃紧时也不丢弃对话示例", checked = bl("pin_examples", false), onCheckedChange = { pu["pin_examples"] = it })
        P0ToggleRow(title = "永不发送示例消息", sub = "对话示例只供参考，不进入提示词", checked = bl("strip_examples", false), onCheckedChange = { pu["strip_examples"] = it })
        P0ToggleRow(title = "折叠连续换行", sub = "发送前把多个空行压缩成一个", checked = bl("collapse_newlines", false), onCheckedChange = { pu["collapse_newlines"] = it })
        Spacer(Modifier.height(24.dp))
    }
}

// ── 共用小件 ─────────────────────────────────────────────────────────────────
@Composable
private fun Offline(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
}

@Composable
private fun Loading(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
