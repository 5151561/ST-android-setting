package io.github.sanitised.st

import org.junit.Assert.assertEquals
import org.junit.Test

class SillyTavernUrlTest {
    @Test
    fun localWebUrlUsesLoopbackAndProvidedPort() {
        assertEquals("http://127.0.0.1:8000/", SillyTavernUrl.localWebUrl(8000))
        assertEquals("http://127.0.0.1:65535/", SillyTavernUrl.localWebUrl(65535))
    }

    @Test
    fun invalidPortsFallBackToDefaultPort() {
        assertEquals("http://127.0.0.1:8000/", SillyTavernUrl.localWebUrl(0))
        assertEquals("http://127.0.0.1:8000/", SillyTavernUrl.localWebUrl(70000))
    }
}
