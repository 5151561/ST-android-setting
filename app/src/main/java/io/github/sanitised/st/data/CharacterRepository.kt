package io.github.sanitised.st.data

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.CharacterSaveRequest
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.TavernCoreApi

data class CharacterImport(
    val fileName: String,
    val bytes: ByteArray,
)

interface CharacterRepository {
    suspend fun listCharacters(serverRunning: Boolean): List<CharacterSummary>
    suspend fun getCharacter(avatar: String): CharacterDetail
    suspend fun setFavorite(avatar: String, favorite: Boolean): CharacterDetail
    suspend fun createCharacter(request: CharacterSaveRequest): String
    suspend fun importCharacters(documents: List<CharacterImport>): List<String>
}

class DefaultCharacterRepository(
    private val clientProvider: () -> TavernCoreApi,
    private val localReader: LocalTavernLibraryReader,
) : CharacterRepository {
    override suspend fun listCharacters(serverRunning: Boolean): List<CharacterSummary> =
        if (serverRunning) clientProvider().listCharacters() else localReader.listCharacters()

    override suspend fun getCharacter(avatar: String): CharacterDetail =
        clientProvider().getCharacter(avatar)

    override suspend fun setFavorite(avatar: String, favorite: Boolean): CharacterDetail {
        val client = clientProvider()
        client.mergeCharacterAttributes(avatar, isFavorite = favorite)
        return client.getCharacter(avatar)
    }

    override suspend fun createCharacter(request: CharacterSaveRequest): String =
        clientProvider().createCharacter(request)

    override suspend fun importCharacters(documents: List<CharacterImport>): List<String> {
        val client = clientProvider()
        return documents.map { document ->
            client.importCharacter(document.fileName, document.bytes)
        }
    }
}
