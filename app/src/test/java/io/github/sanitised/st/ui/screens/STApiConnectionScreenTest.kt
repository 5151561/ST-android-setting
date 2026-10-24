package io.github.sanitised.st.ui.screens

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.sanitised.st.HiltTestActivity
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.ConnectionProfile
import io.github.sanitised.st.api.SecretProviderState
import io.github.sanitised.st.data.repository.ConnectionRepository
import io.github.sanitised.st.ui.theme.STAppTheme
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class STApiConnectionScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Test
    fun stoppedStateDisplaysServiceNotRunningAndDoesNotQueryRepository() {
        val repository = mockk<ConnectionRepository>(relaxed = true)
        val stoppedStatus = NodeStatus(state = NodeState.STOPPED, port = 8000, message = "Stopped")

        composeRule.setContent {
            STAppTheme {
                STApiConnectionScreen(
                    status = stoppedStatus,
                    baseUrl = "http://127.0.0.1:8000",
                    onBack = {},
                    onShowMessage = {},
                    connectionRepository = repository,
                )
            }
        }

        composeRule.onNodeWithText("API 连接设置").assertIsDisplayed()
        composeRule.onNodeWithText("○ 服务未启动").assertIsDisplayed()
        coVerify(exactly = 0) { repository.getSettings() }
    }

    @Test
    fun runningStateLoadsFromRepositoryAndSwitchingModePersistsSettings() {
        val repository = mockk<ConnectionRepository>(relaxed = true)
        coEvery { repository.listConnectionProfiles() } returns listOf(
            ConnectionProfile(label = "默认档案", url = "https://api.openai.com/v1", lastConnection = 100L)
        )
        coEvery { repository.listSecrets() } returns listOf(
            SecretProviderState(key = "api_key_openai", label = "OpenAI", entries = emptyList())
        )
        coEvery { repository.getSettings() } returns mapOf(
            "main_api" to "openai",
            "chat_completion_source" to "openai",
            "power_user" to mapOf("last_connection" to "默认档案"),
        )
        coJustRun { repository.saveSettings(any()) }

        val messages = mutableListOf<String>()
        val runningStatus = NodeStatus(state = NodeState.RUNNING, port = 8000, message = "Running")

        composeRule.setContent {
            STAppTheme {
                STApiConnectionScreen(
                    status = runningStatus,
                    baseUrl = "http://127.0.0.1:8000",
                    onBack = {},
                    onShowMessage = { messages += it },
                    connectionRepository = repository,
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("● 服务运行中").assertIsDisplayed()
        composeRule.onNodeWithText("默认档案").assertIsDisplayed()
        coVerify(atLeast = 1) { repository.getSettings() }

        // 切换到「文本补全」模式，验证触发 saveSettings
        composeRule.onNodeWithText("文本补全").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("文本补全 — 后端").performScrollTo().assertIsDisplayed()
        coVerify(atLeast = 1) { repository.saveSettings(any()) }
        assertEquals(true, messages.any { it.startsWith("已切换至") })
    }

    @Test
    fun activityRecreationRestoresSelectedModeWhenStopped() {
        val repository = mockk<ConnectionRepository>(relaxed = true)
        val stoppedStatus = NodeStatus(state = NodeState.STOPPED, port = 8000, message = "Stopped")
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            STAppTheme {
                STApiConnectionScreen(
                    status = stoppedStatus,
                    baseUrl = "http://127.0.0.1:8000",
                    onBack = {},
                    onShowMessage = {},
                    connectionRepository = repository,
                )
            }
        }

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent(content = content)
        }

        composeRule.onNodeWithText("聊天补全 — 提供商").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("文本补全").performScrollTo().performClick()
        composeRule.onNodeWithText("文本补全 — 后端").performScrollTo().assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent(content = content)
        }

        composeRule.onNodeWithText("文本补全 — 后端").performScrollTo().assertIsDisplayed()
    }
}
