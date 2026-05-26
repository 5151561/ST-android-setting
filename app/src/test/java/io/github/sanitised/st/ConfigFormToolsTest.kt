package io.github.sanitised.st

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yaml.snakeyaml.Yaml

class ConfigFormToolsTest {
    @Test
    fun parseBuildsEditableRowsForNestedYaml() {
        val document = ConfigFormTools.parse(
            """
            port: 8000
            listen: false
            ssl:
              enabled: false
              certPath: "./certs/cert.pem"
            whitelist:
              - ::1
              - 127.0.0.1
            thumbnails:
              dimensions: { 'bg': [160, 90] }
            """.trimIndent()
        )

        assertEquals(ConfigFormRowKind.NUMBER, document.row(ConfigFormTools.pathKey("port")).kind)
        assertEquals(ConfigFormRowKind.BOOLEAN, document.row(ConfigFormTools.pathKey("listen")).kind)
        assertEquals(ConfigFormRowKind.SECTION, document.row(ConfigFormTools.pathKey("ssl")).kind)
        assertEquals(ConfigFormRowKind.TEXT, document.row(ConfigFormTools.pathKey("ssl", "certPath")).kind)
        assertEquals(ConfigFormRowKind.SCALAR_LIST, document.row(ConfigFormTools.pathKey("whitelist")).kind)
        assertEquals(ConfigFormRowKind.SCALAR_LIST, document.row(ConfigFormTools.pathKey("thumbnails", "dimensions", "bg")).kind)
    }

    @Test
    fun writeYamlAppliesTypedFormValues() {
        val document = ConfigFormTools.parse(
            """
            port: 8000
            listen: false
            ssl:
              enabled: false
            whitelist:
              - ::1
              - 127.0.0.1
            """.trimIndent()
        )
        val values = document.initialValues.toMutableMap().apply {
            put(ConfigFormTools.pathKey("port"), "8123")
            put(ConfigFormTools.pathKey("listen"), "true")
            put(ConfigFormTools.pathKey("ssl", "enabled"), "true")
            put(ConfigFormTools.pathKey("whitelist"), "::1\n127.0.0.1\n10.0.2.2")
        }

        val saved = ConfigFormTools.writeYaml(document, values)
        val root = Yaml().load<Map<String, Any?>>(saved)

        assertEquals(8123, root["port"])
        assertEquals(true, root["listen"])
        assertEquals(true, (root["ssl"] as Map<*, *>)["enabled"])
        assertEquals(listOf("::1", "127.0.0.1", "10.0.2.2"), root["whitelist"])
    }

    @Test
    fun complexListsStayEditableAsYamlBlocks() {
        val document = ConfigFormTools.parse(
            """
            requestOverrides:
              - hosts:
                  - example.com
                headers:
                  User-Agent: TestBot
            """.trimIndent()
        )
        val key = ConfigFormTools.pathKey("requestOverrides")

        assertEquals(ConfigFormRowKind.YAML, document.row(key).kind)

        val values = document.initialValues.toMutableMap().apply {
            put(
                key,
                """
                - hosts:
                    - 127.0.0.1:5001
                  headers:
                    Content-Type: application/json
                """.trimIndent()
            )
        }
        val saved = ConfigFormTools.writeYaml(document, values)
        val root = Yaml().load<Map<String, Any?>>(saved)
        val overrides = root["requestOverrides"] as List<*>
        val first = overrides.first() as Map<*, *>

        assertEquals(listOf("127.0.0.1:5001"), first["hosts"])
        assertEquals(mapOf("Content-Type" to "application/json"), first["headers"])
    }

    @Test
    fun invalidTypedValuesFailBeforeWritingYaml() {
        val document = ConfigFormTools.parse("port: 8000\nlisten: false\n")
        val values = document.initialValues.toMutableMap().apply {
            put(ConfigFormTools.pathKey("port"), "abc")
        }

        val failure = runCatching { ConfigFormTools.writeYaml(document, values) }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("port"))
    }

    @Test
    fun startupPortReadsFromYamlSafely() {
        assertEquals(8088, ConfigFormTools.readStartupPort("port: 8088\n"))
        assertEquals(9099, ConfigFormTools.readStartupPort("port: \"9099\"\n"))
        assertEquals(DEFAULT_PORT, ConfigFormTools.readStartupPort("port: 70000\n") ?: DEFAULT_PORT)
    }

    @Test
    fun defaultSillyTavernConfigCanBeParsedAndWrittenFromForm() {
        val configText = File("../SillyTavern/default/config.yaml").readText(Charsets.UTF_8)
        val document = ConfigFormTools.parse(configText)

        val saved = ConfigFormTools.writeYaml(document, document.initialValues)
        val root = Yaml().load<Map<String, Any?>>(saved)

        assertTrue(document.rows.size > 50)
        assertEquals(8000, root["port"])
        assertTrue((root["ssl"] as Map<*, *>).containsKey("enabled"))
        assertTrue((root["extensions"] as Map<*, *>).containsKey("models"))
    }

    private fun ConfigFormDocument.row(key: String): ConfigFormRow =
        rows.first { it.pathKey == key }
}
