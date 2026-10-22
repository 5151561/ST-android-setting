package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────
// 21 · 扩展中心 (ExtensionsScreen)
// ─────────────────────────────────────────────────────────────
@Composable
fun STExtensionsScreen(
    onBack: () -> Unit,
    onOpenQuickReplies: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFilter by remember { mutableStateOf("all") }
    var activeExtras by remember { mutableStateOf(true) }

    var extensionsList by remember {
        mutableStateOf(
            listOf(
                SimulatedExtension("qr", Icons.Filled.Bolt, "快捷回复", "全局/单聊/角色三层触发", true, "chat"),
                SimulatedExtension("mem", Icons.Filled.Memory, "记忆与回顾", "RAG 检索及自动会话回滚机制", true, "data"),
                SimulatedExtension("tts", Icons.Filled.RecordVoiceOver, "TTS / STT 语音", "多音色配音与本地语音输入接口", true, "media"),
                SimulatedExtension("sd", Icons.Filled.Palette, "Stable Diffusion 绘图", "SD 绘图管道自动描述生成", false, "media"),
                SimulatedExtension("trans", Icons.Filled.Translate, "实时翻译扩展", "集成 Google/Deepl 离线词包自动互译", false, "chat")
            )
        )
    }

    val visibleExt = remember(activeFilter, extensionsList) {
        extensionsList.filter { activeFilter == "all" || it.cat == activeFilter }
    }

    Surface(modifier = modifier.fillMaxSize(), color = STThemeBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            STTopHeader(
                title = "扩展中心",
                leading = {
                    STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                },
                actions = {
                    STIconButton(Icons.Filled.CloudDownload, "扩展商店", { onShowMessage("打开扩展应用商店…") })
                    STIconButton(Icons.Filled.Settings, "设置", { onShowMessage("扩展中心高级首选项功能开发中") })
                },
                titleBottomPadding = 4.dp
            )

            STPreviewBanner()

            // Horizontal Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "all" to "全部扩展",
                    "chat" to "聊天增强",
                    "media" to "多媒体",
                    "data" to "数据层"
                ).forEach { (id, label) ->
                    val sel = activeFilter == id
                    FilterChip(
                        selected = sel,
                        onClick = { activeFilter = id },
                        label = { Text(label) }
                    )
                }
            }

            // Extras API status card (Premium Translucent Card style)
            PremiumCard(
                borderColor = if (activeExtras) STThemeTertiary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlowStatusDot(color = if (activeExtras) STThemeTertiary else MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SillyTavern Extras API",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(checked = activeExtras, onCheckedChange = { activeExtras = it })
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (activeExtras) "Extras API 已启用（原型预览，实际状态以后端为准）" else "Extras 离线。绘图、语音朗读、图片识别等依赖项将暂时无法使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            PremiumSectionHeader("已安装的扩展")

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (ext in visibleExt) {
                    Surface(
                        onClick = {
                            if (ext.id == "qr") {
                                onOpenQuickReplies()
                            } else {
                                onShowMessage("正在配置 '${ext.title}' 扩展…")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.024f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (ext.active) STThemePrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (ext.active) STThemePrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                contentColor = if (ext.active) STThemePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(imageVector = ext.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = ext.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (ext.active) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        GlowStatusDot(color = STThemeTertiary)
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = ext.sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Switch(
                                    checked = ext.active,
                                    onCheckedChange = { active ->
                                        extensionsList = extensionsList.map {
                                            if (it.id == ext.id) it.copy(active = active) else it
                                        }
                                        onShowMessage("${ext.title} 已" + (if (active) "启用" else "禁用"))
                                    }
                                )
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 22 · 快捷回复 (QuickReplyScreen)
// ─────────────────────────────────────────────────────────────
@Composable
fun STQuickReplyScreen(
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var scope by remember { mutableStateOf("chat") }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    
    var repliesList by remember {
        mutableStateOf(
            listOf(
                SimulatedReply("让 Aria 继续", "/sendas name=\"Aria\" ...", "促使 AI 立即追加生成多一轮动作描述"),
                SimulatedReply("改写上一句", "/rewrite last", "删掉最后回复并立刻发起新生成指令"),
                SimulatedReply("转场到夜晚", "/narrate \"夜色慢慢变浓…\"", "快捷叙述，推进物理场景时间线")
            )
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = STThemeBg) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                STTopHeader(
                    title = "快捷回复配置",
                    leading = {
                        STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                    },
                    actions = {
                        STIconButton(Icons.Filled.FileUpload, "导出", { toastMessage = "已导出快捷回复配置" })
                        STIconButton(Icons.Filled.Add, "新增", { toastMessage = "新增快捷指令宏成功" })
                    },
                    titleBottomPadding = 4.dp
                )

                STPreviewBanner()

                // Stateful Scope Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    listOf("global" to "全局宏", "chat" to "单聊特有", "character" to "当前角色").forEach { (id, label) ->
                        val sel = scope == id
                        Button(
                            onClick = { scope = id },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .padding(horizontal = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sel) STThemePrimary.copy(alpha = 0.12f) else Color.Transparent,
                                contentColor = if (sel) STThemePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    // REAL-TIME CHAT COMPOSER SIMULATION PILL PREVIEWER (Composer HUD) - Premium restored 1:1!
                    PremiumCard(
                        borderColor = STThemeTertiary.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = "聊天视图输入栏效果预览 (Composer HUD)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = STThemeTertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Horizontal scroll pills of replies
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    repliesList.forEach { r ->
                                        Surface(
                                            onClick = { toastMessage = "快捷发送指令：\"${r.label}\"" },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Bolt,
                                                    contentDescription = null,
                                                    tint = STThemePrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = r.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = STThemePrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Composer input bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.024f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.CenterStart,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        ) {
                                            Text(
                                                text = "在此测试点击上方气泡发送指令…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.size(34.dp),
                                        shape = CircleShape,
                                        color = STThemePrimary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    PremiumSectionHeader(
                        title = "当前集合中指令宏 (${repliesList.size})",
                        trailing = {
                            Text(
                                text = "重置默认",
                                style = MaterialTheme.typography.labelMedium,
                                color = STThemePrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    repliesList = listOf(
                                        SimulatedReply("让 Aria 继续", "/sendas name=\"Aria\" ...", "促使 AI 立即追加生成多一轮动作描述"),
                                        SimulatedReply("改写上一句", "/rewrite last", "删掉最后回复并立刻发起新生成指令"),
                                        SimulatedReply("转场到夜晚", "/narrate \"夜色慢慢变浓…\"", "快捷叙述，推进物理场景时间线")
                                    )
                                    toastMessage = "已重置为出厂快捷宏"
                                }
                            )
                        }
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for ((index, reply) in repliesList.withIndex()) {
                            PremiumCard(
                                borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                        contentColor = STThemePrimary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.SmartButton,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = reply.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = reply.macro,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            repliesList = repliesList.filterIndexed { i, _ -> i != index }
                                            toastMessage = "指令宏已安全删除"
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "删除",
                                            tint = STThemeError,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = reply.desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            AnimatedToast(
                message = toastMessage,
                onDismiss = { toastMessage = null }
            )
        }
    }
}

data class SimulatedReply(
    val label: String,
    val macro: String,
    val desc: String
)

data class SimulatedExtension(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val sub: String,
    val active: Boolean,
    val cat: String
)
