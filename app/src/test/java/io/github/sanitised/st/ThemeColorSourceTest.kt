package io.github.sanitised.st

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeColorSourceTest {
    @Test
    fun defaultsToDynamicMaterialYouColors() {
        assertEquals(ThemeColorSource.DYNAMIC, ThemeColorSource.fromStorage(null))
        assertEquals(ThemeColorSource.DYNAMIC, ThemeColorSource.fromStorage(""))
        assertEquals(ThemeColorSource.DYNAMIC, ThemeColorSource.fromStorage("unknown"))
    }

    @Test
    fun restoresStoredThemeColorSource() {
        assertEquals(ThemeColorSource.DYNAMIC, ThemeColorSource.fromStorage("dynamic"))
        assertEquals(ThemeColorSource.BRAND, ThemeColorSource.fromStorage("brand"))
    }
}
