package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.STTag
import io.github.sanitised.st.api.STTagSettings

object CharacterTagTools {
    fun assignedTagIds(character: CharacterSummary, settings: STTagSettings?): Set<String> {
        val tagMap = settings?.tagMap.orEmpty()
        return listOfNotNull(character.id, character.avatarUrl)
            .flatMap { key -> tagMap[key].orEmpty() }
            .toSet()
    }

    fun stTagNames(character: CharacterSummary, settings: STTagSettings?): List<String> {
        val assigned = assignedTagIds(character, settings)
        return settings?.tags.orEmpty()
            .filter { it.id in assigned }
            .map { it.name }
    }

    fun importEmbeddedTags(settings: STTagSettings, characters: List<CharacterSummary>): STTagSettings {
        var updated = settings
        characters.flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .forEach { tagName ->
                updated = ensureTag(updated, tagName)
            }

        val tagByName = updated.tags.associateBy { it.name.lowercase() }
        val tagMap = updated.tagMap.toMutableMap()
        characters.forEach { character ->
            val importedIds = character.tags.mapNotNull { tagByName[it.trim().lowercase()]?.id }
            if (importedIds.isNotEmpty()) {
                val merged = (tagMap[character.id].orEmpty() + importedIds).distinct()
                tagMap[character.id] = merged
            }
        }
        return updated.copy(tagMap = tagMap)
    }

    fun ensureTag(settings: STTagSettings, name: String, isFolder: Boolean = false): STTagSettings {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return settings
        val existing = settings.tags.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }
        if (existing != null) {
            return if (existing.isFolder == isFolder || !isFolder) {
                settings
            } else {
                renameTag(settings, existing.id, existing.name, isFolder = true)
            }
        }
        val tag = STTag(
            id = newTagId(settings.tags, normalizedName),
            name = normalizedName,
            isFolder = isFolder,
            sortOrder = (settings.tags.maxOfOrNull { it.sortOrder } ?: 0) + 1
        )
        return settings.copy(tags = settings.tags + tag)
    }

    fun renameTag(settings: STTagSettings, tagId: String, newName: String, isFolder: Boolean): STTagSettings {
        val normalizedName = newName.trim()
        if (normalizedName.isBlank()) return settings
        return settings.copy(
            tags = settings.tags.map { tag ->
                if (tag.id == tagId) {
                    tag.copy(name = normalizedName, isFolder = isFolder)
                } else {
                    tag
                }
            }
        )
    }

    fun deleteTag(settings: STTagSettings, tagId: String): STTagSettings {
        return settings.copy(
            tags = settings.tags.filterNot { it.id == tagId },
            tagMap = settings.tagMap.mapValues { (_, ids) -> ids.filterNot { it == tagId } }
        )
    }

    fun setCharacterTags(settings: STTagSettings, avatar: String, tagIds: List<String>): STTagSettings {
        val knownIds = settings.tags.map { it.id }.toSet()
        val cleanIds = tagIds.filter { it in knownIds }.distinct()
        return settings.copy(tagMap = settings.tagMap + (avatar to cleanIds))
    }

    fun addTagsToCharacters(settings: STTagSettings, avatars: Set<String>, tagIds: Set<String>): STTagSettings {
        val knownIds = settings.tags.map { it.id }.toSet()
        val cleanIds = tagIds.filter { it in knownIds }
        if (avatars.isEmpty() || cleanIds.isEmpty()) return settings
        val tagMap = settings.tagMap.toMutableMap()
        avatars.forEach { avatar ->
            tagMap[avatar] = (tagMap[avatar].orEmpty() + cleanIds).distinct()
        }
        return settings.copy(tagMap = tagMap)
    }

    fun removeTagsFromCharacters(settings: STTagSettings, avatars: Set<String>, tagIds: Set<String>): STTagSettings {
        if (avatars.isEmpty() || tagIds.isEmpty()) return settings
        val tagMap = settings.tagMap.toMutableMap()
        avatars.forEach { avatar ->
            tagMap[avatar] = tagMap[avatar].orEmpty().filterNot { it in tagIds }
        }
        return settings.copy(tagMap = tagMap)
    }

    fun drilldownTagsForFolder(
        characters: List<CharacterSummary>,
        settings: STTagSettings,
        folderName: String?
    ): List<STTag> {
        val folder = settings.tags.firstOrNull {
            it.isFolder && it.name.equals(folderName.orEmpty(), ignoreCase = true)
        } ?: return settings.tags.sortedForDisplay()
        val visibleTagIds = characters
            .filter { character -> folder.id in assignedTagIds(character, settings) }
            .flatMap { character -> assignedTagIds(character, settings) }
            .filterNot { it == folder.id }
            .toSet()
        return settings.tags
            .filter { it.id in visibleTagIds }
            .sortedForDisplay()
    }

    fun topLevelTags(settings: STTagSettings): List<STTag> =
        settings.tags.sortedForDisplay()

    private fun List<STTag>.sortedForDisplay(): List<STTag> =
        sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))

    private fun newTagId(tags: List<STTag>, name: String): String {
        val base = name.lowercase()
            .map { char -> if (char.isLetterOrDigit()) char else '-' }
            .joinToString("")
            .trim('-')
            .ifBlank { "tag" }
        val existing = tags.map { it.id }.toSet()
        if (base !in existing) return base
        var suffix = 2
        while ("$base-$suffix" in existing) suffix++
        return "$base-$suffix"
    }
}
