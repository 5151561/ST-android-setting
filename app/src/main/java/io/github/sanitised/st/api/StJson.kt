package io.github.sanitised.st.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * TavernCore API 层的 JSON 编解码工具,基于 kotlinx.serialization。
 *
 * 解析结果保持与旧 SnakeYAML 路径相同的宽松形态(Map<String, Any?> / List<Any?> /
 * String / Boolean / Int / Long / Double),这样 TavernCoreApi 里的取值扩展函数
 * (stringValue()、intValue() 等)无需感知解析器切换。
 */
internal object StJson {
    /** 解析 JSON 文本;空串或非法 JSON 返回 null(对齐 yaml.load 后 `as? Map` 的防御式用法)。 */
    fun parse(body: String): Any? {
        if (body.isBlank()) return null
        return runCatching { Json.parseToJsonElement(body).toAny() }.getOrNull()
    }

    /** 将 Map/List/基本类型组成的树编码为 JSON 文本;null 值会保留为显式的 `"key":null`。 */
    fun encode(value: Any?): String = value.toJsonElement().toString()

    fun encodeObject(vararg pairs: Pair<String, Any?>): String =
        JsonObject(pairs.associate { (key, value) -> key to value.toJsonElement() }).toString()

    private fun JsonElement.toAny(): Any? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> primitiveToAny()
        is JsonArray -> map { it.toAny() }
        is JsonObject -> entries.associate { (key, value) -> key to value.toAny() }
    }

    // 数字分型对齐 SnakeYAML:整数按大小落到 Int/Long/BigInteger,小数落到 Double,
    // 保证 API 返回值在 chat 层等处做 `is Int` / `as? Number` 判断时行为不变。
    private fun JsonPrimitive.primitiveToAny(): Any? {
        if (isString) return content
        booleanOrNull?.let { return it }
        longOrNull?.let {
            return if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it
        }
        if (content.none { ch -> ch == '.' || ch == 'e' || ch == 'E' }) {
            content.toBigIntegerOrNull()?.let { return it }
        }
        return doubleOrNull ?: content
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is List<*> -> JsonArray(map { it.toJsonElement() })
        is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
        else -> JsonPrimitive(toString())
    }
}
