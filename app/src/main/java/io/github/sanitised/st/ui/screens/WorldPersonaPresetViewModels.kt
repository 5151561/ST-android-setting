package io.github.sanitised.st.ui.screens

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.sanitised.st.api.PersonaProfile
import io.github.sanitised.st.api.PersonaSaveRequest
import io.github.sanitised.st.api.PresetLibrary
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.api.WorldInfoSummary
import io.github.sanitised.st.data.repository.PersonaPresetRepository
import io.github.sanitised.st.data.repository.WorldInfoRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorldInfoUiState(
    val loading: Boolean = false,
    val books: List<WorldInfoSummary> = emptyList(),
    val currentBook: WorldInfoBook? = null,
    val settings: Map<String, Any?> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel
class WorldInfoViewModel @Inject constructor(
    val repository: WorldInfoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorldInfoUiState())
    val uiState: StateFlow<WorldInfoUiState> = _uiState.asStateFlow()

    suspend fun listWorldInfos(): List<WorldInfoSummary> = repository.listWorldInfos()
    suspend fun getWorldInfo(name: String): WorldInfoBook = repository.getWorldInfo(name)
    suspend fun saveWorldInfo(book: WorldInfoBook) = repository.saveWorldInfo(book)
    suspend fun deleteWorldInfo(name: String) = repository.deleteWorldInfo(name)
    suspend fun getSettings(): Map<String, Any?> = repository.getSettings()
    suspend fun saveSettings(settings: Map<String, Any?>) = repository.saveSettings(settings)
}

data class PersonaPresetUiState(
    val loading: Boolean = false,
    val personas: List<PersonaProfile> = emptyList(),
    val presetLibrary: PresetLibrary? = null,
    val backgrounds: List<String> = emptyList(),
    val settings: Map<String, Any?> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel
class PersonaPresetViewModel @Inject constructor(
    val repository: PersonaPresetRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PersonaPresetUiState())
    val uiState: StateFlow<PersonaPresetUiState> = _uiState.asStateFlow()

    suspend fun listPersonas(): List<PersonaProfile> = repository.listPersonas()
    suspend fun savePersona(request: PersonaSaveRequest) = repository.savePersona(request)
    suspend fun deletePersona(avatar: String) = repository.deletePersona(avatar)
    suspend fun getPresetLibrary(): PresetLibrary = repository.getPresetLibrary()
    suspend fun selectPreset(apiId: String, name: String) = repository.selectPreset(apiId, name)
    suspend fun listBackgrounds(): List<String> = repository.listBackgrounds()
    suspend fun deleteBackground(bg: String) = repository.deleteBackground(bg)
    suspend fun renameBackground(oldBg: String, newBg: String) = repository.renameBackground(oldBg, newBg)
    suspend fun getSettings(): Map<String, Any?> = repository.getSettings()
    suspend fun saveSettings(settings: Map<String, Any?>) = repository.saveSettings(settings)
}
