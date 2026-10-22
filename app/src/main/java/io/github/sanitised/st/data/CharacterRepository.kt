package io.github.sanitised.st.data

import io.github.sanitised.st.api.CharacterChatSummary
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.CharacterExportFile
import io.github.sanitised.st.api.CharacterExportFormat
import io.github.sanitised.st.api.CharacterSaveRequest
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.CharacterUpload
import io.github.sanitised.st.api.ChatExportFormat
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

    suspend fun createCharacter(
        request: CharacterSaveRequest,
        avatarUpload: CharacterUpload?,
    ): String = createCharacter(request)

    suspend fun updateCharacter(
        request: CharacterSaveRequest,
        avatarUpload: CharacterUpload? = null,
    ): Unit = throw UnsupportedOperationException("updateCharacter not implemented")

    suspend fun deleteCharacter(
        avatar: String,
        deleteChats: Boolean = false,
    ): Unit = throw UnsupportedOperationException("deleteCharacter not implemented")

    suspend fun duplicateCharacter(avatar: String): String =
        throw UnsupportedOperationException("duplicateCharacter not implemented")

    suspend fun renameCharacter(avatar: String, newName: String): String =
        throw UnsupportedOperationException("renameCharacter not implemented")

    suspend fun exportCharacter(
        avatar: String,
        format: CharacterExportFormat,
    ): CharacterExportFile =
        throw UnsupportedOperationException("exportCharacter not implemented")

    suspend fun updateCharacterAvatar(
        avatar: String,
        fileName: String,
        bytes: ByteArray,
    ): Unit = throw UnsupportedOperationException("updateCharacterAvatar not implemented")

    suspend fun importExternalCharacter(
        urlOrUuid: String,
        preservedName: String? = null,
    ): String = throw UnsupportedOperationException("importExternalCharacter not implemented")

    suspend fun mergeCharacterAttributes(
        avatar: String,
        isFavorite: Boolean? = null,
        embeddedTags: List<String>? = null,
    ): Unit = throw UnsupportedOperationException("mergeCharacterAttributes not implemented")

    suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary> = emptyList()

    suspend fun renameCharacterChat(
        avatar: String,
        originalFile: String,
        renamedFile: String,
    ): String = throw UnsupportedOperationException("renameCharacterChat not implemented")

    suspend fun deleteCharacterChat(
        avatar: String,
        chatFile: String,
    ): Unit = throw UnsupportedOperationException("deleteCharacterChat not implemented")

    suspend fun exportCharacterChat(
        avatar: String,
        chatFile: String,
        format: ChatExportFormat,
    ): CharacterExportFile =
        throw UnsupportedOperationException("exportCharacterChat not implemented")

    suspend fun importCharacterChat(
        avatar: String,
        characterName: String,
        fileName: String,
        bytes: ByteArray,
    ): List<String> = emptyList()
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

    override suspend fun createCharacter(
        request: CharacterSaveRequest,
        avatarUpload: CharacterUpload?,
    ): String = clientProvider().createCharacter(request, avatarUpload)

    override suspend fun importCharacters(documents: List<CharacterImport>): List<String> {
        val client = clientProvider()
        return documents.map { document ->
            client.importCharacter(document.fileName, document.bytes)
        }
    }

    override suspend fun updateCharacter(
        request: CharacterSaveRequest,
        avatarUpload: CharacterUpload?,
    ) {
        clientProvider().updateCharacter(request, avatarUpload)
    }

    override suspend fun deleteCharacter(avatar: String, deleteChats: Boolean) {
        clientProvider().deleteCharacter(avatar, deleteChats)
    }

    override suspend fun duplicateCharacter(avatar: String): String =
        clientProvider().duplicateCharacter(avatar)

    override suspend fun renameCharacter(avatar: String, newName: String): String =
        clientProvider().renameCharacter(avatar, newName)

    override suspend fun exportCharacter(
        avatar: String,
        format: CharacterExportFormat,
    ): CharacterExportFile = clientProvider().exportCharacter(avatar, format)

    override suspend fun updateCharacterAvatar(
        avatar: String,
        fileName: String,
        bytes: ByteArray,
    ) {
        clientProvider().updateCharacterAvatar(avatar, fileName, bytes)
    }

    override suspend fun importExternalCharacter(
        urlOrUuid: String,
        preservedName: String?,
    ): String = clientProvider().importExternalCharacter(urlOrUuid, preservedName)

    override suspend fun mergeCharacterAttributes(
        avatar: String,
        isFavorite: Boolean?,
        embeddedTags: List<String>?,
    ) {
        clientProvider().mergeCharacterAttributes(avatar, isFavorite, embeddedTags)
    }

    override suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary> =
        clientProvider().listCharacterChats(avatar)

    override suspend fun renameCharacterChat(
        avatar: String,
        originalFile: String,
        renamedFile: String,
    ): String = clientProvider().renameCharacterChat(avatar, originalFile, renamedFile)

    override suspend fun deleteCharacterChat(avatar: String, chatFile: String) {
        clientProvider().deleteCharacterChat(avatar, chatFile)
    }

    override suspend fun exportCharacterChat(
        avatar: String,
        chatFile: String,
        format: ChatExportFormat,
    ): CharacterExportFile = clientProvider().exportCharacterChat(avatar, chatFile, format)

    override suspend fun importCharacterChat(
        avatar: String,
        characterName: String,
        fileName: String,
        bytes: ByteArray,
    ): List<String> = clientProvider().importCharacterChat(avatar, characterName, fileName, bytes)
}
