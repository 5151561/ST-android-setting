package io.github.sanitised.st.api

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal data class SettingsPayload(
    val response: Map<String, Any?>,
    val settings: Map<String, Any?>
)

internal fun TavernCoreClient.postJson(path: String, json: String): String {
    val builder = Request.Builder()
        .url(normalizedBaseUrl + path.removePrefix("/"))
        .post(json.toRequestBody(TavernCoreClient.jsonMediaType))
    csrfToken().takeIf { it.isNotBlank() }?.let { token ->
        builder.header("x-csrf-token", token)
    }
    val request = builder.build()
    httpClient.newCall(request).execute().use { response ->
        val body = response.body.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("SillyTavern API ${response.code}: $body")
        }
        return body
    }
}

internal fun TavernCoreClient.getJson(path: String): String {
    val builder = Request.Builder()
        .url(normalizedBaseUrl + path.removePrefix("/"))
        .get()
    csrfToken().takeIf { it.isNotBlank() }?.let { token ->
        builder.header("x-csrf-token", token)
    }
    httpClient.newCall(builder.build()).execute().use { response ->
        val body = response.body.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("SillyTavern API ${response.code}: $body")
        }
        return body
    }
}

/** Like [postJson] but uses the long-timeout generation client (slow model replies). */
internal fun TavernCoreClient.postJsonForGeneration(path: String, json: String): String {
    val builder = Request.Builder()
        .url(normalizedBaseUrl + path.removePrefix("/"))
        .post(json.toRequestBody(TavernCoreClient.jsonMediaType))
    csrfToken().takeIf { it.isNotBlank() }?.let { token ->
        builder.header("x-csrf-token", token)
    }
    generationHttpClient.newCall(builder.build()).execute().use { response ->
        val body = response.body.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("SillyTavern API ${response.code}: $body")
        }
        return body
    }
}

internal fun TavernCoreClient.postMultipart(path: String, body: MultipartBody): String {
    val builder = Request.Builder()
        .url(normalizedBaseUrl + path.removePrefix("/"))
        .post(body)
    csrfToken().takeIf { it.isNotBlank() }?.let { token ->
        builder.header("x-csrf-token", token)
    }
    val request = builder.build()
    httpClient.newCall(request).execute().use { response ->
        val responseBody = response.body.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("SillyTavern API ${response.code}: $responseBody")
        }
        return responseBody
    }
}

internal fun TavernCoreClient.postJsonFile(
    path: String,
    json: String,
    fallbackFileName: String
): CharacterExportFile {
    val builder = Request.Builder()
        .url(normalizedBaseUrl + path.removePrefix("/"))
        .post(json.toRequestBody(TavernCoreClient.jsonMediaType))
    csrfToken().takeIf { it.isNotBlank() }?.let { token ->
        builder.header("x-csrf-token", token)
    }
    val request = builder.build()
    httpClient.newCall(request).execute().use { response ->
        val bytes = response.body.bytes()
        if (!response.isSuccessful) {
            throw IllegalStateException("SillyTavern API ${response.code}: ${bytes.toString(Charsets.UTF_8)}")
        }
        return CharacterExportFile(
            fileName = response.header("Content-Disposition")
                ?.contentDispositionFileName()
                ?.ifBlank { fallbackFileName }
                ?: fallbackFileName,
            contentType = response.header("Content-Type")?.substringBefore(';') ?: "application/octet-stream",
            bytes = bytes
        )
    }
}

internal fun TavernCoreClient.csrfToken(): String {
    cachedCsrfToken?.let { return it }
    return runCatching {
        val request = Request.Builder()
            .url(normalizedBaseUrl + "csrf-token")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching ""
            val body = response.body.string().orEmpty()
            val token = (StJson.parse(body) as? Map<*, *>)
                ?.stringValue("token")
                .orEmpty()
            cachedCsrfToken = token
            token
        }
    }.getOrDefault("")
}

internal fun TavernCoreClient.fetchSettings(): SettingsPayload {
    val body = postJson("api/settings/get", "{}")
    val response = (StJson.parse(body) as? Map<*, *>)
        ?.toStringKeyMap()
        ?: emptyMap()
    val settings = (StJson.parse(response.stringAnyValue("settings")) as? Map<*, *>)
        ?.toStringKeyMap()
        ?: emptyMap()
    return SettingsPayload(response = response, settings = settings)
}

internal fun TavernCoreClient.saveSettingsInternal(settings: Map<String, Any?>) {
    postJson(
        path = "api/settings/save",
        json = jsonValue(settings)
    )
}

internal fun Map<*, *>?.stringValue(key: String): String =
    (this?.get(key) as? String).orEmpty()

internal fun Map<*, *>?.longValue(key: String): Long =
    when (val value = this?.get(key)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

internal fun Map<*, *>?.doubleValue(key: String, default: Double): Double =
    when (val value = this?.get(key)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: default
        else -> default
    }

internal fun Map<*, *>?.intValue(key: String, default: Int): Int =
    when (val value = this?.get(key)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }

internal fun Map<*, *>?.booleanValue(key: String): Boolean =
    when (val value = this?.get(key)) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        else -> false
    }

internal fun Map<*, *>?.stringListValue(key: String): List<String> =
    when (val value = this?.get(key)) {
        is List<*> -> value.mapNotNull { it as? String }
        is String -> value.split(',').map { it.trim() }.filter { it.isNotBlank() }
        else -> emptyList()
    }

internal fun Map<*, *>?.mapValue(key: String): Map<*, *> =
    this?.get(key) as? Map<*, *> ?: emptyMap<Any, Any>()

internal fun Map<String, Any?>.anyMapValue(key: String): Map<String, Any?> =
    (this[key] as? Map<*, *>)?.toStringKeyMap() ?: emptyMap()

internal fun Map<String, Any?>.listMapValue(key: String): List<Map<String, Any?>> =
    (this[key] as? List<*>)
        ?.mapNotNull { it as? Map<*, *> }
        ?.map { it.toStringKeyMap() }
        ?: emptyList()

internal fun Map<*, *>.toStringKeyMap(): Map<String, Any?> =
    entries.associate { (key, value) -> key.toString() to normalizeJsonValue(value) }

internal fun normalizeJsonValue(value: Any?): Any? =
    when (value) {
        is Map<*, *> -> value.toStringKeyMap()
        is List<*> -> value.map { normalizeJsonValue(it) }
        else -> value
    }

internal fun Map<String, Any?>.toSTTag(): STTag =
    STTag(
        id = stringAnyValue("id"),
        name = stringAnyValue("name"),
        color = stringAnyValue("color"),
        isFolder = stringAnyValue("folder_type").isNotBlank() || booleanAnyValue("is_folder"),
        sortOrder = intAnyValue("sort_order", 0)
    )

internal fun Map<String, Any?>.stringAnyValue(key: String): String =
    (get(key) as? String).orEmpty()

internal fun Map<String, Any?>.booleanAnyValue(key: String): Boolean =
    when (val value = get(key)) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        else -> false
    }

internal fun Map<String, Any?>.intAnyValue(key: String, default: Int): Int =
    when (val value = get(key)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }

internal fun Map<String, Any?>.longAnyValue(key: String): Long =
    when (val value = get(key)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

internal fun Any?.toStringList(): List<String> =
    when (this) {
        is List<*> -> mapNotNull { it as? String }
        is String -> split(',').map { it.trim() }.filter { it.isNotBlank() }
        else -> emptyList()
    }

internal fun String.contentDispositionFileName(): String {
    val token = "filename=\""
    val start = indexOf(token)
    if (start < 0) return ""
    return substring(start + token.length).substringBefore('"')
}

internal fun jsonObject(vararg pairs: Pair<String, Any?>): String =
    StJson.encodeObject(*pairs)

internal fun jsonArray(values: Iterable<Any?>): String =
    StJson.encode(values.toList())

internal fun jsonValue(value: Any?): String =
    StJson.encode(value)

internal fun quoteJson(value: String): String =
    StJson.encode(value)

internal class InMemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this.cookies) {
            this.cookies.removeAll { existing ->
                cookies.any { it.name == existing.name && it.domain == existing.domain && it.path == existing.path }
            }
            this.cookies.addAll(cookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(cookies) {
            val validCookies = cookies.filter { it.expiresAt > System.currentTimeMillis() }
            cookies.retainAll(validCookies.toSet())
            return validCookies.filter { it.matches(url) }
        }
    }

    fun clear() {
        synchronized(cookies) { cookies.clear() }
    }
}
