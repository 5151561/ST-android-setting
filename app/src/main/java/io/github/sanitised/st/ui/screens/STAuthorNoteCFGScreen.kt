package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────
// 20 · 作者注 & CFG (AuthorNoteCFGScreen)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun STAuthorNoteCFGScreen(
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf("note") }
    var cfgValue by remember { mutableStateOf(1.3f) }
    
    var positivePrompt by remember { mutableStateOf("细腻的心理活动，带有一丝犹豫，多用潜台词描写。") }
    var negativePrompt by remember { mutableStateOf("不要写粗鲁的词，不要快速推平剧情，不要抢玩家台词。") }
    var authorNote by remember { mutableStateOf("[场景：雨夜咖啡馆。风格：慢节奏、细腻动作、轻微暧昧。避免突然跳出角色。]") }

    var cascadeChips by remember {
        mutableStateOf(
            listOf(
                CascadeChipState("慢节奏", true),
                CascadeChipState("细腻动作", true),
                CascadeChipState("忽视提示词", false),
                CascadeChipState("强感情", false)
            )
        )
    }

    var activeSheet by remember { mutableStateOf<String?>(null) }
    var tempEditText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val cfgDesc = remember(cfgValue) {
        when {
            cfgValue < 1.0f -> "保守严谨 (适合逻辑或事实问答，生成不易乱码)"
            cfgValue >= 1.0f && cfgValue < 1.4f -> "平衡采样 (日常角色扮演推荐，兼顾逻辑与性格特征)"
            cfgValue >= 1.4f && cfgValue < 1.8f -> "创意发散 (情节张力大，文字更具小说描绘感)"
            else -> "混沌创作 (AI 情感表达极度充沛，可能产生循环乱码)"
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = STThemeBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            STTopHeader(
                title = "作者注 & CFG",
                leading = {
                    STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                },
                actions = {
                    STIconButton(Icons.Filled.Tune, "采样调试", { onShowMessage("打开采样调试仪表盘…") })
                    STIconButton(
                        icon = Icons.Filled.Save,
                        contentDescription = "保存",
                        onClick = { onShowMessage("作者注与采样逻辑已注入级联栈") }
                    )
                },
                titleBottomPadding = 4.dp
            )

            STPreviewBanner()

            // Stateful Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                listOf("note" to "作者注", "cfg" to "CFG 控制", "cascade" to "级联控制").forEach { (id, label) ->
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
                when (tab) {
                    "note" -> {
                        // Cascade levels preview
                        PremiumCard(
                            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                        ) {
                            Text(
                                text = "注入层级级联预览",
                                style = MaterialTheme.typography.labelSmall,
                                color = STThemePrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("1. 系统提示词 (System)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("开", style = MaterialTheme.typography.bodySmall, color = STThemeTertiary, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("2. 局部作者注 (Author Note)", style = MaterialTheme.typography.bodySmall, color = STThemePrimary, fontWeight = FontWeight.Bold)
                                    Text("深度 4", style = MaterialTheme.typography.bodySmall, color = STThemePrimary, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("3. 聊天会话缓存 (History)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("就绪", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        PremiumSectionHeader(
                            title = "当前聊天作者注",
                            trailing = {
                                Text(
                                    text = "修改内容",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = STThemePrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        activeSheet = "note"
                                        tempEditText = authorNote
                                    }
                                )
                            }
                        )

                        PremiumCard(
                            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                        ) {
                            Text(
                                text = authorNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        var sw1 by remember { mutableStateOf(true) }
                        var sw2 by remember { mutableStateOf(true) }

                        STListItem(
                            headline = "随世界书触发扫描",
                            supporting = "允许作者注激活 Lorebook 中的关联关键词",
                            trailing = { Switch(checked = sw1, onCheckedChange = { sw1 = it }) },
                            onClick = { sw1 = !sw1 }
                        )

                        STListItem(
                            headline = "仅在当前单聊中生效",
                            supporting = "不将该作者注回写到角色卡元数据中",
                            trailing = { Switch(checked = sw2, onCheckedChange = { sw2 = it }) },
                            onClick = { sw2 = !sw2 }
                        )
                    }
                    "cfg" -> {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Classifier-Free Guidance (CFG)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                Text(text = String.format("%.1f", cfgValue), style = MaterialTheme.typography.titleMedium, color = STThemePrimary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = cfgValue,
                                onValueChange = { cfgValue = it },
                                valueRange = 0.5f..2.5f,
                                steps = 19
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = STThemePrimary.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, STThemePrimary.copy(alpha = 0.15f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("当前采样特征：", style = MaterialTheme.typography.labelSmall, color = STThemePrimary, fontWeight = FontWeight.Bold)
                                    Text(cfgDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }

                        PremiumSectionHeader(title = "提示词分块覆盖")

                        PremiumCard(
                            onClick = {
                                activeSheet = "positive"
                                tempEditText = positivePrompt
                            },
                            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlowStatusDot(color = STThemeTertiary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正向文风暗示 (Positive Prompt)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(positivePrompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        PremiumCard(
                            onClick = {
                                activeSheet = "negative"
                                tempEditText = negativePrompt
                            },
                            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlowStatusDot(color = STThemeError)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("反向避开词 (Negative Prompt)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(negativePrompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    "cascade" -> {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "文风微调与级联预设",
                                style = MaterialTheme.typography.titleSmall,
                                color = STThemePrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for ((index, chip) in cascadeChips.withIndex()) {
                                    FilterChip(
                                        selected = chip.active,
                                        onClick = {
                                            cascadeChips = cascadeChips.mapIndexed { i, c ->
                                                if (i == index) c.copy(active = !c.active) else c
                                            }
                                            onShowMessage("已调整合并预设: ${chip.label}")
                                        },
                                        label = { Text(chip.label) }
                                    )
                                }
                            }
                        }

                        PremiumSectionHeader(title = "高级级联属性")

                        var sw3 by remember { mutableStateOf(true) }
                        var sw4 by remember { mutableStateOf(true) }

                        STListItem(
                            headline = "强行合并角色反向提示词",
                            supporting = "自动追加当前角色卡内自带 of negative 字段",
                            trailing = { Switch(checked = sw3, onCheckedChange = { sw3 = it }) },
                            onClick = { sw3 = !sw3 }
                        )

                        STListItem(
                            headline = "对级联使用深度合并策略",
                            supporting = "深度 2 级合并，减少对上下文预算消耗",
                            trailing = { Switch(checked = sw4, onCheckedChange = { sw4 = it }) },
                            onClick = { sw4 = !sw4 }
                        )
                    }
                }
            }
        }

        // Bottom sheet for modifications
        activeSheet?.let { s ->
            ModalBottomSheet(
                onDismissRequest = { activeSheet = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    val title = when (s) {
                        "note" -> "修改作者注内容"
                        "positive" -> "修改正向文风提示词"
                        else -> "修改反向避开词"
                    }
                    val icon = when (s) {
                        "note" -> Icons.Filled.Edit
                        "positive" -> Icons.Filled.AddCircle
                        else -> Icons.Filled.RemoveCircle
                    }
                    val tint = when (s) {
                        "note" -> STThemePrimary
                        "positive" -> STThemeTertiary
                        else -> STThemeError
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { activeSheet = null }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = tempEditText,
                        onValueChange = { tempEditText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("在此输入内容…") }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            when (s) {
                                "note" -> authorNote = tempEditText
                                "positive" -> positivePrompt = tempEditText
                                "negative" -> negativePrompt = tempEditText
                            }
                            onShowMessage("已更新级联参数")
                            activeSheet = null
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
    }
}

data class CascadeChipState(
    val label: String,
    val active: Boolean
)
