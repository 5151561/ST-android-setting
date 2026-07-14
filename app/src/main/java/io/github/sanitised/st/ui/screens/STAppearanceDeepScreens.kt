package io.github.sanitised.st.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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

// ST 内置背景库的全尺寸图 URL(对齐上游 getBackgroundPath: backgrounds/<encoded>)。
private fun bgFullUrl(baseUrl: String, file: String): String =
    "${baseUrl.trimEnd('/')}/backgrounds/" + URLEncoder.encode(file, "UTF-8").replace("+", "%20")

// 把相册选中的图片复制到 app 内部存储(chat_bg 目录),返回可供 Coil 加载的 file:// URI 字符串。
// 只保留最新一张壁纸,避免占用空间。失败返回 null。
private fun copyBackgroundToLocal(context: android.content.Context, uri: android.net.Uri): String? =
    runCatching {
        val dir = java.io.File(context.filesDir, "chat_bg").apply { mkdirs() }
        val dest = java.io.File(dir, "wallpaper_${System.currentTimeMillis()}")
        val ok = context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (!ok) return@runCatching null
        dir.listFiles()?.forEach { if (it.absolutePath != dest.absolutePath) it.delete() }
        android.net.Uri.fromFile(dest).toString()
    }.getOrNull()

private fun gradientFor(seed: Int): List<Color> {
    val grads = listOf(
        listOf(0xFF3A2D1D, 0xFF0E0B07), listOf(0xFF22354A, 0xFF0A0F16), listOf(0xFF46371F, 0xFF14100A),
        listOf(0xFF2C4226, 0xFF0B110A), listOf(0xFF3A2F4A, 0xFF110D16), listOf(0xFF3D3A30, 0xFF121110),
    )
    return grads[(seed % grads.size + grads.size) % grads.size].map { Color(it) }
}

// ── 20/21 背景管理 ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun STBackgroundsScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    chatBackground: String = "",
    onChatBackgroundChanged: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val running = status.state == NodeState.RUNNING
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var backgrounds by remember { mutableStateOf<List<String>>(emptyList()) }
    var reloadKey by remember { mutableStateOf(0) }
    var actionsFor by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val stored = copyBackgroundToLocal(context, uri)
            if (stored != null) {
                onChatBackgroundChanged(stored)
                onShowMessage("已设置聊天背景")
            } else {
                onShowMessage("图片读取失败")
            }
        }
    }

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
            STIconButton(Icons.Filled.AddPhotoAlternate, "从相册选择", onClick = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        }
    ) {
        // 当前壁纸:相册壁纸为本地保存,不依赖服务,故始终显示。
        P0SectionHeader("当前壁纸")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (chatBackground.isBlank()) {
                Text("未设置背景（聊天使用纯色）", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AsyncImage(
                    model = chatBackground,
                    contentDescription = "当前背景",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(RoundedCornerShape(16.dp))
                )
            }
        }
        STListItem(
            headline = "从相册选择图片",
            supporting = "选中后复制到本地，作为聊天全局背景",
            leading = { STTileIcon(Icons.Filled.AddPhotoAlternate) },
            divider = chatBackground.isNotBlank(),
            onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        )
        if (chatBackground.isNotBlank()) {
            STListItem(
                headline = "移除背景",
                supporting = "恢复纯色聊天背景",
                leading = { STTileIcon(Icons.Filled.Wallpaper) },
                onClick = { onChatBackgroundChanged(""); onShowMessage("已移除聊天背景") }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        when {
            !running -> Text(
                "启动服务后可浏览 SillyTavern 内置背景库；相册壁纸为本地保存，不受服务状态影响。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            loading -> Loading("正在读取背景…")
            else -> {
                P0SectionHeader("内置背景库", trailing = {
                    Text("${backgrounds.size} 张", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                })
                if (backgrounds.isEmpty()) {
                    Text("暂无背景图。点击右上「从相册选择」添加本地壁纸。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    backgrounds.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { bg ->
                                BackgroundTile(
                                    bg, baseUrl, modifier = Modifier.weight(1f),
                                    onClick = { onChatBackgroundChanged(bgFullUrl(baseUrl, bg)); onShowMessage("已设为聊天背景「$bg」") },
                                    onLongClick = { actionsFor = bg }
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Text(
                    "点击背景设为聊天全局背景；长按可重命名或删除。内置背景在服务运行时可用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
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
            P0SheetItem(Icons.Filled.Wallpaper, "设为聊天背景") { actionsFor = null; onChatBackgroundChanged(bgFullUrl(baseUrl, bg)); onShowMessage("已设为聊天背景「$bg」") }
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


// ── 23 聊天与消息 ────────────────────────────────────────────────────────────
@Composable
fun STChatBehaviorScreen(
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
        actions = { STIconButton(Icons.Filled.Check, "保存", { save() }) }
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
