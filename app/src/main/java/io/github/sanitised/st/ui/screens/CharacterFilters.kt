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

fun filterCharacters(
    characters: List<CharacterSummary>,
    query: String,
    filter: CharacterListFilter,
    sort: CharacterListSort,
    selectedTag: String? = null,
    selectedTagSource: CharacterTagSource = CharacterTagSource.EMBEDDED,
    tagSettings: STTagSettings? = null
): List<CharacterSummary> {
    val normalizedQuery = query.trim().lowercase()
    val normalizedTag = selectedTag?.trim()?.lowercase().orEmpty()
    val stTagIds = tagSettings
        ?.tags
        ?.filter { tag -> tag.id.lowercase() == normalizedTag || tag.name.lowercase() == normalizedTag }
        ?.map { it.id }
        ?.toSet()
        .orEmpty()
    return characters
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
                character.name.contains(normalizedQuery, ignoreCase = true) ||
                character.creatorNotes.contains(normalizedQuery, ignoreCase = true) ||
                character.tags.any { tag -> tag.contains(normalizedQuery, ignoreCase = true) }
            }
        }
        .filter { character ->
            normalizedTag.isBlank() || when (selectedTagSource) {
                CharacterTagSource.EMBEDDED -> character.tags.any { tag -> tag.lowercase() == normalizedTag }
                CharacterTagSource.ST -> {
                    val tagMap = tagSettings?.tagMap.orEmpty()
                    val assignedIds = listOfNotNull(character.id, character.avatarUrl)
                        .flatMap { key -> tagMap[key].orEmpty() }
                        .toSet()
                    assignedIds.any { id -> id in stTagIds }
                }
            }
        }
        .sortCharacters(sort)
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
