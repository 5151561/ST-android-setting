package io.github.sanitised.st.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TavernCoreClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun healthCheckReturnsOkWhenRootRespondsSuccessfully() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>SillyTavern</html>"))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        assertTrue(client.healthCheck().ok)
    }

    @Test
    fun healthCheckReturnsNotOkWhenRootIsUnavailable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("starting"))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        assertFalse(client.healthCheck().ok)
    }
}
