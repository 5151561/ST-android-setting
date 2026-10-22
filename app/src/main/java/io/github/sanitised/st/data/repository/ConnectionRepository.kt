package io.github.sanitised.st.data.repository

import io.github.sanitised.st.api.ConnectionProfile
import io.github.sanitised.st.api.ConnectionTestResult
import io.github.sanitised.st.api.SecretProviderState
import io.github.sanitised.st.api.SettingsSnapshot
import io.github.sanitised.st.api.TavernCoreApi

interface ConnectionRepository {
    suspend fun getSettings(): Map<String, Any?>
    suspend fun saveSettings(settings: Map<String, Any?>)
    suspend fun listSecrets(): List<SecretProviderState>
    suspend fun writeSecret(key: String, value: String, label: String = ""): String
    suspend fun rotateSecret(key: String, id: String)
    suspend fun renameSecret(key: String, id: String, label: String)
    suspend fun deleteSecret(key: String, id: String? = null)
    suspend fun fetchModels(mode: String, sourceValue: String, apiServer: String = ""): List<String>
    suspend fun testConnection(mode: String, sourceValue: String, apiServer: String = ""): ConnectionTestResult
    suspend fun listConnectionProfiles(): List<ConnectionProfile>
    suspend fun saveConnectionProfile(profile: ConnectionProfile)
    suspend fun listSettingsSnapshots(): List<SettingsSnapshot>
    suspend fun makeSettingsSnapshot()
    suspend fun loadSettingsSnapshot(name: String): String
    suspend fun restoreSettingsSnapshot(name: String)
}

class DefaultConnectionRepository(
    private val clientProvider: () -> TavernCoreApi,
) : ConnectionRepository {
    override suspend fun getSettings(): Map<String, Any?> =
        clientProvider().getSettings()

    override suspend fun saveSettings(settings: Map<String, Any?>) {
        clientProvider().saveSettings(settings)
    }

    override suspend fun listSecrets(): List<SecretProviderState> =
        clientProvider().listSecrets()

    override suspend fun writeSecret(key: String, value: String, label: String): String =
        clientProvider().writeSecret(key, value, label)

    override suspend fun rotateSecret(key: String, id: String) {
        clientProvider().rotateSecret(key, id)
    }

    override suspend fun renameSecret(key: String, id: String, label: String) {
        clientProvider().renameSecret(key, id, label)
    }

    override suspend fun deleteSecret(key: String, id: String?) {
        clientProvider().deleteSecret(key, id)
    }

    override suspend fun fetchModels(mode: String, sourceValue: String, apiServer: String): List<String> =
        clientProvider().fetchModels(mode, sourceValue, apiServer)

    override suspend fun testConnection(mode: String, sourceValue: String, apiServer: String): ConnectionTestResult =
        clientProvider().testConnection(mode, sourceValue, apiServer)

    override suspend fun listConnectionProfiles(): List<ConnectionProfile> =
        clientProvider().listConnectionProfiles()

    override suspend fun saveConnectionProfile(profile: ConnectionProfile) {
        clientProvider().saveConnectionProfile(profile)
    }

    override suspend fun listSettingsSnapshots(): List<SettingsSnapshot> =
        clientProvider().listSettingsSnapshots()

    override suspend fun makeSettingsSnapshot() {
        clientProvider().makeSettingsSnapshot()
    }

    override suspend fun loadSettingsSnapshot(name: String): String =
        clientProvider().loadSettingsSnapshot(name)

    override suspend fun restoreSettingsSnapshot(name: String) {
        clientProvider().restoreSettingsSnapshot(name)
    }
}
