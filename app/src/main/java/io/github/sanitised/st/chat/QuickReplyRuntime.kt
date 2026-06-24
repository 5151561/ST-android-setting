package io.github.sanitised.st.chat

import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import java.io.File

sealed class QuickReplyExecution {
    data class Send(val text: String) : QuickReplyExecution()
    data class Draft(val text: String) : QuickReplyExecution()
    data class Unsupported(val reason: String) : QuickReplyExecution()
}

object QuickReplyRuntime {
    fun visibleReplies(dataRoot: File): List<QuickReplyItem> {
        val userDir = File(dataRoot, "default-user")
        val settingsFile = File(userDir, "settings.json")
        if (!settingsFile.exists()) return emptyList()
        val extensionSettings = runCatching {
            val root = Yaml().load<Any?>(settingsFile.readText(Charsets.UTF_8)) as? Map<*, *>
            val extensions = root?.get("extensions") as? Map<*, *>
            extensions?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()
        }.getOrElse { emptyMap() }
        val quickReplyDir = File(userDir, "QuickReplies")
        val setJsonByName = quickReplyDir
            .takeIf { it.isDirectory }
            ?.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .associate { file ->
                file.nameWithoutExtension to file.readText(Charsets.UTF_8)
            }
        return visibleReplies(extensionSettings, setJsonByName)
    }

    fun visibleReplies(
        extensionSettings: Map<String, Any?>,
        setJsonByName: Map<String, String>,
    ): List<QuickReplyItem> {
        val quickReplySettings = extensionSettings.mapValue("quickReply")
        if (quickReplySettings["quickReplyEnabled"] == false) return emptyList()
        return quickReplySettings.listValue("setList")
            .mapNotNull { it as? Map<*, *> }
            .filter { it["isVisible"] != false }
            .flatMap { setRef ->
                val name = setRef["name"]?.toString().orEmpty()
                val json = setJsonByName[name] ?: return@flatMap emptyList()
                parseSet(name, json)
            }
    }

    fun execute(item: QuickReplyItem): QuickReplyExecution {
        val message = item.message.trim()
        if (message.isBlank()) return QuickReplyExecution.Draft("")
        if (message.startsWith("/")) {
            return QuickReplyExecution.Unsupported("暂不支持原生执行 Slash Command: $message")
        }
        return if (item.disableSend) {
            QuickReplyExecution.Draft(message)
        } else {
            QuickReplyExecution.Send(message)
        }
    }

    private fun parseSet(setName: String, json: String): List<QuickReplyItem> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val setDisableSend = root.optBoolean("disableSend", false)
        val injectInput = root.optBoolean("injectInput", false)
        val placeBeforeInput = root.optBoolean("placeBeforeInput", false)
        val list = root.optJSONArray("qrList") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            if (item.optBoolean("isHidden", false)) return@mapNotNull null
            val label = item.optString("label").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            QuickReplyItem(
                setName = setName,
                label = label,
                icon = item.optString("icon"),
                message = item.optString("message"),
                disableSend = item.optBoolean("disableSend", setDisableSend),
                injectInput = item.optBoolean("injectInput", injectInput),
                placeBeforeInput = item.optBoolean("placeBeforeInput", placeBeforeInput),
            )
        }
    }

    private fun Map<String, Any?>.mapValue(key: String): Map<String, Any?> =
        (this[key] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()

    private fun Map<String, Any?>.listValue(key: String): List<Any?> =
        when (val value = this[key]) {
            is List<*> -> value.toList()
            is Array<*> -> value.toList()
            else -> emptyList()
        }
}
