package io.github.sanitised.st.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class CoreHealth(
    val ok: Boolean,
    val version: String? = null
)

data class CharacterSummary(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false
)

data class ChatSummary(
    val id: String,
    val characterId: String,
    val characterName: String,
    val lastMessage: String? = null,
    val lastUpdated: Long = 0,
    val isPinned: Boolean = false
)

data class GenerationChunk(
    val text: String,
    val isFinal: Boolean = false
)

interface TavernCoreApi {
    suspend fun healthCheck(): CoreHealth
    suspend fun listCharacters(): List<CharacterSummary>
    suspend fun listRecentChats(): List<ChatSummary>
    suspend fun sendMessage(chatId: String, text: String): Flow<GenerationChunk>
    suspend fun stopGeneration(chatId: String)
}

class TavernCoreClient(
    baseUrl: String = "http://127.0.0.1:8000",
    private val httpClient: OkHttpClient = defaultHttpClient
) : TavernCoreApi {
    private val normalizedBaseUrl = normalizeBaseUrl(baseUrl)

    override suspend fun healthCheck(): CoreHealth {
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(normalizedBaseUrl)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    CoreHealth(
                        ok = response.code in 200..399,
                        version = response.header("X-SillyTavern-Version")
                    )
                }
            }.getOrElse {
                CoreHealth(ok = false)
            }
        }
    }

    override suspend fun listCharacters(): List<CharacterSummary> {
        // TODO: GET /api/characters
        return emptyList()
    }

    override suspend fun listRecentChats(): List<ChatSummary> {
        // TODO: GET /api/chats
        return emptyList()
    }

    override suspend fun sendMessage(chatId: String, text: String): Flow<GenerationChunk> {
        // TODO: POST /api/chats/{chatId}/messages with SSE streaming
        return kotlinx.coroutines.flow.emptyFlow()
    }

    override suspend fun stopGeneration(chatId: String) {
        // TODO: POST /api/chats/{chatId}/stop
    }

    private companion object {
        val defaultHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(750, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .callTimeout(2, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun normalizeBaseUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim()
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}
