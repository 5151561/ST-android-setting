package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.STTag
import io.github.sanitised.st.api.STTagSettings
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
    fun filterCharactersCanLimitToSillyTavernTagMap() {
        val characters = listOf(
            CharacterSummary(id = "seraphina.png", name = "Seraphina"),
            CharacterSummary(id = "rowan.png", name = "Rowan")
        )
        val tagSettings = STTagSettings(
            tags = listOf(STTag(id = "tag-1", name = "Lore")),
            tagMap = mapOf("seraphina.png" to listOf("tag-1"))
        )

        assertEquals(
            listOf("seraphina.png"),
            filterCharacters(
                characters = characters,
                query = "",
                filter = CharacterListFilter.ALL,
                sort = CharacterListSort.RECENT,
                selectedTag = "Lore",
                selectedTagSource = CharacterTagSource.ST,
                tagSettings = tagSettings
            ).map { it.id }
        )
    }

    @Test
    fun filterCharactersSearchScoresNameBeforeTagsAndNotes() {
        val characters = listOf(
            CharacterSummary(id = "notes.png", name = "Notes", creatorNotes = "Seraphina appears here."),
            CharacterSummary(id = "tag.png", name = "Tagged", tags = listOf("Seraphina")),
            CharacterSummary(id = "name.png", name = "Seraphina")
        )

        assertEquals(
            listOf("name.png", "tag.png", "notes.png"),
            filterCharacters(
                characters = characters,
                query = "seraphina",
                filter = CharacterListFilter.ALL,
                sort = CharacterListSort.NAME_ASC
            ).map { it.id }
        )
    }

    @Test
    fun filterCharactersSearchCanMatchSillyTavernTags() {
        val characters = listOf(
            CharacterSummary(id = "seraphina.png", name = "Seraphina"),
            CharacterSummary(id = "rowan.png", name = "Rowan")
        )
        val tagSettings = STTagSettings(
            tags = listOf(STTag(id = "tag-lore", name = "Archive Lore")),
            tagMap = mapOf("rowan.png" to listOf("tag-lore"))
        )

        assertEquals(
            listOf("rowan.png"),
            filterCharacters(
                characters = characters,
                query = "lore",
                filter = CharacterListFilter.ALL,
                sort = CharacterListSort.NAME_ASC,
                selectedTagSource = CharacterTagSource.ST,
                tagSettings = tagSettings
            ).map { it.id }
        )
    }

    @Test
    fun filterCharactersSupportsSillyTavernTagMapByIdOrAvatarUrl() {
        val characters = listOf(
            CharacterSummary(id = "Seraphina.png", name = "Seraphina", avatarUrl = "Seraphina.png"),
            CharacterSummary(id = "Rowan", name = "Rowan", avatarUrl = "Rowan.png")
        )
        val tagSettings = STTagSettings(
            tags = listOf(STTag(id = "tag-lore", name = "Lore")),
            tagMap = mapOf(
                "Seraphina.png" to listOf("tag-lore"),
                "Rowan.png" to listOf("tag-lore")
            )
        )

        assertEquals(
            listOf("Rowan", "Seraphina.png"),
            filterCharacters(
                characters = characters,
                query = "",
                filter = CharacterListFilter.ALL,
                sort = CharacterListSort.NAME_ASC,
                selectedTag = "Lore",
                selectedTagSource = CharacterTagSource.ST,
                tagSettings = tagSettings
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
        assertEquals(
            setOf("alpha.png", "beta.png"),
            filterCharacters(characters, "", CharacterListFilter.ALL, CharacterListSort.RANDOM).map { it.id }.toSet()
        )
    }

    @Test
    fun paginateCharactersReturnsRequestedPageAndClampsOutOfRangePages() {
        val characters = (1..53).map { index ->
            CharacterSummary(id = "character-$index.png", name = "Character $index")
        }

        val pageThree = paginateCharacters(characters, requestedPage = 3, pageSize = 25)
        assertEquals(3, pageThree.currentPage)
        assertEquals(3, pageThree.totalPages)
        assertEquals(25, pageThree.pageSize)
        assertEquals(53, pageThree.totalItems)
        assertEquals(51, pageThree.firstItemNumber)
        assertEquals(53, pageThree.lastItemNumber)
        assertEquals(listOf("character-51.png", "character-52.png", "character-53.png"), pageThree.items.map { it.id })

        val clamped = paginateCharacters(characters, requestedPage = 99, pageSize = 25)
        assertEquals(3, clamped.currentPage)
        assertEquals(listOf("character-51.png", "character-52.png", "character-53.png"), clamped.items.map { it.id })
    }

    @Test
    fun paginateCharactersHandlesEmptyLists() {
        val page = paginateCharacters(emptyList(), requestedPage = 4, pageSize = 25)

        assertEquals(1, page.currentPage)
        assertEquals(1, page.totalPages)
        assertEquals(0, page.totalItems)
        assertEquals(0, page.firstItemNumber)
        assertEquals(0, page.lastItemNumber)
        assertEquals(emptyList<CharacterSummary>(), page.items)
    }
}
