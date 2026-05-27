package io.github.sanitised.st.ui.prototype

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        }
        PrototypeSectionHeader(
            title = "本对话生效",
            trailing = {
                Text("${books.count { true }.coerceAtMost(2)} 已激活", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        )
        PrototypeListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
            val rows = books.ifEmpty {
                listOf(
                    WorldInfoSummary("cafe", "常去的咖啡馆"),
                    WorldInfoSummary("galaxy", "星舰联邦 (远征卷)"),
                    WorldInfoSummary("london", "布鲁姆斯伯里 1887")
                )
            }
            rows.forEachIndexed { index, book ->
                PrototypeListItem(
                    headline = book.name,
                    supporting = if (index == 0) "12 条目 · 与当前角色相关的世界设定" else "关键词触发 · 可按需注入",
                    leading = {
                        PrototypeTileIcon(
                            icon = Icons.Filled.AutoStories,
                            tint = if (index < 2) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (index < 2) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = { Switch(checked = index < 2, onCheckedChange = null) },
                    divider = index != rows.lastIndex,
                    onClick = { onShowMessage("世界书详情稍后接入") }
                )
            }
        }
        PrototypeSectionHeader(title = "常用条目预览")
        val entries = firstBook?.entries.orEmpty()
        if (entries.isEmpty()) {
            LoreEntryPreview("焦糖海盐蛋糕, 招牌蛋糕", "咖啡馆店长每周一现做。售完为止。Aria 会偷偷给熟客留一块。", true, false)
            LoreEntryPreview("常客, 老顾客", "咖啡馆的常客包括每天来读报的退休医生、一对中学生情侣，以及周三总迟到的小说家。", false, true)
        } else {
            entries.take(4).forEach { entry ->
                LoreEntryPreview(
                    keys = entry.keys.joinToString(", ").ifBlank { entry.comment.ifBlank { "未命名条目" } },
                    content = entry.content,
                    constant = entry.constant,
                    selective = entry.selective
                )
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
    var personas by remember { mutableStateOf<List<PersonaProfile>>(emptyList()) }
    val running = status.state == NodeState.RUNNING
    LaunchedEffect(running, baseUrl) {
        if (running) {
            runCatching { TavernCoreClient(baseUrl).listPersonas() }
                .onSuccess { personas = it }
                .onFailure { onShowMessage(it.message ?: "扮演者加载失败") }
        }
    }
    PrototypeBackRoot(title = "扮演者", onBack = onBack, modifier = modifier, actions = {
        PrototypeIconButton(Icons.Filled.Add, "新建", { onShowMessage("新建扮演者稍后接入") })
    }) {
        Text(
            text = "模型会用“你”扮演的身份来回应。可以为不同角色绑定不同扮演者。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        PrototypeSectionHeader("当前激活")
        val fallback = listOf(PersonaProfile("me.png", "我（默认）", description = "一名普通的常客，下班顺路。", isDefault = true))
        (personas.ifEmpty { fallback }).sortedByDescending { it.isDefault }.forEachIndexed { index, persona ->
            PrototypePersonaRow(persona, active = persona.isDefault || index == 0)
        }
    }
}

@Composable
fun PrototypeAISettingsScreen(
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PrototypeBackRoot(title = "AI 设置", onBack = onBack, modifier = modifier, actions = {
        PrototypeIconButton(Icons.Filled.RestartAlt, "重置", { onShowMessage("已恢复默认预设") })
        PrototypeIconButton(Icons.Filled.Save, "保存", { onShowMessage("采样预设保存稍后接入") })
    }) {
        PrototypePresetCard()
        PrototypeSectionHeader("提示模板", trailing = {
            Text("仅文本补全", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        })
        PrototypeTemplateRow(Icons.Filled.Code, "Instruct 模板", "ChatML", "开 · 角色名 / 系统提示遵循模型格式", toggle = true)
        PrototypeTemplateRow(Icons.Filled.Bookmarks, "上下文模板", "Default", "角色描述 + 场景 + 历史的组织方式")
        PrototypeTemplateRow(Icons.Filled.Tune, "系统提示", "角色扮演 v3", "开 · 注入到对话最前", toggle = true, checked = true)
        PrototypeSliderSection("核心采样", listOf("温度 Temperature" to 1.05f, "Top P" to 0.92f, "Top K" to 0.20f, "Min P" to 0.05f))
        PrototypeSliderSection("重复抑制", listOf("频率惩罚" to 0.50f, "存在惩罚" to 0.30f, "重复惩罚范围" to 0.25f))
        PrototypeSectionHeader("高级 — 极少改动")
        PrototypeSwitchRow("启用流式输出", "边生成边显示", true)
        PrototypeSwitchRow("禁止思考链泄露", "过滤掉 <think> 标签内容", true)
        PrototypeSwitchRow("DRY (动态重复抑制)", "抗循环更激进的算法", false)
    }
}

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
    PrototypeBackRoot(title = "API 连接", onBack = onBack, modifier = modifier, actions = {
        PrototypeIconButton(Icons.Filled.Help, "帮助", { onShowMessage("API 帮助稍后接入") })
    }) {
        PrototypeConnectionProfileCard(profiles = profiles)
        PrototypeModeControl()
        PrototypeSectionHeader("当前激活")
        PrototypeActiveConnectionCard(connectedCount = secrets.sumOf { it.entries.size }.coerceAtLeast(3))
        PrototypeSectionHeader("聊天补全 — 提供商", trailing = {
            Text("9 个", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        })
        ProviderGrid()
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
    PrototypeRoot(modifier = modifier) {
        PrototypeTopHeader(
            title = "我的",
            leading = { PrototypeIconButton(Icons.Filled.Menu, "打开抽屉", openDrawer) },
            actions = { PrototypeIconButton(Icons.Filled.Search, "搜索设置", { onShowMessage("搜索设置稍后接入") }) }
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PrototypeAvatar("我", size = 56.dp, ringColor = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("我（默认）", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text("已使用 SillyTavern · 本地移动客户端", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PrototypeSectionHeader("外观")
        PrototypeSettingsGroup {
            PrototypeNavRow(Icons.Filled.Palette, "主题", "模式：${themeMode.storageValue} · 色彩：${colorSource.storageValue}") {
                val next = if (colorSource == ThemeColorSource.BRAND) ThemeColorSource.DYNAMIC else ThemeColorSource.BRAND
                onColorSourceChanged(next)
            }
            PrototypeNavRow(Icons.Filled.TextFields, "字号", "15 sp · 中") { onShowMessage("字号设置稍后接入") }
            PrototypeNavRow(Icons.Filled.Image, "聊天背景", "夜雨咖啡馆") { onShowMessage("聊天背景稍后接入") }
            PrototypeSwitchRow("消息冒泡风格", "关闭则使用全宽文档样式", true)
        }
        PrototypeSectionHeader("行为")
        PrototypeSettingsGroup {
            PrototypeSwitchRow("启动时自动连接 API", null, autoOpenBrowserEnabled, onAutoOpenBrowserChanged)
            PrototypeSwitchRow("自动检查更新", "当前通道：${channel.storageValue}", autoCheckEnabled, onAutoCheckChanged)
            PrototypeNavRow(Icons.Filled.Backup, "电池后台权限", if (isBatteryUnrestricted) "已允许后台持续运行" else "建议加入电池白名单", onOpenBatterySettings)
            PrototypeNavRow(Icons.Filled.Refresh, "立即检查更新", if (isChecking) "检查中…" else "手动触发一次", onCheckNow)
        }
        PrototypeSectionHeader("数据与核心")
        PrototypeSettingsGroup {
            PrototypeNavRow(Icons.Filled.Face, "扮演者", "切换当前用户身份", onOpenPersona)
            PrototypeNavRow(Icons.Filled.Tune, "AI 采样设置", "温度、Top P、提示模板", onOpenPresets)
            PrototypeNavRow(Icons.Filled.Cable, "API 连接", "提供商、密钥、连接预设", onOpenConnections)
            PrototypeNavRow(Icons.Filled.AutoStories, "世界书", "关键词触发与常驻条目", onOpenWorldInfo)
            PrototypeNavRow(Icons.Filled.CloudSync, "记忆与回顾", "聊天备份与检查点", onOpenChatBackups)
            PrototypeNavRow(Icons.Filled.Settings, "ST 内核", "服务、内核版本、备份、日志", onOpenManageSt)
            PrototypeNavRow(Icons.Filled.Code, "配置文件", "端口、启动参数", onOpenConfig)
            PrototypeNavRow(Icons.Filled.Info, "运行日志", "stdout · stderr · service", onOpenLogs)
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
        PrototypeListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
            val rows = backups.ifEmpty {
                listOf(ChatBackupSummary("aria-branch.jsonl", "18 KB", 42, "那我多加了一份饼干哦。", "3 天前"))
            }
            rows.forEachIndexed { index, item ->
                PrototypeListItem(
                    headline = item.fileName,
                    supporting = "${item.messageCount} 消息 · ${item.fileSize} · ${item.lastMessage}",
                    leading = { PrototypeTileIcon(Icons.Filled.CloudSync) },
                    divider = index != rows.lastIndex,
                    onClick = { onShowMessage("备份详情稍后接入") }
                )
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
    PrototypeBackRoot(title = "ST 核心", onBack = onBack, modifier = modifier, actions = {
        PrototypeIconButton(Icons.Filled.MoreVert, "更多", { onShowMessage("更多内核操作稍后接入") })
    }) {
        PrototypeStStatusHero(status, stLabel, nodeLabel, onStartService, onStopService, onOpenBrowser)
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
            PrototypeNavRow(Icons.Filled.Download, "内核版本", if (isCustomInstalled) "自定义 · ${customInstalledLabel.orEmpty()}" else "自带 · $stLabel", onLoadCustomZip)
            PrototypeNavRow(Icons.Filled.Upload, "数据备份", "导出或导入完整数据", onExport)
            PrototypeNavRow(Icons.Filled.FileDownload, "导入备份", "恢复 tar.gz / zip", onImport)
            PrototypeNavRow(Icons.Filled.Info, "运行日志", "stdout · stderr · service", onShowLogs)
            PrototypeNavRow(Icons.Filled.RestartAlt, "恢复自带内核", "清理自定义版本", onResetToDefault)
            PrototypeNavRow(Icons.Filled.Delete, "移除用户数据", "危险操作，需要二次确认", onRemoveUserData)
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
private fun LoreEntryPreview(keys: String, content: String, constant: Boolean, selective: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (constant) PrototypeBadge("常驻", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                if (selective) PrototypeBadge("关键词触发", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text("关键词", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            Text(keys, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun PrototypePersonaRow(persona: PersonaProfile, active: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = if (active) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PrototypeAvatar(persona.name, size = 48.dp, ringColor = if (active) MaterialTheme.colorScheme.primary else null)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(persona.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        PrototypeBadge("当前", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Text(persona.description.ifBlank { persona.title.ifBlank { "未填写描述" } }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PrototypePresetCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("采样预设", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("角色扮演 — 强", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("温度高、重复抑制中、Min-P 0.05", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            var value by remember(label) { mutableFloatStateOf(initial) }
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
            Slider(value = value, onValueChange = { value = it })
        }
    }
}

@Composable
private fun PrototypeSwitchRow(label: String, sub: String?, checked: Boolean, onChanged: (Boolean) -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
private fun PrototypeConnectionProfileCard(profiles: List<ConnectionProfile>) {
    val active = profiles.firstOrNull()?.label ?: "日常 — Claude Sonnet 4.5"
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PrototypeTileIcon(Icons.Filled.Bookmarks, tint = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("连接预设", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(active, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PrototypeBadge("${profiles.size.coerceAtLeast(4)} 个", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun PrototypeModeControl() {
    val modes = listOf("聊天补全", "文本补全", "Kobold", "NovelAI")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        modes.forEachIndexed { index, label ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = if (index == 0) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (index == 0) MaterialTheme.colorScheme.primary else Color.Transparent
                )
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun PrototypeActiveConnectionCard(connectedCount: Int) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrototypeAvatar("C", size = 40.dp, square = true, gradient = listOf(0xFF1A1A1A, 0xFF4A2700))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Claude (Anthropic)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("claude-sonnet-4.5 · 200k", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PrototypeStatusDot(MaterialTheme.colorScheme.tertiary)
            }
            Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrototypeStat("状态", "就绪", Modifier.weight(1f), ok = true)
                PrototypeStat("密钥", "$connectedCount 个", Modifier.weight(1f))
                PrototypeStat("延迟", "约 0.8s", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProviderGrid() {
    val providers = listOf("OpenAI" to "O", "Claude" to "C", "Google AI" to "G", "Mistral" to "M", "OpenRouter" to "⌥", "DeepSeek" to "D", "xAI Grok" to "X", "KoboldCpp" to "K", "Ollama" to "O")
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        providers.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEachIndexed { index, provider ->
                    val active = provider.first == "Claude"
                    Surface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large, color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                        Column(modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            PrototypeAvatar(provider.second, size = 36.dp, square = true)
                            Text(provider.first, style = MaterialTheme.typography.labelLarge, color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            Text(if (index == 1 || active) "已连接" else "未连接", style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(18.dp)) {
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = if (action == null) 0.dp else 12.dp))
            action?.invoke()
        }
    }
}

@Composable
private fun PrototypeBadge(label: String, containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(shape = MaterialTheme.shapes.small, color = containerColor, contentColor = contentColor) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
