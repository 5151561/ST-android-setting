package io.github.sanitised.st

import android.content.Context
import java.io.File

class AppPaths(private val context: Context) {
    val filesDir: File get() = context.filesDir
    val cacheDir: File get() = context.cacheDir

    val runtimeRootDir: File get() = runtimeRootDir(context)
    val stDir: File get() = File(filesDir, "st")
    val logsDir: File get() = File(filesDir, "logs")
    val runtimeLogFile: File get() = runtimeLogFile(context)
    val configDir: File get() = File(filesDir, "config")
    val configFile: File get() = File(configDir, "config.yaml")
    val dataDir: File get() = dataDir(context)
    val defaultContentDir: File get() = defaultContentDir(context)
    val tmpDir: File get() = File(filesDir, "tmp")
    val updatesDir: File get() = File(filesDir, "updates")
    val nodeTmpDir: File get() = File(cacheDir, "node_tmp")

    val npmDir: File get() = File(filesDir, "npm")

    val legacyAppDir: File get() = File(filesDir, "node/app")

    fun nodeBin(abi: String): File = File(filesDir, "node/bin/$abi/node")

    companion object {
        fun runtimeRootDir(context: Context): File = File(context.filesDir, "st-runtime")

        fun dataDir(context: Context): File {
            val primaryDataDir = File(context.filesDir, "data")
            val runtimeDataDir = File(runtimeRootDir(context), "data")
            return if (runtimeDataDir.exists() && !primaryDataDir.exists()) {
                runtimeDataDir
            } else {
                primaryDataDir
            }
        }

        fun defaultContentDir(context: Context): File =
            File(runtimeRootDir(context), "default/content")

        fun runtimeLogFile(context: Context): File =
            File(File(context.filesDir, "logs"), "service.log")
    }
}
