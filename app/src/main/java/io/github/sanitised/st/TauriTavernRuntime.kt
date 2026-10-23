package io.github.sanitised.st

import android.content.Context
import android.content.res.AssetManager
import android.os.Process
import io.github.sanitised.st.api.TauriTavernJniBridge
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Embedded TauriTavern Rust runtime lifecycle manager.
 *
 * Replaces the legacy local Node.js foreground service (`NodeService`) and archive extractor
 * (`NodePayload`) with in-process JNI initialization backed by `libtauritavern_jni.so`.
 */
object TauriTavernRuntime {
    const val CORE_LABEL: String = "TauriTavern Core (Rust)"
    const val ENGINE_LABEL: String = "Rust JNI (In-Process)"
    private const val ASSET_DEFAULT_CONTENT_PATH = "tt-default/content"
    private const val ASSET_TAURITAVERN_DEFAULT_PATH = "tauritavern_default"
    private const val ASSET_TAURITAVERN_DEFAULT_CONTENT_PATH = "tauritavern_default/content"
    private const val ASSET_TAURITAVERN_DEFAULT_CONFIG_PATH = "tauritavern_default/config.yaml"

    private val initMutex = Mutex()
    private val listeners = CopyOnWriteArraySet<NodeStatusListener>()

    private val _status = MutableStateFlow(
        NodeStatus(
            state = NodeState.STOPPED,
            message = "未初始化",
            port = DEFAULT_PORT,
            pid = null
        )
    )
    val status: StateFlow<NodeStatus> = _status.asStateFlow()

    fun registerListener(listener: NodeStatusListener) {
        listeners.add(listener)
        listener.onStatus(_status.value)
    }

    fun unregisterListener(listener: NodeStatusListener) {
        listeners.remove(listener)
    }

    suspend fun ensureInitialized(
        context: Context,
        forceReextractDefaultContent: Boolean = false
    ): Result<NodeStatus> = initMutex.withLock {
        val appContext = context.applicationContext ?: context
        if (
            !forceReextractDefaultContent &&
            _status.value.state == NodeState.RUNNING &&
            TauriTavernJniBridge.isInitialized()
        ) {
            return Result.success(_status.value)
        }

        updateStatus(
            NodeStatus(
                state = NodeState.STARTING,
                message = "正在初始化 TauriTavern Rust 核心…",
                port = DEFAULT_PORT,
                pid = currentPid()
            )
        )

        return withContext(Dispatchers.IO) {
            runCatching {
                val dataDir = AppPaths.dataDir(appContext).apply { mkdirs() }
                val defaultContentDir = AppPaths.defaultContentDir(appContext)
                val configDir = AppPaths(appContext).configDir.apply { mkdirs() }
                val logsDir = AppPaths(appContext).logsDir.apply { mkdirs() }
                File(dataDir, "default-user").mkdirs()

                if (forceReextractDefaultContent && defaultContentDir.exists()) {
                    defaultContentDir.deleteRecursively()
                }
                prepareDefaultContentDirectory(appContext.assets, defaultContentDir, configDir)

                appendLogInternal(
                    logsDir = logsDir,
                    message = "Initializing TauriTavern Rust core (dataDir=${dataDir.absolutePath}, defaultContentDir=${defaultContentDir.absolutePath}, configDir=${configDir.absolutePath})"
                )

                TauriTavernJniBridge.initialize(
                    dataRoot = dataDir.absolutePath,
                    defaultContentRoot = defaultContentDir.absolutePath
                )

                val readyStatus = NodeStatus(
                    state = NodeState.RUNNING,
                    message = "TauriTavern Rust 核心已就绪",
                    port = DEFAULT_PORT,
                    pid = currentPid()
                )
                updateStatus(readyStatus)
                appendLogInternal(logsDir, "TauriTavern Rust core ready (pid=${readyStatus.pid})")
                readyStatus
            }.onFailure { error ->
                val message = error.message ?: "TauriTavern Rust 核心初始化失败"
                val failedStatus = NodeStatus(
                    state = NodeState.ERROR,
                    message = message,
                    port = DEFAULT_PORT,
                    pid = null
                )
                updateStatus(failedStatus)
                runCatching {
                    appendLogInternal(
                        logsDir = AppPaths(appContext).logsDir,
                        message = "TauriTavern Rust core failed: $message"
                    )
                }
            }
        }
    }

    suspend fun restart(context: Context): Result<NodeStatus> {
        updateStatus(
            NodeStatus(
                state = NodeState.STARTING,
                message = "正在重新加载 TauriTavern Rust 核心…",
                port = DEFAULT_PORT,
                pid = currentPid()
            )
        )
        return ensureInitialized(context, forceReextractDefaultContent = false)
    }

    suspend fun reinitializeDefaultContent(context: Context): Result<NodeStatus> {
        return ensureInitialized(context, forceReextractDefaultContent = true)
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext ?: context
        val stopped = NodeStatus(
            state = NodeState.STOPPED,
            message = "TauriTavern Rust 核心已暂停",
            port = DEFAULT_PORT,
            pid = null
        )
        updateStatus(stopped)
        runCatching {
            appendLogInternal(AppPaths(appContext).logsDir, "TauriTavern Rust core paused by user")
        }
    }

    suspend fun appendLog(context: Context, message: String) {
        val appContext = context.applicationContext ?: context
        withContext(Dispatchers.IO) {
            appendLogInternal(AppPaths(appContext).logsDir, message)
        }
    }

    private fun updateStatus(newStatus: NodeStatus) {
        _status.value = newStatus
        listeners.forEach { listener ->
            runCatching { listener.onStatus(newStatus) }
        }
    }

    private fun currentPid(): Long? =
        runCatching { Process.myPid().toLong() }.getOrNull()?.takeIf { it > 0L }

    private fun prepareDefaultContentDirectory(
        assets: AssetManager,
        targetDir: File,
        configDir: File? = null
    ) {
        targetDir.mkdirs()

        // Self-heal legacy nested extraction where `tauritavern_default` was copied directly
        // into `default/content`, producing `default/content/content/...`.
        val nestedContentDir = File(targetDir, "content")
        if (nestedContentDir.isDirectory) {
            nestedContentDir.deleteRecursively()
        }

        // Copy default config.yaml into runtime default dir and user config dir if missing
        runCatching {
            assets.open(ASSET_TAURITAVERN_DEFAULT_CONFIG_PATH).use { input ->
                val defaultConfig = File(targetDir.parentFile ?: targetDir, "config.yaml")
                if (!defaultConfig.isFile || defaultConfig.length() == 0L) {
                    defaultConfig.parentFile?.mkdirs()
                    defaultConfig.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        if (configDir != null) {
            runCatching {
                val userConfig = File(configDir, "config.yaml")
                if (!userConfig.isFile || userConfig.length() == 0L) {
                    assets.open(ASSET_TAURITAVERN_DEFAULT_CONFIG_PATH).use { input ->
                        userConfig.parentFile?.mkdirs()
                        userConfig.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }

        val hasTauriTavernContentSubdir = runCatching {
            !assets.list(ASSET_TAURITAVERN_DEFAULT_CONTENT_PATH).isNullOrEmpty()
        }.getOrDefault(false)

        if (hasTauriTavernContentSubdir) {
            runCatching {
                copyAssetTreeIfPresent(assets, ASSET_TAURITAVERN_DEFAULT_CONTENT_PATH, targetDir)
            }
        } else {
            runCatching {
                copyAssetTreeIfPresent(assets, ASSET_TAURITAVERN_DEFAULT_PATH, targetDir)
            }
        }
        runCatching {
            copyAssetTreeIfPresent(assets, ASSET_DEFAULT_CONTENT_PATH, targetDir)
        }
    }

    private fun copyAssetTreeIfPresent(
        assets: AssetManager,
        assetPath: String,
        destDir: File
    ) {
        val children = runCatching { assets.list(assetPath) }.getOrNull() ?: return
        if (children.isEmpty()) {
            runCatching {
                assets.open(assetPath).use { input ->
                    if (destDir.isDirectory) {
                        destDir.deleteRecursively()
                    }
                    destDir.parentFile?.mkdirs()
                    if (!destDir.isFile || destDir.length() == 0L) {
                        destDir.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            return
        }
        destDir.mkdirs()
        for (child in children) {
            val childAssetPath = "$assetPath/$child"
            val childDest = File(destDir, child)
            val grandChildren = runCatching { assets.list(childAssetPath) }.getOrNull()
            if (grandChildren != null && grandChildren.isNotEmpty()) {
                copyAssetTreeIfPresent(assets, childAssetPath, childDest)
            } else {
                val copiedAsFile = runCatching {
                    assets.open(childAssetPath).use { input ->
                        if (childDest.isDirectory) {
                            childDest.deleteRecursively()
                        }
                        childDest.parentFile?.mkdirs()
                        if (!childDest.isFile || childDest.length() == 0L) {
                            childDest.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }.isSuccess
                if (!copiedAsFile && !childDest.exists()) {
                    childDest.mkdirs()
                }
            }
        }
    }

    private fun appendLogInternal(logsDir: File, message: String) {
        if (!logsDir.exists()) logsDir.mkdirs()
        val logFile = File(logsDir, "service.log")
        logFile.appendText(formatServiceLogLine(message), Charsets.UTF_8)
    }
}

/**
 * Lightweight metadata shim preserving compatibility with backup metadata & unit tests
 * after removing the legacy Node.js archive extractor.
 */
class NodePayload(private val context: Context) {
    companion object {
        internal fun selectBundledNodeAsset(
            supportedAbis: Array<String>,
            assetExists: (String) -> Boolean
        ): BundledNodeAsset? {
            for (abi in supportedAbis) {
                for (fileName in listOf("node", "libnode.so")) {
                    val assetPath = "node_payload/bin/$abi/$fileName"
                    if (assetExists(assetPath)) {
                        return BundledNodeAsset(abi = abi, assetPath = assetPath)
                    }
                }
            }
            return null
        }
    }

    data class BundledNodeAsset(
        val abi: String,
        val assetPath: String
    )

    data class ManifestInfo(
        val payloadVersion: String? = "tauritavern-rust",
        val stVersion: String? = "TauriTavern",
        val stCommit: String? = "rust-jni",
        val nodeVersion: String? = "Rust JNI",
        val nodeCommit: String? = null,
        val nodeTag: String? = "Rust JNI",
        val bundleSha256: String? = null
    )

    fun readManifestInfo(): ManifestInfo = ManifestInfo()
}
