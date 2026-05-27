package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.R
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.ChatSummary
import io.github.sanitised.st.data.LocalTavernLibraryReader
import io.github.sanitised.st.ui.components.STSectionCard
import io.github.sanitised.st.ui.theme.STAppTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalTavernLibrarySnapshot(
    val characters: List<CharacterSummary> = emptyList(),
    val recentChats: List<ChatSummary> = emptyList()
)

@Composable
fun rememberLocalTavernLibrarySnapshot(
    dataRoot: File,
    refreshKey: Any?
): State<LocalTavernLibrarySnapshot> {
    return produceState(
        initialValue = LocalTavernLibrarySnapshot(),
        dataRoot,
        refreshKey
    ) {
        value = withContext(Dispatchers.IO) {
            val reader = LocalTavernLibraryReader(dataRoot)
            LocalTavernLibrarySnapshot(
                characters = reader.listCharacters(),
                recentChats = reader.listRecentChats()
            )
        }
    }
}

@Composable
fun CharacterHubScreen(
    characters: List<CharacterSummary>,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    HubScaffold(
        title = stringResource(R.string.character_hub_title),
        subtitle = stringResource(R.string.character_hub_subtitle),
        modifier = modifier
    ) {
        HubActionCard(
            icon = Icons.Filled.UploadFile,
            title = stringResource(R.string.character_hub_import_title),
            body = stringResource(R.string.character_hub_import_body),
            primaryLabel = stringResource(R.string.character_hub_open_chat),
            onPrimary = onOpenChat
        )

        SectionHeader(
            title = stringResource(R.string.character_hub_recent_title),
            actionLabel = stringResource(R.string.character_hub_open_chat),
            onAction = onOpenChat
        )

        if (characters.isEmpty()) {
            EmptyHubCard(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.character_hub_empty_title),
                body = stringResource(R.string.character_hub_empty_body)
            )
        } else {
            HubListCard {
                characters.forEach { character ->
                    HubListRow(
                        avatarLabel = character.name.avatarInitial(),
                        title = character.name,
                        body = stringResource(R.string.character_hub_character_body),
                        trailingLabel = if (character.isFavorite) {
                            stringResource(R.string.character_hub_favorite)
                        } else {
                            stringResource(R.string.character_hub_recent)
                        },
                        onClick = onOpenChat
                    )
                }
            }
        }
    }
}

@Composable
fun ToolsHubScreen(
    onOpenConfig: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenManageSt: () -> Unit,
    onOpenWorldInfo: () -> Unit,
    onOpenPersona: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenChatBackups: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showToolsInfoDialog by remember { mutableStateOf(false) }

    if (showToolsInfoDialog) {
        ToolsHubInfoDialog(onDismiss = { showToolsInfoDialog = false })
    }

    HubScaffold(
        title = stringResource(R.string.tools_hub_title),
        subtitle = null,
        infoContentDescription = stringResource(R.string.tools_hub_info),
        onInfoClick = { showToolsInfoDialog = true },
        modifier = modifier
    ) {
        ToolListSection {
            ToolListRow(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.m3_world_info_title),
                onClick = onOpenWorldInfo
            )
            ToolListRow(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.m3_persona_title),
                onClick = onOpenPersona
            )
            ToolListRow(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.m3_presets_title),
                onClick = onOpenPresets
            )
            ToolListRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.m3_connections_title),
                onClick = onOpenConnections
            )
            ToolListRow(
                icon = Icons.Filled.History,
                title = stringResource(R.string.m3_chat_backups_title),
                onClick = onOpenChatBackups
            )
        }

        ToolListSection {
            ToolListRow(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.config_button_title),
                onClick = onOpenConfig
            )
            ToolListRow(
                icon = Icons.Filled.History,
                title = stringResource(R.string.logs_title),
                onClick = onOpenLogs
            )
            ToolListRow(
                icon = Icons.Filled.FolderOpen,
                title = stringResource(R.string.manage_st_title),
                onClick = onOpenManageSt
            )
        }
    }
}

@Composable
private fun ToolsHubInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_hub_info)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.tools_hub_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToolInfoLine(
                    title = stringResource(R.string.m3_world_info_title),
                    description = stringResource(R.string.tools_hub_world_info_body)
                )
                ToolInfoLine(
                    title = stringResource(R.string.m3_persona_title),
                    description = stringResource(R.string.tools_hub_persona_body)
                )
                ToolInfoLine(
                    title = stringResource(R.string.m3_presets_title),
                    description = stringResource(R.string.tools_hub_presets_body)
                )
                ToolInfoLine(
                    title = stringResource(R.string.m3_connections_title),
                    description = stringResource(R.string.tools_hub_connections_body)
                )
                ToolInfoLine(
                    title = stringResource(R.string.m3_chat_backups_title),
                    description = stringResource(R.string.tools_hub_chat_backups_body)
                )
                ToolInfoLine(
                    title = stringResource(R.string.config_button_title),
                    description = stringResource(R.string.tools_hub_config_body)
                )
                ToolInfoLine(
                    title = stringResource(R.string.logs_title),
                    description = stringResource(R.string.tools_hub_logs_body)
                )
                ToolInfoLine(
                    title = stringResource(R.string.manage_st_title),
                    description = stringResource(R.string.tools_hub_manage_body)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tools_hub_info_dismiss))
            }
        }
    )
}

@Composable
private fun ToolInfoLine(
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DashboardStatusCard(
    status: NodeStatus,
    stLabel: String,
    nodeLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = status.state == NodeState.RUNNING
    val isBusy = status.state == NodeState.STARTING || status.state == NodeState.STOPPING
    STSectionCard(modifier = modifier, contentSpacing = 12.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dashboard_core_service_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.dashboard_core_service_subtitle, status.port),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = status)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardMetric(
                    label = stringResource(R.string.dashboard_metric_st),
                    value = stLabel,
                    modifier = Modifier.weight(1f)
                )
                DashboardMetric(
                    label = stringResource(R.string.dashboard_metric_node),
                    value = nodeLabel,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = if (isRunning) onOpenChat else onStart,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isRunning) {
                            stringResource(R.string.dashboard_continue_chat)
                        } else {
                            stringResource(R.string.start)
                        }
                    )
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = status.state == NodeState.RUNNING || status.state == NodeState.STARTING,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.stop))
                }
            }
    }
}

@Composable
fun DashboardLibrarySections(
    recentChats: List<ChatSummary>,
    characters: List<CharacterSummary>,
    onOpenChat: () -> Unit,
    onOpenCharacters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_recent_chats),
            actionLabel = stringResource(R.string.dashboard_open_all),
            onAction = onOpenChat
        )
        if (recentChats.isEmpty()) {
            EmptyHubCard(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = stringResource(R.string.dashboard_recent_chats_empty_title),
                body = stringResource(R.string.dashboard_recent_chats_empty_body)
            )
        } else {
            HubListCard {
                recentChats.forEach { chat ->
                    HubListRow(
                        avatarLabel = chat.characterName.avatarInitial(),
                        title = chat.characterName,
                        body = chat.lastMessage ?: stringResource(R.string.dashboard_recent_chat_no_preview),
                        trailingLabel = stringResource(R.string.dashboard_continue),
                        onClick = onOpenChat
                    )
                }
            }
        }

        SectionHeader(
            title = stringResource(R.string.dashboard_recent_characters),
            actionLabel = stringResource(R.string.dashboard_open_all),
            onAction = onOpenCharacters
        )
        if (characters.isEmpty()) {
            EmptyHubCard(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.dashboard_recent_characters_empty_title),
                body = stringResource(R.string.dashboard_recent_characters_empty_body)
            )
        } else {
            HubListCard {
                characters.take(3).forEach { character ->
                    HubListRow(
                        avatarLabel = character.name.avatarInitial(),
                        title = character.name,
                        body = stringResource(R.string.character_hub_character_body),
                        trailingLabel = stringResource(R.string.dashboard_open),
                        onClick = onOpenCharacters
                    )
                }
            }
        }
    }
}

@Composable
private fun HubScaffold(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    infoContentDescription: String? = null,
    onInfoClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxSize(), color = colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (onInfoClick != null) {
                    IconButton(onClick = onInfoClick) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = infoContentDescription,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun HubActionCard(
    icon: ImageVector,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledHint: String? = null
) {
    val colors = MaterialTheme.colorScheme
    STSectionCard(modifier = modifier, contentSpacing = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconPill(icon = icon)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!enabled && disabledHint != null) {
                Text(
                    text = disabledHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPrimary,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(primaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    OutlinedButton(
                        onClick = onSecondary,
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
    }
}

@Composable
private fun ToolListSection(
    content: @Composable ColumnScope.() -> Unit = {}
) {
    STSectionCard(
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        contentSpacing = 0.dp,
        content = content
    )
}

@Composable
private fun ToolListRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconPill(icon = icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.dashboard_open),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onAction)
        )
    }
}

@Composable
private fun EmptyHubCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconPill(icon = icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HubListCard(content: @Composable ColumnScope.() -> Unit) {
    STSectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), content = content)
}

@Composable
private fun HubListRow(
    avatarLabel: String,
    title: String,
    body: String,
    trailingLabel: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = avatarLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = trailingLabel,
            style = MaterialTheme.typography.labelSmall,
            color = colors.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun IconPill(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(colors.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DashboardMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusChip(status: NodeStatus) {
    val colors = MaterialTheme.colorScheme
    val (label, fg, bg) = when (status.state) {
        NodeState.RUNNING -> Triple(
            stringResource(R.string.dashboard_status_running),
            colors.onTertiaryContainer,
            colors.tertiaryContainer
        )
        NodeState.STARTING, NodeState.STOPPING -> Triple(
            stringResource(R.string.dashboard_status_busy),
            colors.onSecondaryContainer,
            colors.secondaryContainer
        )
        NodeState.ERROR -> Triple(
            stringResource(R.string.dashboard_status_error),
            colors.onErrorContainer,
            colors.errorContainer
        )
        NodeState.STOPPED -> Triple(
            stringResource(R.string.dashboard_status_stopped),
            colors.onSurfaceVariant,
            colors.surfaceContainerHighest
        )
    }
    Surface(shape = CircleShape, color = bg) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun String.avatarInitial(): String =
    trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

@Preview(showBackground = true)
@Composable
private fun CharacterHubScreenPreview() {
    STAppTheme {
        CharacterHubScreen(
            characters = listOf(CharacterSummary(id = "seraphina.png", name = "Seraphina")),
            onOpenChat = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ToolsHubScreenPreview() {
    STAppTheme {
        ToolsHubScreen(
            onOpenConfig = {},
            onOpenLogs = {},
            onOpenManageSt = {},
            onOpenWorldInfo = {},
            onOpenPersona = {},
            onOpenPresets = {},
            onOpenConnections = {},
            onOpenChatBackups = {}
        )
    }
}
