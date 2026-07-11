@file:OptIn(ExperimentalMaterial3Api::class)

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
import io.github.sanitised.st.ThemeColorSource
import io.github.sanitised.st.ThemeMode
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun STApiConnectionScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSecrets: (() -> Unit)? = null,
    onOpenProviderDetail: (String) -> Unit = {},
    onSettingsChanged: () -> Unit = {}
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
                STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
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
                STIconButton(Icons.AutoMirrored.Filled.Help, "帮助", { onShowMessage("已打开 API 说明书") })
            }

            // Connection Profile Presets row card
            STConnectionProfileCard(
                profiles = profiles,
                selectedProfileName = selectedProfileName,
                onClick = { showProfileSheet = true }
            )

            // Dynamic API Mode Segmented Picker
            STModeControl(
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
                                onSettingsChanged()
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
            STActiveConnectionCard(
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
                                onSettingsChanged()
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
                STListItem(
                    headline = "自定义 OpenAI 兼容反代 (Reverse Proxy)",
                    supporting = "可自定义 API 根地址，完美适配自建 OneAPI",
                    leading = { STTileIcon(Icons.Filled.Code) },
                    onClick = { onShowMessage("自建配置功能开发中，可通过配置详细后端进行调整") }
                )
                STListItem(
                    headline = "KoboldAI Horde 共享池",
                    supporting = "使用社区免费志愿贡献者的 GPU 算力",
                    leading = { STTileIcon(Icons.Filled.Face) },
                    onClick = { onShowMessage("Horde 配置功能开发中") }
                )
            } else if (activeMode == "tc") {
                PremiumSectionHeader(title = "当前后端的指令模版契约")
                STListItem(
                    headline = "自动匹配 Instruct 模板",
                    supporting = "根据后端模型自动选择指令格式",
                    leading = { STTileIcon(Icons.Filled.Code) },
                    onClick = { onShowMessage("指令模板设置请在 AI 采样页中进行") }
                )
                STListItem(
                    headline = "上下文拼接模板 (Context Templates)",
                    supporting = "Default — 先行角色设定，后贴入场景描述",
                    leading = { STTileIcon(Icons.Filled.Bookmarks) },
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
private fun STConnectionProfileCard(
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
private fun STModeControl(
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
private fun STActiveConnectionCard(
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
            STIconButton(Icons.Filled.Settings, "配置详细后端", onConfigure)
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = Color(0x0DFFFFFF))
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            STStat(
                label = "连接状态", 
                value = connectionStatusText,
                tone = if (connectionStatusOk) "ok" else "error"
            )
            STStat(
                label = "密钥配置",
                value = activeSecretLabel ?: "${configuredProviderCount} 个已配置"
            )
            STStat(
                label = "连接验证",
                value = "尚未测试"
            )
        }
    }
}

@Composable
private fun RowScope.STStat(label: String, value: String, tone: String? = null) {
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
                STSystemInfoCard("暂无保存的服务器", "当前使用本地 SillyTavern 服务。")
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

