package io.github.sanitised.st

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeColorSourceTest {
    @Test
    fun defaultsToBrandPrototypeColors() {
        assertEquals(ThemeColorSource.BRAND, ThemeColorSource.fromStorage(null))
        assertEquals(ThemeColorSource.BRAND, ThemeColorSource.fromStorage(""))
        assertEquals(ThemeColorSource.BRAND, ThemeColorSource.fromStorage("unknown"))
    }

    @Test
    fun restoresStoredThemeColorSource() {
        assertEquals(ThemeColorSource.DYNAMIC, ThemeColorSource.fromStorage("dynamic"))
        assertEquals(ThemeColorSource.BRAND, ThemeColorSource.fromStorage("brand"))
    }
}
