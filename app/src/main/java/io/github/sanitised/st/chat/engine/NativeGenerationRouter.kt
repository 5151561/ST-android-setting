package io.github.sanitised.st.chat.engine

import io.github.sanitised.st.chat.prompt.TextPromptBuilder

data class NativeGenerationRoute(
    val mode: NativeEngineMode,
    val api: String,
    val source: String,
    val settings: Map<String, Any?>
)

object NativeGenerationRouter {
    fun route(settings: Map<String, Any?>, authorsNote: String = ""): NativeGenerationRoute {
        return when (val mainApi = settings["main_api"] as? String) {
            "openai" -> NativeGenerationRoute(
                mode = NativeEngineMode.CHAT_COMPLETION,
                api = "openai",
                source = settings.mapValue("oai_settings")
                    .stringValue("chat_completion_source")
                    .ifBlank { "openai" },
                settings = settings
            )
            "textgenerationwebui" -> textCompletionRoute(
                api = "textgenerationwebui",
                source = settings.mapValue("textgenerationwebui_settings")
                    .stringValue("type")
                    .ifBlank { "ooba" },
                settings = settings,
                authorsNote = authorsNote
            )
            "kobold" -> textCompletionRoute(
                api = "kobold",
                source = "kobold",
                settings = settings.withTextCompletionType("kobold"),
                authorsNote = authorsNote
            )
            "koboldhorde" -> textCompletionRoute(
                api = "koboldhorde",
                source = "koboldhorde",
                settings = settings.withTextCompletionType("koboldhorde"),
                authorsNote = authorsNote
            )
            "novel" -> textCompletionRoute(
                api = "novel",
                source = "novelai",
                settings = settings.withTextCompletionType("novelai"),
                authorsNote = authorsNote
            )
            else -> NativeGenerationRoute(
                mode = NativeEngineMode.UNSUPPORTED,
                api = mainApi.orEmpty(),
                source = "",
                settings = settings
            )
        }
    }

    private fun textCompletionRoute(
        api: String,
        source: String,
        settings: Map<String, Any?>,
        authorsNote: String,
    ): NativeGenerationRoute =
        NativeGenerationRoute(
            mode = if (TextPromptBuilder.supports(settings, authorsNote)) {
                NativeEngineMode.TEXT_COMPLETION
            } else {
                NativeEngineMode.UNSUPPORTED
            },
            api = api,
            source = source,
            settings = settings
        )

    private fun Map<String, Any?>.withTextCompletionType(type: String): Map<String, Any?> {
        val updated = toMutableMap()
        val textGen = mapValue("textgenerationwebui_settings").toMutableMap()
        textGen["type"] = type
        updated["main_api"] = "textgenerationwebui"
        updated["textgenerationwebui_settings"] = textGen
        return updated
    }
}

internal fun Map<String, Any?>.mapValue(key: String): Map<String, Any?> =
    (this[key] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()

internal fun Map<String, Any?>.stringValue(key: String): String =
    this[key]?.toString().orEmpty()
