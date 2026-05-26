package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.api.CharacterSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterFiltersTest {
    @Test
    fun filterCharactersMatchesNameTagsAndCreatorNotes() {
        val characters = listOf(
            CharacterSummary(
                id = "seraphina.png",
                name = "Seraphina",
                tags = listOf("archive"),
                creatorNotes = "Keeps the blue ledger."
            ),
            CharacterSummary(
                id = "rowan.png",
                name = "Rowan",
                tags = listOf("forest"),
                creatorNotes = "Quiet scout."
            )
        )

        assertEquals(
            listOf("seraphina.png"),
            filterCharacters(
                characters = characters,
                query = "ledger",
                filter = CharacterListFilter.ALL,
                sort = CharacterListSort.RECENT
            ).map { it.id }
        )
        assertEquals(
            listOf("rowan.png"),
            filterCharacters(
                characters = characters,
                query = "forest",
                filter = CharacterListFilter.ALL,
                sort = CharacterListSort.RECENT
            ).map { it.id }
        )
    }

    @Test
    fun filterCharactersCanLimitToFavoritesOrRecentlyChatted() {
        val characters = listOf(
            CharacterSummary(id = "favorite.png", name = "Favorite", isFavorite = true),
            CharacterSummary(id = "recent.png", name = "Recent", lastChatAt = 10L),
            CharacterSummary(id = "idle.png", name = "Idle")
        )

        assertEquals(
            listOf("favorite.png"),
            filterCharacters(characters, "", CharacterListFilter.FAVORITES, CharacterListSort.RECENT).map { it.id }
        )
        assertEquals(
            listOf("recent.png"),
            filterCharacters(characters, "", CharacterListFilter.RECENT, CharacterListSort.RECENT).map { it.id }
        )
    }

    @Test
    fun filterCharactersCanLimitToEmbeddedTag() {
        val characters = listOf(
            CharacterSummary(id = "archive.png", name = "Archive", tags = listOf("archive")),
            CharacterSummary(id = "forest.png", name = "Forest", tags = listOf("forest"))
        )

        assertEquals(
            listOf("archive.png"),
            filterCharacters(
                characters = characters,
                query = "",
                filter = CharacterListFilter.ALL,
                sort = CharacterListSort.RECENT,
                selectedTag = "archive"
            ).map { it.id }
        )
    }

    @Test
    fun filterCharactersSupportsOriginalManagerSortOptions() {
        val characters = listOf(
            CharacterSummary(
                id = "beta.png",
                name = "Beta",
                createDate = "2024-01-01T00:00:00.000Z",
                lastChatAt = 2L,
                chatSize = 5L,
                dataSize = 20L
            ),
            CharacterSummary(
                id = "alpha.png",
                name = "Alpha",
                createDate = "2026-01-01T00:00:00.000Z",
                lastChatAt = 3L,
                chatSize = 10L,
                dataSize = 10L
            )
        )

        assertEquals(
            listOf("alpha.png", "beta.png"),
            filterCharacters(characters, "", CharacterListFilter.ALL, CharacterListSort.NAME_ASC).map { it.id }
        )
        assertEquals(
            listOf("beta.png", "alpha.png"),
            filterCharacters(characters, "", CharacterListFilter.ALL, CharacterListSort.NAME_DESC).map { it.id }
        )
        assertEquals(
            listOf("alpha.png", "beta.png"),
            filterCharacters(characters, "", CharacterListFilter.ALL, CharacterListSort.NEWEST).map { it.id }
        )
        assertEquals(
            listOf("alpha.png", "beta.png"),
            filterCharacters(characters, "", CharacterListFilter.ALL, CharacterListSort.MOST_CHATS).map { it.id }
        )
        assertEquals(
            listOf("beta.png", "alpha.png"),
            filterCharacters(characters, "", CharacterListFilter.ALL, CharacterListSort.MOST_TOKENS).map { it.id }
        )
    }
}
