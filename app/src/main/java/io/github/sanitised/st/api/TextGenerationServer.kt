package io.github.sanitised.st.api

import java.net.URI

fun resolveTextGenServer(settings: Map<String, Any?>, apiType: String): String {
    val server = when (apiType) {
        "featherless" -> "https://api.featherless.ai/v1"
        "mancer" -> "https://neuro.mancer.tech"
        "togetherai" -> "https://api.together.xyz"
        "infermaticai" -> "https://api.totalgpt.ai"
        "dreamgen" -> "https://dreamgen.com"
        "openrouter" -> "https://openrouter.ai/api"
        else -> settings
            .mapValue("textgenerationwebui_settings")
            .mapValue("server_urls")
            .stringValue(apiType)
    }
    return normalizeLocalhostServer(server)
}

private fun normalizeLocalhostServer(server: String): String {
    val uri = runCatching { URI(server) }.getOrNull() ?: return server
    if (!uri.host.equals("localhost", ignoreCase = true)) return server
    val authority = uri.rawAuthority ?: return server
    val hostStart = authority.lastIndexOf('@').let { if (it >= 0) it + 1 else 0 }
    val hostEnd = hostStart + uri.host.length
    val normalizedAuthority = authority.replaceRange(hostStart, hostEnd, "127.0.0.1")
    val authorityStart = server.indexOf(authority)
    if (authorityStart < 0) return server
    return server.replaceRange(authorityStart, authorityStart + authority.length, normalizedAuthority)
}

private fun Map<String, Any?>.mapValue(key: String): Map<String, Any?> =
    (this[key] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()

private fun Map<String, Any?>.stringValue(key: String): String =
    (this[key] as? String).orEmpty()
