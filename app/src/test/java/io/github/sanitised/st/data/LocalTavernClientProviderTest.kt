package io.github.sanitised.st.data

import io.github.sanitised.st.DEFAULT_PORT
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTavernClientProviderTest {
    @Test
    fun keepsOneValidatedApplicationLevelPort() {
        val provider = LocalTavernClientProvider(initialPort = 9000)

        assertEquals(9000, provider.currentPort())
        provider.updatePort(12345)
        assertEquals(12345, provider.currentPort())
        provider.updatePort(0)
        assertEquals(DEFAULT_PORT, provider.currentPort())
    }
}
