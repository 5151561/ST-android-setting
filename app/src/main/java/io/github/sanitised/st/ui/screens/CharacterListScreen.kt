package io.github.sanitised.st.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.R
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.STTagSettings
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.ui.theme.STTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private data class CharacterListLoadState(
    val loading: Boolean = false,
    val characters: List<CharacterSummary> = emptyList(),
    val tagSettings: STTagSettings? = null,
    val error: String? = null
)

private enum class CharacterViewMode {
    LIST,
    GRID,
    BATCH
}

private val characterImportMimeTypes = arrayOf(
    "application/json",
    "image/png",
    "application/x-yaml",
    "text/yaml",
    "application/octet-stream",
    "*/*"
)

@Composable
fun CharacterListScreen(
    status: NodeStatus,
    baseUrl: String,
    onStartService: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    onCreateCharacter: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = STTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(CharacterListFilter.ALL) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedTagSource by remember { mutableStateOf(CharacterTagSource.EMBEDDED) }
    var sort by remember { mutableStateOf(CharacterListSort.RECENT) }
    var viewMode by remember { mutableStateOf(CharacterViewMode.LIST) }
    var selectedCharacters by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hotSwapsExpanded by remember { mutableStateOf(true) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var loadState by remember { mutableStateOf(CharacterListLoadState()) }
    var pendingDelete by remember { mutableStateOf<CharacterSummary?>(null) }
    var deleteChats by remember { mutableStateOf(false) }
    var showExternalImport by remember { mutableStateOf(false) }
    var externalImportText by remember { mutableStateOf("") }
    val serverRunning = status.state == NodeState.RUNNING
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val client = TavernCoreClient(baseUrl = baseUrl)
                uris.forEach { uri ->
                    val document = context.readPickedDocument(uri)
                    client.importCharacter(document.fileName, document.bytes)
                }
            }.onSuccess {
                onShowMessage(context.getString(R.string.character_import_success))
                refreshKey++
            }.onFailure { error ->
                onShowMessage(error.message ?: context.getString(R.string.character_import_failed))
            }
        }
    }

    LaunchedEffect(serverRunning, baseUrl, refreshKey) {
        if (!serverRunning) {
            loadState = CharacterListLoadState()
            return@LaunchedEffect
        }
        loadState = CharacterListLoadState(loading = true)
        loadState = runCatching {
            val client = TavernCoreClient(baseUrl = baseUrl)
            val characters = client.listCharacters()
            val tagSettings = runCatching { client.getTagSettings() }.getOrNull()
            CharacterListLoadState(characters = characters, tagSettings = tagSettings)
        }.getOrElse { error ->
            CharacterListLoadState(error = error.message ?: context.getString(R.string.character_list_load_failed))
        }
    }

    val availableEmbeddedTags = loadState.characters
        .flatMap { it.tags }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
    val availableStTags = loadState.tagSettings
        ?.tags
        .orEmpty()
        .filter { it.name.isNotBlank() }
        .sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
    val availableTags = when (selectedTagSource) {
        CharacterTagSource.EMBEDDED -> availableEmbeddedTags
        CharacterTagSource.ST -> availableStTags.map { it.name }
    }
    val visibleCharacters = filterCharacters(
        characters = loadState.characters,
        query = query,
        filter = filter,
        sort = sort,
        selectedTag = selectedTag,
        selectedTagSource = selectedTagSource,
        tagSettings = loadState.tagSettings
    )
    val hotSwapCharacters = visibleCharacters.filter { it.isFavorite }.take(8)

    pendingDelete?.let { character ->
        AlertDialog(
            onDismissRequest = {
                pendingDelete = null
                deleteChats = false
            },
            title = { Text(stringResource(R.string.character_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.character_delete_body, character.name))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = deleteChats,
                            onCheckedChange = { deleteChats = it }
                        )
                        Text(stringResource(R.string.character_delete_chats))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = character.id
                        val removeChats = deleteChats
                        pendingDelete = null
                        deleteChats = false
                        scope.launch {
                            runCatching {
                                TavernCoreClient(baseUrl = baseUrl).deleteCharacter(target, removeChats)
                            }.onSuccess {
                                onShowMessage(context.getString(R.string.character_delete_success))
                                refreshKey++
                            }.onFailure { error ->
                                onShowMessage(error.message ?: context.getString(R.string.character_delete_failed))
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        deleteChats = false
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showExternalImport) {
        AlertDialog(
            onDismissRequest = {
                showExternalImport = false
                externalImportText = ""
            },
            title = { Text(stringResource(R.string.character_external_import)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.character_external_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                    OutlinedTextField(
                        value = externalImportText,
                        onValueChange = { externalImportText = it },
                        enabled = serverRunning,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = serverRunning,
                    onClick = {
                        val target = externalImportText.trim()
                        if (target.isBlank()) {
                            onShowMessage(context.getString(R.string.character_external_import_hint))
                            return@TextButton
                        }
                        showExternalImport = false
                        externalImportText = ""
                        scope.launch {
                            runCatching {
                                TavernCoreClient(baseUrl = baseUrl).importExternalCharacter(target)
                            }.onSuccess {
                                onShowMessage(context.getString(R.string.character_import_success))
                                refreshKey++
                            }.onFailure { error ->
                                onShowMessage(error.message ?: context.getString(R.string.character_import_failed))
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.import_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExternalImport = false
                        externalImportText = ""
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.character_hub_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.fg
                    )
                    Text(
                        text = stringResource(R.string.character_list_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
                IconButton(onClick = { refreshKey++ }, enabled = serverRunning) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.character_list_refresh))
                }
                TextButton(
                    onClick = { importLauncher.launch(characterImportMimeTypes) },
                    enabled = serverRunning
                ) {
                    Text(stringResource(R.string.import_action))
                }
                TextButton(
                    onClick = { showExternalImport = true },
                    enabled = serverRunning
                ) {
                    Text(stringResource(R.string.character_external_import))
                }
                IconButton(onClick = onCreateCharacter) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.character_list_new))
                }
            }

            if (!serverRunning) {
                CharacterServiceCard(onStartService = onStartService)
                return@Column
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                label = { Text(stringResource(R.string.character_list_search_hint)) }
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CharacterListFilter.values().forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = {
                            filter = item
                            selectedTag = null
                        },
                        label = {
                            Text(
                                text = when (item) {
                                    CharacterListFilter.ALL -> stringResource(R.string.character_filter_all)
                                    CharacterListFilter.FAVORITES -> stringResource(R.string.character_filter_favorites)
                                    CharacterListFilter.RECENT -> stringResource(R.string.character_filter_recent)
                                }
                            )
                        }
                    )
                }
                FilterChip(
                    selected = selectedTagSource == CharacterTagSource.EMBEDDED,
                    onClick = {
                        selectedTagSource = CharacterTagSource.EMBEDDED
                        selectedTag = null
                    },
                    label = { Text(stringResource(R.string.character_filter_embedded_tags)) }
                )
                FilterChip(
                    selected = selectedTagSource == CharacterTagSource.ST,
                    onClick = {
                        selectedTagSource = CharacterTagSource.ST
                        selectedTag = null
                    },
                    label = { Text(stringResource(R.string.character_filter_st_tags)) }
                )
                availableTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = {
                            filter = CharacterListFilter.ALL
                            selectedTag = if (selectedTag == tag) null else tag
                        },
                        label = { Text(tag) }
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CharacterViewMode.values().forEach { mode ->
                    FilterChip(
                        selected = viewMode == mode,
                        onClick = {
                            viewMode = mode
                            if (mode != CharacterViewMode.BATCH) {
                                selectedCharacters = emptySet()
                            }
                        },
                        label = {
                            Text(
                                when (mode) {
                                    CharacterViewMode.LIST -> stringResource(R.string.character_view_list)
                                    CharacterViewMode.GRID -> stringResource(R.string.character_view_grid)
                                    CharacterViewMode.BATCH -> stringResource(R.string.character_view_batch)
                                }
                            )
                        }
                    )
                }
                Box {
                    TextButton(onClick = { sortMenuExpanded = true }) {
                        Text(stringResource(R.string.character_sort_label, characterSortLabel(sort)))
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        CharacterListSort.values().forEach { item ->
                            DropdownMenuItem(
                                text = { Text(characterSortLabel(item)) },
                                onClick = {
                                    sort = item
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (hotSwapCharacters.isNotEmpty()) {
                CharacterHotSwaps(
                    baseUrl = baseUrl,
                    characters = hotSwapCharacters,
                    expanded = hotSwapsExpanded,
                    onToggleExpanded = { hotSwapsExpanded = !hotSwapsExpanded },
                    onOpenCharacter = { onOpenCharacter(it.id) }
                )
            }

            if (viewMode == CharacterViewMode.BATCH) {
                CharacterBatchBar(
                    count = selectedCharacters.size,
                    onFavoriteSelected = {
                        scope.launch {
                            runCatching {
                                val client = TavernCoreClient(baseUrl = baseUrl)
                                selectedCharacters.forEach { character ->
                                    client.mergeCharacterAttributes(character, isFavorite = true)
                                }
                            }.onSuccess {
                                onShowMessage(context.getString(R.string.character_batch_favorite))
                                refreshKey++
                            }.onFailure { error ->
                                onShowMessage(error.message ?: context.getString(R.string.character_save_failed))
                            }
                        }
                    },
                    onDeleteSelected = {
                        selectedCharacters.firstOrNull()?.let { target ->
                            pendingDelete = loadState.characters.firstOrNull { it.id == target }
                        }
                    }
                )
            }

            when {
                loadState.loading -> CharacterInfoCard(
                    title = stringResource(R.string.character_list_loading),
                    body = stringResource(R.string.waiting_for_server)
                )

                loadState.error != null -> CharacterInfoCard(
                    title = stringResource(R.string.character_list_error_title),
                    body = loadState.error.orEmpty(),
                    actionLabel = stringResource(R.string.webview_retry),
                    onAction = { refreshKey++ }
                )

                visibleCharacters.isEmpty() -> CharacterInfoCard(
                    title = stringResource(R.string.character_hub_empty_title),
                    body = stringResource(R.string.character_list_empty_body)
                )

                else -> CharacterListCard(
                    baseUrl = baseUrl,
                    characters = visibleCharacters,
                    viewMode = viewMode,
                    selectedCharacters = selectedCharacters,
                    onOpenCharacter = onOpenCharacter,
                    onToggleSelection = { character ->
                        selectedCharacters = if (character.id in selectedCharacters) {
                            selectedCharacters - character.id
                        } else {
                            selectedCharacters + character.id
                        }
                    },
                    onDuplicateCharacter = { character ->
                        scope.launch {
                            runCatching {
                                TavernCoreClient(baseUrl = baseUrl).duplicateCharacter(character.id)
                            }.onSuccess {
                                onShowMessage(context.getString(R.string.character_duplicate_success))
                                refreshKey++
                            }.onFailure { error ->
                                onShowMessage(error.message ?: context.getString(R.string.character_duplicate_failed))
                            }
                        }
                    },
                    onDeleteCharacter = { character ->
                        pendingDelete = character
                        deleteChats = false
                    }
                )
            }

            CharacterLocalBottomBar(
                active = CharacterLocalNav.CHARACTERS,
                onCharacters = { refreshKey++ },
                onLorebook = { onShowMessage(context.getString(R.string.character_lorebook_unavailable)) },
                onPersona = { onShowMessage(context.getString(R.string.character_persona_unavailable)) }
            )
        }
    }
}

@Composable
private fun characterSortLabel(sort: CharacterListSort): String {
    return when (sort) {
        CharacterListSort.RECENT -> stringResource(R.string.character_sort_recent)
        CharacterListSort.NAME_ASC -> stringResource(R.string.character_sort_name_asc)
        CharacterListSort.NAME_DESC -> stringResource(R.string.character_sort_name_desc)
        CharacterListSort.NEWEST -> stringResource(R.string.character_sort_newest)
        CharacterListSort.OLDEST -> stringResource(R.string.character_sort_oldest)
        CharacterListSort.FAVORITES -> stringResource(R.string.character_sort_favorites)
        CharacterListSort.MOST_CHATS -> stringResource(R.string.character_sort_most_chats)
        CharacterListSort.LEAST_CHATS -> stringResource(R.string.character_sort_least_chats)
        CharacterListSort.MOST_TOKENS -> stringResource(R.string.character_sort_most_tokens)
        CharacterListSort.LEAST_TOKENS -> stringResource(R.string.character_sort_least_tokens)
        CharacterListSort.RANDOM -> stringResource(R.string.character_sort_random)
    }
}

@Composable
private fun CharacterServiceCard(onStartService: () -> Unit) {
    CharacterInfoCard(
        title = stringResource(R.string.webview_error_service_stopped_title),
        body = stringResource(R.string.webview_error_service_stopped_body),
        actionLabel = stringResource(R.string.webview_start_service),
        onAction = onStartService
    )
}

@Composable
private fun CharacterInfoCard(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = STTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun CharacterListCard(
    baseUrl: String,
    characters: List<CharacterSummary>,
    viewMode: CharacterViewMode,
    selectedCharacters: Set<String>,
    onOpenCharacter: (String) -> Unit,
    onToggleSelection: (CharacterSummary) -> Unit,
    onDuplicateCharacter: (CharacterSummary) -> Unit,
    onDeleteCharacter: (CharacterSummary) -> Unit
) {
    val colors = STTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            when (viewMode) {
                CharacterViewMode.GRID -> characters.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { character ->
                            CharacterGridCell(
                                baseUrl = baseUrl,
                                character = character,
                                onOpenCharacter = { onOpenCharacter(character.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                CharacterViewMode.LIST,
                CharacterViewMode.BATCH -> characters.forEach { character ->
                    CharacterRow(
                        baseUrl = baseUrl,
                        character = character,
                        selected = character.id in selectedCharacters,
                        selectable = viewMode == CharacterViewMode.BATCH,
                        onToggleSelection = { onToggleSelection(character) },
                        onOpenCharacter = { onOpenCharacter(character.id) },
                        onDuplicateCharacter = { onDuplicateCharacter(character) },
                        onDeleteCharacter = { onDeleteCharacter(character) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterRow(
    baseUrl: String,
    character: CharacterSummary,
    selected: Boolean,
    selectable: Boolean,
    onToggleSelection: () -> Unit,
    onOpenCharacter: () -> Unit,
    onDuplicateCharacter: () -> Unit,
    onDeleteCharacter: () -> Unit
) {
    val colors = STTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (selectable) onToggleSelection else onOpenCharacter)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectable) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelection() }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        CharacterAvatar(baseUrl = baseUrl, avatar = character.avatarUrl, label = character.name)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (character.isFavorite) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(R.string.character_filter_favorites),
                        tint = colors.warn,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (character.characterVersion.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = character.characterVersion,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = character.tags.take(3).joinToString(" / ").ifBlank {
                    stringResource(R.string.character_list_no_tags)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (character.creatorNotes.isNotBlank()) {
                Text(
                    text = character.creatorNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.fg2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.character_action_edit)) },
                    onClick = {
                        menuExpanded = false
                        onOpenCharacter()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.character_action_duplicate)) },
                    onClick = {
                        menuExpanded = false
                        onDuplicateCharacter()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.character_action_delete)) },
                    onClick = {
                        menuExpanded = false
                        onDeleteCharacter()
                    }
                )
            }
        }
    }
}

@Composable
private fun CharacterGridCell(
    baseUrl: String,
    character: CharacterSummary,
    onOpenCharacter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = STTheme.colors
    Surface(
        modifier = modifier
            .height(160.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenCharacter),
        color = colors.surfaceWarm,
        border = BorderStroke(1.dp, colors.borderSoft)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CharacterAvatar(baseUrl = baseUrl, avatar = character.avatarUrl, label = character.name)
            Text(
                text = character.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = character.tags.take(2).joinToString(" / ").ifBlank {
                    stringResource(R.string.character_list_no_tags)
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CharacterHotSwaps(
    baseUrl: String,
    characters: List<CharacterSummary>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenCharacter: (CharacterSummary) -> Unit
) {
    val colors = STTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceWarm),
        border = BorderStroke(1.dp, colors.borderSoft)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.character_list_hotswaps),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.character_list_hotswaps_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
                TextButton(onClick = onToggleExpanded) {
                    Text(
                        if (expanded) {
                            stringResource(R.string.character_list_hotswaps_collapse)
                        } else {
                            stringResource(R.string.character_list_hotswaps_expand)
                        }
                    )
                }
            }
            if (expanded) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    characters.forEach { character ->
                        Column(
                            modifier = Modifier
                                .width(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenCharacter(character) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CharacterAvatar(baseUrl = baseUrl, avatar = character.avatarUrl, label = character.name)
                            Text(
                                text = character.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.fg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterBatchBar(
    count: Int,
    onFavoriteSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = STTheme.colors.surface),
        border = BorderStroke(1.dp, STTheme.colors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.character_batch_selected, count),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onFavoriteSelected,
                    enabled = count > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.character_batch_favorite))
                }
                OutlinedButton(
                    onClick = onDeleteSelected,
                    enabled = count > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.character_batch_delete))
                }
            }
        }
    }
}

@Composable
private fun CharacterAvatar(baseUrl: String, avatar: String?, label: String) {
    val colors = STTheme.colors
    val image by produceState<ImageBitmap?>(initialValue = null, baseUrl, avatar) {
        value = withContext(Dispatchers.IO) {
            loadAvatarBitmap(baseUrl, avatar)
        }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.surfaceWarm),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.fg2
            )
        }
    }
}

private fun loadAvatarBitmap(baseUrl: String, avatar: String?): ImageBitmap? {
    if (avatar.isNullOrBlank()) return null
    return runCatching {
        val url = "${baseUrl.trimEnd('/')}/characters/${Uri.encode(avatar)}"
        val request = Request.Builder().url(url).get().build()
        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }.getOrNull()
}
