package io.github.sanitised.st.chat.prompt

import org.json.JSONObject

/** Extracts an incremental text delta from ST-proxied generation SSE JSON. */
object GenerationDeltaParser {

    fun extract(dataJson: String): String? {
        val obj = runCatching { JSONObject(dataJson) }.getOrNull() ?: return null

        obj.optJSONArray("choices")?.optJSONObject(0)?.let { choice ->
            choice.optJSONObject("delta")
                ?.stringOrNull("content")
                ?.let { return it }
            choice.stringOrNull("text")?.let { return it }
        }

        obj.optJSONObject("delta")
            ?.stringOrNull("text")
            ?.let { return it }

        obj.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.let { parts ->
                val text = buildString {
                    for (idx in 0 until parts.length()) {
                        parts.optJSONObject(idx)?.stringOrNull("text")?.let(::append)
                    }
                }
                if (text.isNotEmpty()) return text
            }

        obj.stringOrNull("content")?.let { return it }
        return null
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
}
