package io.github.sanitised.st.ui.prototype

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
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StickyNote2
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
import io.github.sanitised.st.ThemeColorSource
import io.github.sanitised.st.ThemeMode
import io.github.sanitised.st.UpdateChannel
import io.github.sanitised.st.api.ChatBackupSummary
import io.github.sanitised.st.api.ConnectionProfile
import io.github.sanitised.st.api.PersonaProfile
import io.github.sanitised.st.api.SecretProviderState
import io.github.sanitised.st.api.SettingsSnapshot
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.api.WorldInfoSummary
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer

@Composable
fun PrototypeWorldInfoScreen(
    status: NodeStatus,
    baseUrl: String,
    onStartService: () -> Unit,
    onShowMessage: (String) -> Unit,
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

    PrototypeRoot(modifier = modifier) {
        PrototypeTopHeader(
            title = "世界书",
            subtitle = "动态注入的设定。条目根据关键词触发或常驻。",
            leading = {
                PrototypeIconButton(Icons.Filled.Menu, "打开抽屉", openDrawer)
            },
            actions = {
                PrototypeIconButton(Icons.Filled.FileDownload, "导入", { onShowMessage("世界书导入功能开发中") })
                PrototypeIconButton(Icons.Filled.Add, "新增", { onShowMessage("新增世界书条目功能开发中") })
            }
        )
        if (!running) {
            PrototypeSystemInfoCard("本地服务未启动", "启动服务后会加载真实世界书。") {
                Button(onClick = onStartService) { Text("启动服务") }
            }
        } else if (loading) {
            PrototypeLoadingCard("正在读取世界书…")
        } else if (books.isEmpty()) {
            PrototypeSystemInfoCard("暂无世界书", "在 SillyTavern 中尚未创建世界书。")
        } else {
            PrototypeSectionHeader(
                title = "本地世界书",
                trailing = {
                    Text("${books.size} 本已加载", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            )
            PrototypeListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                books.forEachIndexed { index, book ->
                    PrototypeListItem(
                        headline = book.name,
                        supporting = "包含设定与条目",
                        leading = {
                            PrototypeTileIcon(
                                icon = Icons.Filled.AutoStories,
                                tint = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        },
                        trailing = { Switch(checked = true, onCheckedChange = null) },
                        divider = index != books.lastIndex,
                        onClick = { onShowMessage("世界书详情功能开发中") }
                    )
                }
            }
            PrototypeSectionHeader(title = "常用条目预览")
            val entries = firstBook?.entries.orEmpty()
            if (entries.isNotEmpty()) {
                entries.take(4).forEach { entry ->
                    LoreEntryPreview(
                        keys = entry.keys.joinToString(", ").ifBlank { entry.comment.ifBlank { "未命名条目" } },
                        content = entry.content,
                        constant = entry.constant,
                        selective = entry.selective
                    )
                }
            } else {
                PrototypeSystemInfoCard("暂无世界书条目", "当绑定并启用世界书时，符合触发关键词的条目会在此预览。")
            }
        }
    }
}

@Composable
fun PrototypePersonaScreen(
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
                PrototypeTopHeader(
                    title = "扮演者",
                    leading = {
                        PrototypeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                    },
                    actions = {
                        PrototypeIconButton(Icons.Filled.AutoFixHigh, "AI 生成", { onShowMessage("AI 生成扮演者功能开发中") })
                        PrototypeIconButton(Icons.Filled.Add, "新建", { onShowMessage("新建扮演者功能开发中") })
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
                    PrototypeSystemInfoCard("本地服务未启动", "启动 SillyTavern 服务后可加载和管理扮演者设定。")
                } else if (personas.isEmpty()) {
                    PrototypeSystemInfoCard("暂无扮演者设定", "点击右下角“新建”以添加您在聊天中扮演的角色。")
                } else {
                    PrototypeSectionHeader("当前激活")
                    activePersonas.forEach { persona ->
                        PrototypePersonaRow(persona = persona, active = true)
                    }

                    if (otherPersonas.isNotEmpty()) {
                        PrototypeSectionHeader("所有扮演者", trailing = {
                            Text("管理绑定", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        })
                        otherPersonas.forEach { persona ->
                            PrototypePersonaRow(
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
fun PrototypeAISettingsScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
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

    PrototypeBackRoot(title = "AI 设置", onBack = onBack, modifier = modifier, actions = {
        PrototypeIconButton(Icons.Filled.RestartAlt, "重置", {
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
        PrototypeIconButton(Icons.Filled.Save, "保存", {
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
                        onShowMessage("采样参数已保存至后端")
                    }.onFailure { onShowMessage("保存失败：${it.message}") }
                }
            } else {
                onShowMessage("服务未运行，无法保存")
            }
        })
    }) {
        PrototypePresetCard(
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
        PrototypeSectionHeader("提示模板", trailing = {
            Text("仅文本补全", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        })
        val instructName = presetLibrary?.categories?.firstOrNull { it.apiId == "instruct" }
            ?.presets?.firstOrNull { it.selected }?.name ?: "—"
        val contextName = presetLibrary?.categories?.firstOrNull { it.apiId == "context" }
            ?.presets?.firstOrNull { it.selected }?.name ?: "—"
        val syspromptName = presetLibrary?.categories?.firstOrNull { it.apiId == "sysprompt" }
            ?.presets?.firstOrNull { it.selected }?.name ?: "—"
        PrototypeTemplateRow(Icons.Filled.Code, "Instruct 模板", instructName, "角色名 / 系统提示遵循模型格式", toggle = true)
        PrototypeTemplateRow(Icons.Filled.Bookmarks, "上下文模板", contextName, "角色描述 + 场景 + 历史的组织方式")
        PrototypeTemplateRow(Icons.Filled.Tune, "系统提示", syspromptName, "注入到对话最前", toggle = true, checked = true)
        PrototypeTemplateRow(Icons.Filled.StickyNote2, "作者注 / 深度笔记", "未设置", "按需注入到指定深度")
        PrototypeSectionHeader("核心采样")
        PrototypeStatefulSlider("温度 Temperature", temp, 0f..2f) { temp = it }
        PrototypeStatefulSlider("Top P", topP, 0f..1f) { topP = it }
        PrototypeStatefulSlider("Top K", topK, 0f..1f) { topK = it }
        PrototypeStatefulSlider("Min P", minP, 0f..1f) { minP = it }
        PrototypeSectionHeader("重复抑制")
        PrototypeStatefulSlider("频率惩罚", freqPenalty, -2f..2f) { freqPenalty = it }
        PrototypeStatefulSlider("存在惩罚", presPenalty, -2f..2f) { presPenalty = it }
        PrototypeSectionHeader("高级 — 极少改动")
        PrototypeSwitchRow("启用流式输出", "边生成边显示", streamingEnabled) { streamingEnabled = it }
        PrototypeSwitchRow("禁止思考链泄露", "过滤掉 <think> 标签内容", true)
        PrototypeSwitchRow("DRY (动态重复抑制)", "抗循环更激进的算法", false)
        PrototypeSwitchRow("温度最后采样", "先 Top-P 再温度", false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeApiConnectionScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSecrets: (() -> Unit)? = null,
    onOpenProviderDetail: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<ConnectionProfile>>(emptyList()) }
    var secrets by remember { mutableStateOf<List<SecretProviderState>>(emptyList()) }
    var settings by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    val running = status.state == NodeState.RUNNING

    var activeMode by remember { mutableStateOf("cc") }
    var activeProviderId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(running, baseUrl) {
        if (running) {
            runCatching {
                val client = TavernCoreClient(baseUrl)
                profiles = client.listConnectionProfiles()
                val secretsList = client.listSecrets()
                secrets = secretsList
                val coreSettings = client.getSettings()
                settings = coreSettings

                val loadedState = buildApiConnectionUiState(
                    settings = coreSettings,
                    secrets = secretsList,
                    serviceRunning = true
                )
                activeMode = loadedState.activeMode
                activeProviderId = loadedState.activeProvider.id
            }.onFailure { onShowMessage(it.message ?: "API 连接加载失败") }
        }
    }

    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var selectedProfileName by remember { mutableStateOf("当前 SillyTavern 设置") }
    var showProfileSheet by remember { mutableStateOf(false) }

    LaunchedEffect(profiles) {
        if (profiles.isNotEmpty() && selectedProfileId == null) {
            val first = profiles.first()
            selectedProfileId = first.label
            selectedProfileName = first.label
        }
    }
    val connectionState = remember(settings, secrets, running, activeMode, activeProviderId) {
        buildApiConnectionUiState(
            settings = settings,
            secrets = secrets,
            serviceRunning = running,
            selectedProviderId = activeProviderId,
            selectedMode = activeMode
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = STThemeBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // AppBar — aligned exactly to design draft
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrototypeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "API 连接设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "SillyTavern 核心端点接入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PrototypeIconButton(Icons.Filled.Help, "帮助", { onShowMessage("已打开 API 说明书") })
            }

            // Connection Profile Presets row card
            PrototypeConnectionProfileCard(
                profiles = profiles,
                selectedProfileName = selectedProfileName,
                onClick = { showProfileSheet = true }
            )

            // Dynamic API Mode Segmented Picker
            PrototypeModeControl(
                activeMode = activeMode,
                onModeChange = { mode ->
                    activeMode = mode
                    val firstProvider = apiConnectionProvidersForMode(mode).firstOrNull()
                    activeProviderId = firstProvider?.id
                    if (running && firstProvider != null) {
                        scope.launch {
                            runCatching {
                                val client = TavernCoreClient(baseUrl)
                                val updated = settingsWithSelectedApiProvider(
                                    settings = settings,
                                    provider = firstProvider
                                )
                                client.saveSettings(updated)
                                settings = updated
                                onShowMessage("已切换至 ${firstProvider.label}")
                            }.onFailure {
                                onShowMessage("模式切换失败：${it.message}")
                            }
                        }
                    }
                }
            )

            // Section Header: 当前核心装载状态
            PremiumSectionHeader(
                title = "当前核心装载状态",
                trailing = {
                    val statusText = if (running) "● 服务运行中" else "○ 服务未启动"
                    val statusColor = if (running) STThemeTertiary else STThemeError
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            )

            // Active Connection HUD Card
            PrototypeActiveConnectionCard(
                activeMode = connectionState.activeMode,
                activeProvider = connectionState.activeProvider.label,
                configuredProviderCount = connectionState.configuredProviderCount,
                activeModel = connectionState.activeModel,
                connectionStatusText = connectionState.connectionStatusText,
                connectionStatusOk = connectionState.connectionStatusOk,
                activeSecretLabel = connectionState.activeProvider.activeSecretLabel,
                onConfigure = {
                    onOpenProviderDetail(connectionState.activeProvider.id)
                }
            )

            // Section Header: 选择 API 提供商
            PremiumSectionHeader(
                title = if (activeMode == "tc") "文本补全 — 后端" else "聊天补全 — 提供商",
                trailing = {
                    Text(
                        text = "${connectionState.visibleProviders.size} 个可用",
                        style = MaterialTheme.typography.labelMedium,
                        color = STThemePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            )

            // Provider grid
            ProviderGrid(
                providers = connectionState.visibleProviders,
                activeProviderId = connectionState.activeProvider.id,
                onProviderChange = { provider ->
                    activeProviderId = provider.id
                    activeMode = provider.mode
                    if (running) {
                        scope.launch {
                            runCatching {
                                val client = TavernCoreClient(baseUrl)
                                val updated = settingsWithSelectedApiProvider(
                                    settings = settings,
                                    provider = provider.definition
                                )
                                client.saveSettings(updated)
                                settings = updated
                                onShowMessage("已切换 API 提供商为 ${provider.label}")
                            }.onFailure {
                                onShowMessage("切换失败：${it.message}")
                            }
                        }
                    }
                }
            )

            // Additional Static Options based on mode
            if (activeMode == "cc") {
                PremiumSectionHeader(title = "本地与社区反代")
                PrototypeListItem(
                    headline = "自定义 OpenAI 兼容反代 (Reverse Proxy)",
                    supporting = "可自定义 API 根地址，完美适配自建 OneAPI",
                    leading = { PrototypeTileIcon(Icons.Filled.Code) },
                    onClick = { onShowMessage("自建配置功能开发中，可通过配置详细后端进行调整") }
                )
                PrototypeListItem(
                    headline = "KoboldAI Horde 共享池",
                    supporting = "使用社区免费志愿贡献者的 GPU 算力",
                    leading = { PrototypeTileIcon(Icons.Filled.Face) },
                    onClick = { onShowMessage("Horde 配置功能开发中") }
                )
            } else if (activeMode == "tc") {
                PremiumSectionHeader(title = "当前后端的指令模版契约")
                PrototypeListItem(
                    headline = "自动匹配 Instruct 模板",
                    supporting = "根据后端模型自动选择指令格式",
                    leading = { PrototypeTileIcon(Icons.Filled.Code) },
                    onClick = { onShowMessage("指令模板设置请在 AI 采样页中进行") }
                )
                PrototypeListItem(
                    headline = "上下文拼接模板 (Context Templates)",
                    supporting = "Default — 先行角色设定，后贴入场景描述",
                    leading = { PrototypeTileIcon(Icons.Filled.Bookmarks) },
                    onClick = { onShowMessage("上下文模板设置请在 AI 采样页中进行") }
                )
            }

            if (showProfileSheet) {
                ConnectionProfileBottomSheet(
                    apiProfiles = profiles,
                    onDismiss = { showProfileSheet = false },
                    selectedProfileId = selectedProfileId,
                    onProfileSelected = { id, name ->
                        selectedProfileId = id
                        selectedProfileName = name
                        showProfileSheet = false
                        if (running) {
                            val profile = profiles.firstOrNull { it.label == id }
                            if (profile != null) {
                                scope.launch {
                                    runCatching {
                                        TavernCoreClient(baseUrl).saveConnectionProfile(profile.copy(
                                            lastConnection = System.currentTimeMillis()
                                        ))
                                        onShowMessage("已选择服务器：$name")
                                    }.onFailure {
                                        onShowMessage("切换失败：${it.message}")
                                    }
                                }
                            }
                        } else {
                            onShowMessage("已选择服务器：$name")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PrototypeMeScreen(
    autoCheckEnabled: Boolean,
    onAutoCheckChanged: (Boolean) -> Unit,
    autoOpenBrowserEnabled: Boolean,
    onAutoOpenBrowserChanged: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    colorSource: ThemeColorSource,
    onColorSourceChanged: (ThemeColorSource) -> Unit,
    isBatteryUnrestricted: Boolean,
    onOpenBatterySettings: () -> Unit,
    channel: UpdateChannel,
    onChannelChanged: (UpdateChannel) -> Unit,
    onCheckNow: () -> Unit,
    isChecking: Boolean,
    bubbleStyle: Boolean,
    onBubbleStyleChanged: (Boolean) -> Unit,
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
    appVersion: String = "",
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val openDrawer = LocalSTOpenDrawer.current

    PrototypeRoot(modifier = modifier) {
        PrototypeTopHeader(
            title = "我的",
            leading = { PrototypeIconButton(Icons.Filled.Menu, "打开抽屉", openDrawer) },
            actions = { PrototypeIconButton(Icons.Filled.Search, "搜索设置", { onShowMessage("搜索设置功能开发中") }) }
        )
        // User card with chevron - direct and simple borderless style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowMessage("用户资料功能开发中") }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrototypeAvatar("我", size = 56.dp, ringColor = MaterialTheme.colorScheme.primary)
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
        PrototypeSectionHeader("外观")
        PrototypeSettingsGroup {
            PrototypeListItem(
                headline = "主题",
                supporting = when (themeMode) {
                    ThemeMode.LIGHT -> "浅色模式"
                    ThemeMode.DARK -> "深色模式"
                    ThemeMode.AUTO -> "跟随系统"
                },
                leading = { PrototypeTileIcon(Icons.Filled.Palette) },
                trailing = { PrototypeMiniSwatch() },
                divider = true,
                onClick = onOpenAppearance
            )
            PrototypeListItem(
                headline = "字号",
                supporting = "点击调整",
                leading = { PrototypeTileIcon(Icons.Filled.TextFields) },
                divider = true,
                onClick = onOpenAppearance
            )
            PrototypeListItem(
                headline = "聊天背景",
                supporting = "点击设置",
                leading = { PrototypeTileIcon(Icons.Filled.Image) },
                divider = true,
                onClick = onOpenAppearance
            )
            PrototypeSwitchRow("消息冒泡风格", "关闭则使用全宽文档样式", bubbleStyle, onBubbleStyleChanged)
        }
        // ── 行为 ──
        PrototypeSectionHeader("行为")
        PrototypeSettingsGroup {
            PrototypeSwitchRow("流式生成时震动反馈", "逐字到达时轻微震动", vibrationFeedback, onVibrationFeedbackChanged)
            PrototypeSwitchRow("敏感操作二次确认", "删除消息、清空对话等", secondConfirmation, onSecondConfirmationChanged)
            PrototypeSwitchRow("启动时自动连接 API", null, autoOpenBrowserEnabled, onAutoOpenBrowserChanged)
            PrototypeSwitchRow("滑动呼出抽屉", "从左边缘横扫", swipeDrawer, onSwipeDrawerChanged)
        }
        // ── 数据 ──
        PrototypeSectionHeader("数据")
        PrototypeSettingsGroup {
            PrototypeListItem(
                headline = "自动备份",
                supporting = "点击配置",
                leading = { PrototypeTileIcon(Icons.Filled.Backup) },
                divider = true,
                onClick = { onShowMessage("备份设置功能开发中") }
            )
            PrototypeListItem(
                headline = "同步",
                supporting = "未开启",
                leading = { PrototypeTileIcon(Icons.Filled.CloudSync) },
                divider = true,
                onClick = { onShowMessage("同步设置功能开发中") }
            )
            PrototypeListItem(
                headline = "导出全部数据",
                supporting = ".charx + .json 包",
                leading = { PrototypeTileIcon(Icons.Filled.FolderZip) },
                onClick = { onShowMessage("数据导出功能开发中") }
            )
        }
        // ── 服务与安全 ──
        PrototypeSectionHeader("服务与安全")
        PrototypeSettingsGroup {
            PrototypeListItem(
                headline = "API 连接设置",
                supporting = "SillyTavern 核心端点接入与预设管理",
                leading = { PrototypeTileIcon(Icons.Filled.Cable) },
                divider = true,
                onClick = onOpenConnections
            )
            PrototypeListItem(
                headline = "API 密钥管理",
                supporting = "管理所有 AI 供应商 API 凭证",
                leading = { PrototypeTileIcon(Icons.Filled.VpnKey) },
                divider = true,
                onClick = onOpenSecrets
            )
            PrototypeListItem(
                headline = "扩展管理",
                supporting = "管理 SillyTavern 扩展插件",
                leading = { PrototypeTileIcon(Icons.Filled.Extension) },
                onClick = onOpenExtensions
            )
        }

        // ── 实验性 ──
        PrototypeSectionHeader("实验性")
        PrototypeSettingsGroup {
            PrototypeSwitchRow("开发者模式", "显示 token 计数与请求 JSON", developerMode, onDeveloperModeChanged)
        }
        // ── 关于 ──
        PrototypeSectionHeader("关于")
        PrototypeSettingsGroup {
            PrototypeListItem(
                headline = "SillyTavern Mobile",
                supporting = "${appVersion.ifBlank { "—" }} · 第三方移动客户端",
                leading = { PrototypeTileIcon(Icons.Filled.Info) },
                onClick = { onShowMessage("版本信息") }
            )
        }
    }
}



@Composable
fun PrototypeStCoreScreen(
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

    PrototypeBackRoot(title = "ST 核心", onBack = onBack, modifier = modifier, actions = {
        PrototypeIconButton(Icons.Filled.MoreVert, "更多", { onShowMessage("更多内核操作功能开发中") })
    }) {
        PrototypeStStatusHero(status, stLabel, nodeLabel, onStartService, onStopService, onOpenBrowser)
        
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
            PrototypeSystemInfoCard("正在处理", busyMessage)
        }
        if (showBackupOperationCard) {
            PrototypeOperationCard(backupOperationTitle, backupOperationDetails, backupOperationProgressPercent, backupOperationAnchor?.name.orEmpty())
        }
        if (showCustomOperationCard) {
            PrototypeOperationCard(customOperationTitle, customOperationDetails, customOperationProgressPercent, customOperationAnchor?.name.orEmpty()) {
                if (customOperationCancelable) onCancelCustomOperation()
            }
        }
        
        PrototypeSectionHeader("管理")
        PrototypeSettingsGroup {
            PrototypeListItem(
                headline = "内核版本",
                supporting = if (isCustomInstalled) "自定义 · ${customInstalledLabel.orEmpty()}" else "自带 · $stLabel",
                leading = { PrototypeTileIcon(Icons.Filled.Settings, tint = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) },
                divider = true,
                onClick = onLoadCustomZip
            )
            PrototypeListItem(
                headline = "数据备份与快照",
                supporting = "导出或导入完整数据",
                leading = { PrototypeTileIcon(Icons.Filled.Upload, tint = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) },
                divider = true,
                onClick = onExport
            )
            PrototypeListItem(
                headline = "运行日志",
                supporting = "stdout · stderr · service",
                leading = { PrototypeTileIcon(Icons.Filled.Info, tint = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) },
                divider = true,
                onClick = onShowLogs
            )
            PrototypeListItem(
                headline = "清除用户数据",
                supporting = "危险操作，清除全部数据",
                leading = { PrototypeTileIcon(Icons.Filled.Delete, tint = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) },
                divider = true,
                onClick = onRemoveUserData
            )
            PrototypeListItem(
                headline = "恢复自带内核",
                supporting = "清理自定义 ST 版本",
                leading = { PrototypeTileIcon(Icons.Filled.RestartAlt, tint = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface) },
                onClick = onResetToDefault
            )
        }

        PrototypeSectionHeader("自动化")
        PrototypeListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrototypeListItem(
                headline = "启动 App 时自动唤起服务",
                supporting = "进入主屏后立即拉起 Node 进程",
                trailing = { Switch(checked = autoStartService, onCheckedChange = onAutoStartServiceChanged) },
                divider = true,
                onClick = { onAutoStartServiceChanged(!autoStartService) }
            )
            PrototypeListItem(
                headline = "服务就绪后自动打开浏览器",
                supporting = "对 :8000 进行 TCP 探测后跳转",
                trailing = { Switch(checked = autoOpenBrowser, onCheckedChange = onAutoOpenBrowserChanged) },
                divider = true,
                onClick = { onAutoOpenBrowserChanged(!autoOpenBrowser) }
            )
            PrototypeListItem(
                headline = "允许后台持续运行",
                supporting = "加入电池白名单后更稳定",
                trailing = { Switch(checked = allowBackgroundRun, onCheckedChange = { allowBackgroundRun = it }) },
                onClick = { allowBackgroundRun = !allowBackgroundRun }
            )
        }

        PrototypeSectionHeader("设置快照", trailing = {
            Text(if (settingsSnapshotsLoading) "加载中" else "${settingsSnapshots.size} 个", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        })
        PrototypeListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrototypeListItem(
                headline = "创建设置快照",
                supporting = settingsSnapshotMessage.ifBlank { "保存当前设置，方便回滚" },
                leading = { PrototypeTileIcon(Icons.Filled.Backup) },
                divider = settingsSnapshots.isNotEmpty(),
                onClick = onCreateSettingsSnapshot
            )
            settingsSnapshots.take(5).forEachIndexed { index, snap ->
                PrototypeListItem(
                    headline = snap.name,
                    supporting = "${snap.size} bytes",
                    leading = { PrototypeTileIcon(Icons.Filled.CheckCircle) },
                    divider = index != settingsSnapshots.take(5).lastIndex,
                    onClick = { onRestoreSettingsSnapshot(snap.name) }
                )
            }
        }
    }
}

@Composable
private fun PrototypeRoot(
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
private fun PrototypeBackRoot(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    PrototypeRoot(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrototypeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    if (constant) PrototypeBadge("常驻", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    if (selective) PrototypeBadge("关键词触发", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
                if (order > 0) {
                    Text("序号 $order", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("关键词", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                keys.split(", ").forEach { keyword ->
                    PrototypeBadge(keyword, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = MaterialTheme.typography.bodyMedium.lineHeight)
        }
    }
}

@Composable
private fun PrototypePersonaRow(
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
            PrototypeAvatar(persona.name, size = 48.dp, ringColor = if (active) MaterialTheme.colorScheme.primary else null)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(persona.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (active) {
                        PrototypeBadge("当前", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
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
private fun PrototypePresetCard(
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
private fun PrototypeTemplateRow(icon: ImageVector, label: String, value: String, hint: String, toggle: Boolean = false, checked: Boolean = false) {
    PrototypeListItem(
        headline = "$label · $value",
        supporting = hint,
        leading = { PrototypeTileIcon(icon) },
        trailing = { if (toggle) Switch(checked = checked, onCheckedChange = null) },
        onClick = {}
    )
}

@Composable
private fun PrototypeSliderSection(title: String, values: List<Pair<String, Float>>) {
    PrototypeSectionHeader(title)
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
private fun PrototypeStatefulSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
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
private fun PrototypeSwitchRow(label: String, sub: String?, checked: Boolean, onChanged: (Boolean) -> Unit = {}) {
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
private fun PrototypeConnectionProfileCard(
    profiles: List<ConnectionProfile>,
    selectedProfileName: String,
    onClick: () -> Unit
) {
    PremiumCard(
        onClick = onClick,
        borderColor = Color(0x0DFFFFFF)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Bookmarks, null, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("当前连接预设 (Presets)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                Text(selectedProfileName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0x0AFFFFFF),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "${profiles.size} 项",
                    style = MaterialTheme.typography.labelSmall,
                    color = STThemePrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Icon(Icons.Filled.UnfoldMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrototypeModeControl(
    activeMode: String,
    onModeChange: (String) -> Unit
) {
    val modes = listOf(
        "cc" to "聊天补全",
        "tc" to "文本补全",
        "kobold" to "Kobold",
        "novel" to "NovelAI"
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = "API 运行模式 (Mode)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
            letterSpacing = 0.5.sp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x08FFFFFF), RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            modes.forEach { (modeId, label) ->
                val sel = modeId == activeMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) STThemePrimary else Color.Transparent)
                        .clickable { onModeChange(modeId) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sel) Color(0xFF4A2700) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val desc = when (activeMode) {
            "cc" -> "💡 Chat Completion：适合云端闭源模型。支持 message 结构化输入，内置自动思考过滤。"
            "tc" -> "💡 Text Completion：适合本地部署推理。支持原始 Token 级别 Instruct 拼接控制。"
            "kobold" -> "💡 KoboldAI Classic：旧版兼容，连接至 Horde 或 Kobold 本地端点。"
            "novel" -> "💡 NovelAI：专为轻小说续写设计，专享 Kayra/Erato 独特采样体系。"
            else -> ""
        }
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun PrototypeActiveConnectionCard(
    activeMode: String,
    activeProvider: String,
    configuredProviderCount: Int,
    activeModel: String,
    connectionStatusText: String,
    connectionStatusOk: Boolean,
    activeSecretLabel: String?,
    onConfigure: () -> Unit
) {
    val title = when (activeMode) {
        "cc" -> "$activeProvider (聊天补全)"
        "tc" -> "$activeProvider (文本补全)"
        "kobold" -> "KoboldAI Horde · 社区"
        "novel" -> "NovelAI Official"
        else -> "未选择"
    }
    val subtitle = activeModel
    val avatarLabel = when (activeMode) {
        "cc" -> activeProvider.take(1).uppercase()
        "tc" -> activeProvider.take(1).uppercase()
        "kobold" -> "H"
        "novel" -> "N"
        else -> "?"
    }
    
    PremiumCard(
        borderColor = if (connectionStatusOk) Color(0x407FCE8E) else Color(0x0DFFFFFF)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (connectionStatusOk) MaterialTheme.colorScheme.primaryContainer else Color(0x0DFFFFFF),
                contentColor = if (connectionStatusOk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = avatarLabel,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle, 
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            PrototypeIconButton(Icons.Filled.Settings, "配置详细后端", onConfigure)
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = Color(0x0DFFFFFF))
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrototypeStat(
                label = "连接状态", 
                value = connectionStatusText,
                tone = if (connectionStatusOk) "ok" else "error"
            )
            PrototypeStat(
                label = "密钥配置",
                value = activeSecretLabel ?: "${configuredProviderCount} 个已配置"
            )
            PrototypeStat(
                label = "连接验证",
                value = "尚未测试"
            )
        }
    }
}

@Composable
private fun RowScope.PrototypeStat(label: String, value: String, tone: String? = null) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall, 
            color = MaterialTheme.colorScheme.onSurfaceVariant, 
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = when (tone) {
                "ok" -> STThemeTertiary
                "error" -> STThemeError
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ProviderGrid(
    providers: List<ApiConnectionProviderState>,
    activeProviderId: String,
    onProviderChange: (ApiConnectionProviderState) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        providers.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                row.forEach { provider ->
                    val active = provider.id == activeProviderId
                    Surface(
                        onClick = { onProviderChange(provider) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        color = if (active) STThemePrimary.copy(alpha = 0.08f) else Color(0x05FFFFFF),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (active) STThemePrimary.copy(alpha = 0.35f) else Color(0x0AFFFFFF)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) STThemePrimary else Color(0x08FFFFFF),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = provider.icon,
                                        color = if (active) Color(0xFF18130E) else STThemePrimary,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Text(
                                text = provider.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp),
                                fontSize = 11.sp
                            )
                            Text(
                                text = provider.secretStatusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (provider.hasConfiguredSecret) STThemeTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionProfileBottomSheet(
    apiProfiles: List<ConnectionProfile>,
    onDismiss: () -> Unit,
    selectedProfileId: String?,
    onProfileSelected: (String, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(Icons.Filled.Bookmarks, null, tint = STThemePrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("切换连接服务器", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text("选择已保存的 SillyTavern 服务端点", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "关闭")
                }
            }
            HorizontalDivider(color = Color(0x0DFFFFFF))
            Spacer(modifier = Modifier.height(8.dp))

            if (apiProfiles.isEmpty()) {
                PrototypeSystemInfoCard("暂无保存的服务器", "当前使用本地 SillyTavern 服务。")
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)
                ) {
                    apiProfiles.forEach { profile ->
                        val active = profile.label == selectedProfileId
                        Surface(
                            onClick = { onProfileSelected(profile.label, profile.label) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = if (active) Color(0x08FFFFFF) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (active) STThemePrimary else Color(0x0DFFFFFF),
                                    contentColor = if (active) Color(0xFF4A2700) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Cable, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = profile.label.ifBlank { profile.url },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (active) STThemePrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = profile.url,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                if (active) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = STThemePrimary)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0x0DFFFFFF))
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { /* New preset action */ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1EFFFFFF))
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("新建预设", color = MaterialTheme.colorScheme.onSurface)
                }
                Button(
                    onClick = { /* Export preset action */ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x0AFFFFFF), contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Filled.Download, null)
                    Spacer(Modifier.width(6.dp))
                    Text("导出预设")
                }
            }
        }
    }
}

@Composable
private fun PrototypeSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    PrototypeListSurface(modifier = Modifier.padding(horizontal = 16.dp), content = content)
}

@Composable
private fun PrototypeNavRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    PrototypeListItem(
        headline = title,
        supporting = sub,
        leading = { PrototypeTileIcon(icon) },
        onClick = onClick
    )
}

@Composable
private fun PrototypeStStatusHero(status: NodeStatus, stLabel: String, nodeLabel: String, onStart: () -> Unit, onStop: () -> Unit, onOpen: () -> Unit) {
    val running = status.state == NodeState.RUNNING
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrototypeStatusDot(if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(10.dp))
            Text(if (running) "运行中" else "已停止", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            PrototypeBadge(":${status.port}")
        }
        Text(if (running) "SillyTavern 正在为你运行" else "SillyTavern 已停止", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp))
        Text("$stLabel · $nodeLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.OpenInNew, null)
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
private fun PrototypeOperationCard(title: String, details: String, progress: Int?, anchor: String, onCancel: (() -> Unit)? = null) {
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
private fun PrototypeLoadingCard(text: String) {
    PrototypeSystemInfoCard(text, "请稍候。") {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun PrototypeSystemInfoCard(title: String, body: String, action: @Composable (() -> Unit)? = null) {
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
private fun PrototypeBadge(label: String, containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(shape = MaterialTheme.shapes.small, color = containerColor, contentColor = contentColor) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun PrototypeMiniSwatch() {
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
fun PrototypeGlassTextField(
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
                    color = Color(0x05FFFFFF),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isFocused) STThemePrimary else Color(0x0DFFFFFF),
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

@Composable
fun PrototypeProviderDetailScreen(
    status: NodeStatus,
    baseUrl: String,
    providerId: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    
    var customUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var initialApiKey by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    
    var modelsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedModel by remember { mutableStateOf("") }
    var isLoadingModels by remember { mutableStateOf(false) }
    
    var temp by remember { mutableStateOf(1.0f) }
    var contextSize by remember { mutableStateOf(4096f) }
    
    val running = status.state == NodeState.RUNNING
    val providerDefinition = remember(providerId) {
        apiConnectionProviderForId(providerId) ?: apiConnectionProviderForId("openai")!!
    }
    var providerState by remember(providerDefinition.id) {
        mutableStateOf<ApiConnectionProviderState?>(null)
    }
    
    LaunchedEffect(running, baseUrl, providerId) {
        if (running) {
            runCatching {
                val client = TavernCoreClient(baseUrl)
                val coreSettings = client.getSettings()
                settings = coreSettings
                
                val group = providerDefinition.modelSettingsGroup
                val groupMap = if (!group.isNullOrBlank()) {
                    (coreSettings[group] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()
                } else emptyMap()

                customUrl = when (providerDefinition.mode) {
                    "cc" -> (groupMap["reverse_proxy"] as? String).orEmpty()
                    "tc" -> coreSettings.stringValue("api_server")
                    else -> ""
                }

                selectedModel = modelForProvider(coreSettings, providerDefinition)

                temp = when (providerDefinition.mode) {
                    "cc" -> groupMap.floatValue("temp", 1.0f)
                    "tc" -> groupMap.floatValue("temp", 1.0f)
                    else -> coreSettings.floatValue("temp", 1.0f)
                }
                contextSize = when (providerDefinition.mode) {
                    "cc" -> groupMap.intValue("openai_max_context", 4096).toFloat()
                    "tc" -> groupMap.intValue("max_context", 4096).toFloat()
                    else -> 4096f
                }
                
                val secretsList = client.listSecrets()
                providerState = buildApiConnectionUiState(
                    settings = coreSettings,
                    secrets = secretsList,
                    serviceRunning = true,
                    selectedProviderId = providerDefinition.id,
                    selectedMode = providerDefinition.mode
                ).activeProvider
                val firstEntry = providerDefinition.secretKeys
                    .asSequence()
                    .mapNotNull { secretKey -> secretsList.firstOrNull { it.key == secretKey } }
                    .flatMap { it.entries.asSequence() }
                    .firstOrNull { it.active }
                    ?: providerDefinition.secretKeys
                        .asSequence()
                        .mapNotNull { secretKey -> secretsList.firstOrNull { it.key == secretKey } }
                        .flatMap { it.entries.asSequence() }
                        .firstOrNull()
                apiKey = firstEntry?.value ?: ""
                initialApiKey = apiKey
                
                isLoadingModels = true
                modelsList = client.fetchModels(providerId)
                isLoadingModels = false
            }.onFailure {
                onShowMessage("加载配置失败：${it.message}")
            }
        }
    }
    
    val displayName = providerDefinition.label
    
    PrototypeBackRoot(
        title = "$displayName 配置",
        onBack = onBack,
        modifier = modifier,
        actions = {
            PrototypeIconButton(
                icon = Icons.Filled.Save,
                contentDescription = "保存配置",
                onClick = {
                    scope.launch {
                        runCatching {
                            val client = TavernCoreClient(baseUrl)
                            val updatedSettings = settingsWithSelectedApiProvider(
                                settings = settings,
                                provider = providerDefinition
                            ).toMutableMap()

                            val modelGroup = providerDefinition.modelSettingsGroup
                            val modelKey = providerDefinition.modelKey
                            val groupSettings = if (!modelGroup.isNullOrBlank()) {
                                (updatedSettings[modelGroup] as? Map<*, *>)
                                    ?.entries
                                    ?.associate { (key, value) -> key.toString() to value }
                                    ?.toMutableMap()
                                    ?: mutableMapOf()
                            } else null

                            if (groupSettings != null && !modelKey.isNullOrBlank()) {
                                groupSettings[modelKey] = selectedModel
                            }

                            when (providerDefinition.mode) {
                                "cc" -> {
                                    if (groupSettings != null) {
                                        if (customUrl.isNotBlank()) groupSettings["reverse_proxy"] = customUrl
                                        groupSettings["temp"] = temp
                                        groupSettings["openai_max_context"] = contextSize.toInt()
                                    }
                                }
                                "tc" -> {
                                    if (customUrl.isNotBlank()) updatedSettings["api_server"] = customUrl
                                    if (groupSettings != null) {
                                        groupSettings["temp"] = temp
                                        groupSettings["max_context"] = contextSize.toInt()
                                    }
                                }
                            }

                            if (groupSettings != null && !modelGroup.isNullOrBlank()) {
                                updatedSettings[modelGroup] = groupSettings
                            }

                            client.saveSettings(updatedSettings)

                            if (apiKey != initialApiKey && providerDefinition.secretKeys.isNotEmpty()) {
                                client.writeSecret(providerDefinition.secretKeys.first(), apiKey, "默认密钥")
                            }

                            onShowMessage("配置已成功保存！")
                            onBack()
                        }.onFailure {
                            onShowMessage("保存失败：${it.message}")
                        }
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "连接状态",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        val configured = providerState?.hasConfiguredSecret == true
                        val statusText = when {
                            !running -> "服务未启动"
                            configured -> "密钥已配置，尚未验证"
                            else -> "未配置密钥"
                        }
                        val statusOk = running && configured
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (statusOk) STThemeTertiary else STThemeError, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (statusOk) STThemeTertiary else STThemeError
                        )
                    }
                }
            }
            
            HorizontalDivider(color = Color(0x0DFFFFFF), modifier = Modifier.padding(vertical = 8.dp))
            
            PremiumSectionHeader(title = "端点设置")
            
            PrototypeGlassTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = if (providerDefinition.mode == "cc") "反向代理 URL (Reverse Proxy)" else "后端服务器地址",
                placeholder = if (providerDefinition.mode == "cc") "https://api.openai.com/v1" else "http://127.0.0.1:5000"
            )
            
            PrototypeGlassTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = "API 密钥 (API Key)",
                placeholder = "sk-...",
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            
            HorizontalDivider(color = Color(0x0DFFFFFF), modifier = Modifier.padding(vertical = 12.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "可用模型列表",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                val angle = remember { androidx.compose.animation.core.Animatable(0f) }
                val isRefreshing = remember { mutableStateOf(false) }
                IconButton(
                    onClick = {
                        if (!isRefreshing.value && running) {
                            scope.launch {
                                isRefreshing.value = true
                                angle.animateTo(
                                    targetValue = angle.value + 360f,
                                    animationSpec = tween(800, easing = LinearEasing)
                                )
                                runCatching {
                                    val client = TavernCoreClient(baseUrl)
                                    modelsList = client.fetchModels(providerId)
                                    onShowMessage("模型列表已成功刷新！")
                                }.onFailure {
                                    onShowMessage("模型刷新失败：${it.message}")
                                }
                                isRefreshing.value = false
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "刷新模型列表",
                        tint = STThemePrimary,
                        modifier = Modifier.rotate(angle.value)
                    )
                }
            }
            
            if (modelsList.isEmpty()) {
                Text(
                    text = "暂无可用模型，请点击刷新获取",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                PrototypeListSurface(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    modelsList.forEachIndexed { index, model ->
                        val isSelected = model == selectedModel
                        PrototypeListItem(
                            headline = model,
                            supporting = if (isSelected) "当前选中" else null,
                            leading = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "已选中",
                                        tint = STThemePrimary
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(24.dp))
                                }
                            },
                            divider = index != modelsList.lastIndex,
                            onClick = { selectedModel = model }
                        )
                    }
                }
            }
            
            HorizontalDivider(color = Color(0x0DFFFFFF), modifier = Modifier.padding(vertical = 12.dp))
            
            PremiumSectionHeader(title = "模型并发与参数重载")
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "推理温度 (Temperature)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f", temp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = STThemePrimary
                    )
                }
                Slider(
                    value = temp,
                    onValueChange = { temp = it },
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = STThemePrimary,
                        activeTrackColor = STThemePrimary,
                        inactiveTrackColor = Color(0x0DFFFFFF)
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "最大上下文窗口 (Context Window)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${contextSize.toInt()} tokens",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = STThemePrimary
                    )
                }
                Slider(
                    value = contextSize,
                    onValueChange = { contextSize = it },
                    valueRange = 2048f..200000f,
                    colors = SliderDefaults.colors(
                        thumbColor = STThemePrimary,
                        activeTrackColor = STThemePrimary,
                        inactiveTrackColor = Color(0x0DFFFFFF)
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun Map<*, *>?.stringValue(key: String): String {
    return (this?.get(key) as? String).orEmpty()
}

private fun Map<*, *>?.floatValue(key: String, default: Float): Float {
    return when (val value = this?.get(key)) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull() ?: default
        else -> default
    }
}

private fun Map<*, *>?.intValue(key: String, default: Int): Int {
    return when (val value = this?.get(key)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }
}
