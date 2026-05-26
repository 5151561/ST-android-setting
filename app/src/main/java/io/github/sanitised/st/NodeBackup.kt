package io.github.sanitised.st

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.time.Instant
import java.util.Date
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.yaml.snakeyaml.Yaml

enum class BackupImportKind {
    APP_BACKUP,
    ST_UI_USER_BACKUP
}

enum class BackupCoverageStatus {
    PRESENT,
    MISSING
}

data class BackupManifest(
    val formatVersion: String,
    val appVersion: String,
    val stCommit: String?,
    val exportedAt: String,
    val configBytes: Long,
    val dataBytes: Long,
    val includesSecrets: Boolean
)

data class BackupCoverageItem(
    val path: String,
    val status: BackupCoverageStatus,
    val count: Int
)

data class BackupImportPreview(
    val kind: BackupImportKind,
    val hasConfig: Boolean,
    val userHandle: String,
    val manifest: BackupManifest?,
    val coverage: List<BackupCoverageItem>,
    val warningMessages: List<String>
)

object NodeBackup {
    private const val BACKUP_ROOT = "st_backup"
    private const val DEFAULT_USER_HANDLE = "default-user"
    private const val UI_BACKUP_ROOT = "ui_backup"
    private const val MANIFEST_FILE = "manifest.yaml"
    private const val SETTINGS_FILE = "settings.json"
    private const val CHATS_DIR = "chats"
    private val DATA_ROOT_INFRA = setOf(
        "_storage",
        "_uploads",
        "cookie-secret.txt"
    )
    private val COVERAGE_PATHS = listOf(
        SETTINGS_FILE,
        "characters",
        CHATS_DIR,
        "worlds",
        "groups",
        "User Avatars",
        "QuickReplies",
        "secrets.json"
    )

    data class BackupProgress(
        val message: String,
        val percent: Int?
    )

    fun exportToUri(
        context: Context,
        uri: Uri,
        onProgress: (BackupProgress) -> Unit = {}
    ): Result<String> {
        return runCatching {
            val paths = AppPaths(context)
            val configFile = paths.configFile
            val dataDir = paths.dataDir
            val hasConfig = configFile.exists()
            val hasData = dataDir.exists() && dataDir.listFiles()?.isNotEmpty() == true
            if (!hasConfig && !hasData) {
                throw IllegalStateException("Nothing to export")
            }
            val totalBytes = (if (hasConfig) configFile.length() else 0L) +
                (if (hasData) totalRegularFileBytes(dataDir) else 0L)
            var copiedBytes = 0L
            var lastPercent = -1
            fun report(message: String, force: Boolean = false) {
                val percent = if (totalBytes > 0L) {
                    ((copiedBytes.coerceAtMost(totalBytes) * 100L) / totalBytes).toInt().coerceIn(0, 100)
                } else {
                    null
                }
                if (!force && percent != null && percent == lastPercent) return
                if (percent != null) {
                    lastPercent = percent
                }
                onProgress(BackupProgress(message = message, percent = percent))
            }

            report(context.getString(R.string.backup_progress_preparing_export), force = true)
            val manifest = buildExportManifest(
                appVersion = appVersionName(context),
                stCommit = NodePayload(context).readManifestInfo()?.stCommit,
                exportedAtEpochMs = System.currentTimeMillis(),
                configFile = configFile,
                dataDir = dataDir
            )
            context.contentResolver.openOutputStream(uri)?.use { output ->
                BufferedOutputStream(output).use { buffered ->
                    GZIPOutputStream(buffered).use { gz ->
                        TarArchiveOutputStream(gz).use { tar ->
                            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                            tar.setAddPaxHeadersForNonAsciiNames(true)
                            writeTarDirectory(tar, "$BACKUP_ROOT/")
                            writeTarText(
                                output = tar,
                                name = "$BACKUP_ROOT/$MANIFEST_FILE",
                                text = manifestToYaml(manifest)
                            )
                            if (hasConfig) {
                                writeTarFile(
                                    output = tar,
                                    name = "$BACKUP_ROOT/config.yaml",
                                    file = configFile
                                ) { copied ->
                                    copiedBytes += copied
                                    report(context.getString(R.string.backup_progress_exporting))
                                }
                            }
                            if (dataDir.exists()) {
                                writeTarDirectory(tar, "$BACKUP_ROOT/data/", sourceDir = dataDir)
                                writeTarTree(tar, dataDir, "$BACKUP_ROOT/data") { copied ->
                                    copiedBytes += copied
                                    report(context.getString(R.string.backup_progress_exporting))
                                }
                            }
                            tar.finish()
                        }
                    }
                }
            } ?: throw IllegalStateException("Unable to open destination")
            copiedBytes = totalBytes
            val completedMsg = context.getString(R.string.backup_progress_export_completed)
            report(completedMsg, force = true)
            completedMsg
        }
    }

    fun inspectImportUri(
        context: Context,
        uri: Uri
    ): Result<BackupImportPreview> {
        return runCatching {
            val paths = AppPaths(context)
            val importDir = File(paths.tmpDir, "import_preview")
            if (importDir.exists()) {
                importDir.deleteRecursively()
            }
            importDir.mkdirs()
            try {
                context.contentResolver.openInputStream(uri)?.use { raw ->
                    extractArchiveStream(BufferedInputStream(raw), importDir)
                } ?: throw IllegalStateException("Unable to open archive")
                inspectImportDirectory(importDir)
            } finally {
                importDir.deleteRecursively()
            }
        }
    }

    internal fun buildExportManifest(
        appVersion: String,
        stCommit: String?,
        exportedAtEpochMs: Long,
        configFile: File,
        dataDir: File
    ): BackupManifest {
        return BackupManifest(
            formatVersion = "1",
            appVersion = appVersion,
            stCommit = stCommit,
            exportedAt = Instant.ofEpochMilli(exportedAtEpochMs).toString(),
            configBytes = configFile.takeIf { it.exists() }?.length() ?: 0L,
            dataBytes = totalRegularFileBytes(dataDir),
            includesSecrets = File(dataDir, "$DEFAULT_USER_HANDLE/secrets.json").isFile
        )
    }

    internal fun manifestToYaml(manifest: BackupManifest): String {
        val map = linkedMapOf<String, Any?>(
            "format_version" to manifest.formatVersion,
            "app_version" to manifest.appVersion,
            "st_commit" to manifest.stCommit,
            "exported_at" to manifest.exportedAt,
            "config_bytes" to manifest.configBytes,
            "data_bytes" to manifest.dataBytes,
            "includes_secrets" to manifest.includesSecrets
        )
        return Yaml().dump(map)
    }

    internal fun inspectImportDirectory(importDir: File): BackupImportPreview {
        val configSrc = File(importDir, "config/config.yaml")
        val dataSrc = File(importDir, "data")
        val uiBackupRoot = File(importDir, UI_BACKUP_ROOT)
        val manifest = readManifest(File(importDir, MANIFEST_FILE))

        if (dataSrc.exists()) {
            val userRoot = detectSingleUserDataRoot(dataSrc)
            return buildImportPreview(
                kind = BackupImportKind.APP_BACKUP,
                hasConfig = configSrc.exists(),
                userHandle = DEFAULT_USER_HANDLE,
                userRoot = userRoot,
                manifest = manifest
            )
        }

        val uiRoot = if (uiBackupRoot.exists()) detectUiBackupRoot(uiBackupRoot) else null
        if (uiRoot != null) {
            return buildImportPreview(
                kind = BackupImportKind.ST_UI_USER_BACKUP,
                hasConfig = false,
                userHandle = DEFAULT_USER_HANDLE,
                userRoot = uiRoot,
                manifest = manifest
            )
        }

        if (configSrc.exists()) {
            return BackupImportPreview(
                kind = BackupImportKind.APP_BACKUP,
                hasConfig = true,
                userHandle = DEFAULT_USER_HANDLE,
                manifest = manifest,
                coverage = COVERAGE_PATHS.map { path ->
                    BackupCoverageItem(path, BackupCoverageStatus.MISSING, 0)
                },
                warningMessages = emptyList()
            )
        }

        throw IllegalStateException(
            "No recognizable data found in archive. " +
                    "Make sure you selected a valid SillyTavern backup (.tar.gz or .zip)."
        )
    }

    fun importFromUri(
        context: Context,
        uri: Uri,
        onProgress: (BackupProgress) -> Unit = {}
    ): Result<String> {
        return runCatching {
            val paths = AppPaths(context)
            val tmpRoot = paths.tmpDir
            val importDir = File(tmpRoot, "import")
            if (importDir.exists()) {
                importDir.deleteRecursively()
            }
            importDir.mkdirs()
            val totalBytes = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    afd.length.takeIf { it > 0L }
                }
            }.getOrNull()
            onProgress(BackupProgress(context.getString(R.string.backup_progress_preparing_import), null))

            val extractingMsg = context.getString(R.string.backup_progress_extracting)
            context.contentResolver.openInputStream(uri)?.use { raw ->
                val countingRaw = CountingInputStream(BufferedInputStream(raw))
                var lastPercent = -1
                var lastMessage = ""
                fun reportExtractProgress(message: String, force: Boolean) {
                    val percent = totalBytes?.let { total ->
                        ((countingRaw.bytesRead.coerceAtMost(total) * 100L) / total).toInt().coerceIn(0, 100)
                    }
                    if (!force && message == lastMessage && percent == lastPercent) {
                        return
                    }
                    lastMessage = message
                    if (percent != null) {
                        lastPercent = percent
                    }
                    onProgress(BackupProgress(message, percent))
                }
                reportExtractProgress(extractingMsg, true)
                extractArchiveStream(countingRaw, importDir) {
                    reportExtractProgress(extractingMsg, false)
                }
            } ?: throw IllegalStateException("Unable to open archive")
            onProgress(BackupProgress(context.getString(R.string.backup_progress_applying), null))

            inspectImportDirectory(importDir)
            val configSrc = File(importDir, "config/config.yaml")
            val uiBackupRoot = File(importDir, UI_BACKUP_ROOT)
            val initialDataSrc = File(importDir, "data")
            val dataSrc = when {
                initialDataSrc.exists() -> {
                    normalizeImportedDataRoot(initialDataSrc)
                    initialDataSrc
                }

                uiBackupRoot.exists() -> {
                    materializeUiBackup(uiBackupRoot, importDir)
                }

                else -> null
            }
            if (!configSrc.exists() && dataSrc == null) {
                throw IllegalStateException(
                    "No recognizable data found in archive. " +
                            "Make sure you selected a valid SillyTavern backup (.tar.gz or .zip)."
                )
            }
            val configDest = paths.configFile
            val dataDest = paths.dataDir

            // Atomic swap for data directory: rename old aside, rename new in.
            // renameTo within the same filesystem is atomic at the OS level,
            // so no partial state is visible even if the process is killed.
            if (dataSrc != null && dataSrc.exists()) {
                val oldDataDir = File(tmpRoot, "import_data_old")
                if (oldDataDir.exists()) oldDataDir.deleteRecursively()
                if (dataDest.exists()) {
                    if (!dataDest.renameTo(oldDataDir)) {
                        dataDest.deleteRecursively()
                    }
                }
                if (!dataSrc.renameTo(dataDest)) {
                    dataDest.parentFile?.mkdirs()
                    dataSrc.copyRecursively(dataDest, overwrite = true)
                }
                if (oldDataDir.exists()) oldDataDir.deleteRecursively()
            } else {
                dataDest.mkdirs()
            }

            // Atomic swap for config file.
            if (configSrc.exists()) {
                configDest.parentFile?.mkdirs()
                val oldConfig = File(tmpRoot, "import_config_old.yaml")
                if (oldConfig.exists()) oldConfig.delete()
                if (configDest.exists()) {
                    if (!configDest.renameTo(oldConfig)) {
                        configDest.delete()
                    }
                }
                if (!configSrc.renameTo(configDest)) {
                    configSrc.copyTo(configDest, overwrite = true)
                }
                if (oldConfig.exists()) oldConfig.delete()
            }

            importDir.deleteRecursively()
            val importCompleteMsg = context.getString(R.string.backup_progress_import_complete)
            onProgress(BackupProgress(importCompleteMsg, 100))
            importCompleteMsg
        }
    }

    private fun extractBackupFromZip(
        input: InputStream,
        destDir: File,
        onProgressTick: () -> Unit = {}
    ) {
        try {
            ZipArchiveInputStream(input).use { zis ->
                while (true) {
                    val archiveEntry = zis.nextEntry ?: break
                    val entry = archiveEntry as? ZipArchiveEntry ?: continue
                    if (entry.name.isNotEmpty()) {
                        val target = mapBackupPath(destDir, entry.name)
                        if (target != null) {
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { out -> zis.copyTo(out) }
                            }
                        }
                    }
                    onProgressTick()
                }
            }
        } catch (e: IOException) {
            throw IllegalStateException("Unable to read ZIP archive: ${e.message}", e)
        }
    }

    private fun extractArchiveStream(
        input: InputStream,
        destDir: File,
        onProgressTick: () -> Unit = {}
    ) {
        val pushback = PushbackInputStream(input, 2)
        val sig = ByteArray(2)
        val read = pushback.read(sig)
        if (read > 0) pushback.unread(sig, 0, read)
        when {
            read == 2 && sig[0] == 0x50.toByte() && sig[1] == 0x4B.toByte() ->
                extractBackupFromZip(pushback, destDir, onProgressTick)

            read == 2 && sig[0] == 0x1F.toByte() && sig[1] == 0x8B.toByte() ->
                extractBackup(GZIPInputStream(pushback), destDir, onProgressTick)

            else ->
                extractBackup(pushback, destDir, onProgressTick)
        }
    }

    private fun extractBackup(
        input: InputStream,
        destDir: File,
        onProgressTick: () -> Unit = {}
    ) {
        BufferedInputStream(input).use { stream ->
            TarArchiveInputStream(stream).use { tar ->
                while (true) {
                    val archiveEntry = tar.nextEntry ?: break
                    val entry = archiveEntry as? TarArchiveEntry ?: continue
                    val entryName = entry.name ?: continue
                    val target = mapBackupPath(destDir, entryName) ?: continue
                    when {
                        entry.isDirectory -> {
                            target.mkdirs()
                            onProgressTick()
                        }

                        entry.isFile -> {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { output ->
                                tar.copyTo(output)
                            }
                            onProgressTick()
                        }
                    }
                }
            }
        }
    }

    private fun mapBackupPath(destDir: File, rawName: String): File? {
        val clean = rawName.removePrefix("./")
        val stripped = if (clean.startsWith("$BACKUP_ROOT/")) {
            clean.removePrefix("$BACKUP_ROOT/")
        } else {
            clean
        }
        if (stripped.isEmpty() || stripped == "$BACKUP_ROOT" || stripped == "$BACKUP_ROOT/") return null
        val normalized = mapServerBackupPath(stripped) ?: "$UI_BACKUP_ROOT/$stripped"
        return TarUtils.resolveArchiveEntryName(destDir, normalized)
    }

    private fun mapServerBackupPath(path: String): String? {
        return when {
            path == MANIFEST_FILE -> MANIFEST_FILE
            path == "config.yaml" -> "config/config.yaml"
            path == "config/config.yaml" -> "config/config.yaml"
            path == "data" -> "data"
            path.startsWith("data/") -> path
            else -> null
        }
    }

    private fun buildImportPreview(
        kind: BackupImportKind,
        hasConfig: Boolean,
        userHandle: String,
        userRoot: File,
        manifest: BackupManifest?
    ): BackupImportPreview {
        val coverage = COVERAGE_PATHS.map { path ->
            val target = File(userRoot, path)
            val count = when {
                target.isFile -> 1
                target.isDirectory -> countRegularFiles(target)
                else -> 0
            }
            val present = target.exists() && (target.isFile || target.isDirectory)
            BackupCoverageItem(
                path = path,
                status = if (present) BackupCoverageStatus.PRESENT else BackupCoverageStatus.MISSING,
                count = count
            )
        }
        val warnings = buildList {
            val secrets = coverage.firstOrNull { it.path == "secrets.json" }
            if (kind == BackupImportKind.ST_UI_USER_BACKUP && secrets?.status == BackupCoverageStatus.MISSING) {
                add("This SillyTavern UI backup does not include secrets.json; API keys may need to be set again.")
            }
        }
        return BackupImportPreview(
            kind = kind,
            hasConfig = hasConfig,
            userHandle = userHandle,
            manifest = manifest,
            coverage = coverage,
            warningMessages = warnings
        )
    }

    private fun detectSingleUserDataRoot(dataDir: File): File {
        val defaultUserDir = File(dataDir, DEFAULT_USER_HANDLE)
        if (defaultUserDir.exists()) return defaultUserDir

        val candidates = dataDir.listFiles()
            ?.filterNot { it.name.startsWith(".") || it.name in DATA_ROOT_INFRA }
            .orEmpty()

        if (candidates.isEmpty()) return dataDir
        if (candidates.size != 1 || !candidates[0].isDirectory) {
            throw IllegalStateException(
                "This backup does not contain a '$DEFAULT_USER_HANDLE' profile and appears to be multi-user. " +
                        "Only single-user SillyTavern backups can be imported."
            )
        }
        return candidates[0]
    }

    private fun readManifest(file: File): BackupManifest? {
        if (!file.isFile) return null
        return runCatching {
            val map = file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                Yaml().load<Any?>(reader)
            } as? Map<*, *> ?: return null
            BackupManifest(
                formatVersion = map.stringValue("format_version").ifBlank { "1" },
                appVersion = map.stringValue("app_version"),
                stCommit = map.stringValue("st_commit").ifBlank { null },
                exportedAt = map.stringValue("exported_at"),
                configBytes = map.longValue("config_bytes"),
                dataBytes = map.longValue("data_bytes"),
                includesSecrets = map.booleanValue("includes_secrets")
            )
        }.getOrNull()
    }

    private fun normalizeImportedDataRoot(dataDir: File) {
        val defaultUserDir = File(dataDir, DEFAULT_USER_HANDLE)
        if (defaultUserDir.exists()) return

        val candidates = dataDir.listFiles()
            ?.filterNot { it.name.startsWith(".") || it.name in DATA_ROOT_INFRA }
            .orEmpty()

        if (candidates.isEmpty()) return
        if (candidates.size != 1 || !candidates[0].isDirectory) {
            throw IllegalStateException(
                "This backup does not contain a '$DEFAULT_USER_HANDLE' profile and appears to be multi-user. " +
                        "Only single-user SillyTavern backups can be imported."
            )
        }

        if (!candidates[0].renameTo(defaultUserDir)) {
            candidates[0].copyRecursively(defaultUserDir, overwrite = true)
            candidates[0].deleteRecursively()
        }
    }

    private fun materializeUiBackup(uiBackupRoot: File, importDir: File): File {
        val sourceRoot = detectUiBackupRoot(uiBackupRoot) ?: throw IllegalStateException(
            "No recognizable data found in archive. " +
                    "Expected a SillyTavern user backup containing '$SETTINGS_FILE' and a '$CHATS_DIR' directory."
        )
        val dataRoot = File(importDir, "data")
        val defaultUserDir = File(dataRoot, DEFAULT_USER_HANDLE)
        dataRoot.mkdirs()
        if (!sourceRoot.renameTo(defaultUserDir)) {
            sourceRoot.copyRecursively(defaultUserDir, overwrite = true)
        }
        return dataRoot
    }

    private fun detectUiBackupRoot(uiBackupRoot: File): File? {
        if (isUiBackupRoot(uiBackupRoot)) {
            return uiBackupRoot
        }
        val children = uiBackupRoot.listFiles()
            ?.filterNot { it.name.startsWith(".") }
            .orEmpty()
        if (children.size == 1 && children[0].isDirectory && isUiBackupRoot(children[0])) {
            return children[0]
        }
        return null
    }

    private fun isUiBackupRoot(dir: File): Boolean {
        return File(dir, SETTINGS_FILE).isFile && File(dir, CHATS_DIR).isDirectory
    }

    private fun writeTarTree(
        output: TarArchiveOutputStream,
        root: File,
        baseName: String,
        onBytesCopied: (Long) -> Unit = {}
    ) {
        val entries = root.listFiles() ?: return
        for (entry in entries) {
            val name = "$baseName/${entry.name}"
            if (entry.isDirectory) {
                writeTarDirectory(output, "$name/", sourceDir = entry)
                writeTarTree(output, entry, name, onBytesCopied)
            } else if (entry.isFile) {
                writeTarFile(output, name, entry, onBytesCopied)
            }
        }
    }

    private fun writeTarDirectory(
        output: TarArchiveOutputStream,
        name: String,
        sourceDir: File? = null
    ) {
        val normalized = if (name.endsWith("/")) name else "$name/"
        val entry = TarArchiveEntry(normalized).apply {
            mode = 493
            size = 0L
            modTime = Date(sourceDir?.lastModified()?.takeIf { it > 0L } ?: System.currentTimeMillis())
        }
        output.putArchiveEntry(entry)
        output.closeArchiveEntry()
    }

    private fun writeTarFile(
        output: TarArchiveOutputStream,
        name: String,
        file: File,
        onBytesCopied: (Long) -> Unit = {}
    ) {
        val entry = TarArchiveEntry(file, name).apply {
            mode = 420
        }
        output.putArchiveEntry(entry)
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                onBytesCopied(read.toLong())
            }
        }
        output.closeArchiveEntry()
    }

    private fun writeTarText(
        output: TarArchiveOutputStream,
        name: String,
        text: String
    ) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val entry = TarArchiveEntry(name).apply {
            mode = 420
            size = bytes.size.toLong()
            modTime = Date()
        }
        output.putArchiveEntry(entry)
        output.write(bytes)
        output.closeArchiveEntry()
    }

    private fun totalRegularFileBytes(root: File): Long {
        if (!root.exists()) return 0L
        if (root.isFile) return root.length()
        val children = root.listFiles() ?: return 0L
        var total = 0L
        for (child in children) {
            total += totalRegularFileBytes(child)
        }
        return total
    }

    private fun countRegularFiles(root: File): Int {
        if (!root.exists()) return 0
        if (root.isFile) return 1
        val children = root.listFiles() ?: return 0
        var total = 0
        for (child in children) {
            total += countRegularFiles(child)
        }
        return total
    }

    private fun appVersionName(context: Context): String {
        return runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "unknown"
        }.getOrElse { "unknown" }
    }

    private class CountingInputStream(
        private val delegate: InputStream
    ) : InputStream() {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val result = delegate.read()
            if (result >= 0) {
                bytesRead += 1
            }
            return result
        }

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            val result = delegate.read(buffer, off, len)
            if (result > 0) {
                bytesRead += result.toLong()
            }
            return result
        }

        override fun close() {
            delegate.close()
        }
    }

    // Tar parsing helpers live in TarUtils.
}

private fun Map<*, *>.stringValue(key: String): String {
    return this[key]?.toString().orEmpty()
}

private fun Map<*, *>.longValue(key: String): Long {
    return when (val value = this[key]) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }
}

private fun Map<*, *>.booleanValue(key: String): Boolean {
    return when (val value = this[key]) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        else -> false
    }
}
