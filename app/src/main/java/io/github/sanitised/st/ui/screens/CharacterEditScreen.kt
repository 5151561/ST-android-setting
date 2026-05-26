package io.github.sanitised.st.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.R
import io.github.sanitised.st.api.CharacterExportFormat
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.CharacterSaveRequest
import io.github.sanitised.st.api.CharacterUpload
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.ui.theme.STTheme
import kotlinx.coroutines.launch

private data class CharacterEditDraft(
    val avatar: String? = null,
    val name: String = "",
    val description: String = "",
    val firstMessage: String = "",
    val creatorNotes: String = "",
    val messageExample: String = "",
    val personality: String = "",
    val scenario: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val tagsText: String = "",
    val creator: String = "",
    val characterVersion: String = "",
    val world: String = "",
    val talkativenessText: String = "0.5",
    val alternateGreetings: List<String> = emptyList(),
    val depthPrompt: String = "",
    val depthPromptDepthText: String = "4",
    val depthPromptRole: String = "system",
    val chat: String = "",
    val createDate: String = "",
    val rawJsonData: String = "",
    val sourceUrl: String = "",
    val isFavorite: Boolean = false
) {
    fun toSaveRequest(): CharacterSaveRequest {
        return CharacterSaveRequest(
            avatar = avatar,
            name = name.trim(),
            description = description,
            firstMessage = firstMessage,
            creatorNotes = creatorNotes,
            messageExample = messageExample,
            personality = personality,
            scenario = scenario,
            systemPrompt = systemPrompt,
            postHistoryInstructions = postHistoryInstructions,
            tags = tagsText.split(',').map { it.trim() }.filter { it.isNotBlank() },
            creator = creator,
            characterVersion = characterVersion,
            world = world,
            talkativeness = talkativenessText.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.5,
            alternateGreetings = alternateGreetings.map { it.trim() }.filter { it.isNotBlank() },
            depthPrompt = depthPrompt,
            depthPromptDepth = depthPromptDepthText.toIntOrNull() ?: 4,
            depthPromptRole = depthPromptRole.trim().ifBlank { "system" },
            chat = chat,
            createDate = createDate,
            rawJsonData = rawJsonData,
            sourceUrl = sourceUrl,
            isFavorite = isFavorite
        )
    }
}

private enum class CharacterEditorTab {
    BASIC,
    PROMPT,
    METADATA
}

private enum class CharacterReplaceMode {
    FILE,
    SOURCE
}

private val characterReplaceMimeTypes = arrayOf(
    "application/json",
    "image/png",
    "application/x-yaml",
    "text/yaml",
    "application/octet-stream",
    "*/*"
)

@Composable
fun CharacterEditScreen(
    status: NodeStatus,
    baseUrl: String,
    avatar: String?,
    onStartService: () -> Unit,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = STTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember(avatar) { mutableStateOf(CharacterEditDraft(avatar = avatar)) }
    var loading by remember(avatar) { mutableStateOf(!avatar.isNullOrBlank()) }
    var saving by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    var deleteChats by remember { mutableStateOf(false) }
    var pendingAvatarUpload by remember(avatar) { mutableStateOf<CharacterUpload?>(null) }
    var pendingReplaceMode by remember { mutableStateOf<CharacterReplaceMode?>(null) }
    var selectedEditorTab by remember { mutableStateOf(CharacterEditorTab.BASIC) }
    val serverRunning = status.state == NodeState.RUNNING
    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val document = context.readPickedDocument(uri)
                CharacterUpload(document.fileName, document.bytes)
            }.onSuccess { upload ->
                pendingAvatarUpload = upload
                onShowMessage(context.getString(R.string.character_avatar_selected))
            }.onFailure { error ->
                onShowMessage(error.messageOr(context, R.string.character_avatar_failed))
            }
        }
    }
    val replaceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        replaceCharacterFromFile(
            context = context,
            uri = uri,
            draft = draft,
            baseUrl = baseUrl,
            onShowMessage = onShowMessage,
            onSavingChanged = { saving = it },
            onSaved = onSaved,
            scope = scope
        )
    }
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exportCharacter(
            context = context,
            uri = uri,
            format = CharacterExportFormat.JSON,
            draft = draft,
            baseUrl = baseUrl,
            onShowMessage = onShowMessage,
            onSavingChanged = { saving = it },
            scope = scope
        )
    }
    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exportCharacter(
            context = context,
            uri = uri,
            format = CharacterExportFormat.PNG,
            draft = draft,
            baseUrl = baseUrl,
            onShowMessage = onShowMessage,
            onSavingChanged = { saving = it },
            scope = scope
        )
    }

    LaunchedEffect(serverRunning, baseUrl, avatar) {
        if (!serverRunning || avatar.isNullOrBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        runCatching {
            TavernCoreClient(baseUrl = baseUrl).getCharacter(avatar)
        }.onSuccess { detail ->
            draft = detail.toDraft()
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_load_failed))
        }
        loading = false
    }

    if (pendingDelete && !draft.avatar.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = {
                pendingDelete = false
                deleteChats = false
            },
            title = { Text(stringResource(R.string.character_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.character_delete_body, draft.name.ifBlank { draft.avatar.orEmpty() }))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = deleteChats,
                            onCheckedChange = { deleteChats = it },
                            enabled = !saving
                        )
                        Text(stringResource(R.string.character_delete_chats))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        val removeChats = deleteChats
                        pendingDelete = false
                        deleteChats = false
                        deleteCharacter(
                            context = context,
                            draft = draft,
                            baseUrl = baseUrl,
                            deleteChats = removeChats,
                            onShowMessage = onShowMessage,
                            onSavingChanged = { saving = it },
                            onDeleted = onBack,
                            scope = scope
                        )
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        pendingDelete = false
                        deleteChats = false
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingReplaceMode?.let { mode ->
        if (!draft.avatar.isNullOrBlank()) {
            AlertDialog(
                onDismissRequest = { pendingReplaceMode = null },
                title = { Text(stringResource(R.string.character_replace_title)) },
                text = {
                    Text(
                        when (mode) {
                            CharacterReplaceMode.FILE -> stringResource(R.string.character_replace_file_body)
                            CharacterReplaceMode.SOURCE -> stringResource(R.string.character_replace_source_body)
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !saving,
                        onClick = {
                            pendingReplaceMode = null
                            when (mode) {
                                CharacterReplaceMode.FILE -> replaceLauncher.launch(characterReplaceMimeTypes)
                                CharacterReplaceMode.SOURCE -> replaceCharacterFromSource(
                                    context = context,
                                    draft = draft,
                                    baseUrl = baseUrl,
                                    onShowMessage = onShowMessage,
                                    onSavingChanged = { saving = it },
                                    onSaved = onSaved,
                                    scope = scope
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.character_replace_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingReplaceMode = null }, enabled = !saving) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = colors.bg) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) editContent@ {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (avatar.isNullOrBlank()) {
                                stringResource(R.string.character_edit_new_title)
                            } else {
                                stringResource(R.string.character_edit_title)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.fg
                        )
                        Text(
                            text = stringResource(R.string.character_edit_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted
                        )
                    }
                    IconButton(
                        enabled = serverRunning && !loading && !saving,
                        onClick = {
                            saveCharacter(
                                context = context,
                                draft = draft,
                                baseUrl = baseUrl,
                                onShowMessage = onShowMessage,
                                onSavingChanged = { saving = it },
                                onSaved = onSaved,
                                isNew = avatar.isNullOrBlank(),
                                avatarUpload = pendingAvatarUpload,
                                scope = scope
                            )
                        }
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
                    }
                }

                if (!serverRunning) {
                    CharacterEditorInfoCard(
                        title = stringResource(R.string.webview_error_service_stopped_title),
                        body = stringResource(R.string.webview_error_service_stopped_body),
                        actionLabel = stringResource(R.string.webview_start_service),
                        onAction = onStartService
                    )
                    return@editContent
                }

                if (loading) {
                    CharacterEditorInfoCard(
                        title = stringResource(R.string.character_edit_loading),
                        body = stringResource(R.string.waiting_for_server)
                    )
                    return@editContent
                }

                CharacterAvatarEditorSection(
                    baseUrl = baseUrl,
                    draft = draft,
                    pendingAvatarUpload = pendingAvatarUpload,
                    saving = saving,
                    onChooseAvatar = { avatarLauncher.launch(arrayOf("image/*")) },
                    onUpdateAvatarNow = {
                        val upload = pendingAvatarUpload
                        if (upload != null) {
                            updateCharacterAvatar(
                                context = context,
                                upload = upload,
                                draft = draft,
                                baseUrl = baseUrl,
                                onShowMessage = onShowMessage,
                                onSavingChanged = { saving = it },
                                onUpdated = { pendingAvatarUpload = null },
                                scope = scope
                            )
                        }
                    }
                )

                CharacterEditorTabs(
                    selectedTab = selectedEditorTab,
                    onSelected = { selectedEditorTab = it }
                )

                CharacterEditorFields(
                    draft = draft,
                    saving = saving,
                    selectedTab = selectedEditorTab,
                    onDraftChanged = { draft = it }
                )

                CharacterTokenCounterSection(draft = draft)

                if (!draft.avatar.isNullOrBlank()) {
                    CharacterManagementActions(
                        saving = saving,
                        onRename = {
                            renameCharacter(
                                context = context,
                                draft = draft,
                                baseUrl = baseUrl,
                                onShowMessage = onShowMessage,
                                onSavingChanged = { saving = it },
                                onSaved = onSaved,
                                scope = scope
                            )
                        },
                        onDuplicate = {
                            duplicateCharacter(
                                context = context,
                                draft = draft,
                                baseUrl = baseUrl,
                                onShowMessage = onShowMessage,
                                onSavingChanged = { saving = it },
                                onSaved = onSaved,
                                scope = scope
                            )
                        },
                        onChangeAvatar = {
                            avatarLauncher.launch(arrayOf("image/*"))
                        },
                        onExportJson = {
                            exportJsonLauncher.launch(defaultExportFileName(draft, CharacterExportFormat.JSON))
                        },
                        onExportPng = {
                            exportPngLauncher.launch(defaultExportFileName(draft, CharacterExportFormat.PNG))
                        },
                        onReplaceFromFile = {
                            pendingReplaceMode = CharacterReplaceMode.FILE
                        },
                        onUpdateFromSource = {
                            pendingReplaceMode = CharacterReplaceMode.SOURCE
                        },
                        sourceAvailable = draft.sourceUrl.isNotBlank(),
                        onDelete = {
                            pendingDelete = true
                            deleteChats = false
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = onBack, modifier = Modifier.weight(1f), enabled = !saving) {
                        Text(stringResource(R.string.back))
                    }
                    Button(
                        onClick = {
                            saveCharacter(
                                context = context,
                                draft = draft,
	                                baseUrl = baseUrl,
	                                onShowMessage = onShowMessage,
	                                onSavingChanged = { saving = it },
	                                onSaved = onSaved,
	                                isNew = avatar.isNullOrBlank(),
	                                avatarUpload = pendingAvatarUpload,
	                                scope = scope
	                            )
	                        },
                        modifier = Modifier.weight(1f),
                        enabled = !saving
                    ) {
                        Text(if (saving) stringResource(R.string.saving) else stringResource(R.string.save))
                    }
                }
            }

        }
    }
}

@Composable
private fun CharacterAvatarEditorSection(
    baseUrl: String,
    draft: CharacterEditDraft,
    pendingAvatarUpload: CharacterUpload?,
    saving: Boolean,
    onChooseAvatar: () -> Unit,
    onUpdateAvatarNow: () -> Unit
) {
    CharacterEditorSection(title = stringResource(R.string.character_avatar_section)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CharacterAvatarImage(
                baseUrl = baseUrl,
                avatar = draft.avatar,
                label = draft.name.ifBlank { draft.avatar.orEmpty() },
                size = 72.dp,
                localBytes = pendingAvatarUpload?.bytes
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = pendingAvatarUpload?.fileName ?: draft.avatar.orEmpty().ifBlank {
                        stringResource(R.string.unknown_short)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = STTheme.colors.muted
                )
                OutlinedButton(onClick = onChooseAvatar, enabled = !saving) {
                    Text(stringResource(R.string.character_avatar_choose))
                }
                if (!draft.avatar.isNullOrBlank() && pendingAvatarUpload != null) {
                    OutlinedButton(onClick = onUpdateAvatarNow, enabled = !saving) {
                        Text(stringResource(R.string.character_avatar_update_now))
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.character_avatar_processing_hint),
            style = MaterialTheme.typography.bodySmall,
            color = STTheme.colors.muted
        )
        pendingAvatarUpload?.let { upload ->
            val outputName = CharacterEditTools.avatarOutputFileName(upload.fileName)
            Text(
                text = stringResource(R.string.character_avatar_processing_output, outputName),
                style = MaterialTheme.typography.bodySmall,
                color = STTheme.colors.muted
            )
        }
    }
}

@Composable
private fun CharacterEditorTabs(
    selectedTab: CharacterEditorTab,
    onSelected: (CharacterEditorTab) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CharacterEditorTab.values().forEach { tab ->
            FilterChip(
                selected = selectedTab == tab,
                onClick = { onSelected(tab) },
                label = {
                    Text(
                        when (tab) {
                            CharacterEditorTab.BASIC -> stringResource(R.string.character_editor_tab_basic)
                            CharacterEditorTab.PROMPT -> stringResource(R.string.character_editor_tab_prompt)
                            CharacterEditorTab.METADATA -> stringResource(R.string.character_editor_tab_metadata)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun CharacterEditorFields(
    draft: CharacterEditDraft,
    saving: Boolean,
    selectedTab: CharacterEditorTab,
    onDraftChanged: (CharacterEditDraft) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (selectedTab) {
            CharacterEditorTab.BASIC -> CharacterEditorSection(title = stringResource(R.string.character_edit_basic_section)) {
                CharacterField(
                    label = stringResource(R.string.character_edit_name),
                    value = draft.name,
                    enabled = !saving,
                    onValueChange = { onDraftChanged(draft.copy(name = it)) }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.isFavorite,
                        onCheckedChange = { onDraftChanged(draft.copy(isFavorite = it)) },
                        enabled = !saving
                    )
                    Text(stringResource(R.string.character_edit_favorite))
                }
                CharacterField(
                    label = stringResource(R.string.character_edit_tags),
                    value = draft.tagsText,
                    enabled = !saving,
                    onValueChange = { onDraftChanged(draft.copy(tagsText = it)) }
                )
                CharacterField(
                    label = stringResource(R.string.character_edit_description),
                    value = draft.description,
                    enabled = !saving,
                    minLines = 4,
                    onValueChange = { onDraftChanged(draft.copy(description = it)) }
                )
                CharacterField(
                    label = stringResource(R.string.character_edit_first_message),
                    value = draft.firstMessage,
                    enabled = !saving,
                    minLines = 3,
                    onValueChange = { onDraftChanged(draft.copy(firstMessage = it)) }
                )
            }

            CharacterEditorTab.PROMPT -> {
                CharacterEditorSection(title = stringResource(R.string.character_edit_prompt_section)) {
                    CharacterAlternateGreetingsEditor(
                        greetings = draft.alternateGreetings,
                        enabled = !saving,
                        onGreetingsChanged = { onDraftChanged(draft.copy(alternateGreetings = it)) }
                    )
                    CharacterField(
                        label = stringResource(R.string.character_edit_system_prompt),
                        value = draft.systemPrompt,
                        enabled = !saving,
                        minLines = 3,
                        onValueChange = { onDraftChanged(draft.copy(systemPrompt = it)) }
                    )
                    CharacterField(
                        label = stringResource(R.string.character_edit_post_history),
                        value = draft.postHistoryInstructions,
                        enabled = !saving,
                        minLines = 3,
                        onValueChange = { onDraftChanged(draft.copy(postHistoryInstructions = it)) }
                    )
                    CharacterField(
                        label = stringResource(R.string.character_edit_depth_prompt),
                        value = draft.depthPrompt,
                        enabled = !saving,
                        minLines = 3,
                        onValueChange = { onDraftChanged(draft.copy(depthPrompt = it)) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CharacterField(
                            label = stringResource(R.string.character_edit_depth_prompt_depth),
                            value = draft.depthPromptDepthText,
                            enabled = !saving,
                            onValueChange = { onDraftChanged(draft.copy(depthPromptDepthText = it)) },
                            modifier = Modifier.weight(1f)
                        )
                        CharacterField(
                            label = stringResource(R.string.character_edit_depth_prompt_role),
                            value = draft.depthPromptRole,
                            enabled = !saving,
                            onValueChange = { onDraftChanged(draft.copy(depthPromptRole = it)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            CharacterEditorTab.METADATA -> {
                CharacterEditorSection(title = stringResource(R.string.character_edit_metadata_section)) {
                    CharacterField(
                        label = stringResource(R.string.character_edit_creator),
                        value = draft.creator,
                        enabled = !saving,
                        onValueChange = { onDraftChanged(draft.copy(creator = it)) }
                    )
                    CharacterField(
                        label = stringResource(R.string.character_edit_version),
                        value = draft.characterVersion,
                        enabled = !saving,
                        onValueChange = { onDraftChanged(draft.copy(characterVersion = it)) }
                    )
                    CharacterField(
                        label = stringResource(R.string.character_edit_world),
                        value = draft.world,
                        enabled = !saving,
                        onValueChange = { onDraftChanged(draft.copy(world = it)) }
                    )
                    CharacterField(
                        label = stringResource(R.string.character_edit_source_url),
                        value = draft.sourceUrl,
                        enabled = !saving,
                        onValueChange = { onDraftChanged(draft.copy(sourceUrl = it)) }
                    )
                    CharacterField(
                        label = stringResource(R.string.character_edit_creator_notes),
                        value = draft.creatorNotes,
                        enabled = !saving,
                        minLines = 3,
                        onValueChange = { onDraftChanged(draft.copy(creatorNotes = it)) }
                    )
                    CharacterField(
                        label = stringResource(R.string.character_edit_personality),
                        value = draft.personality,
                        enabled = !saving,
                        minLines = 3,
                        onValueChange = { onDraftChanged(draft.copy(personality = it)) }
                    )
                    CharacterField(
                        label = stringResource(R.string.character_edit_scenario),
                        value = draft.scenario,
                        enabled = !saving,
                        minLines = 3,
                        onValueChange = { onDraftChanged(draft.copy(scenario = it)) }
                    )
                    CharacterField(
                        label = stringResource(R.string.character_edit_message_example),
                        value = draft.messageExample,
                        enabled = !saving,
                        minLines = 3,
                        onValueChange = { onDraftChanged(draft.copy(messageExample = it)) }
                    )
                    CharacterTalkativenessField(
                        value = draft.talkativenessText,
                        enabled = !saving,
                        onValueChange = { onDraftChanged(draft.copy(talkativenessText = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterAlternateGreetingsEditor(
    greetings: List<String>,
    enabled: Boolean,
    onGreetingsChanged: (List<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.character_edit_alternate_greetings),
            style = MaterialTheme.typography.labelLarge,
            color = STTheme.colors.muted
        )
        if (greetings.isEmpty()) {
            Text(
                text = stringResource(R.string.character_greeting_empty),
                style = MaterialTheme.typography.bodySmall,
                color = STTheme.colors.muted
            )
        }
        greetings.forEachIndexed { index, greeting ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CharacterField(
                    label = stringResource(R.string.character_greeting_label, index + 1),
                    value = greeting,
                    enabled = enabled,
                    minLines = 2,
                    onValueChange = { value ->
                        onGreetingsChanged(greetings.toMutableList().also { it[index] = value })
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = enabled && index > 0,
                        onClick = {
                            onGreetingsChanged(greetings.toMutableList().also {
                                val item = it.removeAt(index)
                                it.add(index - 1, item)
                            })
                        }
                    ) {
                        Text(stringResource(R.string.character_greeting_move_up))
                    }
                    TextButton(
                        enabled = enabled && index < greetings.lastIndex,
                        onClick = {
                            onGreetingsChanged(greetings.toMutableList().also {
                                val item = it.removeAt(index)
                                it.add(index + 1, item)
                            })
                        }
                    ) {
                        Text(stringResource(R.string.character_greeting_move_down))
                    }
                    TextButton(
                        enabled = enabled,
                        onClick = {
                            onGreetingsChanged(greetings.toMutableList().also { it.removeAt(index) })
                        }
                    ) {
                        Text(stringResource(R.string.character_greeting_remove))
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { onGreetingsChanged(greetings + "") },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.character_greeting_add))
        }
    }
}

@Composable
private fun CharacterTalkativenessField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    val numeric = value.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CharacterField(
            label = stringResource(R.string.character_edit_talkativeness),
            value = value,
            enabled = enabled,
            onValueChange = { text ->
                val clean = text.toDoubleOrNull()?.coerceIn(0.0, 1.0)?.toString() ?: text
                onValueChange(clean)
            }
        )
        Slider(
            value = numeric,
            enabled = enabled,
            onValueChange = { onValueChange(String.format(java.util.Locale.US, "%.2f", it)) }
        )
    }
}

@Composable
private fun CharacterTokenCounterSection(draft: CharacterEditDraft) {
    val stats = CharacterEditTools.tokenStats(
        CharacterTokenInput(
            description = draft.description,
            firstMessage = draft.firstMessage,
            alternateGreetings = draft.alternateGreetings,
            systemPrompt = draft.systemPrompt,
            postHistoryInstructions = draft.postHistoryInstructions,
            depthPrompt = draft.depthPrompt,
            creatorNotes = draft.creatorNotes,
            personality = draft.personality,
            scenario = draft.scenario,
            messageExample = draft.messageExample
        )
    )
    CharacterEditorSection(title = stringResource(R.string.character_token_section)) {
        Text(
            text = stringResource(R.string.character_token_body),
            style = MaterialTheme.typography.bodySmall,
            color = STTheme.colors.muted
        )
        CharacterTokenRow(stringResource(R.string.character_token_total), stats.total)
        CharacterTokenRow(stringResource(R.string.character_token_description), stats.description)
        CharacterTokenRow(stringResource(R.string.character_token_greetings), stats.greetings)
        CharacterTokenRow(stringResource(R.string.character_token_prompt_note), stats.promptAndNote)
        CharacterTokenRow(stringResource(R.string.character_token_metadata_examples), stats.metadataAndExamples)
    }
}

@Composable
private fun CharacterTokenRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.character_token_count, count),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CharacterEditorSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = STTheme.colors.surface),
        border = BorderStroke(1.dp, STTheme.colors.border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun CharacterField(
    label: String,
    value: String,
    enabled: Boolean,
    minLines: Int = 1,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        minLines = minLines,
        modifier = modifier
    )
}

@Composable
private fun CharacterEditorInfoCard(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = STTheme.colors.surface),
        border = BorderStroke(1.dp, STTheme.colors.border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = STTheme.colors.muted)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun CharacterManagementActions(
    saving: Boolean,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onChangeAvatar: () -> Unit,
    onExportJson: () -> Unit,
    onExportPng: () -> Unit,
    onReplaceFromFile: () -> Unit,
    onUpdateFromSource: () -> Unit,
    sourceAvailable: Boolean,
    onDelete: () -> Unit
) {
    CharacterEditorSection(title = stringResource(R.string.character_edit_management_section)) {
        Text(
            text = stringResource(R.string.character_edit_management_body),
            style = MaterialTheme.typography.bodySmall,
            color = STTheme.colors.muted
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRename,
                enabled = !saving,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.character_action_rename))
            }
            OutlinedButton(
                onClick = onDuplicate,
                enabled = !saving,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.character_action_duplicate))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onChangeAvatar,
                enabled = !saving,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.character_action_avatar))
            }
            OutlinedButton(
                onClick = onExportJson,
                enabled = !saving,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.character_action_export_json))
            }
        }
        OutlinedButton(
            onClick = onExportPng,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.character_action_export_png))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReplaceFromFile,
                enabled = !saving,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.character_action_replace_file))
            }
            OutlinedButton(
                onClick = onUpdateFromSource,
                enabled = !saving && sourceAvailable,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.character_action_update_source))
            }
        }
        OutlinedButton(
            onClick = onDelete,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.character_action_delete))
        }
    }
}

private fun CharacterDetail.toDraft(): CharacterEditDraft {
    return CharacterEditDraft(
        avatar = id,
        name = name,
        description = description,
        firstMessage = firstMessage,
        creatorNotes = creatorNotes,
        messageExample = messageExample,
        personality = personality,
        scenario = scenario,
        systemPrompt = systemPrompt,
        postHistoryInstructions = postHistoryInstructions,
        tagsText = tags.joinToString(", "),
        creator = creator,
        characterVersion = characterVersion,
        world = world,
        talkativenessText = talkativeness.toString(),
        alternateGreetings = alternateGreetings,
        depthPrompt = depthPrompt,
        depthPromptDepthText = depthPromptDepth.toString(),
        depthPromptRole = depthPromptRole,
        chat = chat,
        createDate = createDate,
        rawJsonData = rawJsonData,
        sourceUrl = sourceUrl,
        isFavorite = isFavorite
    )
}

private fun saveCharacter(
    context: Context,
    draft: CharacterEditDraft,
    baseUrl: String,
    onShowMessage: (String) -> Unit,
    onSavingChanged: (Boolean) -> Unit,
    onSaved: (String) -> Unit,
    isNew: Boolean,
    avatarUpload: CharacterUpload?,
    scope: kotlinx.coroutines.CoroutineScope
) {
    if (draft.name.isBlank()) {
        onShowMessage(context.getString(R.string.character_name_required))
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            val client = TavernCoreClient(baseUrl = baseUrl)
            val preparedAvatar = avatarUpload?.let {
                context.prepareCharacterAvatarUpload(it)
            }
            if (isNew) {
                client.createCharacter(draft.toSaveRequest(), preparedAvatar)
            } else {
                client.updateCharacter(draft.toSaveRequest(), preparedAvatar)
                draft.avatar.orEmpty()
            }
        }.onSuccess { avatar ->
            onShowMessage(context.getString(R.string.character_save_success))
            onSaved(avatar)
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_save_failed))
        }
        onSavingChanged(false)
    }
}

private fun updateCharacterAvatar(
    context: Context,
    upload: CharacterUpload,
    draft: CharacterEditDraft,
    baseUrl: String,
    onShowMessage: (String) -> Unit,
    onSavingChanged: (Boolean) -> Unit,
    onUpdated: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val avatar = draft.avatar?.takeIf { it.isNotBlank() }
    if (avatar == null) {
        onShowMessage(context.getString(R.string.character_save_before_avatar))
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            val preparedAvatar = context.prepareCharacterAvatarUpload(upload)
            TavernCoreClient(baseUrl = baseUrl).updateCharacterAvatar(
                avatar,
                preparedAvatar.fileName,
                preparedAvatar.bytes
            )
        }.onSuccess {
            onUpdated()
            onShowMessage(context.getString(R.string.character_avatar_success))
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_avatar_failed))
        }
        onSavingChanged(false)
    }
}

private fun replaceCharacterFromFile(
    context: Context,
    uri: Uri,
    draft: CharacterEditDraft,
    baseUrl: String,
    onShowMessage: (String) -> Unit,
    onSavingChanged: (Boolean) -> Unit,
    onSaved: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val avatar = draft.avatar?.takeIf { it.isNotBlank() }
    if (avatar == null) {
        onShowMessage(context.getString(R.string.character_save_before_replace))
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            val document = context.readPickedDocument(uri)
            TavernCoreClient(baseUrl = baseUrl).importCharacter(
                fileName = document.fileName,
                bytes = document.bytes,
                preservedName = avatar
            )
        }.onSuccess { newAvatar ->
            onShowMessage(context.getString(R.string.character_replace_success))
            onSaved(newAvatar.ifBlank { avatar })
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_replace_failed))
        }
        onSavingChanged(false)
    }
}

private fun replaceCharacterFromSource(
    context: Context,
    draft: CharacterEditDraft,
    baseUrl: String,
    onShowMessage: (String) -> Unit,
    onSavingChanged: (Boolean) -> Unit,
    onSaved: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val avatar = draft.avatar?.takeIf { it.isNotBlank() }
    val sourceUrl = draft.sourceUrl.trim()
    if (avatar == null) {
        onShowMessage(context.getString(R.string.character_save_before_replace))
        return
    }
    if (sourceUrl.isBlank()) {
        onShowMessage(context.getString(R.string.character_replace_source_missing))
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            TavernCoreClient(baseUrl = baseUrl).importExternalCharacter(sourceUrl, preservedName = avatar)
        }.onSuccess { newAvatar ->
            onShowMessage(context.getString(R.string.character_replace_success))
            onSaved(newAvatar.ifBlank { avatar })
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_replace_failed))
        }
        onSavingChanged(false)
    }
}

private fun exportCharacter(
    context: Context,
    uri: Uri,
    format: CharacterExportFormat,
    draft: CharacterEditDraft,
    baseUrl: String,
    onShowMessage: (String) -> Unit,
    onSavingChanged: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val avatar = draft.avatar?.takeIf { it.isNotBlank() }
    if (avatar == null) {
        onShowMessage(context.getString(R.string.character_save_before_export))
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            val file = TavernCoreClient(baseUrl = baseUrl).exportCharacter(avatar, format)
            context.writePickedDocument(uri, file.bytes)
        }.onSuccess {
            onShowMessage(context.getString(R.string.character_export_success))
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_export_failed))
        }
        onSavingChanged(false)
    }
}

private fun defaultExportFileName(draft: CharacterEditDraft, format: CharacterExportFormat): String {
    val baseName = draft.avatar
        ?.removeSuffix(".png")
        ?.takeIf { it.isNotBlank() }
        ?: draft.name.trim().ifBlank { "character" }
    return "$baseName.${format.fileExtension}"
}

private fun renameCharacter(
    context: Context,
    draft: CharacterEditDraft,
    baseUrl: String,
    onShowMessage: (String) -> Unit,
    onSavingChanged: (Boolean) -> Unit,
    onSaved: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val avatar = draft.avatar?.takeIf { it.isNotBlank() }
    val newName = draft.name.trim()
    if (avatar == null || newName.isBlank()) {
        onShowMessage(context.getString(R.string.character_name_required))
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            TavernCoreClient(baseUrl = baseUrl).renameCharacter(avatar, newName)
        }.onSuccess { newAvatar ->
            onShowMessage(context.getString(R.string.character_rename_success))
            onSaved(newAvatar.ifBlank { avatar })
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_rename_failed))
        }
        onSavingChanged(false)
    }
}

private fun duplicateCharacter(
    context: Context,
    draft: CharacterEditDraft,
    baseUrl: String,
    onShowMessage: (String) -> Unit,
    onSavingChanged: (Boolean) -> Unit,
    onSaved: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val avatar = draft.avatar?.takeIf { it.isNotBlank() }
    if (avatar == null) {
        onShowMessage(context.getString(R.string.character_save_before_duplicate))
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            TavernCoreClient(baseUrl = baseUrl).duplicateCharacter(avatar)
        }.onSuccess { newAvatar ->
            onShowMessage(context.getString(R.string.character_duplicate_success))
            onSaved(newAvatar.ifBlank { avatar })
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_duplicate_failed))
        }
        onSavingChanged(false)
    }
}

private fun deleteCharacter(
    context: Context,
    draft: CharacterEditDraft,
    baseUrl: String,
    deleteChats: Boolean,
    onShowMessage: (String) -> Unit,
    onSavingChanged: (Boolean) -> Unit,
    onDeleted: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val avatar = draft.avatar?.takeIf { it.isNotBlank() }
    if (avatar == null) {
        onShowMessage(context.getString(R.string.character_save_before_delete))
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            TavernCoreClient(baseUrl = baseUrl).deleteCharacter(avatar, deleteChats)
        }.onSuccess {
            onShowMessage(context.getString(R.string.character_delete_success))
            onDeleted()
        }.onFailure { error ->
            onShowMessage(error.messageOr(context, R.string.character_delete_failed))
        }
        onSavingChanged(false)
    }
}

private fun Throwable.messageOr(context: Context, fallbackResId: Int): String {
    return message ?: context.getString(fallbackResId)
}
