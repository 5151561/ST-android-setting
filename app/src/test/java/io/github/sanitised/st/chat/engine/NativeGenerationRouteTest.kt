package io.github.sanitised.st.chat.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeGenerationRouteTest {

    @Test
    fun nativeRouteStopsLocallyForCharacterChat() {
        assertEquals(
            GenerationStopTarget.NATIVE,
            stopTargetForGeneration(mode = "character", route = ActiveGenerationRoute.NATIVE)
        )
    }

    @Test
    fun groupChatsDoNotRouteStopToRemovedBridgeRuntime() {
        assertEquals(
            GenerationStopTarget.NATIVE,
            stopTargetForGeneration(mode = "group", route = ActiveGenerationRoute.NATIVE)
        )
    }
}
