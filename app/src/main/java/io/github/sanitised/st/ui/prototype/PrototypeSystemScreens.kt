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
                PrototypeIconButton(Icons.Filled.FileDownload, "导入", { onShowMessage("世界书导入稍后接入") })
                PrototypeIconButton(Icons.Filled.Add, "新增", { onShowMessage("新增世界书条目稍后接入") })
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
                        onClick = { onShowMessage("世界书详情稍后接入") }
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
                        PrototypeIconButton(Icons.Filled.AutoFixHigh, "AI 生成", { onShowMessage("AI 生成扮演者稍后接入") })
                        PrototypeIconButton(Icons.Filled.Add, "新建", { onShowMessage("新建扮演者稍后接入") })
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
                onClick = { onShowMessage("新建扮演者稍后接入") },
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
    }

    PrototypeBackRoot(title = "AI 设置", onBack = onBack, modifier = modifier, actions = {
        PrototypeIconButton(Icons.Filled.RestartAlt, "重置", { onShowMessage("已恢复默认预设") })
        PrototypeIconButton(Icons.Filled.Save, "保存", { onShowMessage("采样参数已保存") })
    }) {
        PrototypePresetCard(
            presetLibrary = presetLibrary,
            onSelectPreset = { apiId, name ->
                scope.launch {
                    try {
                        TavernCoreClient(baseUrl).selectPreset(apiId, name)
                        onShowMessage("已切换预设：$name")
                        loadPresets()
                    } catch (e: Exception) {
                        onShowMessage(e.message ?: "切换预设失败")
                    }
                }
            }
        )
        PrototypeSectionHeader("提示模板", trailing = {
            Text("仅文本补全", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        })
        PrototypeTemplateRow(Icons.Filled.Code, "Instruct 模板", "ChatML", "开 · 角色名 / 系统提示遵循模型格式", toggle = true)
        PrototypeTemplateRow(Icons.Filled.Bookmarks, "上下文模板", "Default", "角色描述 + 场景 + 历史的组织方式")
        PrototypeTemplateRow(Icons.Filled.Tune, "系统提示", "角色扮演 v3", "开 · 注入到对话最前", toggle = true, checked = true)
        PrototypeTemplateRow(Icons.Filled.StickyNote2, "作者注 / 深度笔记", "未设置", "按需注入到指定深度")
        PrototypeSliderSection("核心采样", listOf("温度 Temperature" to 1.05f, "Top P" to 0.92f, "Top K" to 0.20f, "Min P" to 0.05f))
        PrototypeSliderSection("重复抑制", listOf("频率惩罚" to 0.50f, "存在惩罚" to 0.30f, "重复惩罚范围" to 0.25f))
        PrototypeSectionHeader("响应控制", trailing = {
            Text("展开", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        })
        PrototypeSliderSection("响应参数", listOf("最大新 Token 数" to 0.25f, "上下文窗口" to 0.16f))
        PrototypeSectionHeader("高级 — 极少改动")
        PrototypeSwitchRow("启用流式输出", "边生成边显示", true)
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
    modifier: Modifier = Modifier
) {
    var profiles by remember { mutableStateOf<List<ConnectionProfile>>(emptyList()) }
    var secrets by remember { mutableStateOf<List<SecretProviderState>>(emptyList()) }
    val running = status.state == NodeState.RUNNING
    LaunchedEffect(running, baseUrl) {
        if (running) {
            runCatching {
                val client = TavernCoreClient(baseUrl)
                profiles = client.listConnectionProfiles()
                secrets = client.listSecrets()
            }.onFailure { onShowMessage(it.message ?: "API 连接加载失败") }
        }
    }

    var activeMode by remember { mutableStateOf("cc") }
    var activeProvider by remember(activeMode) {
        mutableStateOf(if (activeMode == "cc") "Claude" else if (activeMode == "tc") "KoboldCpp" else "")
    }
    var selectedProfileId by remember { mutableStateOf("daily") }
    var selectedProfileName by remember { mutableStateOf("日常 — Claude Sonnet 4.5") }
    var showProfileSheet by remember { mutableStateOf(false) }

    PrototypeBackRoot(title = "API 连接", onBack = onBack, modifier = modifier, actions = {
        PrototypeIconButton(Icons.Filled.Help, "帮助", { onShowMessage("API 帮助稍后接入") })
    }) {
        PrototypeConnectionProfileCard(
            profiles = profiles,
            selectedProfileName = selectedProfileName,
            onClick = { showProfileSheet = true }
        )
        PrototypeModeControl(
            activeMode = activeMode,
            onModeChange = { activeMode = it }
        )
        PrototypeSectionHeader("当前激活")
        PrototypeActiveConnectionCard(
            activeMode = activeMode,
            connectedCount = secrets.sumOf { it.entries.size }.coerceAtLeast(3)
        )
        PrototypeSectionHeader(
            title = if (activeMode == "tc") "文本补全 — 后端" else "聊天补全 — 提供商",
            trailing = {
                Text("9 个", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        )
        ProviderGrid(
            activeMode = activeMode,
            activeProvider = activeProvider,
            onProviderChange = { activeProvider = it }
        )

        if (activeMode == "cc") {
            PrototypeSectionHeader("本地 / 自建")
            PrototypeListItem(
                headline = "自定义 OpenAI 兼容",
                supporting = "任意 base URL + API key · 适合自建反代",
                leading = { PrototypeTileIcon(Icons.Filled.Code) },
                onClick = { onShowMessage("自建配置稍后接入") }
            )
            PrototypeSectionHeader("免费")
            PrototypeListItem(
                headline = "KoboldAI Horde",
                supporting = "社区共享算力 · 排队中",
                leading = { PrototypeTileIcon(Icons.Filled.Face) },
                onClick = { onShowMessage("Horde 状态：排队中") }
            )
        } else if (activeMode == "tc") {
            PrototypeSectionHeader("当前后端的格式")
            PrototypeListItem(
                headline = "Instruct 模板",
                supporting = "Mistral V3 — Tekken · [INST] / [/INST] 包裹",
                leading = { PrototypeTileIcon(Icons.Filled.Code) },
                onClick = { onShowMessage("Instruct 模板设置稍后接入") }
            )
            PrototypeListItem(
                headline = "上下文模板",
                supporting = "Default — 角色卡 + 场景 + 历史",
                leading = { PrototypeTileIcon(Icons.Filled.Bookmarks) },
                onClick = { onShowMessage("上下文模板设置稍后接入") }
            )
        }

        if (showProfileSheet) {
            ConnectionProfileBottomSheet(
                apiProfiles = profiles,
                onDismiss = { showProfileSheet = false },
                selectedProfileId = selectedProfileId,
                onProfileSelected = { id, name, mode ->
                    selectedProfileId = id
                    selectedProfileName = name
                    activeMode = mode
                    activeProvider = if (mode == "cc") {
                        if (id == "daily" || id == "long" || id.contains("claude", ignoreCase = true)) "Claude" else "OpenAI"
                    } else if (mode == "tc") {
                        "KoboldCpp"
                    } else {
                        ""
                    }
                    showProfileSheet = false
                }
            )
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
    onOpenWorldInfo: () -> Unit,
    onOpenPersona: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenChatBackups: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenManageSt: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val openDrawer = LocalSTOpenDrawer.current
    var messageBubbleStyle by remember { mutableStateOf(true) }
    var vibrationFeedback by remember { mutableStateOf(false) }
    var secondConfirmation by remember { mutableStateOf(true) }
    var swipeDrawer by remember { mutableStateOf(true) }
    var enableExtensions by remember { mutableStateOf(true) }
    var developerMode by remember { mutableStateOf(false) }

    PrototypeRoot(modifier = modifier) {
        PrototypeTopHeader(
            title = "我的",
            leading = { PrototypeIconButton(Icons.Filled.Menu, "打开抽屉", openDrawer) },
            actions = { PrototypeIconButton(Icons.Filled.Search, "搜索设置", { onShowMessage("搜索设置稍后接入") }) }
        )
        // User card with chevron - direct and simple borderless style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowMessage("用户资料稍后接入") }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrototypeAvatar("我", size = 56.dp, ringColor = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("我（默认）", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("已使用 SillyTavern · 142 天 · 2.4M token", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                supporting = "暖琥珀 · ${themeMode.storageValue}",
                leading = { PrototypeTileIcon(Icons.Filled.Palette) },
                trailing = { PrototypeMiniSwatch() },
                divider = true,
                onClick = {
                    val next = if (colorSource == ThemeColorSource.BRAND) ThemeColorSource.DYNAMIC else ThemeColorSource.BRAND
                    onColorSourceChanged(next)
                }
            )
            PrototypeListItem(
                headline = "字号",
                supporting = "15 sp · 中",
                leading = { PrototypeTileIcon(Icons.Filled.TextFields) },
                divider = true,
                onClick = { onShowMessage("字号设置稍后接入") }
            )
            PrototypeListItem(
                headline = "聊天背景",
                supporting = "夜雨咖啡馆",
                leading = { PrototypeTileIcon(Icons.Filled.Image) },
                divider = true,
                onClick = { onShowMessage("聊天背景稍后接入") }
            )
            PrototypeSwitchRow("消息冒泡风格", "关闭则使用全宽文档样式", messageBubbleStyle, { messageBubbleStyle = it })
        }
        // ── 行为 ──
        PrototypeSectionHeader("行为")
        PrototypeSettingsGroup {
            PrototypeSwitchRow("流式生成时震动反馈", "逐字到达时轻微震动", vibrationFeedback, { vibrationFeedback = it })
            PrototypeSwitchRow("敏感操作二次确认", "删除消息、清空对话等", secondConfirmation, { secondConfirmation = it })
            PrototypeSwitchRow("启动时自动连接 API", null, autoOpenBrowserEnabled, onAutoOpenBrowserChanged)
            PrototypeSwitchRow("滑动呼出抽屉", "从左边缘横扫", swipeDrawer, { swipeDrawer = it })
        }
        // ── 数据 ──
        PrototypeSectionHeader("数据")
        PrototypeSettingsGroup {
            PrototypeListItem(
                headline = "自动备份",
                supporting = "每周 · 上次：3 天前",
                leading = { PrototypeTileIcon(Icons.Filled.Backup) },
                trailing = { Switch(checked = true, onCheckedChange = null) },
                divider = true,
                onClick = { onShowMessage("备份设置稍后接入") }
            )
            PrototypeListItem(
                headline = "同步",
                supporting = "未开启",
                leading = { PrototypeTileIcon(Icons.Filled.CloudSync) },
                divider = true,
                onClick = { onShowMessage("同步设置稍后接入") }
            )
            PrototypeListItem(
                headline = "导出全部数据",
                supporting = ".charx + .json 包",
                leading = { PrototypeTileIcon(Icons.Filled.FolderZip) },
                onClick = { onShowMessage("数据导出稍后接入") }
            )
        }
        // ── 实验性 ──
        PrototypeSectionHeader("实验性")
        PrototypeSettingsGroup {
            PrototypeSwitchRow("启用扩展", "6 个已安装", enableExtensions, { enableExtensions = it })
            PrototypeSwitchRow("开发者模式", "显示 token 计数与请求 JSON", developerMode, { developerMode = it })
        }
        // ── 关于 ──
        PrototypeSectionHeader("关于")
        PrototypeSettingsGroup {
            PrototypeListItem(
                headline = "SillyTavern Mobile",
                supporting = "1.13.0 · 第三方移动客户端",
                leading = { PrototypeTileIcon(Icons.Filled.Info) },
                onClick = { onShowMessage("版本信息") }
            )
        }
    }
}

@Composable
fun PrototypeMemoryScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var backups by remember { mutableStateOf<List<ChatBackupSummary>>(emptyList()) }
    val running = status.state == NodeState.RUNNING
    LaunchedEffect(running, baseUrl) {
        if (running) {
            runCatching { backups = TavernCoreClient(baseUrl).listChatBackups() }
                .onFailure { onShowMessage(it.message ?: "备份列表加载失败") }
        }
    }
    PrototypeBackRoot(title = "记忆与回顾", onBack = onBack, modifier = modifier) {
        PrototypeSectionHeader("聊天备份")
        if (!running) {
            PrototypeSystemInfoCard("本地服务未启动", "启动 SillyTavern 服务后可加载和管理聊天历史备份。")
        } else if (backups.isEmpty()) {
            PrototypeSystemInfoCard("暂无聊天备份", "可在与角色聊天中进行手动备份或启用自动备份。")
        } else {
            PrototypeListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                backups.forEachIndexed { index, item ->
                    PrototypeListItem(
                        headline = item.fileName,
                        supporting = "${item.messageCount} 消息 · ${item.fileSize} · ${item.lastMessage}",
                        leading = { PrototypeTileIcon(Icons.Filled.CloudSync) },
                        divider = index != backups.lastIndex,
                        onClick = { onShowMessage("备份详情稍后接入") }
                    )
                }
            }
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

    var autoStartService by remember { mutableStateOf(true) }
    var autoOpenBrowser by remember { mutableStateOf(true) }
    var allowBackgroundRun by remember { mutableStateOf(true) }

    PrototypeBackRoot(title = "ST 核心", onBack = onBack, modifier = modifier, actions = {
        PrototypeIconButton(Icons.Filled.MoreVert, "更多", { onShowMessage("更多内核操作稍后接入") })
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
                            text = if (running) "${status.pid ?: 21487}" else "—",
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
                trailing = { Switch(checked = autoStartService, onCheckedChange = { autoStartService = it }) },
                divider = true,
                onClick = { autoStartService = !autoStartService }
            )
            PrototypeListItem(
                headline = "服务就绪后自动打开浏览器",
                supporting = "对 :8000 进行 TCP 探测后跳转",
                trailing = { Switch(checked = autoOpenBrowser, onCheckedChange = { autoOpenBrowser = it }) },
                divider = true,
                onClick = { autoOpenBrowser = !autoOpenBrowser }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrototypeTileIcon(Icons.Filled.Bookmarks, tint = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("连接预设", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(selectedProfileName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PrototypeBadge("${profiles.size.coerceAtLeast(4)} 个", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "API 模式",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            modes.forEach { (modeId, label) ->
                val sel = modeId == activeMode
                Surface(
                    onClick = { onModeChange(modeId) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    color = if (sel) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (sel) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val desc = when (activeMode) {
            "tc" -> "当前：Text Completion · 适合本地推理 / 微调模型。会启用 Instruct 模板和原始 prompt 拼接。"
            "cc" -> "当前：Chat Completion · 适合云端商用模型。按 messages 数组发送，不需要 Instruct 模板。"
            "kobold" -> "当前：KoboldAI Classic · 适合老版本 Horde / United 等经典格式。"
            "novel" -> "当前：NovelAI · 适合 Kayra / Erato 等小说写作专用大模型。"
            else -> ""
        }
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrototypeActiveConnectionCard(
    activeMode: String,
    connectedCount: Int
) {
    val title = when (activeMode) {
        "cc" -> "Claude (Anthropic)"
        "tc" -> "KoboldCpp · 本地"
        "kobold" -> "KoboldAI Horde · 社区"
        "novel" -> "NovelAI Erato"
        else -> "未选择"
    }
    val subtitle = when (activeMode) {
        "cc" -> "claude-sonnet-4.5 · 200k"
        "tc" -> "Mistral-Nemo-Instruct-2407.Q5_K_M · 32k"
        "kobold" -> "Horde Community Models"
        "novel" -> "Erato · 8k 上下文"
        else -> ""
    }
    val avatarLabel = when (activeMode) {
        "cc" -> "C"
        "tc" -> "K"
        "kobold" -> "H"
        "novel" -> "N"
        else -> "?"
    }
    val avatarGradient = when (activeMode) {
        "cc" -> listOf(0xFF1A1A1A, 0xFF4A2700)
        "tc" -> listOf(0xFF102A1F, 0xFF004A27)
        "kobold" -> listOf(0xFF2A102A, 0xFF4A004A)
        "novel" -> listOf(0xFF2A2A10, 0xFF4A4A00)
        else -> listOf(0xFF1A1A1A, 0xFF4A2700)
    }
    val status = when (activeMode) {
        "kobold" -> "排队中"
        else -> "就绪"
    }
    val statusColor = when (activeMode) {
        "kobold" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.tertiary
    }
    val latency = when (activeMode) {
        "cc" -> "约 0.8s"
        "tc" -> "12 tok/s"
        "kobold" -> "约 8s"
        "novel" -> "约 0.5s"
        else -> "—"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrototypeAvatar(avatarLabel, size = 40.dp, square = true, gradient = avatarGradient)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrototypeStatusDot(statusColor)
            Spacer(Modifier.width(6.dp))
            Text(status, style = MaterialTheme.typography.labelSmall, color = statusColor)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PrototypeStat("状态", status, ok = status == "就绪")
            PrototypeStat(if (activeMode == "cc") "密钥" else "上下文", if (activeMode == "cc") "$connectedCount 个" else if (activeMode == "tc") "32k" else if (activeMode == "novel") "8k" else "社区共享")
            PrototypeStat("延迟", latency)
        }
    }
}

@Composable
private fun ProviderGrid(
    activeMode: String,
    activeProvider: String,
    onProviderChange: (String) -> Unit
) {
    val ccProviders = listOf(
        "OpenAI" to "O", "Claude" to "C", "Google AI" to "G",
        "Mistral" to "M", "OpenRouter" to "⌥", "DeepSeek" to "D",
        "xAI Grok" to "X", "Cohere" to "C", "Perplexity" to "P"
    )
    val tcProviders = listOf(
        "KoboldCpp" to "K", "Text-Gen WebUI" to "T", "TabbyAPI" to "τ",
        "Aphrodite" to "φ", "Mancer" to "M", "Featherless" to "F",
        "Horde (文本)" to "H", "llama.cpp" to "L", "Ollama" to "O"
    )
    val providers = if (activeMode == "tc") tcProviders else ccProviders

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        providers.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                row.forEach { (name, icon) ->
                    val active = name == activeProvider
                    Surface(
                        onClick = { onProviderChange(name) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PrototypeAvatar(icon, size = 36.dp, square = true)
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                text = if (active || name == "OpenAI" || name == "llama.cpp" || name == "KoboldCpp") "已连接" else "未连接",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
    selectedProfileId: String,
    onProfileSelected: (String, String, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val displayProfiles = remember(apiProfiles) {
        if (apiProfiles.isNotEmpty()) {
            apiProfiles.map { profile ->
                val mode = if (profile.url.contains("kobold", ignoreCase = true) || profile.url.contains("127.0.0.1", ignoreCase = true)) "tc" else "cc"
                Triple(profile.label, profile.label, mode)
            }
        } else {
            listOf(
                Triple("daily", "日常 — Claude Sonnet 4.5", "cc"),
                Triple("long", "长篇写作 — Claude Opus", "cc"),
                Triple("local", "本地 — KoboldCpp · Mistral-Nemo", "tc"),
                Triple("free", "免费 — Horde", "kobold"),
                Triple("novel", "小说 — NovelAI Erato", "novel")
            )
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("切换连接预设", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("每个预设保存 API 模式 + 模型 + 采样器组合。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                displayProfiles.forEach { (id, name, mode) ->
                    val active = id == selectedProfileId
                    Surface(
                        onClick = {
                            onProfileSelected(id, name, mode)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = if (active) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = mode.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                val sub = when (mode) {
                                    "cc" -> "Chat Completion · Anthropic · 200k"
                                    "tc" -> "Text Completion · KoboldCpp · 32k"
                                    "kobold" -> "KoboldAI Horde · 排队约 8 秒"
                                    "novel" -> "NovelAI · 8k 上下文"
                                    else -> ""
                                }
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (active) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { /* 新建预设 */ }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("新建预设")
                }
                TextButton(onClick = { /* 导入/导出 */ }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Download, null)
                    Spacer(Modifier.width(6.dp))
                    Text("导入 / 导出")
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
