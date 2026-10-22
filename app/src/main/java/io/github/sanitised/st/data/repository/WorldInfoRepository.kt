package io.github.sanitised.st.data.repository

import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.api.WorldInfoSummary

interface WorldInfoRepository {
    suspend fun listWorldInfos(): List<WorldInfoSummary>
    suspend fun getWorldInfo(name: String): WorldInfoBook
    suspend fun saveWorldInfo(book: WorldInfoBook)
    suspend fun deleteWorldInfo(name: String)
    suspend fun getSettings(): Map<String, Any?>
    suspend fun saveSettings(settings: Map<String, Any?>)
}

class DefaultWorldInfoRepository(
    private val clientProvider: () -> TavernCoreApi,
) : WorldInfoRepository {
    override suspend fun listWorldInfos(): List<WorldInfoSummary> =
        clientProvider().listWorldInfos()

    override suspend fun getWorldInfo(name: String): WorldInfoBook =
        clientProvider().getWorldInfo(name)

    override suspend fun saveWorldInfo(book: WorldInfoBook) {
        clientProvider().saveWorldInfo(book)
    }

    override suspend fun deleteWorldInfo(name: String) {
        clientProvider().deleteWorldInfo(name)
    }

    override suspend fun getSettings(): Map<String, Any?> =
        clientProvider().getSettings()

    override suspend fun saveSettings(settings: Map<String, Any?>) {
        clientProvider().saveSettings(settings)
    }
}
