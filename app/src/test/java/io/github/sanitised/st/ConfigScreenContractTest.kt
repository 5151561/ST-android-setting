package io.github.sanitised.st

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigScreenContractTest {
    @Test
    fun configScreenUsesCompactTopBarModeToggleInsteadOfLargeModeButtons() {
        val source = File("src/main/java/io/github/sanitised/st/UiConfig.kt").readText()

        assertTrue(source.contains("ConfigModeAction("))
        assertTrue(source.contains("actions = {"))
        assertTrue(source.contains("config_mode_yaml"))
        assertTrue(source.contains("config_mode_form"))
        assertFalse(source.contains("ConfigModeSwitcher("))
    }

    @Test
    fun configFormRowsDoNotRenderPathTextUnderEveryField() {
        val source = File("src/main/java/io/github/sanitised/st/UiConfig.kt").readText()

        assertTrue(source.contains("ConfigFieldRow("))
        assertTrue(source.contains("CompactConfigTextField("))
        assertFalse(source.contains("helperText(row)"))
        assertFalse(source.contains("ConfigTextRow("))
        assertFalse(source.contains("OutlinedTextField("))
    }
}
