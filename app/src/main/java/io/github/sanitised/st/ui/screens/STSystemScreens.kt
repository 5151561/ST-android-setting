package io.github.sanitised.st.ui.screens

import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import io.github.sanitised.st.api.PersonaSaveRequest
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.border
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.BackupOperationAnchor
import io.github.sanitised.st.CustomOperationAnchor
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.UpdateChannel
import io.github.sanitised.st.api.ChatBackupSummary
import io.github.sanitised.st.api.ConnectionProfile
import io.github.sanitised.st.api.PersonaProfile
import io.github.sanitised.st.api.SecretProviderState
import io.github.sanitised.st.api.SettingsSnapshot
import io.github.sanitised.st.api.ConnectionTestResult
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.api.WorldInfoSummary
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer

@Composable
fun STWorldInfoScreen(
    status: NodeStatus,
    baseUrl: String,
    onStartService: () -> Unit,
    onShowMessage: (String) -> Unit,
    onOpenManage: () -> Unit = {},
    onOpenBook: (String) -> Unit = {},
    onOpenGlobalSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val openDrawer = LocalSTOpenDrawer.current
    var loading by remember { mutableStateOf(false) }
    var books by remember { mutableStateOf<List<WorldInfoSummary>>(emptyList()) }
    var firstBook by remember { mutableStateOf<WorldInfoBook?>(null) }
    val running = status.state == NodeState.RUNNING

    LaunchedEffect(running, baseUrl) {
        if (!running) return@LaunchedEffect
        loading = true
        runCatching {
            val client = TavernCoreClient(baseUrl)
            val list = client.listWorldInfos()
            books = list
            firstBook = list.firstOrNull()?.let { client.getWorldInfo(it.name) }
        }.onFailure { onShowMessage(it.message ?: "世界书加载失败") }
        loading = false
    }

    STRoot(modifier = modifier) {
        STTopHeader(
            title = "世界书",
            subtitle = "动态注入的设定。条目根据关键词触发或常驻。",
            leading = {
                STIconButton(Icons.Filled.Menu, "打开抽屉", openDrawer)
            },
            actions = {
                STIconButton(Icons.Filled.Tune, "管理", onOpenManage)
                STIconButton(Icons.Filled.Settings, "全局设置", onOpenGlobalSettings)
            }
        )
        if (!running) {
            STSystemInfoCard("本地服务未启动", "启动服务后会加载真实世界书。") {
                Button(onClick = onStartService) { Text("启动服务") }
            }
        } else if (loading) {
            STLoadingCard("正在读取世界书…")
        } else if (books.isEmpty()) {
            STSystemInfoCard("暂无世界书", "在 SillyTavern 中尚未创建世界书。")
        } else {
            STSectionHeader(
                title = "本地世界书",
                trailing = {
                    Text("${books.size} 本已加载", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            )
            STListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                books.forEachIndexed { index, book ->
                    STListItem(
                        headline = book.name,
                        supporting = "包含设定与条目",
                        leading = {
                            STTileIcon(
                                icon = Icons.Filled.AutoStories,
                                tint = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        },
                        trailing = { Switch(checked = true, onCheckedChange = null) },
                        divider = index != books.lastIndex,
                        onClick = { onOpenBook(book.id) }
                    )
                }
            }
            STSectionHeader(title = "常用条目预览")
            val entries = firstBook?.entries.orEmpty()
            if (entries.isNotEmpty()) {
                entries.take(4).forEach { entry ->
                    LoreEntryPreview(
                        keys = entry.keys.joinToString(", ").ifBlank { entry.comment.ifBlank { "未命名条目" } },
                        content = entry.content,
                        constant = entry.constant,
                        selective = entry.selective,
                        order = entry.order
                    )
                }
            } else {
                STSystemInfoCard("暂无世界书条目", "当绑定并启用世界书时，符合触发关键词的条目会在此预览。")
            }
        }
    }
}

@Composable
fun STPersonaScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var personas by remember { mutableStateOf<List<PersonaProfile>>(emptyList()) }
    val running = status.state == NodeState.RUNNING

    fun loadPersonas() {
        if (running) {
            scope.launch {
                try {
                    val result = TavernCoreClient(baseUrl).listPersonas()
                    personas = result
                } catch (e: Exception) {
                    onShowMessage(e.message ?: "扮演者加载失败")
                }
            }
        }
    }

    LaunchedEffect(running, baseUrl) {
        loadPersonas()
    }

    val activePersonas = personas.filter { it.isDefault }
    val otherPersonas = personas.filter { !it.isDefault }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
            ) {
                STTopHeader(
                    title = "扮演者",
                    leading = {
                        STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                    },
                    actions = {
                        STIconButton(Icons.Filled.AutoFixHigh, "AI 生成", { onShowMessage("AI 生成扮演者功能开发中") })
                        STIconButton(Icons.Filled.Add, "新建", { onShowMessage("新建扮演者功能开发中") })
                    },
                    titleBottomPadding = 4.dp
                )
                Text(
                    text = "模型会用“你”扮演的身份来回应。可以为不同角色绑定不同扮演者。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )

                if (!running) {
                    STSystemInfoCard("本地服务未启动", "启动 SillyTavern 服务后可加载和管理扮演者设定。")
                } else if (personas.isEmpty()) {
                    STSystemInfoCard("暂无扮演者设定", "点击右下角“新建”以添加您在聊天中扮演的角色。")
                } else {
                    STSectionHeader("当前激活")
                    activePersonas.forEach { persona ->
                        STPersonaRow(persona = persona, active = true)
                    }

                    if (otherPersonas.isNotEmpty()) {
                        STSectionHeader("所有扮演者", trailing = {
                            Text("管理绑定", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        })
                        otherPersonas.forEach { persona ->
                            STPersonaRow(
                                persona = persona,
                                active = false,
                                onClick = {
                                    scope.launch {
                                        try {
                                            TavernCoreClient(baseUrl).savePersona(
                                                PersonaSaveRequest(
                                                    avatar = persona.avatar,
                                                    name = persona.name,
                                                    title = persona.title,
                                                    description = persona.description,
                                                    makeDefault = true
                                                )
                                            )
                                            onShowMessage("已切换默认扮演者：${persona.name}")
                                            loadPersonas()
                                        } catch (e: Exception) {
                                            onShowMessage(e.message ?: "切换扮演者失败")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(104.dp))
            }

            ExtendedFloatingActionButton(
                onClick = { onShowMessage("新建扮演者功能开发中") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新建") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun STAISettingsScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSettingsChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var presetLibrary by remember { mutableStateOf<io.github.sanitised.st.api.PresetLibrary?>(null) }
    val running = status.state == NodeState.RUNNING
    var settings by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }

    var temp by remember { mutableFloatStateOf(1.0f) }
    var topP by remember { mutableFloatStateOf(1.0f) }
    var topK by remember { mutableFloatStateOf(0f) }
    var minP by remember { mutableFloatStateOf(0f) }
    var freqPenalty by remember { mutableFloatStateOf(0f) }
    var presPenalty by remember { mutableFloatStateOf(0f) }
    var streamingEnabled by remember { mutableStateOf(true) }

    fun loadFromSettings(s: Map<String, Any?>) {
        val oai = (s["oai_settings"] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()
        temp = oai.floatValue("temp", 1.0f)
        topP = oai.floatValue("top_p_openai", 1.0f)
        topK = oai.floatValue("top_k_openai", 0f).coerceIn(0f, 1f)
        minP = oai.floatValue("min_p_openai", 0f)
        freqPenalty = oai.floatValue("frequency_penalty_openai", 0f)
        presPenalty = oai.floatValue("presence_penalty_openai", 0f)
        streamingEnabled = when (val v = oai["stream_openai"]) {
            is Boolean -> v
            else -> true
        }
    }

    fun loadPresets() {
        if (running) {
            scope.launch {
                try {
                    val result = TavernCoreClient(baseUrl).getPresetLibrary()
                    presetLibrary = result
                } catch (e: Exception) {
                    onShowMessage(e.message ?: "加载采样预设失败")
                }
            }
        }
    }

    LaunchedEffect(running, baseUrl) {
        loadPresets()
        if (running) {
            runCatching {
                val s = TavernCoreClient(baseUrl).getSettings()
                settings = s
                loadFromSettings(s)
            }.onFailure { onShowMessage(it.message ?: "加载设置失败") }
        }
    }

    STBackRoot(title = "AI 设置", onBack = onBack, modifier = modifier, actions = {
        STIconButton(Icons.Filled.RestartAlt, "重置", {
            if (running) {
                scope.launch {
                    runCatching {
                        val s = TavernCoreClient(baseUrl).getSettings()
                        settings = s
                        loadFromSettings(s)
                        onShowMessage("已从服务器重新加载设置")
                    }.onFailure { onShowMessage("重置失败：${it.message}") }
                }
            } else {
                onShowMessage("服务未运行，无法重置")
            }
        })
        STIconButton(Icons.Filled.Save, "保存", {
            if (running) {
                scope.launch {
                    runCatching {
                        val updated = settings.toMutableMap()
                        val oai = ((updated["oai_settings"] as? Map<*, *>)
                            ?.entries?.associate { (k, v) -> k.toString() to v }
                            ?: emptyMap()).toMutableMap()
                        oai["temp"] = temp
                        oai["top_p_openai"] = topP
                        oai["top_k_openai"] = topK
                        oai["min_p_openai"] = minP
                        oai["frequency_penalty_openai"] = freqPenalty
                        oai["presence_penalty_openai"] = presPenalty
                        oai["stream_openai"] = streamingEnabled
                        updated["oai_settings"] = oai
                        TavernCoreClient(baseUrl).saveSettings(updated)
                        settings = updated
                        onSettingsChanged()
                        onShowMessage("采样参数已保存至后端")
                    }.onFailure { onShowMessage("保存失败：${it.message}") }
                }
            } else {
                onShowMessage("服务未运行，无法保存")
            }
        })
    }) {
        STPresetCard(
            presetLibrary = presetLibrary,
            onSelectPreset = { apiId, name ->
                scope.launch {
                    try {
                        TavernCoreClient(baseUrl).selectPreset(apiId, name)
                        onShowMessage("已切换预设：$name")
                        loadPresets()
                        runCatching {
                            val s = TavernCoreClient(baseUrl).getSettings()
                            settings = s
                            loadFromSettings(s)
                        }
                    } catch (e: Exception) {
                        onShowMessage(e.message ?: "切换预设失败")
                    }
                }
            }
        )
        STSectionHeader("提示模板", trailing = {
            Text("仅文本补全", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        })
        val instructName = presetLibrary?.categories?.firstOrNull { it.apiId == "instruct" }
            ?.presets?.firstOrNull { it.selected }?.name ?: "—"
        val contextName = presetLibrary?.categories?.firstOrNull { it.apiId == "context" }
            ?.presets?.firstOrNull { it.selected }?.name ?: "—"
        val syspromptName = presetLibrary?.categories?.firstOrNull { it.apiId == "sysprompt" }
            ?.presets?.firstOrNull { it.selected }?.name ?: "—"
        STTemplateRow(Icons.Filled.Code, "Instruct 模板", instructName, "角色名 / 系统提示遵循模型格式", toggle = true)
        STTemplateRow(Icons.Filled.Bookmarks, "上下文模板", contextName, "角色描述 + 场景 + 历史的组织方式")
        STTemplateRow(Icons.Filled.Tune, "系统提示", syspromptName, "注入到对话最前", toggle = true, checked = true)
        STTemplateRow(Icons.AutoMirrored.Filled.StickyNote2, "作者注 / 深度笔记", "未设置", "按需注入到指定深度")
        STSectionHeader("核心采样")
        STStatefulSlider("温度 Temperature", temp, 0f..2f) { temp = it }
        STStatefulSlider("Top P", topP, 0f..1f) { topP = it }
        STStatefulSlider("Top K", topK, 0f..1f) { topK = it }
        STStatefulSlider("Min P", minP, 0f..1f) { minP = it }
        STSectionHeader("重复抑制")
        STStatefulSlider("频率惩罚", freqPenalty, -2f..2f) { freqPenalty = it }
        STStatefulSlider("存在惩罚", presPenalty, -2f..2f) { presPenalty = it }
        STSectionHeader("高级 — 极少改动")
        STSwitchRow("启用流式输出", "边生成边显示", streamingEnabled) { streamingEnabled = it }
        STSwitchRow("禁止思考链泄露", "过滤掉 <think> 标签内容", true)
        STSwitchRow("DRY (动态重复抑制)", "抗循环更激进的算法", false)
        STSwitchRow("温度最后采样", "先 Top-P 再温度", false)
    }
}

@Composable
fun STMeScreen(
    autoCheckEnabled: Boolean,
    onAutoCheckChanged: (Boolean) -> Unit,
    autoOpenBrowserEnabled: Boolean,
    onAutoOpenBrowserChanged: (Boolean) -> Unit,
    isBatteryUnrestricted: Boolean,
    onOpenBatterySettings: () -> Unit,
    channel: UpdateChannel,
    onChannelChanged: (UpdateChannel) -> Unit,
    onCheckNow: () -> Unit,
    isChecking: Boolean,
    vibrationFeedback: Boolean,
    onVibrationFeedbackChanged: (Boolean) -> Unit,
    secondConfirmation: Boolean,
    onSecondConfirmationChanged: (Boolean) -> Unit,
    swipeDrawer: Boolean,
    onSwipeDrawerChanged: (Boolean) -> Unit,
    developerMode: Boolean,
    onDeveloperModeChanged: (Boolean) -> Unit,
    onOpenWorldInfo: () -> Unit,
    onOpenPersona: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenChatBackups: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenManageSt: () -> Unit,
    onOpenSecrets: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAccount: () -> Unit = {},
    onOpenBackgrounds: () -> Unit = {},
    onOpenChatBehavior: () -> Unit = {},
    appVersion: String = "",
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val openDrawer = LocalSTOpenDrawer.current

    STRoot(modifier = modifier) {
        STTopHeader(
            title = "我的",
            leading = { STIconButton(Icons.Filled.Menu, "打开抽屉", openDrawer) },
            actions = { STIconButton(Icons.Filled.Search, "搜索设置", { onShowMessage("搜索设置功能开发中") }) }
        )
        // User card with chevron - direct and simple borderless style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenAccount() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            STAvatar("我", size = 56.dp, ringColor = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("我（默认）", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("默认用户配置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // ── 外观 ──
        STSectionHeader("外观")
        STSettingsGroup {
            STListItem(
                headline = "主题外观",
                supporting = "明暗模式、界面配色、字号、消息样式",
                leading = { STTileIcon(Icons.Filled.Palette) },
                trailing = { STMiniSwatch() },
                divider = true,
                onClick = onOpenAppearance
            )
            STListItem(
                headline = "聊天背景",
                supporting = "为聊天设置全局壁纸",
                leading = { STTileIcon(Icons.Filled.Image) },
                onClick = onOpenBackgrounds
            )
        }
        // ── 行为 ──
        STSectionHeader("行为")
        STSettingsGroup {
            STListItem(
                headline = "聊天与消息",
                supporting = "平滑流式、自动继续、消息显示、示例消息行为",
                leading = { STTileIcon(Icons.AutoMirrored.Filled.Chat) },
                divider = true,
                onClick = onOpenChatBehavior
            )
            STSwitchRow("流式生成时震动反馈", "逐字到达时轻微震动", vibrationFeedback, onVibrationFeedbackChanged)
            STSwitchRow("敏感操作二次确认", "删除消息、清空对话等", secondConfirmation, onSecondConfirmationChanged)
            STSwitchRow("服务就绪后自动打开浏览器", null, autoOpenBrowserEnabled, onAutoOpenBrowserChanged)
            STSwitchRow("滑动呼出抽屉", "从左边缘横扫", swipeDrawer, onSwipeDrawerChanged)
        }
        // ── 数据 ──
        STSectionHeader("数据")
        STSettingsGroup {
            STListItem(
                headline = "自动备份",
                supporting = "点击配置",
                leading = { STTileIcon(Icons.Filled.Backup) },
                divider = true,
                onClick = { onShowMessage("备份设置功能开发中") }
            )
            STListItem(
                headline = "同步",
                supporting = "未开启",
                leading = { STTileIcon(Icons.Filled.CloudSync) },
                divider = true,
                onClick = { onShowMessage("同步设置功能开发中") }
            )
            STListItem(
                headline = "导出全部数据",
                supporting = ".charx + .json 包",
                leading = { STTileIcon(Icons.Filled.FolderZip) },
                onClick = { onShowMessage("数据导出功能开发中") }
            )
        }
        // ── 服务与安全 ──
        STSectionHeader("服务与安全")
        STSettingsGroup {
            STListItem(
                headline = "API 连接设置",
                supporting = "SillyTavern 核心端点接入与预设管理",
                leading = { STTileIcon(Icons.Filled.Cable) },
                divider = true,
                onClick = onOpenConnections
            )
            STListItem(
                headline = "API 密钥管理",
                supporting = "管理所有 AI 供应商 API 凭证",
                leading = { STTileIcon(Icons.Filled.VpnKey) },
                divider = true,
                onClick = onOpenSecrets
            )
            STListItem(
                headline = "扩展管理",
                supporting = "管理 SillyTavern 扩展插件",
                leading = { STTileIcon(Icons.Filled.Extension) },
                onClick = onOpenExtensions
            )
        }

        // ── 实验性 ──
        STSectionHeader("实验性")
        STSettingsGroup {
            STSwitchRow("开发者模式", "显示 token 计数与请求 JSON", developerMode, onDeveloperModeChanged)
        }
        // ── 关于 ──
        STSectionHeader("关于")
        STSettingsGroup {
            STListItem(
                headline = "SillyTavern Mobile",
                supporting = "${appVersion.ifBlank { "—" }} · 第三方移动客户端",
                leading = { STTileIcon(Icons.Filled.Info) },
                onClick = { onShowMessage("版本信息") }
            )
        }
    }
}



@Composable
fun STStCoreScreen(
    status: NodeStatus,
    stLabel: String,
    nodeLabel: String,
    isCustomInstalled: Boolean,
    customInstalledLabel: String?,
    busyMessage: String,
    settingsSnapshots: List<SettingsSnapshot>,
    settingsSnapshotsLoading: Boolean,
    settingsSnapshotMessage: String,
    showBackupOperationCard: Boolean,
    backupOperationTitle: String,
    backupOperationDetails: String,
    backupOperationProgressPercent: Int?,
    backupOperationAnchor: BackupOperationAnchor?,
    showCustomOperationCard: Boolean,
    customOperationTitle: String,
    customOperationDetails: String,
    customOperationProgressPercent: Int?,
    customOperationCancelable: Boolean,
    customOperationAnchor: CustomOperationAnchor?,
    onBack: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenBrowser: () -> Unit,
    onShowLogs: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRefreshSettingsSnapshots: () -> Unit,
    onCreateSettingsSnapshot: () -> Unit,
    onRestoreSettingsSnapshot: (String) -> Unit,
    onLoadCustomZip: () -> Unit,
    onResetToDefault: () -> Unit,
    onRemoveUserData: () -> Unit,
    onCancelCustomOperation: () -> Unit,
    autoStartService: Boolean,
    onAutoStartServiceChanged: (Boolean) -> Unit,
    autoOpenBrowser: Boolean,
    onAutoOpenBrowserChanged: (Boolean) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val running = status.state == NodeState.RUNNING
    val starting = status.state == NodeState.STARTING

    // Live Uptime calculation
    var uptimeSec by remember(running) { mutableStateOf(0) }
    LaunchedEffect(running) {
        if (running) {
            uptimeSec = 0
            while (true) {
                kotlinx.coroutines.delay(1000)
                uptimeSec++
            }
        } else {
            uptimeSec = 0
        }
    }
    val uptimeText = if (running) {
        val min = uptimeSec / 60
        val sec = uptimeSec % 60
        if (min > 0) "${min} 分 ${sec} 秒" else "${sec} 秒"
    } else {
        "—"
    }

    var allowBackgroundRun by remember { mutableStateOf(true) }

    STBackRoot(title = "ST 核心", onBack = onBack, modifier = modifier, actions = {
        STIconButton(Icons.Filled.MoreVert, "更多", { onShowMessage("更多内核操作功能开发中") })
    }) {
        STStStatusHero(status, stLabel, nodeLabel, onStartService, onStopService, onOpenBrowser)
        
        if (running || starting) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("进程 PID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (running) "${status.pid ?: "—"}" else "—",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("运行时长", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(uptimeText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("监听地址", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("127.0.0.1:${status.port}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface)
                    }
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("健康检查", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (running) Icons.Filled.CheckCircle else Icons.Filled.Face,
                                contentDescription = null,
                                tint = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (running) "已就绪" else "等待 HTTP 200…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (busyMessage.isNotBlank()) {
            STSystemInfoCard("正在处理", busyMessage)
        }
        if (showBackupOperationCard) {
            STOperationCard(backupOperationTitle, backupOperationDetails, backupOperationProgressPercent, backupOperationAnchor?.name.orEmpty())
        }
        if (showCustomOperationCard) {
            STOperationCard(customOperationTitle, customOperationDetails, customOperationProgressPercent, customOperationAnchor?.name.orEmpty()) {
                if (customOperationCancelable) onCancelCustomOperation()
            }
        }
        
        STSectionHeader("管理")
        STSettingsGroup {
            STListItem(
                headline = "内核版本",
                supporting = if (isCustomInstalled) "自定义 · ${customInstalledLabel.orEmpty()}" else "自带 · $stLabel",
                leading = { STTileIcon(Icons.Filled.Settings, tint = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) },
                divider = true,
                onClick = onLoadCustomZip
            )
            STListItem(
                headline = "数据备份与快照",
                supporting = "导出或导入完整数据",
                leading = { STTileIcon(Icons.Filled.Upload, tint = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) },
                divider = true,
                onClick = onExport
            )
            STListItem(
                headline = "运行日志",
                supporting = "stdout · stderr · service",
                leading = { STTileIcon(Icons.Filled.Info, tint = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) },
                divider = true,
                onClick = onShowLogs
            )
            STListItem(
                headline = "清除用户数据",
                supporting = "危险操作，清除全部数据",
                leading = { STTileIcon(Icons.Filled.Delete, tint = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) },
                divider = true,
                onClick = onRemoveUserData
            )
            STListItem(
                headline = "恢复自带内核",
                supporting = "清理自定义 ST 版本",
                leading = { STTileIcon(Icons.Filled.RestartAlt, tint = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface) },
                onClick = onResetToDefault
            )
        }

        STSectionHeader("自动化")
        STListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
            STListItem(
                headline = "启动 App 时自动唤起服务",
                supporting = "进入主屏后立即拉起 Node 进程",
                trailing = { Switch(checked = autoStartService, onCheckedChange = onAutoStartServiceChanged) },
                divider = true,
                onClick = { onAutoStartServiceChanged(!autoStartService) }
            )
            STListItem(
                headline = "服务就绪后自动打开浏览器",
                supporting = "对 :8000 进行 TCP 探测后跳转",
                trailing = { Switch(checked = autoOpenBrowser, onCheckedChange = onAutoOpenBrowserChanged) },
                divider = true,
                onClick = { onAutoOpenBrowserChanged(!autoOpenBrowser) }
            )
            STListItem(
                headline = "允许后台持续运行",
                supporting = "加入电池白名单后更稳定",
                trailing = { Switch(checked = allowBackgroundRun, onCheckedChange = { allowBackgroundRun = it }) },
                onClick = { allowBackgroundRun = !allowBackgroundRun }
            )
        }

        STSectionHeader("设置快照", trailing = {
            Text(if (settingsSnapshotsLoading) "加载中" else "${settingsSnapshots.size} 个", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        })
        STListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
            STListItem(
                headline = "创建设置快照",
                supporting = settingsSnapshotMessage.ifBlank { "保存当前设置，方便回滚" },
                leading = { STTileIcon(Icons.Filled.Backup) },
                divider = settingsSnapshots.isNotEmpty(),
                onClick = onCreateSettingsSnapshot
            )
            settingsSnapshots.take(5).forEachIndexed { index, snap ->
                STListItem(
                    headline = snap.name,
                    supporting = "${snap.size} bytes",
                    leading = { STTileIcon(Icons.Filled.CheckCircle) },
                    divider = index != settingsSnapshots.take(5).lastIndex,
                    onClick = { onRestoreSettingsSnapshot(snap.name) }
                )
            }
        }
    }
}

@Composable
private fun STRoot(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            content = content
        )
    }
}

@Composable
internal fun STBackRoot(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    STRoot(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        content()
    }
}

@Composable
private fun LoreEntryPreview(keys: String, content: String, constant: Boolean, selective: Boolean, order: Int = 0) {
    // 直接点击整条即展开/收起,不再单独放按钮(展开后无需再找按钮,误触也能就地收起)。
    var expanded by remember { mutableStateOf(false) }
    var overflowing by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = overflowing || expanded) { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (constant) {
                        STBadge(
                            "常驻",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    if (selective) {
                        STBadge(
                            "关键词触发",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                if (order > 0) {
                    Text("序号 $order", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("关键词", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                keys.split(", ").forEach { keyword ->
                    STBadge(
                        keyword,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!expanded && result.hasVisualOverflow) overflowing = true
                }
            )
        }
    }
}

@Composable
private fun STPersonaRow(
    persona: PersonaProfile,
    active: Boolean,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = if (active) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            STAvatar(persona.name, size = 48.dp, ringColor = if (active) MaterialTheme.colorScheme.primary else null)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(persona.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (active) {
                        STBadge(
                            "当前",
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Text(
                    text = persona.description.ifBlank { persona.title.ifBlank { "未填写描述" } },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            IconButton(onClick = { }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun STPresetCard(
    presetLibrary: io.github.sanitised.st.api.PresetLibrary?,
    onSelectPreset: (String, String) -> Unit
) {
    if (presetLibrary == null || presetLibrary.categories.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("采样预设", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("未连接服务 / 无可用预设", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    } else {
        presetLibrary.categories.forEach { category ->
            val selectedPreset = category.presets.firstOrNull { it.selected }
            var expanded by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedPreset?.name ?: "未选择", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.RestartAlt else Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (expanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    category.presets.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectPreset(category.apiId, preset.name)
                                    expanded = false
                                }
                                .padding(horizontal = 36.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (preset.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (preset.selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (preset.selected) {
                                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun STTemplateRow(icon: ImageVector, label: String, value: String, hint: String, toggle: Boolean = false, checked: Boolean = false) {
    STListItem(
        headline = "$label · $value",
        supporting = hint,
        leading = { STTileIcon(icon) },
        trailing = { if (toggle) Switch(checked = checked, onCheckedChange = null) },
        onClick = {}
    )
}

@Composable
private fun STSliderSection(title: String, values: List<Pair<String, Float>>) {
    STSectionHeader(title)
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        values.forEach { (label, initial) ->
            val range = remember(label) {
                when {
                    label.contains("温度") || label.contains("Temperature") -> 0f..2f
                    label.contains("惩罚") && !label.contains("范围") -> -2f..2f
                    else -> 0f..1f
                }
            }
            var value by remember(label) { mutableFloatStateOf(initial.coerceIn(range.start, range.endInclusive)) }
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f", value),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = range,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun STStatefulSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = String.format(Locale.US, "%.2f", value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun STSwitchRow(label: String, sub: String?, checked: Boolean, onChanged: (Boolean) -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChanged(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun STSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    STListSurface(modifier = Modifier.padding(horizontal = 16.dp), content = content)
}

@Composable
private fun STNavRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    STListItem(
        headline = title,
        supporting = sub,
        leading = { STTileIcon(icon) },
        onClick = onClick
    )
}

@Composable
private fun STStStatusHero(status: NodeStatus, stLabel: String, nodeLabel: String, onStart: () -> Unit, onStop: () -> Unit, onOpen: () -> Unit) {
    val running = status.state == NodeState.RUNNING
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            STStatusDot(if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(10.dp))
            Text(if (running) "运行中" else "已停止", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            STBadge(":${status.port}")
        }
        Text(if (running) "SillyTavern 正在为你运行" else "SillyTavern 已停止", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp))
        Text("$stLabel · $nodeLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("在浏览器打开")
                }
                OutlinedButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("停止")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("启动服务")
                }
            }
        }
    }
}

@Composable
private fun STOperationCard(title: String, details: String, progress: Int?, anchor: String, onCancel: (() -> Unit)? = null) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title.ifBlank { anchor.ifBlank { "正在处理" } }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            if (onCancel != null) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) { Text("取消") }
            }
        }
    }
}

@Composable
private fun STLoadingCard(text: String) {
    STSystemInfoCard(text, "请稍候。") {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
internal fun STSystemInfoCard(title: String, body: String, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        if (body.isNotBlank()) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(4.dp))
            action()
        }
    }
}

@Composable
private fun STMiniSwatch() {
    val colors = listOf(0xFFFFB871, 0xFFC6CB95, 0xFFE5C0A2, 0xFF251F17)
    Row {
        colors.forEachIndexed { index, colorLong ->
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .then(if (index > 0) Modifier.padding(start = 0.dp) else Modifier)
                    .background(Color(colorLong), CircleShape)
                    .then(
                        Modifier.padding(0.dp) // border via overlay approach
                    )
            )
        }
    }
}

@Composable
fun STGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused) STThemePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isFocused) STThemePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        visualTransformation = visualTransformation,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = if (visualTransformation != VisualTransformation.None) FontFamily.Monospace else FontFamily.Default
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(STThemePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                    )
                }
                if (trailingIcon != null) {
                    Spacer(Modifier.width(8.dp))
                    trailingIcon()
                }
            }
        }
    }
}

// ── Map 读取扩展(被多个 ST 屏幕共享)──
internal fun Map<*, *>?.stringValue(key: String): String {
    return (this?.get(key) as? String).orEmpty()
}

internal fun Map<*, *>?.floatValue(key: String, default: Float): Float {
    return when (val value = this?.get(key)) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull() ?: default
        else -> default
    }
}

internal fun Map<*, *>?.intValue(key: String, default: Int): Int {
    return when (val value = this?.get(key)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }
}
