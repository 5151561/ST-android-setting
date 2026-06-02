package io.github.sanitised.st.chat.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeGenerationRouteTest {

    @Test
    fun bridgeFallbackRouteStopsThroughBridgeEvenInCharacterChat() {
        assertEquals(
            GenerationStopTarget.BRIDGE,
            stopTargetForGeneration(mode = "character", route = ActiveGenerationRoute.BRIDGE)
        )
    }

    @Test
    fun nativeRouteStopsLocallyForCharacterChat() {
        assertEquals(
            GenerationStopTarget.NATIVE,
            stopTargetForGeneration(mode = "character", route = ActiveGenerationRoute.NATIVE)
        )
    }

    @Test
    fun groupChatsAlwaysStopThroughBridge() {
        assertEquals(
            GenerationStopTarget.BRIDGE,
            stopTargetForGeneration(mode = "group", route = ActiveGenerationRoute.NATIVE)
        )
    }
}
