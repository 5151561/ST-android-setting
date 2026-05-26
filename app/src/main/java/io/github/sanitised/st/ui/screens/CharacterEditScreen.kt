package io.github.sanitised.st.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
    val alternateGreetingsText: String = "",
    val depthPrompt: String = "",
    val depthPromptDepthText: String = "4",
    val depthPromptRole: String = "system",
    val chat: String = "",
    val createDate: String = "",
    val rawJsonData: String = "",
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
            alternateGreetings = alternateGreetingsText.lines().map { it.trim() }.filter { it.isNotBlank() },
            depthPrompt = depthPrompt,
            depthPromptDepth = depthPromptDepthText.toIntOrNull() ?: 4,
            depthPromptRole = depthPromptRole.trim().ifBlank { "system" },
            chat = chat,
            createDate = createDate,
            rawJsonData = rawJsonData,
            isFavorite = isFavorite
        )
    }
}

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
    val serverRunning = status.state == NodeState.RUNNING
    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        updateCharacterAvatar(
            context = context,
            uri = uri,
            draft = draft,
            baseUrl = baseUrl,
            onShowMessage = onShowMessage,
            onSavingChanged = { saving = it },
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
            onShowMessage(error.message ?: "Unable to load character")
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
                            draft = draft,
                            baseUrl = baseUrl,
                            onShowMessage = onShowMessage,
                            onSavingChanged = { saving = it },
                            onSaved = onSaved,
                            isNew = avatar.isNullOrBlank(),
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
                return@Column
            }

            if (loading) {
                CharacterEditorInfoCard(
                    title = stringResource(R.string.character_edit_loading),
                    body = stringResource(R.string.waiting_for_server)
                )
                return@Column
            }

            CharacterEditorFields(
                draft = draft,
                saving = saving,
                onDraftChanged = { draft = it }
            )

            if (!draft.avatar.isNullOrBlank()) {
                CharacterManagementActions(
                    saving = saving,
                    onRename = {
                        renameCharacter(
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
                            draft = draft,
                            baseUrl = baseUrl,
                            onShowMessage = onShowMessage,
                            onSavingChanged = { saving = it },
                            onSaved = onSaved,
                            isNew = avatar.isNullOrBlank(),
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

@Composable
private fun CharacterEditorFields(
    draft: CharacterEditDraft,
    saving: Boolean,
    onDraftChanged: (CharacterEditDraft) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CharacterEditorSection(title = stringResource(R.string.character_edit_basic_section)) {
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
            CharacterField(
                label = stringResource(R.string.character_edit_alternate_greetings),
                value = draft.alternateGreetingsText,
                enabled = !saving,
                minLines = 3,
                onValueChange = { onDraftChanged(draft.copy(alternateGreetingsText = it)) }
            )
        }

        CharacterEditorSection(title = stringResource(R.string.character_edit_prompt_section)) {
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
        }

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
                label = stringResource(R.string.character_edit_creator_notes),
                value = draft.creatorNotes,
                enabled = !saving,
                minLines = 3,
                onValueChange = { onDraftChanged(draft.copy(creatorNotes = it)) }
            )
        }

        CharacterEditorSection(title = stringResource(R.string.character_edit_advanced_section)) {
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
        }

        CharacterEditorSection(title = stringResource(R.string.character_edit_note_section)) {
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
            CharacterField(
                label = stringResource(R.string.character_edit_talkativeness),
                value = draft.talkativenessText,
                enabled = !saving,
                onValueChange = { onDraftChanged(draft.copy(talkativenessText = it)) }
            )
        }
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
        alternateGreetingsText = alternateGreetings.joinToString("\n"),
        depthPrompt = depthPrompt,
        depthPromptDepthText = depthPromptDepth.toString(),
        depthPromptRole = depthPromptRole,
        chat = chat,
        createDate = createDate,
        rawJsonData = rawJsonData,
        isFavorite = isFavorite
    )
}

private fun saveCharacter(
    draft: CharacterEditDraft,
    baseUrl: String,
    onShowMessage: (String) -> Unit,
    onSavingChanged: (Boolean) -> Unit,
    onSaved: (String) -> Unit,
    isNew: Boolean,
    scope: kotlinx.coroutines.CoroutineScope
) {
    if (draft.name.isBlank()) {
        onShowMessage("Character name is required.")
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            val client = TavernCoreClient(baseUrl = baseUrl)
            if (isNew) {
                client.createCharacter(draft.toSaveRequest())
            } else {
                client.updateCharacter(draft.toSaveRequest())
                draft.avatar.orEmpty()
            }
        }.onSuccess { avatar ->
            onShowMessage("Character saved.")
            onSaved(avatar)
        }.onFailure { error ->
            onShowMessage(error.message ?: "Unable to save character")
        }
        onSavingChanged(false)
    }
}

private fun updateCharacterAvatar(
    context: Context,
    uri: Uri,
    draft: CharacterEditDraft,
    baseUrl: String,
    onShowMessage: (String) -> Unit,
    onSavingChanged: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val avatar = draft.avatar?.takeIf { it.isNotBlank() }
    if (avatar == null) {
        onShowMessage("Save the character before changing its avatar.")
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            val document = context.readPickedDocument(uri)
            TavernCoreClient(baseUrl = baseUrl).updateCharacterAvatar(avatar, document.fileName, document.bytes)
        }.onSuccess {
            onShowMessage("Avatar updated.")
        }.onFailure { error ->
            onShowMessage(error.message ?: "Unable to update avatar")
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
        onShowMessage("Save the character before exporting it.")
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            val file = TavernCoreClient(baseUrl = baseUrl).exportCharacter(avatar, format)
            context.writePickedDocument(uri, file.bytes)
        }.onSuccess {
            onShowMessage("Character exported.")
        }.onFailure { error ->
            onShowMessage(error.message ?: "Unable to export character")
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
        onShowMessage("Character name is required.")
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            TavernCoreClient(baseUrl = baseUrl).renameCharacter(avatar, newName)
        }.onSuccess { newAvatar ->
            onShowMessage("Character renamed.")
            onSaved(newAvatar.ifBlank { avatar })
        }.onFailure { error ->
            onShowMessage(error.message ?: "Unable to rename character")
        }
        onSavingChanged(false)
    }
}

private fun duplicateCharacter(
    draft: CharacterEditDraft,
    baseUrl: String,
    onShowMessage: (String) -> Unit,
    onSavingChanged: (Boolean) -> Unit,
    onSaved: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val avatar = draft.avatar?.takeIf { it.isNotBlank() }
    if (avatar == null) {
        onShowMessage("Save the character before duplicating it.")
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            TavernCoreClient(baseUrl = baseUrl).duplicateCharacter(avatar)
        }.onSuccess { newAvatar ->
            onShowMessage("Character duplicated.")
            onSaved(newAvatar.ifBlank { avatar })
        }.onFailure { error ->
            onShowMessage(error.message ?: "Unable to duplicate character")
        }
        onSavingChanged(false)
    }
}

private fun deleteCharacter(
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
        onShowMessage("Save the character before deleting it.")
        return
    }
    scope.launch {
        onSavingChanged(true)
        runCatching {
            TavernCoreClient(baseUrl = baseUrl).deleteCharacter(avatar, deleteChats)
        }.onSuccess {
            onShowMessage("Character deleted.")
            onDeleted()
        }.onFailure { error ->
            onShowMessage(error.message ?: "Unable to delete character")
        }
        onSavingChanged(false)
    }
}
