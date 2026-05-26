package io.github.sanitised.st.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.R
import io.github.sanitised.st.api.CharacterUpload
import io.github.sanitised.st.api.ChatBackupSummary
import io.github.sanitised.st.api.ConnectionProfile
import io.github.sanitised.st.api.PersonaProfile
import io.github.sanitised.st.api.PersonaSaveRequest
import io.github.sanitised.st.api.PresetCategory
import io.github.sanitised.st.api.PresetLibrary
import io.github.sanitised.st.api.PresetSummary
import io.github.sanitised.st.api.SecretProviderState
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.api.WorldInfoEntry
import io.github.sanitised.st.api.WorldInfoSummary
import io.github.sanitised.st.ui.components.STConfirmDialog
import io.github.sanitised.st.ui.components.STInfoCard
import io.github.sanitised.st.ui.components.STSectionCard
import kotlinx.coroutines.launch

private enum class PersonaViewMode {
    LIST,
    DETAIL
}

private enum class WorldInfoViewMode {
    LIST,
    DETAIL
}

private enum class PresetViewMode {
    LIST,
    DETAIL
}

private enum class ConnectionViewMode {
    LIST,
    DETAIL
}

private enum class ConnectionDetailMode {
    KEY,
    ENDPOINT
}

private enum class M3HeroTone {
    PRIMARY,
    TERTIARY,
    SURFACE
}

@Composable
fun WorldInfoScreen(
    status: NodeStatus,
    baseUrl: String,
    onStartService: () -> Unit,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverRunning = status.state == NodeState.RUNNING
    var worlds by remember { mutableStateOf<List<WorldInfoSummary>>(emptyList()) }
    var viewMode by remember { mutableStateOf(WorldInfoViewMode.LIST) }
    var selectedWorldId by remember { mutableStateOf<String?>(null) }
    var book by remember { mutableStateOf<WorldInfoBook?>(null) }
    var selectedEntryUid by remember { mutableStateOf<Int?>(null) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var newWorldDialog by remember { mutableStateOf(false) }
    var newWorldName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<WorldInfoBook?>(null) }

    fun loadBook(worldId: String) {
        scope.launch {
            loading = true
            runCatching {
                TavernCoreClient(baseUrl).getWorldInfo(worldId)
            }.onSuccess { loaded ->
                book = loaded
                selectedWorldId = worldId
                selectedEntryUid = loaded.entries.firstOrNull()?.uid
                viewMode = WorldInfoViewMode.DETAIL
            }.onFailure {
                onShowMessage(context.getString(R.string.m3_world_info_load_failed))
            }
            loading = false
        }
    }

    fun refresh() {
        if (!serverRunning) return
        scope.launch {
            loading = true
            runCatching {
                TavernCoreClient(baseUrl).listWorldInfos()
            }.onSuccess { loaded ->
                worlds = loaded
                val nextId = selectedWorldId?.takeIf { id -> loaded.any { it.id == id } }
                selectedWorldId = nextId
                if (viewMode == WorldInfoViewMode.DETAIL && nextId != null) {
                    loadBook(nextId)
                } else if (nextId == null) {
                    book = null
                    selectedEntryUid = null
                }
            }.onFailure {
                onShowMessage(context.getString(R.string.m3_world_info_load_failed))
            }
            loading = false
        }
    }

    LaunchedEffect(serverRunning, baseUrl) {
        if (serverRunning) refresh()
    }

    pendingDelete?.let { target ->
        STConfirmDialog(
            title = stringResource(R.string.m3_world_info_delete_title),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    runCatching {
                        TavernCoreClient(baseUrl).deleteWorldInfo(target.name)
                    }.onSuccess {
                        onShowMessage(context.getString(R.string.m3_world_info_deleted))
                        book = null
                        selectedWorldId = null
                        viewMode = WorldInfoViewMode.LIST
                        refresh()
                    }.onFailure {
                        onShowMessage(context.getString(R.string.m3_world_info_save_failed))
                    }
                }
            },
            onDismiss = { pendingDelete = null },
            body = { Text(stringResource(R.string.m3_world_info_delete_body, target.name)) }
        )
    }

    if (newWorldDialog) {
        AlertDialog(
            onDismissRequest = { newWorldDialog = false },
            title = { Text(stringResource(R.string.m3_world_info_new)) },
            text = {
                OutlinedTextField(
                    value = newWorldName,
                    onValueChange = { newWorldName = it },
                    label = { Text(stringResource(R.string.m3_world_info_new_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newWorldName.isNotBlank(),
                    onClick = {
                        val name = newWorldName.trim()
                        newWorldDialog = false
                        newWorldName = ""
                        scope.launch {
                            runCatching {
                                val created = WorldInfoBook(name = name, rawData = mapOf("name" to name))
                                TavernCoreClient(baseUrl).saveWorldInfo(created)
                                created
                            }.onSuccess {
                                onShowMessage(context.getString(R.string.m3_world_info_saved))
                                selectedWorldId = name
                                viewMode = WorldInfoViewMode.DETAIL
                                refresh()
                            }.onFailure {
                                onShowMessage(context.getString(R.string.m3_world_info_save_failed))
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { newWorldDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    M3ManagerScaffold(
        title = stringResource(R.string.m3_world_info_title),
        subtitle = stringResource(R.string.m3_world_info_subtitle),
        status = status,
        onStartService = onStartService,
        onBack = onBack,
        onRefresh = ::refresh,
        modifier = modifier
    ) {
        if (!serverRunning) return@M3ManagerScaffold
        when (viewMode) {
            WorldInfoViewMode.LIST -> {
                M3StateBanner(
                    title = stringResource(R.string.m3_state_list_world_info),
                    body = stringResource(R.string.m3_state_list_world_info_body)
                )
                M3HeroSurface(
                    title = stringResource(R.string.m3_world_info_list_hero_title),
                    body = stringResource(R.string.m3_world_info_list_hero_body, worlds.size),
                    labels = listOf(
                        stringResource(R.string.tools_hub_list_detail),
                        stringResource(R.string.m3_source_fidelity)
                    ),
                    tone = M3HeroTone.TERTIARY
                )
                M3SearchField(search = search, onSearchChanged = { search = it })
                OutlinedButton(onClick = { newWorldDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.m3_world_info_new))
                }
                WorldListCard(
                    worlds = worlds.filter { it.name.contains(search, ignoreCase = true) || it.id.contains(search, ignoreCase = true) },
                    selectedWorldId = selectedWorldId,
                    loading = loading,
                    onSelect = { loadBook(it.id) }
                )
                M3SectionSurface(
                    title = stringResource(R.string.m3_list_responsibility),
                    body = stringResource(R.string.m3_world_info_list_responsibility)
                )
            }

            WorldInfoViewMode.DETAIL -> {
                val current = book
                M3StateBanner(
                    title = stringResource(R.string.m3_state_detail_world_info),
                    body = stringResource(R.string.m3_state_detail_world_info_body)
                )
                M3HeroSurface(
                    title = current?.name ?: stringResource(R.string.m3_world_info_title),
                    body = stringResource(
                        R.string.m3_world_info_detail_hero_body,
                        current?.entries?.size ?: 0
                    ),
                    labels = listOf(
                        stringResource(R.string.m3_detail_form_only),
                        stringResource(R.string.m3_source_fidelity)
                    ),
                    tone = M3HeroTone.TERTIARY
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { viewMode = WorldInfoViewMode.LIST }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.back))
                    }
                    OutlinedButton(
                        onClick = {
                            if (current != null) {
                                val nextUid = (current.entries.maxOfOrNull { it.uid } ?: 0) + 1
                                val nextEntry = WorldInfoEntry(uid = nextUid, comment = "Entry $nextUid")
                                book = current.copy(entries = current.entries + nextEntry)
                                selectedEntryUid = nextUid
                            }
                        },
                        enabled = current != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.m3_world_info_entry_new))
                    }
                }
                current?.let { editable ->
                    WorldEntryEditor(
                        book = editable,
                        selectedEntryUid = selectedEntryUid,
                        onSelectEntry = { selectedEntryUid = it },
                        onBookChanged = { book = it },
                        onSave = {
                            scope.launch {
                                runCatching {
                                    TavernCoreClient(baseUrl).saveWorldInfo(editable)
                                }.onSuccess {
                                    onShowMessage(context.getString(R.string.m3_world_info_saved))
                                    refresh()
                                }.onFailure {
                                    onShowMessage(context.getString(R.string.m3_world_info_save_failed))
                                }
                            }
                        },
                        onDelete = { pendingDelete = editable }
                    )
                }
            }
        }
    }
}

@Composable
fun PersonaScreen(
    status: NodeStatus,
    baseUrl: String,
    onStartService: () -> Unit,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverRunning = status.state == NodeState.RUNNING
    var personas by remember { mutableStateOf<List<PersonaProfile>>(emptyList()) }
    var viewMode by remember { mutableStateOf(PersonaViewMode.LIST) }
    var selectedAvatar by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var makeDefault by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<PersonaProfile?>(null) }

    fun applyPersonaDraft(persona: PersonaProfile) {
        selectedAvatar = persona.avatar
        name = persona.name
        title = persona.title
        description = persona.description
        makeDefault = persona.isDefault
    }

    fun openPersona(persona: PersonaProfile) {
        applyPersonaDraft(persona)
        viewMode = PersonaViewMode.DETAIL
    }

    fun refresh() {
        if (!serverRunning) return
        scope.launch {
            loading = true
            runCatching {
                TavernCoreClient(baseUrl).listPersonas()
            }.onSuccess { loaded ->
                personas = loaded
                val current = selectedAvatar?.let { avatar -> loaded.firstOrNull { it.avatar == avatar } }
                if (current != null) {
                    applyPersonaDraft(current)
                } else if (viewMode == PersonaViewMode.DETAIL) {
                    selectedAvatar = null
                    viewMode = PersonaViewMode.LIST
                }
            }.onFailure {
                onShowMessage(context.getString(R.string.m3_persona_load_failed))
            }
            loading = false
        }
    }

    val avatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val picked = context.readPickedDocument(uri)
                val upload = context.prepareCharacterAvatarUpload(CharacterUpload(picked.fileName, picked.bytes))
                val uploaded = TavernCoreClient(baseUrl).uploadPersonaAvatar(
                    fileName = upload.fileName,
                    bytes = upload.bytes,
                    overwriteName = selectedAvatar.takeIf { viewMode == PersonaViewMode.DETAIL }
                )
                val personaName = name.ifBlank { uploaded.substringBeforeLast('.') }
                TavernCoreClient(baseUrl).savePersona(
                    PersonaSaveRequest(
                        avatar = uploaded,
                        name = personaName,
                        title = title,
                        description = description,
                        makeDefault = makeDefault
                    )
                )
                uploaded
            }.onSuccess { uploaded ->
                onShowMessage(context.getString(R.string.m3_persona_uploaded))
                selectedAvatar = uploaded
                viewMode = PersonaViewMode.DETAIL
                refresh()
            }.onFailure {
                onShowMessage(context.getString(R.string.m3_persona_save_failed))
            }
        }
    }

    LaunchedEffect(serverRunning, baseUrl) {
        if (serverRunning) refresh()
    }

    pendingDelete?.let { persona ->
        STConfirmDialog(
            title = stringResource(R.string.m3_persona_delete_title),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    runCatching {
                        TavernCoreClient(baseUrl).deletePersona(persona.avatar)
                    }.onSuccess {
                        onShowMessage(context.getString(R.string.m3_persona_deleted))
                        selectedAvatar = null
                        viewMode = PersonaViewMode.LIST
                        refresh()
                    }.onFailure {
                        onShowMessage(context.getString(R.string.m3_persona_save_failed))
                    }
                }
            },
            onDismiss = { pendingDelete = null },
            body = { Text(stringResource(R.string.m3_persona_delete_body, persona.name)) }
        )
    }

    M3ManagerScaffold(
        title = stringResource(R.string.m3_persona_title),
        subtitle = stringResource(R.string.m3_persona_subtitle),
        status = status,
        onStartService = onStartService,
        onBack = onBack,
        onRefresh = ::refresh,
        modifier = modifier
    ) {
        if (!serverRunning) return@M3ManagerScaffold
        when (viewMode) {
            PersonaViewMode.LIST -> {
                M3StateBanner(
                    title = stringResource(R.string.m3_state_list_persona),
                    body = stringResource(R.string.m3_state_list_persona_body)
                )
                M3HeroSurface(
                    title = stringResource(R.string.m3_persona_list_hero_title),
                    body = stringResource(R.string.m3_persona_list_hero_body, personas.size),
                    labels = listOf(
                        stringResource(R.string.tools_hub_list_detail),
                        stringResource(R.string.m3_persona_default)
                    )
                )
                M3SearchField(search = search, onSearchChanged = { search = it })
                OutlinedButton(
                    onClick = {
                        selectedAvatar = null
                        name = ""
                        title = ""
                        description = ""
                        makeDefault = false
                        avatarLauncher.launch(arrayOf("image/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.m3_persona_upload))
                }
                PersonaListCard(
                    personas = personas.filter {
                        it.name.contains(search, ignoreCase = true) ||
                            it.avatar.contains(search, ignoreCase = true) ||
                            it.description.contains(search, ignoreCase = true)
                    },
                    loading = loading,
                    onOpen = ::openPersona,
                    onEnable = { persona ->
                        scope.launch {
                            runCatching {
                                TavernCoreClient(baseUrl).savePersona(
                                    PersonaSaveRequest(
                                        avatar = persona.avatar,
                                        name = persona.name,
                                        title = persona.title,
                                        description = persona.description,
                                        makeDefault = true
                                    )
                                )
                            }.onSuccess {
                                selectedAvatar = persona.avatar
                                onShowMessage(context.getString(R.string.m3_persona_saved))
                                refresh()
                            }.onFailure {
                                onShowMessage(context.getString(R.string.m3_persona_save_failed))
                            }
                        }
                    }
                )
                M3SectionSurface(
                    title = stringResource(R.string.m3_list_responsibility),
                    body = stringResource(R.string.m3_persona_list_responsibility)
                )
            }

            PersonaViewMode.DETAIL -> {
                M3StateBanner(
                    title = stringResource(R.string.m3_state_detail_persona),
                    body = stringResource(R.string.m3_state_detail_persona_body)
                )
                M3HeroSurface(
                    title = name.ifBlank { stringResource(R.string.m3_persona_title) },
                    body = selectedAvatar ?: stringResource(R.string.m3_persona_missing_avatar),
                    labels = listOf(
                        if (makeDefault) stringResource(R.string.m3_enabled) else stringResource(R.string.m3_detail_form_only),
                        stringResource(R.string.m3_persona_upload)
                    )
                )
                PersonaDetailEditor(
                    avatar = selectedAvatar,
                    name = name,
                    title = title,
                    description = description,
                    makeDefault = makeDefault,
                    onNameChanged = { name = it },
                    onTitleChanged = { title = it },
                    onDescriptionChanged = { description = it },
                    onDefaultChanged = { makeDefault = it },
                    onChooseAvatar = { avatarLauncher.launch(arrayOf("image/*")) },
                    onSave = {
                        val avatar = selectedAvatar ?: return@PersonaDetailEditor
                        scope.launch {
                            runCatching {
                                TavernCoreClient(baseUrl).savePersona(
                                    PersonaSaveRequest(
                                        avatar = avatar,
                                        name = name,
                                        title = title,
                                        description = description,
                                        makeDefault = makeDefault
                                    )
                                )
                            }.onSuccess {
                                onShowMessage(context.getString(R.string.m3_persona_saved))
                                refresh()
                            }.onFailure {
                                onShowMessage(context.getString(R.string.m3_persona_save_failed))
                            }
                        }
                    },
                    onDelete = {
                        selectedAvatar?.let { avatar ->
                            personas.firstOrNull { it.avatar == avatar }?.let { pendingDelete = it }
                        }
                    },
                    onBackToList = { viewMode = PersonaViewMode.LIST }
                )
            }
        }
    }
}

@Composable
fun PresetLiteScreen(
    status: NodeStatus,
    baseUrl: String,
    onStartService: () -> Unit,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverRunning = status.state == NodeState.RUNNING
    var library by remember { mutableStateOf(PresetLibrary()) }
    var viewMode by remember { mutableStateOf(PresetViewMode.LIST) }
    var selectedApiId by remember { mutableStateOf<String?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var presetName by remember { mutableStateOf("") }
    var presetJson by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun applyPresetDraft(preset: PresetSummary) {
        selectedApiId = preset.apiId
        selectedName = preset.name
        presetName = preset.name
        presetJson = preset.content.ifBlank { "{}" }
    }

    fun openPreset(preset: PresetSummary) {
        applyPresetDraft(preset)
        viewMode = PresetViewMode.DETAIL
    }

    fun refresh() {
        if (!serverRunning) return
        scope.launch {
            runCatching {
                TavernCoreClient(baseUrl).getPresetLibrary()
            }.onSuccess { loaded ->
                library = loaded
                selectedApiId = selectedApiId?.takeIf { apiId -> loaded.categories.any { it.apiId == apiId } }
                    ?: loaded.categories.firstOrNull()?.apiId
                val current = loaded.categories
                    .flatMap { it.presets }
                    .firstOrNull { it.apiId == selectedApiId && it.name == selectedName }
                if (current != null) {
                    applyPresetDraft(current)
                } else if (viewMode == PresetViewMode.DETAIL) {
                    selectedName = null
                    viewMode = PresetViewMode.LIST
                }
            }.onFailure {
                onShowMessage(context.getString(R.string.m3_presets_load_failed))
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val picked = context.readPickedDocument(uri)
                presetName = picked.fileName.substringBeforeLast('.').ifBlank { presetName }
                presetJson = picked.bytes.toString(Charsets.UTF_8)
            }.onSuccess {
                onShowMessage(context.getString(R.string.m3_presets_imported))
            }.onFailure {
                onShowMessage(context.getString(R.string.m3_presets_save_failed))
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.writePickedDocument(uri, export.second.toByteArray())
            }.onSuccess {
                onShowMessage(context.getString(R.string.m3_presets_exported))
            }.onFailure {
                onShowMessage(context.getString(R.string.m3_presets_save_failed))
            }
        }
    }

    LaunchedEffect(serverRunning, baseUrl) {
        if (serverRunning) refresh()
    }

    M3ManagerScaffold(
        title = stringResource(R.string.m3_presets_title),
        subtitle = stringResource(R.string.m3_presets_subtitle),
        status = status,
        onStartService = onStartService,
        onBack = onBack,
        onRefresh = ::refresh,
        modifier = modifier
    ) {
        if (!serverRunning) return@M3ManagerScaffold
        when (viewMode) {
            PresetViewMode.LIST -> {
                M3StateBanner(
                    title = stringResource(R.string.m3_state_list_presets),
                    body = stringResource(R.string.m3_state_list_presets_body)
                )
                M3HeroSurface(
                    title = stringResource(R.string.m3_presets_list_hero_title),
                    body = stringResource(
                        R.string.m3_presets_list_hero_body,
                        library.categories.sumOf { it.presets.size }
                    ),
                    labels = listOf(
                        stringResource(R.string.tools_hub_list_detail),
                        stringResource(R.string.m3_source_fidelity)
                    )
                )
                PresetCategoryChips(
                    categories = library.categories,
                    selectedApiId = selectedApiId,
                    onSelect = { category ->
                        selectedApiId = category.apiId
                        selectedName = null
                    }
                )
                PresetListCard(
                    categories = library.categories,
                    selectedApiId = selectedApiId,
                    onOpen = ::openPreset,
                    onEnable = { preset ->
                        scope.launch {
                            runCatching {
                                TavernCoreClient(baseUrl).selectPreset(preset.apiId, preset.name)
                            }.onSuccess {
                                selectedApiId = preset.apiId
                                selectedName = preset.name
                                onShowMessage(context.getString(R.string.m3_presets_saved))
                                refresh()
                            }.onFailure {
                                onShowMessage(context.getString(R.string.m3_presets_save_failed))
                            }
                        }
                    }
                )
                M3SectionSurface(
                    title = stringResource(R.string.m3_list_responsibility),
                    body = stringResource(R.string.m3_presets_list_responsibility)
                )
            }

            PresetViewMode.DETAIL -> {
                val apiId = selectedApiId
                val apiLabel = apiId.orEmpty().ifBlank { stringResource(R.string.unknown_short) }
                M3StateBanner(
                    title = stringResource(R.string.m3_state_detail_presets),
                    body = stringResource(R.string.m3_state_detail_presets_body)
                )
                M3HeroSurface(
                    title = presetName.ifBlank { stringResource(R.string.m3_presets_title) },
                    body = stringResource(R.string.m3_presets_detail_hero_body, apiLabel),
                    labels = listOf(
                        if (library.categories.flatMap { it.presets }.any { it.apiId == apiId && it.name == selectedName && it.selected }) {
                            stringResource(R.string.m3_enabled)
                        } else {
                            stringResource(R.string.m3_detail_form_only)
                        },
                        stringResource(R.string.m3_source_fidelity)
                    )
                )
                PresetDetailEditor(
                    apiId = apiId,
                    presetName = presetName,
                    presetJson = presetJson,
                    selected = library.categories.flatMap { it.presets }
                        .any { it.apiId == apiId && it.name == selectedName && it.selected },
                    onPresetNameChanged = { presetName = it },
                    onPresetJsonChanged = { presetJson = it },
                    onSave = {
                        val currentApiId = apiId ?: return@PresetDetailEditor
                        scope.launch {
                            runCatching {
                                TavernCoreClient(baseUrl).savePreset(currentApiId, presetName, presetJson)
                            }.onSuccess {
                                onShowMessage(context.getString(R.string.m3_presets_saved))
                                selectedName = presetName
                                refresh()
                            }.onFailure {
                                onShowMessage(context.getString(R.string.m3_presets_save_failed))
                            }
                        }
                    },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onExport = {
                        pendingExport = presetName to presetJson
                        exportLauncher.launch("${presetName.ifBlank { "preset" }}.json")
                    },
                    onDelete = {
                        val currentApiId = apiId ?: return@PresetDetailEditor
                        val nameToDelete = selectedName ?: return@PresetDetailEditor
                        scope.launch {
                            runCatching {
                                TavernCoreClient(baseUrl).deletePreset(currentApiId, nameToDelete)
                            }.onSuccess {
                                onShowMessage(context.getString(R.string.m3_presets_deleted))
                                selectedName = null
                                viewMode = PresetViewMode.LIST
                                refresh()
                            }.onFailure {
                                onShowMessage(context.getString(R.string.m3_presets_save_failed))
                            }
                        }
                    },
                    onRestore = {
                        val currentApiId = apiId ?: return@PresetDetailEditor
                        val nameToRestore = selectedName ?: presetName
                        scope.launch {
                            runCatching {
                                TavernCoreClient(baseUrl).restorePreset(currentApiId, nameToRestore)
                            }.onSuccess { restored ->
                                presetJson = restored
                            }.onFailure {
                                onShowMessage(context.getString(R.string.m3_presets_save_failed))
                            }
                        }
                    },
                    onEnable = {
                        val currentApiId = apiId ?: return@PresetDetailEditor
                        scope.launch {
                            runCatching {
                                TavernCoreClient(baseUrl).selectPreset(currentApiId, presetName)
                            }.onSuccess {
                                onShowMessage(context.getString(R.string.m3_presets_saved))
                                selectedName = presetName
                                refresh()
                            }.onFailure {
                                onShowMessage(context.getString(R.string.m3_presets_save_failed))
                            }
                        }
                    },
                    onBackToList = { viewMode = PresetViewMode.LIST }
                )
            }
        }
    }
}

@Composable
fun ConnectionProfilesScreen(
    status: NodeStatus,
    baseUrl: String,
    onStartService: () -> Unit,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverRunning = status.state == NodeState.RUNNING
    var secrets by remember { mutableStateOf<List<SecretProviderState>>(emptyList()) }
    var connections by remember { mutableStateOf<List<ConnectionProfile>>(emptyList()) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var secretLabel by remember { mutableStateOf("") }
    var secretValue by remember { mutableStateOf("") }
    var selectedSecretEntryId by remember { mutableStateOf<String?>(null) }
    var endpointLabel by remember { mutableStateOf("OpenAI-compatible") }
    var endpointUrl by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf(ConnectionViewMode.LIST) }
    var detailMode by remember { mutableStateOf(ConnectionDetailMode.KEY) }

    fun openSecretDetail(provider: SecretProviderState, entryId: String? = null) {
        val entry = provider.entries.firstOrNull { it.id == entryId }
            ?: provider.entries.firstOrNull { it.active }
            ?: provider.entries.firstOrNull()
        selectedKey = provider.key
        selectedSecretEntryId = entry?.id
        secretLabel = entry?.label ?: provider.label
        secretValue = ""
        detailMode = ConnectionDetailMode.KEY
        viewMode = ConnectionViewMode.DETAIL
    }

    fun openEndpointDetail(profile: ConnectionProfile? = null) {
        endpointLabel = profile?.label ?: "OpenAI-compatible"
        endpointUrl = profile?.url.orEmpty()
        detailMode = ConnectionDetailMode.ENDPOINT
        viewMode = ConnectionViewMode.DETAIL
    }

    fun refresh() {
        if (!serverRunning) return
        scope.launch {
            runCatching {
                val client = TavernCoreClient(baseUrl)
                client.listSecrets() to client.listConnectionProfiles()
            }.onSuccess { (loadedSecrets, loadedConnections) ->
                secrets = loadedSecrets
                connections = loadedConnections
                selectedKey = selectedKey?.takeIf { key -> loadedSecrets.any { it.key == key } }
                    ?: loadedSecrets.firstOrNull()?.key
                val selectedProvider = loadedSecrets.firstOrNull { it.key == selectedKey }
                selectedSecretEntryId = selectedSecretEntryId?.takeIf { id ->
                    selectedProvider?.entries?.any { it.id == id } == true
                }
                if (secretLabel.isBlank() || viewMode == ConnectionViewMode.LIST) {
                    secretLabel = selectedProvider?.label.orEmpty()
                }
            }.onFailure {
                onShowMessage(context.getString(R.string.m3_connections_load_failed))
            }
        }
    }

    LaunchedEffect(serverRunning, baseUrl) {
        if (serverRunning) refresh()
    }

    M3ManagerScaffold(
        title = stringResource(R.string.m3_connections_title),
        subtitle = stringResource(R.string.m3_connections_subtitle),
        status = status,
        onStartService = onStartService,
        onBack = onBack,
        onRefresh = ::refresh,
        modifier = modifier
    ) {
        if (!serverRunning) return@M3ManagerScaffold
        when (viewMode) {
            ConnectionViewMode.LIST -> {
                val selected = secrets.firstOrNull { it.key == selectedKey }
                M3StateBanner(
                    title = stringResource(R.string.m3_state_list_connections),
                    body = stringResource(R.string.m3_state_list_connections_body)
                )
                M3HeroSurface(
                    title = selected?.label ?: stringResource(R.string.m3_connections_title),
                    body = stringResource(R.string.m3_connections_list_hero_body, selected?.entries?.size ?: 0, connections.size),
                    labels = listOf(
                        stringResource(R.string.tools_hub_sensitive),
                        stringResource(R.string.m3_connections_masked)
                    )
                )
                SecretProviderChips(
                    providers = secrets,
                    selectedKey = selectedKey,
                    onSelect = {
                        selectedKey = it.key
                        secretLabel = it.label
                        secretValue = ""
                        selectedSecretEntryId = null
                    }
                )
                M3SectionSurface(title = selected?.label ?: stringResource(R.string.m3_connections_title)) {
                    selected?.entries.orEmpty().forEach { entry ->
                        M3ListRow(
                            avatarLabel = if (entry.active) "*" else entry.label.avatarInitial(),
                            title = entry.label,
                            body = entry.value,
                            trailing = if (entry.active) stringResource(R.string.m3_enabled) else stringResource(R.string.m3_connections_rotate),
                            onClick = { selected?.let { openSecretDetail(it, entry.id) } }
                        )
                    }
                    OutlinedButton(
                        onClick = { selected?.let { openSecretDetail(it) } },
                        enabled = selected != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.m3_connections_write_key))
                    }
                }
                M3SectionSurface(title = stringResource(R.string.m3_connections_endpoint_label)) {
                    connections.take(6).forEach { profile ->
                        M3ListRow(
                            avatarLabel = profile.label.avatarInitial(),
                            title = profile.label,
                            body = profile.url,
                            trailing = stringResource(R.string.dashboard_open),
                            onClick = { openEndpointDetail(profile) }
                        )
                    }
                    OutlinedButton(onClick = { openEndpointDetail() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.m3_connections_save_endpoint))
                    }
                }
                M3SectionSurface(
                    title = stringResource(R.string.m3_list_responsibility),
                    body = stringResource(R.string.m3_connections_list_responsibility)
                )
            }

            ConnectionViewMode.DETAIL -> {
                val selected = secrets.firstOrNull { it.key == selectedKey }
                val selectedEntry = selected?.entries?.firstOrNull { it.id == selectedSecretEntryId }
                M3StateBanner(
                    title = if (detailMode == ConnectionDetailMode.KEY) {
                        stringResource(R.string.m3_state_detail_connection_key)
                    } else {
                        stringResource(R.string.m3_state_detail_connection_endpoint)
                    },
                    body = stringResource(R.string.m3_state_detail_connections_body)
                )
                M3HeroSurface(
                    title = if (detailMode == ConnectionDetailMode.KEY) {
                        secretLabel.ifBlank { selected?.label ?: stringResource(R.string.m3_connections_title) }
                    } else {
                        endpointLabel.ifBlank { stringResource(R.string.m3_connections_endpoint_label) }
                    },
                    body = if (detailMode == ConnectionDetailMode.KEY) {
                        selectedEntry?.value ?: stringResource(R.string.m3_connections_masked)
                    } else {
                        endpointUrl.ifBlank { stringResource(R.string.m3_connections_endpoint_url) }
                    },
                    labels = listOf(
                        stringResource(R.string.m3_detail_form_only),
                        if (detailMode == ConnectionDetailMode.KEY) {
                            stringResource(R.string.m3_connections_masked)
                        } else {
                            stringResource(R.string.tools_hub_guarded)
                        }
                    )
                )
                if (detailMode == ConnectionDetailMode.KEY) {
                    M3SectionSurface(title = selected?.label ?: stringResource(R.string.m3_connections_title)) {
                        OutlinedTextField(
                            value = secretLabel,
                            onValueChange = { secretLabel = it },
                            label = { Text(stringResource(R.string.m3_connections_key_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = secretValue,
                            onValueChange = { secretValue = it },
                            label = { Text(stringResource(R.string.m3_connections_key_value)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { viewMode = ConnectionViewMode.LIST }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.back))
                            }
                            Button(
                                onClick = {
                                    val key = selectedKey ?: return@Button
                                    scope.launch {
                                        runCatching {
                                            TavernCoreClient(baseUrl).writeSecret(key, secretValue, secretLabel.ifBlank { key })
                                        }.onSuccess {
                                            secretValue = ""
                                            onShowMessage(context.getString(R.string.m3_connections_key_saved))
                                            refresh()
                                        }.onFailure {
                                            onShowMessage(context.getString(R.string.m3_connections_save_failed))
                                        }
                                    }
                                },
                                enabled = selectedKey != null && secretValue.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.m3_connections_write_key))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    val key = selectedKey ?: return@OutlinedButton
                                    val entry = selectedEntry ?: return@OutlinedButton
                                    scope.launch {
                                        runCatching {
                                            TavernCoreClient(baseUrl).renameSecret(key, entry.id, secretLabel.ifBlank { entry.label })
                                        }.onSuccess {
                                            onShowMessage(context.getString(R.string.m3_connections_key_saved))
                                            refresh()
                                        }.onFailure {
                                            onShowMessage(context.getString(R.string.m3_connections_save_failed))
                                        }
                                    }
                                },
                                enabled = selectedEntry != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.m3_connections_rename))
                            }
                            OutlinedButton(
                                onClick = {
                                    val key = selectedKey ?: return@OutlinedButton
                                    val entry = selectedEntry ?: return@OutlinedButton
                                    scope.launch {
                                        runCatching { TavernCoreClient(baseUrl).rotateSecret(key, entry.id) }
                                            .onSuccess { refresh() }
                                            .onFailure { onShowMessage(context.getString(R.string.m3_connections_save_failed)) }
                                    }
                                },
                                enabled = selectedEntry != null && selectedEntry.active.not(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.m3_connections_rotate))
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val key = selectedKey ?: return@OutlinedButton
                                val entry = selectedEntry ?: return@OutlinedButton
                                scope.launch {
                                    runCatching { TavernCoreClient(baseUrl).deleteSecret(key, entry.id) }
                                        .onSuccess {
                                            viewMode = ConnectionViewMode.LIST
                                            refresh()
                                        }
                                        .onFailure { onShowMessage(context.getString(R.string.m3_connections_save_failed)) }
                                }
                            },
                            enabled = selectedEntry != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.m3_connections_delete))
                        }
                    }
                } else {
                    M3SectionSurface(title = stringResource(R.string.m3_connections_endpoint_label)) {
                        OutlinedTextField(
                            value = endpointLabel,
                            onValueChange = { endpointLabel = it },
                            label = { Text(stringResource(R.string.m3_connections_endpoint_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = endpointUrl,
                            onValueChange = { endpointUrl = it },
                            label = { Text(stringResource(R.string.m3_connections_endpoint_url)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { viewMode = ConnectionViewMode.LIST }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.back))
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            TavernCoreClient(baseUrl).saveConnectionProfile(
                                                ConnectionProfile(label = endpointLabel, url = endpointUrl)
                                            )
                                        }.onSuccess {
                                            onShowMessage(context.getString(R.string.m3_connections_endpoint_saved))
                                            refresh()
                                        }.onFailure {
                                            onShowMessage(context.getString(R.string.m3_connections_save_failed))
                                        }
                                    }
                                },
                                enabled = endpointLabel.isNotBlank() && endpointUrl.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.m3_connections_save_endpoint))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBackupsScreen(
    status: NodeStatus,
    baseUrl: String,
    onStartService: () -> Unit,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverRunning = status.state == NodeState.RUNNING
    var backups by remember { mutableStateOf<List<ChatBackupSummary>>(emptyList()) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var selectedBackupNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    fun toggleBackupSelection(fileName: String) {
        selectedBackupNames = if (fileName in selectedBackupNames) {
            selectedBackupNames - fileName
        } else {
            selectedBackupNames + fileName
        }
    }

    fun refresh() {
        if (!serverRunning) return
        scope.launch {
            runCatching { TavernCoreClient(baseUrl).listChatBackups() }
                .onSuccess {
                    backups = it
                    selectedBackupNames = selectedBackupNames.intersect(it.map { backup -> backup.fileName }.toSet())
                }
                .onFailure { onShowMessage(context.getString(R.string.m3_chat_backups_load_failed)) }
        }
    }

    fun deleteSelectedChatBackups() {
        val names = selectedBackupNames.toList()
        if (names.isEmpty()) return
        scope.launch {
            runCatching {
                val client = TavernCoreClient(baseUrl)
                names.forEach { client.deleteChatBackup(it) }
            }.onSuccess {
                selectedBackupNames = emptySet()
                onShowMessage(context.resources.getQuantityString(R.plurals.m3_chat_backups_deleted_count, names.size, names.size))
                refresh()
            }.onFailure {
                onShowMessage(context.getString(R.string.m3_chat_backups_action_failed))
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/jsonl")) { uri ->
        val name = pendingExport
        pendingExport = null
        if (uri == null || name == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val file = TavernCoreClient(baseUrl).downloadChatBackup(name)
                context.writePickedDocument(uri, file.bytes)
            }.onSuccess {
                onShowMessage(context.getString(R.string.m3_chat_backups_exported))
            }.onFailure {
                onShowMessage(context.getString(R.string.m3_chat_backups_action_failed))
            }
        }
    }

    LaunchedEffect(serverRunning, baseUrl) {
        if (serverRunning) refresh()
    }

    if (confirmDeleteSelected) {
        STConfirmDialog(
            title = stringResource(R.string.m3_chat_backups_delete_selected),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmDeleteSelected = false
                deleteSelectedChatBackups()
            },
            onDismiss = { confirmDeleteSelected = false },
            body = {
                Text(stringResource(R.string.m3_chat_backups_delete_selected_body, selectedBackupNames.size))
            }
        )
    }

    M3ManagerScaffold(
        title = stringResource(R.string.m3_chat_backups_title),
        subtitle = stringResource(R.string.m3_chat_backups_subtitle),
        status = status,
        onStartService = onStartService,
        onBack = onBack,
        onRefresh = ::refresh,
        modifier = modifier
    ) {
        if (!serverRunning) return@M3ManagerScaffold
        M3StateBanner(
            title = stringResource(R.string.m3_state_chat_backups),
            body = stringResource(R.string.m3_state_chat_backups_body)
        )
        M3HeroSurface(
            title = stringResource(R.string.m3_chat_backups_hero_title),
            body = stringResource(R.string.m3_chat_backups_hero_body, backups.size),
            labels = listOf(
                stringResource(R.string.m3_chat_backups_export),
                stringResource(R.string.m3_chat_backups_delete_selected)
            ),
            tone = M3HeroTone.TERTIARY
        )
        STSectionCard(borderColor = MaterialTheme.colorScheme.outlineVariant, contentSpacing = 8.dp) {
            if (backups.isEmpty()) {
                Text(
                    text = stringResource(R.string.m3_chat_backups_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedBackupNames.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.m3_chat_backups_selected_count, selectedBackupNames.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { selectedBackupNames = emptySet() }) {
                        Text(stringResource(R.string.m3_chat_backups_clear_selection))
                    }
                    OutlinedButton(onClick = { confirmDeleteSelected = true }) {
                        Text(stringResource(R.string.m3_chat_backups_delete_selected))
                    }
                }
                Text(
                    text = stringResource(R.string.m3_chat_backups_select_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            backups.forEach { backup ->
                ChatBackupListRow(
                    backup = backup,
                    selected = backup.fileName in selectedBackupNames,
                    selectionMode = selectedBackupNames.isNotEmpty(),
                    onClick = {
                        if (selectedBackupNames.isNotEmpty()) {
                            toggleBackupSelection(backup.fileName)
                        } else {
                            pendingExport = backup.fileName
                            exportLauncher.launch(backup.fileName)
                        }
                    },
                    onLongClick = { toggleBackupSelection(backup.fileName) },
                    onExport = {
                        pendingExport = backup.fileName
                        exportLauncher.launch(backup.fileName)
                    }
                )
            }
        }
    }
}

@Composable
private fun M3StateBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun M3HeroSurface(
    title: String,
    body: String,
    labels: List<String>,
    modifier: Modifier = Modifier,
    tone: M3HeroTone = M3HeroTone.PRIMARY
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        M3HeroTone.PRIMARY -> colors.primaryContainer to colors.onPrimaryContainer
        M3HeroTone.TERTIARY -> colors.tertiaryContainer to colors.onTertiaryContainer
        M3HeroTone.SURFACE -> colors.surfaceContainerHigh to colors.onSurface
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.78f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                labels.take(3).forEach { label ->
                    M3StatusChip(label = label, tone = tone)
                }
            }
        }
    }
}

@Composable
private fun M3SectionSurface(
    title: String? = null,
    body: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun M3StatusChip(label: String, tone: M3HeroTone = M3HeroTone.SURFACE) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        M3HeroTone.PRIMARY -> colors.surfaceContainerLowest to colors.onSurfaceVariant
        M3HeroTone.TERTIARY -> colors.surfaceContainerLowest to colors.onSurfaceVariant
        M3HeroTone.SURFACE -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun M3ManagerScaffold(
    title: String,
    subtitle: String,
    status: NodeStatus,
    onStartService: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val serverRunning = status.state == NodeState.RUNNING
    Surface(modifier = modifier.fillMaxSize(), color = colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onRefresh, enabled = serverRunning) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.m3_refresh))
                }
            }
            if (!serverRunning) {
                STInfoCard(
                    title = stringResource(R.string.webview_error_service_stopped_title),
                    body = stringResource(R.string.m3_requires_server),
                    actionLabel = stringResource(R.string.webview_start_service),
                    onAction = onStartService
                )
            } else {
                content()
            }
        }
    }
}

@Composable
private fun M3SearchField(search: String, onSearchChanged: (String) -> Unit) {
    OutlinedTextField(
        value = search,
        onValueChange = onSearchChanged,
        label = { Text(stringResource(R.string.m3_search)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun WorldListCard(
    worlds: List<WorldInfoSummary>,
    selectedWorldId: String?,
    loading: Boolean,
    onSelect: (WorldInfoSummary) -> Unit
) {
    STSectionCard(title = stringResource(R.string.m3_world_info_title), borderColor = MaterialTheme.colorScheme.outlineVariant) {
        if (loading) {
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (worlds.isEmpty()) {
            Text(stringResource(R.string.m3_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        worlds.forEach { world ->
            M3ListRow(
                avatarLabel = world.name.avatarInitial(),
                title = world.name,
                body = world.id,
                trailing = if (world.id == selectedWorldId) stringResource(R.string.enable) else stringResource(R.string.dashboard_open),
                onClick = { onSelect(world) }
            )
        }
    }
}

@Composable
private fun WorldEntryEditor(
    book: WorldInfoBook,
    selectedEntryUid: Int?,
    onSelectEntry: (Int) -> Unit,
    onBookChanged: (WorldInfoBook) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    val entry = book.entries.firstOrNull { it.uid == selectedEntryUid } ?: book.entries.firstOrNull()
    STSectionCard(title = stringResource(R.string.m3_world_info_entries), borderColor = MaterialTheme.colorScheme.outlineVariant) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            book.entries.take(8).forEach { item ->
                FilterChip(
                    selected = item.uid == entry?.uid,
                    onClick = { onSelectEntry(item.uid) },
                    label = { Text(item.comment.ifBlank { item.uid.toString() }, maxLines = 1) }
                )
            }
        }
        if (entry == null) {
            Text(stringResource(R.string.m3_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            WorldEntryFields(
                entry = entry,
                onEntryChanged = { changed ->
                    onBookChanged(book.copy(entries = book.entries.map { if (it.uid == changed.uid) changed else it }))
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.m3_world_info_save))
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.m3_world_info_delete))
            }
        }
    }
}

@Composable
private fun WorldEntryFields(entry: WorldInfoEntry, onEntryChanged: (WorldInfoEntry) -> Unit) {
    OutlinedTextField(
        value = entry.comment,
        onValueChange = { onEntryChanged(entry.copy(comment = it)) },
        label = { Text(stringResource(R.string.m3_world_info_entry_comment)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = entry.keys.joinToString(", "),
        onValueChange = { onEntryChanged(entry.copy(keys = it.csvItems())) },
        label = { Text(stringResource(R.string.m3_world_info_primary_keys)) },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = entry.secondaryKeys.joinToString(", "),
        onValueChange = { onEntryChanged(entry.copy(secondaryKeys = it.csvItems())) },
        label = { Text(stringResource(R.string.m3_world_info_secondary_keys)) },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = entry.content,
        onValueChange = { onEntryChanged(entry.copy(content = it)) },
        label = { Text(stringResource(R.string.m3_world_info_content)) },
        minLines = 6,
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        NumberField(
            value = entry.order,
            label = stringResource(R.string.m3_world_info_order),
            onValueChanged = { onEntryChanged(entry.copy(order = it)) },
            modifier = Modifier.weight(1f)
        )
        NumberField(
            value = entry.depth,
            label = stringResource(R.string.m3_world_info_depth),
            onValueChanged = { onEntryChanged(entry.copy(depth = it)) },
            modifier = Modifier.weight(1f)
        )
        NumberField(
            value = entry.position,
            label = stringResource(R.string.m3_world_info_position),
            onValueChanged = { onEntryChanged(entry.copy(position = it)) },
            modifier = Modifier.weight(1f)
        )
    }
    M3SwitchRow(stringResource(R.string.m3_world_info_constant), entry.constant) {
        onEntryChanged(entry.copy(constant = it))
    }
    M3SwitchRow(stringResource(R.string.m3_world_info_selective), entry.selective) {
        onEntryChanged(entry.copy(selective = it))
    }
    M3SwitchRow(stringResource(R.string.m3_world_info_disabled), entry.disabled) {
        onEntryChanged(entry.copy(disabled = it))
    }
}

@Composable
private fun NumberField(value: Int, label: String, onValueChanged: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toIntOrNull()?.let(onValueChanged)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun PersonaDetailEditor(
    avatar: String?,
    name: String,
    title: String,
    description: String,
    makeDefault: Boolean,
    onNameChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onDefaultChanged: (Boolean) -> Unit,
    onChooseAvatar: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBackToList: () -> Unit
) {
    STSectionCard(title = name.ifBlank { stringResource(R.string.m3_persona_title) }, borderColor = MaterialTheme.colorScheme.outlineVariant) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name.avatarInitial(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = avatar.orEmpty().ifBlank { stringResource(R.string.m3_persona_missing_avatar) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedButton(onClick = onChooseAvatar) {
                    Text(stringResource(R.string.m3_persona_upload))
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            label = { Text(stringResource(R.string.m3_persona_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChanged,
            label = { Text(stringResource(R.string.m3_persona_title_field)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChanged,
            label = { Text(stringResource(R.string.m3_persona_description)) },
            minLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
        M3EnableRow(
            label = stringResource(R.string.m3_persona_default),
            enabled = makeDefault,
            onEnable = { onDefaultChanged(true) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBackToList, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.back))
            }
            Button(onClick = onSave, enabled = avatar != null && name.isNotBlank(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.m3_persona_save))
            }
        }
        OutlinedButton(onClick = onDelete, enabled = avatar != null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.m3_persona_delete))
        }
    }
}

@Composable
private fun PersonaListCard(
    personas: List<PersonaProfile>,
    loading: Boolean,
    onOpen: (PersonaProfile) -> Unit,
    onEnable: (PersonaProfile) -> Unit
) {
    STSectionCard(title = stringResource(R.string.m3_persona_title), borderColor = MaterialTheme.colorScheme.outlineVariant) {
        if (loading) {
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (personas.isEmpty()) {
            Text(stringResource(R.string.m3_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        personas.forEach { persona ->
            M3ListRow(
                avatarLabel = persona.name.avatarInitial(),
                title = persona.name,
                body = listOf(persona.title, persona.description, if (persona.hasAvatar) persona.avatar else stringResource(R.string.m3_persona_missing_avatar))
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                trailingContent = {
                    M3SmallActionButton(
                        label = if (persona.isDefault) stringResource(R.string.m3_enabled) else stringResource(R.string.enable),
                        enabled = !persona.isDefault,
                        onClick = { onEnable(persona) }
                    )
                },
                onClick = { onOpen(persona) }
            )
        }
    }
}

@Composable
private fun PresetCategoryChips(
    categories: List<PresetCategory>,
    selectedApiId: String?,
    onSelect: (PresetCategory) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = category.apiId == selectedApiId,
                onClick = { onSelect(category) },
                label = { Text(category.title, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun PresetListCard(
    categories: List<PresetCategory>,
    selectedApiId: String?,
    onOpen: (PresetSummary) -> Unit,
    onEnable: (PresetSummary) -> Unit
) {
    val presets = categories.firstOrNull { it.apiId == selectedApiId }?.presets
        ?: categories.firstOrNull()?.presets
        ?: emptyList()
    STSectionCard(borderColor = MaterialTheme.colorScheme.outlineVariant) {
        if (presets.isEmpty()) {
            Text(stringResource(R.string.m3_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        presets.forEach { preset ->
            M3ListRow(
                avatarLabel = preset.name.avatarInitial(),
                title = preset.name,
                body = preset.apiId,
                trailingContent = {
                    M3SmallActionButton(
                        label = if (preset.selected) stringResource(R.string.m3_enabled) else stringResource(R.string.enable),
                        enabled = !preset.selected,
                        onClick = { onEnable(preset) }
                    )
                },
                onClick = { onOpen(preset) }
            )
        }
    }
}

@Composable
private fun PresetDetailEditor(
    apiId: String?,
    presetName: String,
    presetJson: String,
    selected: Boolean,
    onPresetNameChanged: (String) -> Unit,
    onPresetJsonChanged: (String) -> Unit,
    onSave: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onEnable: () -> Unit,
    onBackToList: () -> Unit
) {
    STSectionCard(title = presetName.ifBlank { stringResource(R.string.m3_presets_title) }, borderColor = MaterialTheme.colorScheme.outlineVariant) {
        Text(
            text = apiId.orEmpty().ifBlank { stringResource(R.string.unknown_short) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        M3EnableRow(
            label = stringResource(R.string.m3_presets_title),
            enabled = selected,
            onEnable = onEnable
        )
        OutlinedTextField(
            value = presetName,
            onValueChange = onPresetNameChanged,
            label = { Text(stringResource(R.string.m3_world_info_new_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = presetJson,
            onValueChange = onPresetJsonChanged,
            label = { Text(stringResource(R.string.m3_presets_json)) },
            minLines = 8,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBackToList, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.back))
            }
            Button(
                onClick = onSave,
                enabled = apiId != null && presetName.isNotBlank() && presetJson.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.m3_presets_save))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.import_action))
            }
            OutlinedButton(onClick = onExport, enabled = presetJson.isNotBlank(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.m3_presets_export))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRestore, enabled = apiId != null && presetName.isNotBlank(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.m3_presets_restore))
            }
            OutlinedButton(onClick = onDelete, enabled = apiId != null, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun M3EnableRow(label: String, enabled: Boolean, onEnable: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        M3SmallActionButton(
            label = if (enabled) stringResource(R.string.m3_enabled) else stringResource(R.string.enable),
            enabled = !enabled,
            onClick = onEnable
        )
    }
}

@Composable
private fun M3SmallActionButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBackupListRow(
    backup: ChatBackupSummary,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "C",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = backup.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(backup.fileSize, backup.messageCount.toString(), backup.lastMessageAt)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        } else {
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.m3_chat_backups_export))
            }
        }
    }
}

@Composable
private fun SecretProviderChips(
    providers: List<SecretProviderState>,
    selectedKey: String?,
    onSelect: (SecretProviderState) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        providers.take(4).forEach { provider ->
            FilterChip(
                selected = provider.key == selectedKey,
                onClick = { onSelect(provider) },
                label = { Text(provider.label, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun M3SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun M3ListRow(
    avatarLabel: String,
    title: String,
    body: String,
    trailing: String? = null,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = avatarLabel.take(1).ifBlank { "?" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { stringResource(R.string.unknown_short) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = body.ifBlank { stringResource(R.string.unknown_short) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailingContent != null) {
            trailingContent()
        } else if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

private fun String.csvItems(): List<String> =
    split(',').map { it.trim() }.filter { it.isNotBlank() }

private fun String.avatarInitial(): String =
    trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
