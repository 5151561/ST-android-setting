package io.github.sanitised.st.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.CharacterSaveRequest
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.data.CharacterImport
import io.github.sanitised.st.data.CharacterRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CharacterUiMessage(
    val id: Long,
    val text: String,
)

data class CharacterLibraryUiState(
    val loading: Boolean = false,
    val importing: Boolean = false,
    val characters: List<CharacterSummary> = emptyList(),
    val error: String? = null,
    val message: CharacterUiMessage? = null,
)

class CharacterLibraryViewModel(
    private val repository: CharacterRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CharacterLibraryUiState())
    val uiState: StateFlow<CharacterLibraryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var serverRunning = false
    private var messageId = 0L

    fun refresh(serverRunning: Boolean) {
        this.serverRunning = serverRunning
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.listCharacters(serverRunning) }
                .onSuccess { characters ->
                    _uiState.update {
                        it.copy(loading = false, characters = characters, error = null)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: if (serverRunning) "角色加载失败" else "本地角色读取失败",
                        )
                    }
                }
        }
    }

    fun importCharacters(documents: List<CharacterImport>) {
        if (documents.isEmpty() || _uiState.value.importing) return
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true) }
            runCatching { repository.importCharacters(documents) }
                .onSuccess {
                    _uiState.update {
                        it.copy(importing = false, message = nextMessage("角色导入成功"))
                    }
                    refresh(serverRunning)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            importing = false,
                            message = nextMessage(error.message ?: "角色导入失败"),
                        )
                    }
                }
        }
    }

    fun messageShown(id: Long) {
        _uiState.update { state ->
            if (state.message?.id == id) state.copy(message = null) else state
        }
    }

    private fun nextMessage(text: String) = CharacterUiMessage(++messageId, text)

    companion object {
        fun factory(repository: CharacterRepository): ViewModelProvider.Factory =
            simpleViewModelFactory(CharacterLibraryViewModel::class.java) {
                CharacterLibraryViewModel(repository)
            }
    }
}

data class CharacterProfileUiState(
    val loading: Boolean = false,
    val savingFavorite: Boolean = false,
    val detail: CharacterDetail? = null,
    val error: String? = null,
    val message: CharacterUiMessage? = null,
)

class CharacterProfileViewModel(
    private val repository: CharacterRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CharacterProfileUiState())
    val uiState: StateFlow<CharacterProfileUiState> = _uiState.asStateFlow()

    private var avatar: String? = null
    private var serverRunning = false
    private var loadJob: Job? = null
    private var messageId = 0L

    fun load(serverRunning: Boolean, avatar: String) {
        this.serverRunning = serverRunning
        this.avatar = avatar
        loadJob?.cancel()
        if (!serverRunning) {
            _uiState.value = CharacterProfileUiState()
            return
        }
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.getCharacter(avatar) }
                .onSuccess { detail ->
                    _uiState.update { it.copy(loading = false, detail = detail, error = null) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loading = false, error = error.message ?: "角色详情加载失败")
                    }
                }
        }
    }

    fun toggleFavorite() {
        val currentAvatar = avatar ?: return
        val detail = _uiState.value.detail ?: return
        if (!serverRunning || _uiState.value.savingFavorite) return
        viewModelScope.launch {
            _uiState.update { it.copy(savingFavorite = true) }
            val next = !detail.isFavorite
            runCatching { repository.setFavorite(currentAvatar, next) }
                .onSuccess { refreshed ->
                    _uiState.update {
                        it.copy(savingFavorite = false, detail = refreshed, error = null)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            savingFavorite = false,
                            message = nextMessage(error.message ?: "收藏状态保存失败"),
                        )
                    }
                }
        }
    }

    fun messageShown(id: Long) {
        _uiState.update { state ->
            if (state.message?.id == id) state.copy(message = null) else state
        }
    }

    private fun nextMessage(text: String) = CharacterUiMessage(++messageId, text)

    companion object {
        fun factory(repository: CharacterRepository): ViewModelProvider.Factory =
            simpleViewModelFactory(CharacterProfileViewModel::class.java) {
                CharacterProfileViewModel(repository)
            }
    }
}

data class CharacterCreateUiState(
    val name: String = "",
    val subtitle: String = "",
    val description: String = "",
    val greeting: String = "",
    val saving: Boolean = false,
    val createdAvatar: String? = null,
    val message: CharacterUiMessage? = null,
)

class CharacterCreateViewModel(
    private val repository: CharacterRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CharacterCreateUiState())
    val uiState: StateFlow<CharacterCreateUiState> = _uiState.asStateFlow()

    private var messageId = 0L

    fun setName(value: String) = _uiState.update { it.copy(name = value) }
    fun setSubtitle(value: String) = _uiState.update { it.copy(subtitle = value) }
    fun setDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun setGreeting(value: String) = _uiState.update { it.copy(greeting = value) }

    fun save(serverRunning: Boolean) {
        val state = _uiState.value
        if (!serverRunning || state.name.isBlank() || state.saving) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            val request = CharacterSaveRequest(
                name = state.name.trim(),
                description = state.description.trim().ifBlank { state.subtitle.trim() },
                firstMessage = state.greeting.trim(),
                creatorNotes = state.subtitle.trim(),
            )
            runCatching { repository.createCharacter(request) }
                .onSuccess { avatar ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            createdAvatar = avatar,
                            message = nextMessage("角色已创建"),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            message = nextMessage(error.message ?: "角色创建失败"),
                        )
                    }
                }
        }
    }

    fun creationHandled(avatar: String) {
        _uiState.update { state ->
            if (state.createdAvatar == avatar) state.copy(createdAvatar = null) else state
        }
    }

    fun messageShown(id: Long) {
        _uiState.update { state ->
            if (state.message?.id == id) state.copy(message = null) else state
        }
    }

    private fun nextMessage(text: String) = CharacterUiMessage(++messageId, text)

    companion object {
        fun factory(repository: CharacterRepository): ViewModelProvider.Factory =
            simpleViewModelFactory(CharacterCreateViewModel::class.java) {
                CharacterCreateViewModel(repository)
            }
    }
}

private fun <T : ViewModel> simpleViewModelFactory(
    expectedClass: Class<T>,
    create: () -> T,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
        require(modelClass.isAssignableFrom(expectedClass)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return create() as VM
    }
}
