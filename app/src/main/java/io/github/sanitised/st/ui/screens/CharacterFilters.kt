package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.STTagSettings
import kotlin.random.Random

enum class CharacterListFilter {
    ALL,
    FAVORITES,
    RECENT
}

enum class CharacterListSort {
    RECENT,
    NAME_ASC,
    NAME_DESC,
    NEWEST,
    OLDEST,
    FAVORITES,
    MOST_CHATS,
    LEAST_CHATS,
    MOST_TOKENS,
    LEAST_TOKENS,
    RANDOM
}

enum class CharacterTagSource {
    EMBEDDED,
    ST
}

data class CharacterPage(
    val items: List<CharacterSummary>,
    val currentPage: Int,
    val totalPages: Int,
    val pageSize: Int,
    val totalItems: Int,
    val firstItemNumber: Int,
    val lastItemNumber: Int
)

fun filterCharacters(
    characters: List<CharacterSummary>,
    query: String,
    filter: CharacterListFilter,
    sort: CharacterListSort,
    selectedTag: String? = null,
    selectedTagSource: CharacterTagSource = CharacterTagSource.EMBEDDED,
    selectedFolderTag: String? = null,
    tagSettings: STTagSettings? = null
): List<CharacterSummary> {
    val normalizedQuery = query.trim().lowercase()
    val normalizedTag = selectedTag?.trim()?.lowercase().orEmpty()
    val normalizedFolderTag = selectedFolderTag?.trim()?.lowercase().orEmpty()
    val stTagIds = tagSettings
        ?.tags
        ?.filter { tag -> tag.id.lowercase() == normalizedTag || tag.name.lowercase() == normalizedTag }
        ?.map { it.id }
        ?.toSet()
        .orEmpty()
    val stFolderTagIds = tagSettings
        ?.tags
        ?.filter { tag -> tag.id.lowercase() == normalizedFolderTag || tag.name.lowercase() == normalizedFolderTag }
        ?.map { it.id }
        ?.toSet()
        .orEmpty()
    val filtered = characters
        .filter { character ->
            when (filter) {
                CharacterListFilter.ALL -> true
                CharacterListFilter.FAVORITES -> character.isFavorite
                CharacterListFilter.RECENT -> character.lastChatAt > 0L
            }
        }
        .filter { character ->
            if (normalizedQuery.isBlank()) {
                true
            } else {
                character.searchScore(normalizedQuery, tagSettings) > 0
            }
        }
        .filter { character ->
            normalizedFolderTag.isBlank() || CharacterTagTools
                .assignedTagIds(character, tagSettings)
                .any { id -> id in stFolderTagIds }
        }
        .filter { character ->
            normalizedTag.isBlank() || when (selectedTagSource) {
                CharacterTagSource.EMBEDDED -> character.tags.any { tag -> tag.lowercase() == normalizedTag }
                CharacterTagSource.ST -> {
                    val assignedIds = CharacterTagTools.assignedTagIds(character, tagSettings)
                    assignedIds.any { id -> id in stTagIds }
                }
            }
        }
    return if (normalizedQuery.isBlank()) {
        filtered.sortCharacters(sort)
    } else {
        filtered.sortCharacters(sort)
            .sortedByDescending { it.searchScore(normalizedQuery, tagSettings) }
    }
}

fun paginateCharacters(
    characters: List<CharacterSummary>,
    requestedPage: Int,
    pageSize: Int
): CharacterPage {
    val safePageSize = pageSize.takeIf { it > 0 } ?: characters.size.coerceAtLeast(1)
    val totalItems = characters.size
    val totalPages = ((totalItems + safePageSize - 1) / safePageSize).coerceAtLeast(1)
    val currentPage = requestedPage.coerceIn(1, totalPages)
    val startIndex = ((currentPage - 1) * safePageSize).coerceAtMost(totalItems)
    val endIndex = (startIndex + safePageSize).coerceAtMost(totalItems)
    return CharacterPage(
        items = characters.subList(startIndex, endIndex),
        currentPage = currentPage,
        totalPages = totalPages,
        pageSize = safePageSize,
        totalItems = totalItems,
        firstItemNumber = if (totalItems == 0) 0 else startIndex + 1,
        lastItemNumber = endIndex
    )
}

private fun List<CharacterSummary>.sortCharacters(sort: CharacterListSort): List<CharacterSummary> {
    return when (sort) {
        CharacterListSort.RECENT -> sortedWith(
            compareByDescending<CharacterSummary> { it.lastChatAt }
                .thenBy { it.name.lowercase() }
        )
        CharacterListSort.NAME_ASC -> sortedBy { it.name.lowercase() }
        CharacterListSort.NAME_DESC -> sortedByDescending { it.name.lowercase() }
        CharacterListSort.NEWEST -> sortedWith(
            compareByDescending<CharacterSummary> { it.createDate }
                .thenBy { it.name.lowercase() }
        )
        CharacterListSort.OLDEST -> sortedWith(
            compareBy<CharacterSummary> { it.createDate }
                .thenBy { it.name.lowercase() }
        )
        CharacterListSort.FAVORITES -> sortedWith(
            compareByDescending<CharacterSummary> { it.isFavorite }
                .thenBy { it.name.lowercase() }
        )
        CharacterListSort.MOST_CHATS -> sortedWith(
            compareByDescending<CharacterSummary> { it.chatSize }
                .thenBy { it.name.lowercase() }
        )
        CharacterListSort.LEAST_CHATS -> sortedWith(
            compareBy<CharacterSummary> { it.chatSize }
                .thenBy { it.name.lowercase() }
        )
        CharacterListSort.MOST_TOKENS -> sortedWith(
            compareByDescending<CharacterSummary> { it.dataSize }
                .thenBy { it.name.lowercase() }
        )
        CharacterListSort.LEAST_TOKENS -> sortedWith(
            compareBy<CharacterSummary> { it.dataSize }
                .thenBy { it.name.lowercase() }
        )
        CharacterListSort.RANDOM -> shuffled(Random.Default)
    }
}

private fun CharacterSummary.searchScore(query: String, tagSettings: STTagSettings?): Int {
    return when {
        name.contains(query, ignoreCase = true) -> 400
        tags.any { tag -> tag.contains(query, ignoreCase = true) } -> 300
        CharacterTagTools.stTagNames(this, tagSettings).any { tag -> tag.contains(query, ignoreCase = true) } -> 200
        creatorNotes.contains(query, ignoreCase = true) -> 100
        else -> 0
    }
}
