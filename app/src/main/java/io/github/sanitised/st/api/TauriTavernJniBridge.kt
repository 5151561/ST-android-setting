package io.github.sanitised.st.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-local JNI bridge to the TauriTavern Rust backend (`libtauritavern_jni`).
 */
object TauriTavernJniBridge {

    interface Driver {
        fun init(dataRoot: String, defaultContentRoot: String): String
        fun invokeJson(command: String, payloadJson: String): String
        fun invokeBinary(command: String, metaJson: String, bytes: ByteArray): String
        fun exportCharacter(metaJson: String): ByteArray?
        fun startChatStream(requestJson: String): String
        fun readChatStream(streamId: String): String
        fun closeChatStream(streamId: String): String
    }

    private val libraryLoaded = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)

    @Volatile
    private var currentDataRoot: String = ""

    @Volatile
    private var currentDefaultContentRoot: String = ""

    @Volatile
    var driverOverride: Driver? = null

    private val nativeDriver = object : Driver {
        override fun init(dataRoot: String, defaultContentRoot: String): String {
            ensureLibraryLoaded()
            return nativeInit(dataRoot, defaultContentRoot)
        }

        override fun invokeJson(command: String, payloadJson: String): String {
            ensureLibraryLoaded()
            return nativeInvokeJson(command, payloadJson)
        }

        override fun invokeBinary(command: String, metaJson: String, bytes: ByteArray): String {
            ensureLibraryLoaded()
            return nativeInvokeBinary(command, metaJson, bytes)
        }

        override fun exportCharacter(metaJson: String): ByteArray? {
            ensureLibraryLoaded()
            return nativeExportCharacter(metaJson)
        }

        override fun startChatStream(requestJson: String): String {
            ensureLibraryLoaded()
            return nativeStartChatStream(requestJson)
        }

        override fun readChatStream(streamId: String): String {
            ensureLibraryLoaded()
            return nativeReadChatStream(streamId)
        }

        override fun closeChatStream(streamId: String): String {
            ensureLibraryLoaded()
            return nativeCloseChatStream(streamId)
        }
    }

    private fun activeDriver(): Driver = driverOverride ?: nativeDriver

    @Synchronized
    fun ensureLibraryLoaded(): Boolean {
        if (libraryLoaded.get()) return true
        return try {
            System.loadLibrary("tauritavern_jni")
            libraryLoaded.set(true)
            true
        } catch (t: Throwable) {
            val hostCandidates = listOf(
                File("rust/tt-android-jni/target/release/libtauritavern_jni.dylib"),
                File("rust/tt-android-jni/target/debug/libtauritavern_jni.dylib"),
                File("../rust/tt-android-jni/target/release/libtauritavern_jni.dylib"),
                File("../rust/tt-android-jni/target/debug/libtauritavern_jni.dylib"),
                File("rust/tt-android-jni/target/release/libtauritavern_jni.so"),
                File("rust/tt-android-jni/target/debug/libtauritavern_jni.so"),
                File("../rust/tt-android-jni/target/release/libtauritavern_jni.so"),
                File("../rust/tt-android-jni/target/debug/libtauritavern_jni.so"),
            )
            val found = hostCandidates.firstOrNull { it.exists() }
            if (found != null) {
                System.load(found.absolutePath)
                libraryLoaded.set(true)
                true
            } else {
                throw IllegalStateException("Failed to load libtauritavern_jni", t)
            }
        }
    }

    fun isInitialized(): Boolean = initialized.get()

    fun dataRoot(): String = currentDataRoot

    fun defaultContentRoot(): String = currentDefaultContentRoot

    suspend fun initialize(dataRoot: String, defaultContentRoot: String): JSONObject =
        withContext(Dispatchers.IO) {
            val raw = activeDriver().init(dataRoot, defaultContentRoot)
            val json = JSONObject(raw)
            if (!json.optBoolean("ok", false)) {
                throw IllegalStateException(json.optString("error", "TauriTavern JNI init failed"))
            }
            currentDataRoot = dataRoot
            currentDefaultContentRoot = defaultContentRoot
            initialized.set(true)
            json
        }

    suspend fun invokeJson(command: String, payload: JSONObject = JSONObject()): JSONObject =
        withContext(Dispatchers.IO) {
            val raw = activeDriver().invokeJson(command, payload.toString())
            val json = JSONObject(raw)
            if (!json.optBoolean("ok", false)) {
                throw IllegalStateException(
                    json.optString("error", "JNI command '$command' failed")
                )
            }
            json
        }

    suspend fun invokeBinary(
        command: String,
        meta: JSONObject = JSONObject(),
        bytes: ByteArray,
    ): JSONObject = withContext(Dispatchers.IO) {
        val raw = activeDriver().invokeBinary(command, meta.toString(), bytes)
        val json = JSONObject(raw)
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(
                json.optString("error", "JNI binary command '$command' failed")
            )
        }
        json
    }

    suspend fun exportCharacter(meta: JSONObject): ByteArray = withContext(Dispatchers.IO) {
        activeDriver().exportCharacter(meta.toString())
            ?: throw IllegalStateException("Character export returned null")
    }

    suspend fun startChatStream(request: JSONObject): String = withContext(Dispatchers.IO) {
        val raw = activeDriver().startChatStream(request.toString())
        val json = JSONObject(raw)
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("error", "Failed to start chat stream"))
        }
        val data = json.optJSONObject("data")
        data?.optString("stream_id")?.takeIf { it.isNotBlank() }
            ?: json.optString("stream_id").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing stream_id in startChatStream response")
    }

    suspend fun readChatStream(streamId: String): JSONObject = withContext(Dispatchers.IO) {
        val raw = activeDriver().readChatStream(streamId)
        val json = JSONObject(raw)
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("error", "Failed to read chat stream"))
        }
        json.optJSONObject("data") ?: json
    }

    suspend fun closeChatStream(streamId: String) = withContext(Dispatchers.IO) {
        runCatching { activeDriver().closeChatStream(streamId) }
    }

    @JvmStatic
    external fun nativeInit(dataRoot: String, defaultContentRoot: String): String

    @JvmStatic
    external fun nativeInvokeJson(command: String, payloadJson: String): String

    @JvmStatic
    external fun nativeInvokeBinary(command: String, metaJson: String, bytes: ByteArray): String

    @JvmStatic
    external fun nativeExportCharacter(metaJson: String): ByteArray?

    @JvmStatic
    external fun nativeStartChatStream(requestJson: String): String

    @JvmStatic
    external fun nativeReadChatStream(streamId: String): String

    @JvmStatic
    external fun nativeCloseChatStream(streamId: String): String
}
