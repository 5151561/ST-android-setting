package io.github.sanitised.st

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.ChatSummary
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.ui.components.STInfoCard
import io.github.sanitised.st.ui.components.STOperationProgressCard
import io.github.sanitised.st.ui.prototype.PrototypeAvatar
import io.github.sanitised.st.ui.prototype.PrototypeListSurface
import io.github.sanitised.st.ui.prototype.PrototypeListItem
import io.github.sanitised.st.ui.prototype.PrototypeSectionHeader
import io.github.sanitised.st.ui.prototype.PrototypeStatusDot
import io.github.sanitised.st.ui.prototype.PrototypeTileIcon
import io.github.sanitised.st.ui.prototype.PrototypeTopHeader
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun STAndroidApp(
    status: NodeStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpen: () -> Unit,
    autoOpenBrowserWhenReady: Boolean,
    autoOpenBrowserTriggeredForCurrentRun: Boolean,
    onAutoOpenBrowserTriggered: () -> Unit,
    onShowLogs: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onEditConfig: () -> Unit,
    showNotificationPrompt: Boolean,
    showBatteryPrompt: Boolean,
    versionLabel: String,
    stLabel: String,
    nodeLabel: String,
    symlinkSupported: Boolean,
    onShowLegal: () -> Unit,
    showAutoCheckOptInPrompt: Boolean,
    onEnableAutoCheck: () -> Unit,
    onLaterAutoCheck: () -> Unit,
    onDismissBatteryPrompt: () -> Unit,
    showUpdatePrompt: Boolean,
    updateVersionLabel: String,
    updateDetails: String,
    isDownloadingUpdate: Boolean,
    downloadProgressPercent: Int?,
    isUpdateReadyToInstall: Boolean,
    onUpdatePrimary: () -> Unit,
    onUpdateDismiss: () -> Unit,
    onCancelUpdateDownload: () -> Unit,
    showBackupOperationCard: Boolean,
    backupOperationTitle: String,
    backupOperationDetails: String,
    backupOperationProgressPercent: Int?,
    showCustomOperationCard: Boolean,
    customOperationTitle: String,
    customOperationDetails: String,
    customOperationProgressPercent: Int?,
    customOperationCancelable: Boolean,
    onCancelCustomOperation: () -> Unit,
    onShowSettings: () -> Unit,
    onShowManageSt: () -> Unit,
    recentChats: List<ChatSummary> = emptyList(),
    recentCharacters: List<CharacterSummary> = emptyList(),
    onShowCharacters: () -> Unit = {}
) {
    val openDrawer = LocalSTOpenDrawer.current
    val readyState = remember { mutableStateOf(false) }
    val wasReadyToAutoOpen = remember { mutableStateOf(false) }
    LaunchedEffect(status.state, status.port) {
        if (status.state != NodeState.RUNNING) {
            readyState.value = false
            return@LaunchedEffect
        }
        readyState.value = false
        val client = TavernCoreClient(baseUrl = "http://127.0.0.1:${status.port}/")
        val deadline = System.currentTimeMillis() + 60_000L
        while (status.state == NodeState.RUNNING && !readyState.value && System.currentTimeMillis() < deadline) {
            val ok = withContext(Dispatchers.IO) {
                client.healthCheck().ok
            }
            if (ok) {
                readyState.value = true
                break
            }
            delay(1000)
        }
    }
    val readyToAutoOpen = status.state == NodeState.RUNNING && readyState.value
    LaunchedEffect(readyToAutoOpen, autoOpenBrowserWhenReady) {
        val justBecameReady = readyToAutoOpen && !wasReadyToAutoOpen.value
        if (autoOpenBrowserWhenReady && justBecameReady && !autoOpenBrowserTriggeredForCurrentRun) {
            onOpen()
            onAutoOpenBrowserTriggered()
        }
        wasReadyToAutoOpen.value = readyToAutoOpen
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                PrototypeTopHeader(
                    title = "对话",
                    leading = {
                        IconButton(onClick = openDrawer) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.settings_title)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpen) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.m3_search)
                            )
                        }
                        IconButton(onClick = onShowCharacters) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = null
                            )
                        }
                    }
                )

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("全部" to true, "收藏" to false, "进行中" to false, "群聊" to false).forEach { item ->
                        FilterChip(
                            selected = item.second,
                            onClick = {},
                            label = { Text(item.first) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                DashboardServiceHero(
                    status = status,
                    stLabel = stLabel,
                    nodeLabel = nodeLabel,
                    onStart = onStart,
                    onStop = onStop,
                    onOpenChat = onOpen,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                if (!symlinkSupported) {
                    STInfoCard(
                        title = "环境限制",
                        body = stringResource(R.string.symlink_not_supported),
                        borderColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
                    )
                }

                PrototypeSectionHeader(
                    title = stringResource(R.string.dashboard_recent_chats),
                    trailing = {
                        Text(
                            text = stringResource(R.string.app_version_label, versionLabel),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onShowLegal)
                        )
                    }
                )
                if (recentChats.isEmpty()) {
                    STInfoCard(
                        title = stringResource(R.string.dashboard_recent_chats_empty_title),
                        body = stringResource(R.string.dashboard_recent_chats_empty_body),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    PrototypeListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                        recentChats.forEachIndexed { index, chat ->
                            RecentChatListRow(
                                chat = chat,
                                divider = index != recentChats.lastIndex,
                                onOpen = onOpen
                            )
                        }
                    }
                }

                PrototypeSectionHeader(
                    title = stringResource(R.string.dashboard_recent_characters),
                    trailing = {
                        Text(
                            text = stringResource(R.string.dashboard_open_all),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onShowCharacters)
                        )
                    }
                )
                if (recentCharacters.isEmpty()) {
                    STInfoCard(
                        title = stringResource(R.string.dashboard_recent_characters_empty_title),
                        body = stringResource(R.string.dashboard_recent_characters_empty_body),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    PrototypeListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                        recentCharacters.take(4).forEachIndexed { index, character ->
                            RecentCharacterListRow(
                                character = character,
                                divider = index != recentCharacters.take(4).lastIndex,
                                onOpen = onShowCharacters
                            )
                        }
                    }
                }

                PrototypeSectionHeader(title = "ST 核心")
                PrototypeListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                    PrototypeListItem(
                        headline = stringResource(R.string.manage_st_title),
                        supporting = stringResource(R.string.tools_hub_manage_body),
                        leading = {
                            PrototypeTileIcon(icon = Icons.Filled.FolderOpen)
                        },
                        divider = true,
                        onClick = onShowManageSt
                    )
                    PrototypeListItem(
                        headline = stringResource(R.string.logs_title),
                        supporting = stringResource(R.string.tools_hub_logs_body),
                        leading = {
                            PrototypeTileIcon(icon = Icons.Filled.History)
                        },
                        divider = true,
                        onClick = onShowLogs
                    )
                    PrototypeListItem(
                        headline = stringResource(R.string.config_button_title),
                        supporting = stringResource(R.string.tools_hub_config_body),
                        leading = {
                            PrototypeTileIcon(icon = Icons.Filled.Dns)
                        },
                        onClick = onEditConfig
                    )
                }

                if (showAutoCheckOptInPrompt) {
                    AutoCheckOptInCard(
                        visible = true,
                        onEnable = onEnableAutoCheck,
                        onLater = onLaterAutoCheck
                    )
                }
                if (showNotificationPrompt) {
                    NotificationPermissionCard(
                        visible = true,
                        onOpenSettings = onOpenNotificationSettings
                    )
                }
                if (showBatteryPrompt) {
                    BatteryOptimizationCard(
                        visible = true,
                        onSet = onOpenBatterySettings,
                        onDismiss = onDismissBatteryPrompt
                    )
                }
                if (showUpdatePrompt) {
                    UpdatePromptCard(
                        visible = true,
                        versionLabel = updateVersionLabel,
                        details = updateDetails,
                        isDownloading = isDownloadingUpdate,
                        downloadProgressPercent = downloadProgressPercent,
                        isReadyToInstall = isUpdateReadyToInstall,
                        onPrimary = onUpdatePrimary,
                        onDismiss = onUpdateDismiss,
                        onCancelDownload = onCancelUpdateDownload
                    )
                }
                if (showBackupOperationCard) {
                    STOperationProgressCard(
                        title = backupOperationTitle,
                        details = backupOperationDetails,
                        progressPercent = backupOperationProgressPercent,
                        showCancel = false,
                        onCancel = {}
                    )
                }
                if (showCustomOperationCard) {
                    STOperationProgressCard(
                        title = customOperationTitle,
                        details = customOperationDetails,
                        progressPercent = customOperationProgressPercent,
                        showCancel = customOperationCancelable,
                        onCancel = onCancelCustomOperation
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            ExtendedFloatingActionButton(
                onClick = {
                    if (status.state == NodeState.RUNNING) onOpen() else onStart()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = if (status.state == NodeState.RUNNING) "新对话" else stringResource(R.string.start)
                    )
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun DashboardServiceHero(
    status: NodeStatus,
    stLabel: String,
    nodeLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isRunning = status.state == NodeState.RUNNING
    val isBusy = status.state == NodeState.STARTING || status.state == NodeState.STOPPING
    val statusColor = when (status.state) {
        NodeState.RUNNING -> colors.tertiary
        NodeState.STARTING, NodeState.STOPPING -> colors.secondary
        NodeState.ERROR -> colors.error
        NodeState.STOPPED -> colors.outline
    }
    val statusLabel = when (status.state) {
        NodeState.RUNNING -> stringResource(R.string.dashboard_status_running)
        NodeState.STARTING, NodeState.STOPPING -> stringResource(R.string.dashboard_status_busy)
        NodeState.ERROR -> stringResource(R.string.dashboard_status_error)
        NodeState.STOPPED -> stringResource(R.string.dashboard_status_stopped)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrototypeStatusDot(color = statusColor)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colors.surfaceContainerHigh,
                    contentColor = colors.onSurfaceVariant
                ) {
                    Text(
                        text = ":${status.port}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = when (status.state) {
                    NodeState.RUNNING -> "SillyTavern 正在为你运行"
                    NodeState.STARTING -> "正在唤醒 Node 服务…"
                    NodeState.STOPPING -> "正在停止本地服务…"
                    NodeState.ERROR -> "服务需要处理"
                    NodeState.STOPPED -> "SillyTavern 已停止"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface
            )
            Text(
                text = "$stLabel · $nodeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = if (isRunning) onOpenChat else onStart,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.AutoMirrored.Filled.OpenInNew else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isRunning) stringResource(R.string.dashboard_continue_chat) else stringResource(R.string.start))
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = status.state == NodeState.RUNNING || status.state == NodeState.STARTING,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.stop))
                }
            }
        }
    }
}

@Composable
private fun RecentChatListRow(
    chat: ChatSummary,
    divider: Boolean,
    onOpen: () -> Unit
) {
    PrototypeListItem(
        headline = chat.characterName,
        supporting = chat.lastMessage ?: stringResource(R.string.dashboard_recent_chat_no_preview),
        leading = {
            PrototypeAvatar(
                label = chat.characterName,
                size = 52.dp
            )
        },
        trailing = {
            if (chat.isPinned) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        divider = divider,
        onClick = onOpen
    )
}

@Composable
private fun RecentCharacterListRow(
    character: CharacterSummary,
    divider: Boolean,
    onOpen: () -> Unit
) {
    PrototypeListItem(
        headline = character.name,
        supporting = character.tags.take(3).joinToString(" / ").ifBlank {
            stringResource(R.string.character_hub_character_body)
        },
        leading = {
            PrototypeTileIcon(
                icon = Icons.Filled.Person,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        divider = divider,
        onClick = onOpen
    )
}

@Preview(showBackground = true)
@Composable
private fun STAndroidAppPreview() {
    STAndroidApp(
        status = NodeStatus(NodeState.STOPPED, "Idle"),
        onStart = {},
        onStop = {},
        onOpen = {},
        autoOpenBrowserWhenReady = false,
        autoOpenBrowserTriggeredForCurrentRun = false,
        onAutoOpenBrowserTriggered = {},
        onShowLogs = {},
        onOpenNotificationSettings = {},
        onOpenBatterySettings = {},
        onEditConfig = {},
        showNotificationPrompt = false,
        showBatteryPrompt = false,
        versionLabel = "0.4.0-dev",
        stLabel = "SillyTavern 1.12.3",
        nodeLabel = "Node v24.13.0",
        symlinkSupported = true,
        onShowLegal = {},
        showAutoCheckOptInPrompt = false,
        onEnableAutoCheck = {},
        onLaterAutoCheck = {},
        onDismissBatteryPrompt = {},
        showUpdatePrompt = false,
        updateVersionLabel = "",
        updateDetails = "",
        isDownloadingUpdate = false,
        downloadProgressPercent = null,
        isUpdateReadyToInstall = false,
        onUpdatePrimary = {},
        onUpdateDismiss = {},
        onCancelUpdateDownload = {},
        showBackupOperationCard = false,
        backupOperationTitle = "",
        backupOperationDetails = "",
        backupOperationProgressPercent = null,
        showCustomOperationCard = false,
        customOperationTitle = "",
        customOperationDetails = "",
        customOperationProgressPercent = null,
        customOperationCancelable = false,
        onCancelCustomOperation = {},
        onShowSettings = {},
        onShowManageSt = {},
        recentChats = listOf(
            ChatSummary(
                id = "Seraphina/demo",
                characterId = "Seraphina",
                characterName = "Seraphina",
                lastMessage = "Ready to continue."
            )
        ),
        recentCharacters = listOf(
            CharacterSummary(id = "Seraphina.png", name = "Seraphina")
        )
    )
}
