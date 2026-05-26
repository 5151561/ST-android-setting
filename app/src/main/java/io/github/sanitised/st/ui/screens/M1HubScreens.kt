package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
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
import io.github.sanitised.st.ui.theme.STTheme
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsHubScreen(
    onOpenConfig: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenManageSt: () -> Unit,
    modifier: Modifier = Modifier
) {
    HubScaffold(
        title = stringResource(R.string.tools_hub_title),
        subtitle = stringResource(R.string.tools_hub_subtitle),
        modifier = modifier
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2
        ) {
            ToolTile(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.config_button_title),
                body = stringResource(R.string.tools_hub_config_body),
                onClick = onOpenConfig,
                modifier = Modifier.weight(1f)
            )
            ToolTile(
                icon = Icons.Filled.History,
                title = stringResource(R.string.logs_title),
                body = stringResource(R.string.tools_hub_logs_body),
                onClick = onOpenLogs,
                modifier = Modifier.weight(1f)
            )
            ToolTile(
                icon = Icons.Filled.FolderOpen,
                title = stringResource(R.string.manage_st_title),
                body = stringResource(R.string.tools_hub_manage_body),
                onClick = onOpenManageSt,
                modifier = Modifier.weight(1f)
            )
        }
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
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = STTheme.colors
    Surface(modifier = modifier.fillMaxSize(), color = colors.bg) {
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
                        color = colors.fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(24.dp)
                )
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
    val colors = STTheme.colors
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
                    color = colors.warn
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
private fun ToolTile(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = STTheme.colors
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconPill(icon = icon)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    val colors = STTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.borderSoft)
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
    val colors = STTheme.colors
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
                .background(colors.surfaceWarm, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = avatarLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.fg2
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
            color = colors.accent,
            maxLines = 1
        )
    }
}

@Composable
private fun IconPill(icon: ImageVector) {
    val colors = STTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(colors.surfaceWarm, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
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
    val colors = STTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colors.bg,
        border = BorderStroke(1.dp, colors.borderSoft)
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
    val colors = STTheme.colors
    val (label, fg, bg) = when (status.state) {
        NodeState.RUNNING -> Triple(
            stringResource(R.string.dashboard_status_running),
            colors.success,
            colors.success.copy(alpha = 0.12f)
        )
        NodeState.STARTING, NodeState.STOPPING -> Triple(
            stringResource(R.string.dashboard_status_busy),
            colors.warn,
            colors.warn.copy(alpha = 0.18f)
        )
        NodeState.ERROR -> Triple(
            stringResource(R.string.dashboard_status_error),
            colors.danger,
            colors.danger.copy(alpha = 0.12f)
        )
        NodeState.STOPPED -> Triple(
            stringResource(R.string.dashboard_status_stopped),
            colors.muted,
            colors.borderSoft
        )
    }
    Surface(shape = RoundedCornerShape(999.dp), color = bg) {
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
            onOpenManageSt = {}
        )
    }
}
