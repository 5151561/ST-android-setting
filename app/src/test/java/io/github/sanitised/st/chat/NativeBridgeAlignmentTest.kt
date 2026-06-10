package io.github.sanitised.st.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeBridgeAlignmentTest {

    @Test
    fun bridgeFallbackWriteReloadsRuntimeBeforeWriting() {
        val events = mutableListOf<String>()

        runAlignedBridgeWrite(
            reload = { events += "reload" },
            write = { events += "edit" },
        )

        assertEquals(listOf("reload", "edit"), events)
    }
}
