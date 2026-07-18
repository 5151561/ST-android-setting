package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.MainDispatcherRule
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.CharacterSaveRequest
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.data.CharacterImport
import io.github.sanitised.st.data.CharacterRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun libraryStateIsDrivenByRepositoryAndMessageIsExplicitlyConsumed() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeCharacterRepository()
            val viewModel = CharacterLibraryViewModel(repository)

            viewModel.refresh(serverRunning = true)
            advanceUntilIdle()
            assertEquals(listOf("Alice"), viewModel.uiState.value.characters.map { it.name })
            assertFalse(viewModel.uiState.value.loading)

            viewModel.importCharacters(listOf(CharacterImport("Bob.png", byteArrayOf(1))))
            advanceUntilIdle()
            val message = requireNotNull(viewModel.uiState.value.message)
            assertEquals("角色导入成功", message.text)
            viewModel.messageShown(message.id)
            assertNull(viewModel.uiState.value.message)
        }

    @Test
    fun profileFavoriteWriteReturnsToRepositoryTruth() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeCharacterRepository()
            val viewModel = CharacterProfileViewModel(repository)

            viewModel.load(serverRunning = true, avatar = "Alice.png")
            advanceUntilIdle()
            viewModel.toggleFavorite()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.detail?.isFavorite)
            assertEquals(true, repository.favoriteWrites.single())
        }

    @Test
    fun createFormAndResultLiveInViewModelState() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeCharacterRepository()
            val viewModel = CharacterCreateViewModel(repository)

            viewModel.setName(" Bob ")
            viewModel.setSubtitle("友好")
            viewModel.setGreeting("你好")
            viewModel.save(serverRunning = true)
            advanceUntilIdle()

            assertEquals("Bob.png", viewModel.uiState.value.createdAvatar)
            assertEquals("Bob", repository.created.single().name)
            assertEquals("你好", repository.created.single().firstMessage)
            viewModel.creationHandled("Bob.png")
            assertNull(viewModel.uiState.value.createdAvatar)
        }

    private class FakeCharacterRepository : CharacterRepository {
        private var detail = CharacterDetail(id = "Alice.png", name = "Alice")
        val favoriteWrites = mutableListOf<Boolean>()
        val created = mutableListOf<CharacterSaveRequest>()

        override suspend fun listCharacters(serverRunning: Boolean): List<CharacterSummary> =
            listOf(CharacterSummary(id = "Alice.png", name = "Alice"))

        override suspend fun getCharacter(avatar: String): CharacterDetail = detail

        override suspend fun setFavorite(avatar: String, favorite: Boolean): CharacterDetail {
            favoriteWrites += favorite
            detail = detail.copy(isFavorite = favorite)
            return detail
        }

        override suspend fun createCharacter(request: CharacterSaveRequest): String {
            created += request
            return "${request.name}.png"
        }

        override suspend fun importCharacters(documents: List<CharacterImport>): List<String> =
            documents.map { it.fileName }
    }
}
