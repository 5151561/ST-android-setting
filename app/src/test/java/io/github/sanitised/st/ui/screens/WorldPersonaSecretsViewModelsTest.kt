package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.MainDispatcherRule
import io.github.sanitised.st.api.PersonaProfile
import io.github.sanitised.st.api.PersonaSaveRequest
import io.github.sanitised.st.api.PresetLibrary
import io.github.sanitised.st.api.SecretProviderState
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.api.WorldInfoSummary
import io.github.sanitised.st.data.repository.ConnectionRepository
import io.github.sanitised.st.data.repository.PersonaPresetRepository
import io.github.sanitised.st.data.repository.WorldInfoRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorldPersonaSecretsViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun connectionSecretsViewModelExposesInjectedRepositoryAndDelegatesSecretOperations() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = mockk<ConnectionRepository>()
            val expectedSecrets = listOf(
                SecretProviderState(
                    key = "api_key_openai",
                    label = "OpenAI",
                    entries = emptyList(),
                )
            )
            coEvery { repository.listSecrets() } returns expectedSecrets
            coEvery { repository.writeSecret("api_key_openai", "sk-test", "Main") } returns "sec-2"
            coJustRun { repository.rotateSecret("api_key_openai", "sec-2") }
            coJustRun { repository.deleteSecret("api_key_openai", "sec-1") }

            val viewModel = ConnectionSecretsViewModel(repository)

            assertSame(repository, viewModel.repository)
            assertEquals(expectedSecrets, viewModel.repository.listSecrets())
            assertEquals("sec-2", viewModel.repository.writeSecret("api_key_openai", "sk-test", "Main"))
            viewModel.repository.rotateSecret("api_key_openai", "sec-2")
            viewModel.repository.deleteSecret("api_key_openai", "sec-1")

            coVerify(exactly = 1) { repository.listSecrets() }
            coVerify(exactly = 1) { repository.writeSecret("api_key_openai", "sk-test", "Main") }
            coVerify(exactly = 1) { repository.rotateSecret("api_key_openai", "sec-2") }
            coVerify(exactly = 1) { repository.deleteSecret("api_key_openai", "sec-1") }
        }

    @Test
    fun worldInfoViewModelDelegatesCrudAndSettingsToRepository() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = mockk<WorldInfoRepository>()
            val summaries = listOf(WorldInfoSummary(id = "Lorebook A", name = "Lorebook A"))
            val book = WorldInfoBook(name = "Lorebook A")
            val settings = mapOf<String, Any?>("world_info_depth" to 4)

            coEvery { repository.listWorldInfos() } returns summaries
            coEvery { repository.getWorldInfo("Lorebook A") } returns book
            coJustRun { repository.saveWorldInfo(book) }
            coJustRun { repository.deleteWorldInfo("Lorebook A") }
            coEvery { repository.getSettings() } returns settings
            coJustRun { repository.saveSettings(settings) }

            val viewModel = WorldInfoViewModel(repository)

            assertFalse(viewModel.uiState.value.loading)
            assertEquals(emptyList<WorldInfoSummary>(), viewModel.uiState.value.books)
            assertNull(viewModel.uiState.value.error)

            assertEquals(summaries, viewModel.listWorldInfos())
            assertEquals(book, viewModel.getWorldInfo("Lorebook A"))
            viewModel.saveWorldInfo(book)
            viewModel.deleteWorldInfo("Lorebook A")
            assertEquals(settings, viewModel.getSettings())
            viewModel.saveSettings(settings)

            coVerify(exactly = 1) { repository.listWorldInfos() }
            coVerify(exactly = 1) { repository.getWorldInfo("Lorebook A") }
            coVerify(exactly = 1) { repository.saveWorldInfo(book) }
            coVerify(exactly = 1) { repository.deleteWorldInfo("Lorebook A") }
            coVerify(exactly = 1) { repository.getSettings() }
            coVerify(exactly = 1) { repository.saveSettings(settings) }
        }

    @Test
    fun personaPresetViewModelDelegatesPersonasPresetsAndBackgroundsToRepository() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = mockk<PersonaPresetRepository>()
            val personas = listOf(PersonaProfile(avatar = "user-1.png", name = "Traveler", isDefault = true))
            val saveRequest = PersonaSaveRequest(avatar = "user-1.png", name = "Traveler", description = "Adventurer")
            val library = PresetLibrary(categories = emptyList())
            val backgrounds = listOf("tavern.jpg", "forest.png")
            val settings = mapOf<String, Any?>("user_avatar" to "user-1.png")

            coEvery { repository.listPersonas() } returns personas
            coJustRun { repository.savePersona(saveRequest) }
            coJustRun { repository.deletePersona("user-1.png") }
            coEvery { repository.getPresetLibrary() } returns library
            coJustRun { repository.selectPreset("openai", "Default") }
            coEvery { repository.listBackgrounds() } returns backgrounds
            coJustRun { repository.renameBackground("tavern.jpg", "inn.jpg") }
            coJustRun { repository.deleteBackground("forest.png") }
            coEvery { repository.getSettings() } returns settings
            coJustRun { repository.saveSettings(settings) }

            val viewModel = PersonaPresetViewModel(repository)

            assertFalse(viewModel.uiState.value.loading)
            assertEquals(emptyList<PersonaProfile>(), viewModel.uiState.value.personas)
            assertNull(viewModel.uiState.value.presetLibrary)

            assertEquals(personas, viewModel.listPersonas())
            viewModel.savePersona(saveRequest)
            viewModel.deletePersona("user-1.png")
            assertEquals(library, viewModel.getPresetLibrary())
            viewModel.selectPreset("openai", "Default")
            assertEquals(backgrounds, viewModel.listBackgrounds())
            viewModel.renameBackground("tavern.jpg", "inn.jpg")
            viewModel.deleteBackground("forest.png")
            assertEquals(settings, viewModel.getSettings())
            viewModel.saveSettings(settings)

            coVerify(exactly = 1) { repository.listPersonas() }
            coVerify(exactly = 1) { repository.savePersona(saveRequest) }
            coVerify(exactly = 1) { repository.deletePersona("user-1.png") }
            coVerify(exactly = 1) { repository.getPresetLibrary() }
            coVerify(exactly = 1) { repository.selectPreset("openai", "Default") }
            coVerify(exactly = 1) { repository.listBackgrounds() }
            coVerify(exactly = 1) { repository.renameBackground("tavern.jpg", "inn.jpg") }
            coVerify(exactly = 1) { repository.deleteBackground("forest.png") }
            coVerify(exactly = 1) { repository.getSettings() }
            coVerify(exactly = 1) { repository.saveSettings(settings) }
        }
}
