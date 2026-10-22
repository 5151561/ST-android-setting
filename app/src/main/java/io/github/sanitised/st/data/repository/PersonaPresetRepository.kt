package io.github.sanitised.st.data.repository

import io.github.sanitised.st.api.PersonaProfile
import io.github.sanitised.st.api.PersonaSaveRequest
import io.github.sanitised.st.api.PresetLibrary
import io.github.sanitised.st.api.STTagSettings
import io.github.sanitised.st.api.TavernCoreApi

interface PersonaPresetRepository {
    suspend fun listPersonas(): List<PersonaProfile>
    suspend fun savePersona(request: PersonaSaveRequest)
    suspend fun uploadPersonaAvatar(fileName: String, bytes: ByteArray, overwriteName: String? = null): String
    suspend fun deletePersona(avatar: String)
    suspend fun getPresetLibrary(): PresetLibrary
    suspend fun savePreset(apiId: String, name: String, presetJson: String)
    suspend fun selectPreset(apiId: String, name: String)
    suspend fun deletePreset(apiId: String, name: String)
    suspend fun restorePreset(apiId: String, name: String): String
    suspend fun getTagSettings(): STTagSettings
    suspend fun saveTagSettings(settings: STTagSettings)
    suspend fun listBackgrounds(): List<String>
    suspend fun deleteBackground(bg: String)
    suspend fun renameBackground(oldBg: String, newBg: String)
    suspend fun uploadFile(name: String, base64Data: String): String
    suspend fun deleteFile(path: String)
    suspend fun getSettings(): Map<String, Any?>
    suspend fun saveSettings(settings: Map<String, Any?>)
}

class DefaultPersonaPresetRepository(
    private val clientProvider: () -> TavernCoreApi,
) : PersonaPresetRepository {
    override suspend fun listPersonas(): List<PersonaProfile> =
        clientProvider().listPersonas()

    override suspend fun savePersona(request: PersonaSaveRequest) {
        clientProvider().savePersona(request)
    }

    override suspend fun uploadPersonaAvatar(
        fileName: String,
        bytes: ByteArray,
        overwriteName: String?,
    ): String = clientProvider().uploadPersonaAvatar(fileName, bytes, overwriteName)

    override suspend fun deletePersona(avatar: String) {
        clientProvider().deletePersona(avatar)
    }

    override suspend fun getPresetLibrary(): PresetLibrary =
        clientProvider().getPresetLibrary()

    override suspend fun savePreset(apiId: String, name: String, presetJson: String) {
        clientProvider().savePreset(apiId, name, presetJson)
    }

    override suspend fun selectPreset(apiId: String, name: String) {
        clientProvider().selectPreset(apiId, name)
    }

    override suspend fun deletePreset(apiId: String, name: String) {
        clientProvider().deletePreset(apiId, name)
    }

    override suspend fun restorePreset(apiId: String, name: String): String =
        clientProvider().restorePreset(apiId, name)

    override suspend fun getTagSettings(): STTagSettings =
        clientProvider().getTagSettings()

    override suspend fun saveTagSettings(settings: STTagSettings) {
        clientProvider().saveTagSettings(settings)
    }

    override suspend fun listBackgrounds(): List<String> =
        clientProvider().listBackgrounds()

    override suspend fun deleteBackground(bg: String) {
        clientProvider().deleteBackground(bg)
    }

    override suspend fun renameBackground(oldBg: String, newBg: String) {
        clientProvider().renameBackground(oldBg, newBg)
    }

    override suspend fun uploadFile(name: String, base64Data: String): String =
        clientProvider().uploadFile(name, base64Data)

    override suspend fun deleteFile(path: String) {
        clientProvider().deleteFile(path)
    }

    override suspend fun getSettings(): Map<String, Any?> =
        clientProvider().getSettings()

    override suspend fun saveSettings(settings: Map<String, Any?>) {
        clientProvider().saveSettings(settings)
    }
}
