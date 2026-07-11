package io.github.sanitised.st.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalTavernLibraryReaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun listCharactersReturnsRecentCharacterCardsByModifiedTime() {
        val dataRoot = temp.newFolder("data")
        val charactersDir = File(dataRoot, "default-user/characters").apply { mkdirs() }
        val oldCharacter = File(charactersDir, "Old_Companion.png").apply {
            writeText("png")
            setLastModified(1_000L)
        }
        File(charactersDir, "New Friend.png").apply {
            writeText("png")
            setLastModified(2_000L)
        }
        File(charactersDir, ".ignored.png").writeText("hidden")
        File(charactersDir, "notes.txt").writeText("not a character")

        val reader = LocalTavernLibraryReader(dataRoot)

        assertEquals(
            listOf("New Friend", "Old Companion"),
            reader.listCharacters().map { it.name }
        )
        assertEquals("New Friend.png", reader.listCharacters().first().id)
        assertEquals(
            File(charactersDir, "New Friend.png").toURI().toString(),
            reader.listCharacters().first().avatarUrl
        )
        assertEquals(oldCharacter.name, reader.listCharacters().last().id)
    }

    @Test
    fun listRecentChatsReturnsNewestJsonlChatsWithLastMessagePreview() {
        val dataRoot = temp.newFolder("data")
        val charactersDir = File(dataRoot, "default-user/characters").apply { mkdirs() }
        val characterAvatar = File(charactersDir, "Seraphina.png").apply { writeText("png") }
        val chatsDir = File(dataRoot, "default-user/chats/Seraphina").apply { mkdirs() }
        val oldChat = File(chatsDir, "old.jsonl").apply {
            writeText("""{"name":"Seraphina","mes":"older reply"}""")
            setLastModified(1_000L)
        }
        val newChat = File(chatsDir, "new.jsonl").apply {
            writeText(
                """
                {"chat_metadata":{}}
                {"name":"User","is_user":true,"mes":"hello"}
                {"name":"Seraphina","is_user":false,"mes":"latest reply"}
                """.trimIndent()
            )
            setLastModified(2_000L)
        }
        File(chatsDir, "draft.txt").writeText("ignore")

        val reader = LocalTavernLibraryReader(dataRoot)

        val chats = reader.listRecentChats()

        assertEquals(listOf("new", "old"), chats.map { it.id.substringAfter('/') })
        // characterId 必须是完整 avatar 文件名(聊天目录名 + .png),供 /api/characters/get 直接使用。
        assertEquals("Seraphina.png", chats.first().characterId)
        assertEquals("Seraphina", chats.first().characterName)
        assertEquals(characterAvatar.toURI().toString(), chats.first().avatarUrl)
        assertEquals("latest reply", chats.first().lastMessage)
        assertEquals(newChat.lastModified(), chats.first().lastUpdated)
        assertEquals(oldChat.lastModified(), chats.last().lastUpdated)
    }

    @Test
    fun missingDataRootGracefullyReturnsEmptyLists() {
        val dataRoot = Files.createTempDirectory("missing-data").toFile()
        dataRoot.deleteRecursively()

        val reader = LocalTavernLibraryReader(dataRoot)

        assertEquals(emptyList<String>(), reader.listCharacters().map { it.name })
        assertEquals(emptyList<String>(), reader.listRecentChats().map { it.id })
    }
}
