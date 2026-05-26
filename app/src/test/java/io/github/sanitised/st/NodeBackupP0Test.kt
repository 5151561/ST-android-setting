package io.github.sanitised.st

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NodeBackupP0Test {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun buildExportManifestRecordsVersionCommitAndDataSize() {
        val config = temp.newFile("config.yaml").apply {
            writeText("port: 8000\n", Charsets.UTF_8)
        }
        val data = temp.newFolder("data")
        FileTree(data)
            .file("default-user/settings.json", "{}")
            .file("default-user/secrets.json", "{}")
            .file("default-user/chats/Seraphina/hello.jsonl", "{\"mes\":\"hi\"}\n")

        val manifest = NodeBackup.buildExportManifest(
            appVersion = "0.4.0-dev",
            stCommit = "e3f41666c",
            exportedAtEpochMs = 1_748_246_400_000L,
            configFile = config,
            dataDir = data
        )

        assertEquals("1", manifest.formatVersion)
        assertEquals("0.4.0-dev", manifest.appVersion)
        assertEquals("e3f41666c", manifest.stCommit)
        assertEquals(17L, manifest.dataBytes)
        assertTrue(manifest.includesSecrets)
        assertTrue(NodeBackup.manifestToYaml(manifest).contains("st_commit: e3f41666c"))
    }

    @Test
    fun inspectImportDirectorySummarizesAppBackupWithManifest() {
        val importDir = temp.newFolder("import")
        FileTree(importDir)
            .file("manifest.yaml", "format_version: 1\napp_version: 0.4.0-dev\n")
            .file("config/config.yaml", "port: 8000\n")
            .file("data/default-user/settings.json", "{}")
            .file("data/default-user/secrets.json", "{}")
            .file("data/default-user/characters/Seraphina.png", "png")
            .file("data/default-user/chats/Seraphina/hello.jsonl", "{}")
            .file("data/default-user/worlds/Archive.json", "{}")
            .file("data/default-user/groups/group.json", "{}")
            .file("data/default-user/User Avatars/User.png", "png")
            .file("data/default-user/QuickReplies/main.json", "{}")

        val preview = NodeBackup.inspectImportDirectory(importDir)

        assertEquals(BackupImportKind.APP_BACKUP, preview.kind)
        assertTrue(preview.hasConfig)
        assertEquals("0.4.0-dev", preview.manifest?.appVersion)
        assertEquals("default-user", preview.userHandle)
        assertEquals(BackupCoverageStatus.PRESENT, preview.coverage.first { it.path == "settings.json" }.status)
        assertEquals(BackupCoverageStatus.PRESENT, preview.coverage.first { it.path == "secrets.json" }.status)
        assertTrue(preview.warningMessages.isEmpty())
    }

    @Test
    fun inspectImportDirectorySummarizesUiBackupAndWarnsWhenSecretsAreMissing() {
        val importDir = temp.newFolder("ui-import")
        FileTree(importDir)
            .file("ui_backup/settings.json", "{}")
            .file("ui_backup/characters/Seraphina.png", "png")
            .file("ui_backup/chats/Seraphina/hello.jsonl", "{}")
            .file("ui_backup/worlds/Archive.json", "{}")

        val preview = NodeBackup.inspectImportDirectory(importDir)

        assertEquals(BackupImportKind.ST_UI_USER_BACKUP, preview.kind)
        assertEquals("default-user", preview.userHandle)
        assertEquals(BackupCoverageStatus.PRESENT, preview.coverage.first { it.path == "settings.json" }.status)
        assertEquals(BackupCoverageStatus.MISSING, preview.coverage.first { it.path == "secrets.json" }.status)
        assertTrue(preview.warningMessages.any { it.contains("secrets.json") })
    }

    @Test
    fun inspectImportDirectoryRejectsMultiUserAppBackup() {
        val importDir = temp.newFolder("multi-user")
        FileTree(importDir)
            .file("data/alice/settings.json", "{}")
            .file("data/bob/settings.json", "{}")

        val error = runCatching { NodeBackup.inspectImportDirectory(importDir) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("multi-user"))
    }
}

private class FileTree(private val root: java.io.File) {
    fun file(path: String, text: String): FileTree {
        val file = java.io.File(root, path)
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
        return this
    }
}
