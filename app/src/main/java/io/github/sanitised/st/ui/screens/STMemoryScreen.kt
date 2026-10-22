package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sanitised.st.NodeStatus

// ─────────────────────────────────────────────────────────────
// 19 · 记忆与回顾 (MemoryScreen) - Premium High-Fidelity 1:1 Restoration
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun STMemoryScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf("summary") }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // Auto summary states
    var summaryText by remember {
        mutableStateOf(
            "Aria 记得用户常点拿铁和焦糖海盐蛋糕。今天用户改点热可可，Aria 借机提到店长今早做了限量蛋糕，并询问是否加棉花糖或肉桂。"
        )
    }
    var summaryLimit by remember { mutableFloatStateOf(180f) }
    var editingSummary by remember { mutableStateOf(false) }
    var tempSummaryText by remember { mutableStateOf("") }

    // Checkpoints states
    var activeCheckpoint by remember { mutableStateOf<SimulatedCheckpoint?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Vector search matching logic
    val searchResults = remember(query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            emptyList()
        } else {
            val mockDB = listOf(
                VectorResult("焦糖海盐蛋糕, 招牌蛋糕", 0.94f, "店长今早现做的限量招牌焦糖海盐蛋糕，Aria 会偷偷留给熟客。"),
                VectorResult("店长, 老板娘, 雪", 0.82f, "六十多岁，叫雪。开店三十年。睡得早，下午一般不在店里。"),
                VectorResult("常客, 老顾客", 0.76f, "常客包括：每天读报的退休医生；一对中学生情侣；周三总迟到的小说家。"),
                VectorResult("拿铁, 咖啡", 0.71f, "店里最畅销的咖啡。选用埃塞俄比亚拼配豆，深烘，带有一丝可可与胡桃的香气。")
            )
            mockDB.filter { it.keys.lowercase().contains(q) || it.content.lowercase().contains(q) }
        }
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
                    title = "记忆与回顾",
                    leading = {
                        STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                    },
                    actions = {
                        STIconButton(
                            icon = Icons.Filled.History,
                            contentDescription = "检查点历史",
                            onClick = { toastMessage = "检查点历史就绪" }
                        )
                        IconButton(
                            onClick = { toastMessage = "本地 RAG 向量数据库处于运行就绪状态 (SQLite-VSS)" }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = "数据库状态",
                                tint = STThemePrimary
                            )
                        }
                    },
                    titleBottomPadding = 4.dp
                )

                STPreviewBanner()

                // Stateful Tabs (Summary, Vector search, checkpoints timeline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    for ((id, label) in listOf("summary" to "自动摘要", "vector" to "向量检索 (RAG)", "checkpoints" to "回滚检查点")) {
                        val sel = tab == id
                        Button(
                            onClick = { tab = id },
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
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
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
                    when (tab) {
                        "summary" -> {
                            // Smart cognitive dashboard (Memory HUD)
                            PremiumCard(
                                borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                            ) {
                                Text(
                                    text = "智能认知仪表盘 (Memory HUD)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 0.8.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = STThemePrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.024f))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("已压缩容量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("342 t", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.024f))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("触发节奏", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("每 6 轮", style = MaterialTheme.typography.titleMedium, color = STThemeError, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.024f))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("RAG 模式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                GlowStatusDot(color = STThemeTertiary)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("活跃", style = MaterialTheme.typography.titleSmall, color = STThemeTertiary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            PremiumSectionHeader(title = "自动摘要配置")

                            var autoSummaryUpdate by remember { mutableStateOf(true) }
                            STListItem(
                                headline = "自动摘要更新",
                                supporting = "在后台悄悄重构，不阻塞生成等待时间",
                                trailing = { Switch(checked = autoSummaryUpdate, onCheckedChange = { autoSummaryUpdate = it }) },
                                onClick = { autoSummaryUpdate = !autoSummaryUpdate }
                            )

                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("摘要目标字数上限", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                    Text(text = "${summaryLimit.toInt()} 字", style = MaterialTheme.typography.titleMedium, color = STThemePrimary, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Slider(
                                    value = summaryLimit,
                                    onValueChange = { summaryLimit = it },
                                    valueRange = 50f..400f,
                                    steps = 34
                                )
                            }

                            PremiumSectionHeader(
                                title = "当前合并摘要片段",
                                trailing = {
                                    Text(
                                        text = "手动编辑",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = STThemePrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            tempSummaryText = summaryText
                                            editingSummary = true
                                        }
                                    )
                                }
                            )

                            PremiumCard(
                                borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                            ) {
                                Text(
                                    text = summaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        "vector" -> {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(
                                    text = "本地知识片段向量检索 (RAG Test)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = STThemePrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.TravelExplore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (query.isEmpty()) {
                                                Text(
                                                    text = "在此输入“蛋糕”或“店长”模拟召回…",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            androidx.compose.foundation.text.BasicTextField(
                                                value = query,
                                                onValueChange = { query = it },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (query.isNotEmpty()) {
                                            IconButton(onClick = { query = "" }, modifier = Modifier.size(20.dp)) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "清除",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            PremiumSectionHeader(title = "向量库命中结果 (${searchResults.size})")

                            if (searchResults.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (res in searchResults) {
                                        PremiumCard(
                                            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = res.keys,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                    color = STThemePrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                STBadge(
                                                    label = "相似度: " + String.format("%.2f", res.similarity),
                                                    containerColor = STThemeTertiary.copy(alpha = 0.12f),
                                                    contentColor = STThemeTertiary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = res.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FindInPage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "在输入框检索，测试余弦相似度匹配",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        "checkpoints" -> {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Button(
                                    onClick = { toastMessage = "已保存当前节点快照" },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = STThemePrimary.copy(alpha = 0.15f), contentColor = STThemePrimary)
                                ) {
                                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("保存当前会话检查点")
                                }
                            }

                            PremiumSectionHeader(title = "时间线回滚点")

                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val checkpoints = listOf(
                                    SimulatedCheckpoint("cp1", "热可可分支 (加肉桂)", "刚才 · Aria 询问配方", true, "当前"),
                                    SimulatedCheckpoint("cp2", "橱窗蛋糕分支 (焦糖海盐)", "14:03 · 用户刚刚换口味", false, "回滚"),
                                    SimulatedCheckpoint("cp3", "开场白节点", "14:02 · 初始问候阶段", false, "根")
                                )
                                for (cp in checkpoints) {
                                    Surface(
                                        onClick = {
                                            if (!cp.active) {
                                                activeCheckpoint = cp
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (cp.active) STThemePrimary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.012f),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (cp.active) STThemePrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (cp.active) Icons.Filled.RadioButtonChecked else Icons.Filled.Restore,
                                                contentDescription = null,
                                                tint = if (cp.active) STThemePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cp.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = cp.time,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            STBadge(
                                                label = cp.tag,
                                                containerColor = if (cp.active) STThemePrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                                contentColor = if (cp.active) STThemePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Animated Toast HUD
            AnimatedToast(
                message = toastMessage,
                onDismiss = { toastMessage = null }
            )
        }

        // Summary Editor sheet
        if (editingSummary) {
            ModalBottomSheet(
                onDismissRequest = { editingSummary = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EditNote,
                            contentDescription = null,
                            tint = STThemePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "修改摘要内容",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { editingSummary = false }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = tempSummaryText,
                        onValueChange = { tempSummaryText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("在此输入内容…") }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            summaryText = tempSummaryText
                            toastMessage = "手动摘要修改成功"
                            editingSummary = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = STThemePrimary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("保存并写入提示词")
                    }
                }
            }
        }

        // Checkpoint Rollback Sheet
        activeCheckpoint?.let { cp ->
            ModalBottomSheet(
                onDismissRequest = { activeCheckpoint = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = STThemeError,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "会话分支回滚确认",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { activeCheckpoint = null }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = STThemeError.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, STThemeError.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "您正将当前会话回滚至 ${cp.name} 节点。此操作会永久丢弃后续的所有对话记录！建议先导出聊天。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { activeCheckpoint = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("取消")
                        }

                        Button(
                            onClick = {
                                toastMessage = "已成功回滚会话至 \"${cp.name}\""
                                activeCheckpoint = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = STThemeError, contentColor = MaterialTheme.colorScheme.onError),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.History, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("确认回滚")
                        }
                    }
                }
            }
        }
    }
}

data class VectorResult(
    val keys: String,
    val similarity: Float,
    val content: String
)

data class SimulatedCheckpoint(
    val id: String,
    val name: String,
    val time: String,
    val active: Boolean,
    val tag: String
)
