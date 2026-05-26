package io.github.sanitised.st

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsExportTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun exportZipIncludesLogsAndRedactedConfigWithoutUserSecrets() {
        val logsDir = temp.newFolder("logs").apply {
            file("service.log", "service: node exited with code 137\n")
            file("node_stdout.log", "stdout tail\n")
            file("node_stderr.log", "stderr tail\n")
            file("node_stdout.log.1", "rotated stdout\n")
        }
        val configFile = temp.newFile("config.yaml").apply {
            writeText(
                """
                port: 8000
                apiKey: sk-test
                nested:
                  password: let-me-in
                allowKeysExposure: false
                """.trimIndent(),
                Charsets.UTF_8
            )
        }
        val stDir = temp.newFolder("st").apply {
            file("package.json", """{"version":"1.12.0"}""")
        }
        val dataDir = temp.newFolder("data").apply {
            file("default-user/settings.json", "{}")
            file("default-user/secrets.json", """{"openai":"sk-test"}""")
            file("default-user/characters/Alice.png", "png")
            file("default-user/chats/Alice/chat.jsonl", "{}\n")
        }
        val output = ByteArrayOutputStream()

        DiagnosticExporter.export(
            DiagnosticExportRequest(
                appVersion = "0.4.0-dev",
                stLabel = "SillyTavern e3f41666c",
                nodeLabel = "Node v24.13.0",
                generatedAtEpochMs = 1_748_246_400_000L,
                status = NodeStatus(NodeState.ERROR, "Node exited with code 137", port = 8000),
                logsDir = logsDir,
                configFile = configFile,
                stDir = stDir,
                dataDir = dataDir,
                outputStream = output
            )
        )

        val entries = readZipEntries(output.toByteArray())
        assertTrue(entries["summary.txt"].orEmpty().contains("state: ERROR"))
        assertTrue(entries["summary.txt"].orEmpty().contains("port: 8000"))
        assertEquals("service: node exited with code 137\n", entries["logs/service.log"])
        assertEquals("stdout tail\n", entries["logs/node_stdout.log"])
        assertEquals("rotated stdout\n", entries["logs/node_stdout.log.1"])
        assertTrue(entries["config/config.yaml"].orEmpty().contains("apiKey: [redacted]"))
        assertTrue(entries["config/config.yaml"].orEmpty().contains("password: [redacted]"))
        assertTrue(entries["data-summary.txt"].orEmpty().contains("characters: 1"))
        assertTrue(entries["data-summary.txt"].orEmpty().contains("chats: 1"))
        assertFalse(entries.keys.any { it.contains("secrets.json") })
        assertFalse(entries.values.any { it.contains("sk-test") })
    }

    private fun File.file(path: String, text: String) {
        val file = File(this, path)
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return entries
    }
}
