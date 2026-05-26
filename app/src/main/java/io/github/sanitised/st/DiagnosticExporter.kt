package io.github.sanitised.st

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class DiagnosticExportRequest(
    val appVersion: String,
    val stLabel: String,
    val nodeLabel: String,
    val generatedAtEpochMs: Long,
    val status: NodeStatus,
    val logsDir: File,
    val configFile: File,
    val stDir: File,
    val dataDir: File,
    val outputStream: OutputStream
)

internal object DiagnosticExporter {
    private const val MAX_TEXT_FILE_BYTES = 2L * 1024L * 1024L
    private val sensitiveLinePattern = Regex(
        pattern = """(?im)^(\s*[\w.-]*(?:key|secret|token|password)[\w.-]*\s*[:=]\s*).+$"""
    )
    private val urlUserInfoPattern = Regex("""(?i)\b([a-z][a-z0-9+.-]*://)([^/\s@]+)@""")
    private val rawSecretPattern = Regex("""(?i)\b(?:sk|pk|sess|token)-[A-Za-z0-9._-]+""")

    fun export(request: DiagnosticExportRequest) {
        ZipOutputStream(BufferedOutputStream(request.outputStream)).use { zip ->
            zip.addTextEntry("summary.txt", buildSummary(request), request.generatedAtEpochMs)
            zip.addTextEntry("data-summary.txt", buildDataSummary(request.dataDir), request.generatedAtEpochMs)

            if (request.configFile.exists() && request.configFile.isFile) {
                zip.addTextEntry(
                    name = "config/config.yaml",
                    text = redactPotentialSecrets(readTextTail(request.configFile)),
                    timestampMs = request.generatedAtEpochMs
                )
            }

            File(request.stDir, "package.json")
                .takeIf { it.exists() && it.isFile }
                ?.let { file ->
                    zip.addTextEntry(
                        name = "source/package.json",
                        text = redactPotentialSecrets(readTextTail(file)),
                        timestampMs = request.generatedAtEpochMs
                    )
                }

            request.logsDir
                .listFiles()
                .orEmpty()
                .filter { it.isFile && (it.name.endsWith(".log") || it.name.endsWith(".log.1")) }
                .sortedBy { it.name }
                .forEach { file ->
                    zip.addTextEntry(
                        name = "logs/${file.name}",
                        text = redactPotentialSecrets(readTextTail(file)),
                        timestampMs = request.generatedAtEpochMs
                    )
                }
        }
    }

    private fun buildSummary(request: DiagnosticExportRequest): String {
        return buildString {
            appendLine("generated_at: ${Instant.ofEpochMilli(request.generatedAtEpochMs)}")
            appendLine("app_version: ${request.appVersion}")
            appendLine("sillytavern: ${request.stLabel}")
            appendLine("node: ${request.nodeLabel}")
            appendLine("state: ${request.status.state}")
            appendLine("message: ${request.status.message}")
            appendLine("port: ${request.status.port}")
            appendLine("pid: ${request.status.pid ?: "unknown"}")
        }
    }

    private fun buildDataSummary(dataDir: File): String {
        val userDir = File(dataDir, "default-user").takeIf { it.exists() } ?: dataDir
        return buildString {
            appendLine("data_root_present: ${dataDir.exists()}")
            appendLine("default_user_present: ${userDir.exists()}")
            appendLine("settings.json: ${File(userDir, "settings.json").exists()}")
            appendLine("secrets.json: ${File(userDir, "secrets.json").exists()} (not exported)")
            appendLine("characters: ${countFiles(File(userDir, "characters"))}")
            appendLine("chats: ${countFiles(File(userDir, "chats"))}")
            appendLine("worlds: ${countFiles(File(userDir, "worlds"))}")
            appendLine("groups: ${countFiles(File(userDir, "groups"))}")
            appendLine("user_avatars: ${countFiles(File(userDir, "User Avatars"))}")
            appendLine("quick_replies: ${countFiles(File(userDir, "QuickReplies"))}")
            appendLine("backups: ${countFiles(File(userDir, "backups"))}")
            appendLine("total_data_bytes: ${sumBytes(dataDir)}")
        }
    }

    private fun countFiles(dir: File): Int {
        if (!dir.exists()) return 0
        return dir.walkTopDown().count { it.isFile }
    }

    private fun sumBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun readTextTail(file: File): String {
        val length = file.length()
        if (length <= 0L) return ""
        val toRead = length.coerceAtMost(MAX_TEXT_FILE_BYTES)
        val bytes = ByteArray(toRead.toInt())
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(length - toRead)
            raf.readFully(bytes)
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun redactPotentialSecrets(text: String): String {
        return text
            .replace(sensitiveLinePattern) { match -> "${match.groupValues[1]}[redacted]" }
            .replace(urlUserInfoPattern) { match -> "${match.groupValues[1]}[redacted]@" }
            .replace(rawSecretPattern, "[redacted]")
    }

    private fun ZipOutputStream.addTextEntry(name: String, text: String, timestampMs: Long) {
        val entry = ZipEntry(name).apply { time = timestampMs }
        putNextEntry(entry)
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
