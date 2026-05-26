package io.github.sanitised.st.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.R
import io.github.sanitised.st.api.CharacterChatSummary
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.ChatExportFormat
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.ui.components.FavoriteIconButton
import io.github.sanitised.st.ui.components.STConfirmDialog
import io.github.sanitised.st.ui.components.STInfoCard
import io.github.sanitised.st.ui.components.STSectionCard
import kotlinx.coroutines.launch

private enum class CharacterDetailTab {
    OVERVIEW,
    CHATS,
    LINKS
}

private val characterChatImportMimeTypes = arrayOf(
    "application/json",
    "application/jsonl",
    "text/plain",
    "*/*"
)

@Composable
fun CharacterDetailScreen(
    status: NodeStatus,
    baseUrl: String,
    avatar: String,
    onStartService: () -> Unit,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenChat: (String?) -> Unit,
    onOpenWorldInfo: () -> Unit,
    onOpenPersona: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val serverRunning = status.state == NodeState.RUNNING
    var loading by remember(avatar) { mutableStateOf(true) }
    var detail by remember(avatar) { mutableStateOf<CharacterDetail?>(null) }
    var chats by remember(avatar) { mutableStateOf<List<CharacterChatSummary>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(CharacterDetailTab.OVERVIEW) }
    var pendingExport by remember { mutableStateOf<Pair<CharacterChatSummary, ChatExportFormat>?>(null) }
    var pendingRenameChat by remember { mutableStateOf<CharacterChatSummary?>(null) }
    var renameChatText by remember { mutableStateOf("") }
    var pendingDeleteChat by remember { mutableStateOf<CharacterChatSummary?>(null) }
    var importingChat by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        exportCharacterChat(
            context = context,
            uri = uri,
            avatar = avatar,
            chat = export.first,
            format = export.second,
            baseUrl = baseUrl,
            onShowMessage = onShowMessage,
            scope = scope
        )
    }
    val importChatLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val characterName = detail?.name ?: avatar.removeSuffix(".png")
        scope.launch {
            importingChat = true
            runCatching {
                val document = context.readPickedDocument(uri)
                val client = TavernCoreClient(baseUrl = baseUrl)
                client.importCharacterChat(
                    avatar = avatar,
                    characterName = characterName,
                    fileName = document.fileName,
                    bytes = document.bytes
                )
                client.listCharacterChats(avatar)
            }.onSuccess { loadedChats ->
                chats = loadedChats
                onShowMessage(context.getString(R.string.character_chat_imported))
            }.onFailure { error ->
                onShowMessage(error.messageOr(context, R.string.character_chat_import_failed))
            }
            importingChat = false
        }
    }

    LaunchedEffect(serverRunning, baseUrl, avatar) {
        if (!serverRunning) {
            loading = false
            detail = null
            chats = emptyList()
            return@LaunchedEffect
        }
        loading = true
        runCatching {
            val client = TavernCoreClient(baseUrl = baseUrl)
            val loadedDetail = client.getCharacter(avatar)
            val loadedChats = client.listCharacterChats(avatar)
            loadedDetail to loadedChats
        }.onSuccess { (loadedDetail, loadedChats) ->
            detail = loadedDetail
            chats = loadedChats
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_load_failed))
        }
        loading = false
    }

    pendingRenameChat?.let { chat ->
        AlertDialog(
            onDismissRequest = {
                pendingRenameChat = null
                renameChatText = ""
            },
            title = { Text(stringResource(R.string.character_chat_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameChatText,
                    onValueChange = { renameChatText = it },
                    label = { Text(stringResource(R.string.character_chat_rename_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameChatText.trim()
                        pendingRenameChat = null
                        renameChatText = ""
                        renameCharacterChat(context, baseUrl, avatar, chat, newName, onShowMessage, scope) { renamedFile ->
                            chats = chats.map { item ->
                                if (item.fileName == chat.fileName) item.copy(fileName = renamedFile) else item
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingRenameChat = null
                        renameChatText = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingDeleteChat?.let { chat ->
        STConfirmDialog(
            title = stringResource(R.string.character_chat_delete_title),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                pendingDeleteChat = null
                deleteCharacterChat(context, baseUrl, avatar, chat, onShowMessage, scope) {
                    chats = chats.filterNot { it.fileName == chat.fileName }
                }
            },
            onDismiss = { pendingDeleteChat = null },
            body = {
                Text(stringResource(R.string.character_chat_delete_body, chat.fileName))
            }
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = colors.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CharacterDetailTopBar(
                    detail = detail,
                    avatar = avatar,
                    onBack = onBack,
                    onToggleFavorite = {
                        val current = detail ?: return@CharacterDetailTopBar
                        scope.launch {
                            runCatching {
                                TavernCoreClient(baseUrl = baseUrl).mergeCharacterAttributes(
                                    avatar = current.id,
                                    isFavorite = !current.isFavorite
                                )
                            }.onSuccess {
                                detail = current.copy(isFavorite = !current.isFavorite)
                            }.onFailure { error ->
                                onShowMessage(error.messageOr(context, R.string.character_save_failed))
                            }
                        }
                    }
                )

                if (!serverRunning) {
                    STInfoCard(
                        title = stringResource(R.string.webview_error_service_stopped_title),
                        body = stringResource(R.string.webview_error_service_stopped_body),
                        actionLabel = stringResource(R.string.webview_start_service),
                        onAction = onStartService
                    )
                } else {
                    val character = detail
                    when {
                        loading -> STInfoCard(
                            title = stringResource(R.string.character_edit_loading),
                            body = stringResource(R.string.waiting_for_server)
                        )

                        character == null -> STInfoCard(
                            title = stringResource(R.string.character_load_failed),
                            body = avatar
                        )

                        else -> {
                            CharacterDetailHero(
                                baseUrl = baseUrl,
                                detail = character,
                                onEdit = { onEdit(character.id) },
                                onOpenChat = { onOpenChat(chats.firstOrNull()?.id) }
                            )
                            CharacterMetrics(detail = character, chats = chats)
                            CharacterDetailTabs(selectedTab = selectedTab, onSelected = { selectedTab = it })
                            when (selectedTab) {
                                CharacterDetailTab.OVERVIEW -> CharacterOverviewPanel(character)
                                CharacterDetailTab.CHATS -> CharacterChatsPanel(
                                    chats = chats,
                                    importingChat = importingChat,
                                    onImportChat = { importChatLauncher.launch(characterChatImportMimeTypes) },
	                                    onOpenChat = { chat -> onOpenChat(chat.id) },
	                                    onRenameChat = { chat ->
	                                        pendingRenameChat = chat
	                                        renameChatText = chat.fileName.removeSuffix(".jsonl")
	                                    },
	                                    onDeleteChat = { chat ->
	                                        pendingDeleteChat = chat
	                                    },
                                    onExportChat = { chat, format ->
                                        pendingExport = chat to format
                                        exportLauncher.launch(defaultChatExportFileName(chat, format))
                                    }
                                )

	                                CharacterDetailTab.LINKS -> CharacterLinksPanel(
	                                    detail = character,
	                                    onOpenLorebook = onOpenWorldInfo,
	                                    onOpenSource = {
	                                        val source = character.sourceUrl
	                                        if (source.isBlank()) {
	                                            onShowMessage(context.getString(R.string.character_detail_source_missing))
	                                        } else {
	                                            runCatching { uriHandler.openUri(source) }
	                                                .onFailure { onShowMessage(source) }
	                                        }
	                                    },
	                                    onOpenPersona = onOpenPersona,
	                                    onSetAssistant = {
	                                        onShowMessage(context.getString(R.string.character_assistant_unavailable))
	                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterDetailTopBar(
    detail: CharacterDetail?,
    avatar: String,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.character_detail_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Text(
                text = stringResource(R.string.character_detail_subtitle, detail?.id ?: avatar),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        FavoriteIconButton(
            isFavorite = detail?.isFavorite == true,
            onToggleFavorite = onToggleFavorite,
            enabled = detail != null
        )
    }
}

@Composable
private fun CharacterDetailHero(
    baseUrl: String,
    detail: CharacterDetail,
    onEdit: () -> Unit,
    onOpenChat: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CharacterAvatarImage(
                    baseUrl = baseUrl,
                    avatar = detail.avatarUrl,
                    label = detail.name,
                    size = 96.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = detail.creatorNotes.ifBlank { detail.description },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    CharacterTagLine(tags = detail.tags)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.character_detail_edit))
                }
                OutlinedButton(onClick = onOpenChat, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.character_hub_open_chat))
                }
            }
        }
    }
}

@Composable
private fun CharacterMetrics(detail: CharacterDetail, chats: List<CharacterChatSummary>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CharacterMetric(
            value = chats.size.toString(),
            label = stringResource(R.string.character_detail_metrics_chats),
            modifier = Modifier.weight(1f)
        )
        CharacterMetric(
            value = detail.rawJsonData.length.toString(),
            label = stringResource(R.string.character_detail_metrics_tokens),
            modifier = Modifier.weight(1f)
        )
        CharacterMetric(
            value = detail.alternateGreetings.size.toString(),
            label = stringResource(R.string.character_detail_metrics_greetings),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CharacterMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CharacterDetailTabs(selectedTab: CharacterDetailTab, onSelected: (CharacterDetailTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CharacterDetailTab.values().forEach { tab ->
            FilterChip(
                selected = selectedTab == tab,
                onClick = { onSelected(tab) },
                label = {
                    Text(
                        when (tab) {
                            CharacterDetailTab.OVERVIEW -> stringResource(R.string.character_detail_tab_overview)
                            CharacterDetailTab.CHATS -> stringResource(R.string.character_detail_tab_chats)
                            CharacterDetailTab.LINKS -> stringResource(R.string.character_detail_tab_links)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun CharacterOverviewPanel(detail: CharacterDetail) {
    STSectionCard(
        title = stringResource(R.string.character_detail_core_fields),
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        contentSpacing = 10.dp
    ) {
        CharacterInfoRow(stringResource(R.string.character_edit_description), detail.description)
        CharacterInfoRow(stringResource(R.string.character_edit_first_message), detail.firstMessage)
        CharacterInfoRow(stringResource(R.string.character_edit_system_prompt), detail.systemPrompt)
        CharacterInfoRow(stringResource(R.string.character_edit_depth_prompt), detail.depthPrompt)
    }
    Spacer(modifier = Modifier.height(2.dp))
    STSectionCard(
        title = stringResource(R.string.character_detail_migration_status),
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        contentSpacing = 10.dp
    ) {
        CharacterInfoRow(stringResource(R.string.character_edit_alternate_greetings), detail.alternateGreetings.size.toString())
        CharacterInfoRow(stringResource(R.string.character_edit_world), detail.world)
        CharacterInfoRow(stringResource(R.string.character_edit_tags), detail.tags.joinToString(", "))
    }
}

@Composable
private fun CharacterChatsPanel(
    chats: List<CharacterChatSummary>,
    importingChat: Boolean,
    onImportChat: () -> Unit,
    onOpenChat: (CharacterChatSummary) -> Unit,
    onRenameChat: (CharacterChatSummary) -> Unit,
    onDeleteChat: (CharacterChatSummary) -> Unit,
    onExportChat: (CharacterChatSummary, ChatExportFormat) -> Unit
) {
    STSectionCard(
        title = stringResource(R.string.character_detail_chats_title),
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        contentSpacing = 10.dp
    ) {
        OutlinedButton(
            onClick = onImportChat,
            enabled = !importingChat,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (importingChat) {
                    stringResource(R.string.busy_importing_data)
                } else {
                    stringResource(R.string.character_chat_import)
                }
            )
        }
        if (chats.isEmpty()) {
            Text(
                text = stringResource(R.string.character_detail_chats_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            chats.forEach { chat ->
                CharacterChatRow(
                    chat = chat,
                    onOpenChat = { onOpenChat(chat) },
                    onRenameChat = { onRenameChat(chat) },
                    onDeleteChat = { onDeleteChat(chat) },
                    onExportJsonl = { onExportChat(chat, ChatExportFormat.JSONL) },
                    onExportTxt = { onExportChat(chat, ChatExportFormat.TXT) }
                )
            }
        }
    }
}

@Composable
private fun CharacterChatRow(
    chat: CharacterChatSummary,
    onOpenChat: () -> Unit,
    onRenameChat: () -> Unit,
    onDeleteChat: () -> Unit,
    onExportJsonl: () -> Unit,
    onExportTxt: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = chat.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = listOf(chat.fileSize, chat.messageCount.toString(), chat.lastMessageAt)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (chat.lastMessage.isNotBlank()) {
                Text(
                    text = chat.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onOpenChat) {
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.character_hub_open_chat))
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.character_chat_rename)) },
                    onClick = {
                        menuExpanded = false
                        onRenameChat()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.character_chat_export_jsonl)) },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onExportJsonl()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.character_chat_export_txt)) },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onExportTxt()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.character_chat_delete)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuExpanded = false
                        onDeleteChat()
                    }
                )
            }
        }
    }
}

@Composable
private fun CharacterLinksPanel(
    detail: CharacterDetail,
    onOpenLorebook: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenPersona: () -> Unit,
    onSetAssistant: () -> Unit
) {
    val sourceUrl = detail.sourceUrl.trim()
    STSectionCard(
        title = stringResource(R.string.character_detail_links_title),
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        contentSpacing = 10.dp
    ) {
        CharacterLinkRow(
            label = stringResource(R.string.character_detail_lorebook_label),
            value = detail.world.ifBlank { stringResource(R.string.character_detail_lorebook_missing) },
            actionLabel = stringResource(R.string.character_detail_manage),
            enabled = true,
            onAction = onOpenLorebook
        )
        CharacterLinkRow(
            label = stringResource(R.string.character_detail_source_label),
            value = sourceUrl.ifBlank { stringResource(R.string.character_detail_source_missing) },
            actionLabel = stringResource(R.string.dashboard_open),
            enabled = sourceUrl.isNotBlank(),
            onAction = onOpenSource
        )
        CharacterLinkRow(
            label = stringResource(R.string.character_detail_persona_label),
            value = stringResource(R.string.character_detail_persona_missing),
            actionLabel = stringResource(R.string.character_detail_manage),
            enabled = true,
            onAction = onOpenPersona
        )
        CharacterLinkRow(
            label = stringResource(R.string.character_detail_assistant_label),
            value = stringResource(R.string.character_detail_assistant_body),
            actionLabel = stringResource(R.string.character_detail_set),
            enabled = true,
            onAction = onSetAssistant
        )
    }
}

@Composable
private fun CharacterLinkRow(
    label: String,
    value: String,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onAction, enabled = enabled) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun CharacterInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.36f)
        )
        Text(
            text = value.ifBlank { stringResource(R.string.unknown_short) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.64f)
        )
    }
}

@Composable
private fun CharacterTagLine(tags: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        tags.take(3).forEach { tag ->
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

private fun renameCharacterChat(
    context: Context,
    baseUrl: String,
    avatar: String,
    chat: CharacterChatSummary,
    renamedName: String,
    onShowMessage: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    onRenamed: (String) -> Unit
) {
    val renamed = renamedName
        .trim()
        .removeSuffix(".jsonl")
        .takeIf { it.isNotBlank() }
        ?.plus(".jsonl")
        ?: return
    scope.launch {
        runCatching {
            TavernCoreClient(baseUrl = baseUrl).renameCharacterChat(avatar, chat.fileName, renamed)
        }.onSuccess { sanitizedName ->
            val displayName = sanitizedName
                .trim()
                .removeSuffix(".jsonl")
                .ifBlank { renamed.removeSuffix(".jsonl") }
                .plus(".jsonl")
            onRenamed(displayName)
            onShowMessage(context.getString(R.string.character_chat_renamed))
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_save_failed))
        }
    }
}

private fun deleteCharacterChat(
    context: Context,
    baseUrl: String,
    avatar: String,
    chat: CharacterChatSummary,
    onShowMessage: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    onDeleted: () -> Unit
) {
    scope.launch {
        runCatching {
            TavernCoreClient(baseUrl = baseUrl).deleteCharacterChat(avatar, chat.fileName)
        }.onSuccess {
            onDeleted()
            onShowMessage(context.getString(R.string.character_chat_deleted))
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_save_failed))
        }
    }
}

private fun exportCharacterChat(
    context: Context,
    uri: Uri,
    avatar: String,
    chat: CharacterChatSummary,
    format: ChatExportFormat,
    baseUrl: String,
    onShowMessage: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch {
        runCatching {
            val file = TavernCoreClient(baseUrl = baseUrl).exportCharacterChat(avatar, chat.fileName, format)
            context.writePickedDocument(uri, file.bytes)
        }.onSuccess {
            onShowMessage(context.getString(R.string.character_chat_exported))
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_export_failed))
        }
    }
}

private fun defaultChatExportFileName(chat: CharacterChatSummary, format: ChatExportFormat): String {
    return chat.fileName.removeSuffix(".jsonl") + "." + format.fileExtension
}

private fun Throwable.messageOr(context: Context, fallbackResId: Int): String {
    return message ?: context.getString(fallbackResId)
}
