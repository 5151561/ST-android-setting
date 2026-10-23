package io.github.sanitised.st.ui

import android.content.Context
import android.net.Uri
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import io.github.sanitised.st.AppPaths
import java.io.File
import java.net.URLDecoder

/**
 * Coil 3 [Interceptor] that intercepts legacy localhost (`127.0.0.1` / `localhost`) and relative
 * SillyTavern image URLs (`/characters/...`, `/User Avatars/...`, `/backgrounds/...`, `/thumbnail?...`)
 * and resolves them directly to on-device files under [AppPaths].
 */
class LocalTavernImageInterceptor(
    private val context: Context
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        val rawUrl = when (data) {
            is String -> data
            is Uri -> data.toString()
            is coil3.Uri -> data.toString()
            else -> null
        }
        if (!rawUrl.isNullOrBlank()) {
            val localFile = resolveLocalFile(context, rawUrl)
            if (localFile != null && localFile.exists() && localFile.isFile) {
                val updatedRequest = chain.request.newBuilder()
                    .data(localFile)
                    .build()
                return chain.withRequest(updatedRequest).proceed()
            }
        }
        return chain.proceed()
    }

    companion object {
        private const val DEFAULT_USER_HANDLE = "default-user"

        fun resolveLocalFile(context: Context, rawUrl: String): File? {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) return null

            if (trimmed.startsWith("file://")) {
                val path = runCatching { Uri.parse(trimmed).path }.getOrNull()
                return path?.let { File(it) }
            }

            val isHttp = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            if (isHttp) {
                val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
                val host = parsed.host?.lowercase().orEmpty()
                if (host != "127.0.0.1" && host != "localhost" && host != "0.0.0.0") {
                    return null
                }
                return resolvePathAndQuery(
                    context = context,
                    encodedPath = parsed.encodedPath.orEmpty(),
                    encodedQuery = parsed.encodedQuery.orEmpty()
                )
            }

            if (trimmed.startsWith("/")) {
                val qIdx = trimmed.indexOf('?')
                val pathPart = if (qIdx >= 0) trimmed.substring(0, qIdx) else trimmed
                val queryPart = if (qIdx >= 0) trimmed.substring(qIdx + 1) else ""
                return resolvePathAndQuery(
                    context = context,
                    encodedPath = pathPart,
                    encodedQuery = queryPart
                )
            }

            return null
        }

        private fun userRootDir(context: Context): File =
            File(AppPaths.dataDir(context), DEFAULT_USER_HANDLE)

        private fun resolvePathAndQuery(
            context: Context,
            encodedPath: String,
            encodedQuery: String
        ): File? {
            val userRoot = userRootDir(context)
            val decodedPath = decodePercent(encodedPath).trimStart('/')
            if (decodedPath.equals("thumbnail", ignoreCase = true)) {
                val params = parseQueryParams(encodedQuery)
                val type = params["type"]?.lowercase().orEmpty()
                val fileParam = params["file"]?.takeIf { it.isNotBlank() } ?: return null
                val safeFileName = File(fileParam).name
                return when (type) {
                    "avatar", "character", "characters" ->
                        File(File(userRoot, "characters"), safeFileName)
                    "persona", "user", "user_avatar" ->
                        File(File(userRoot, "User Avatars"), safeFileName)
                    "bg", "background", "backgrounds" ->
                        File(File(userRoot, "backgrounds"), safeFileName)
                    else -> File(File(userRoot, "characters"), safeFileName)
                }
            }

            return when {
                decodedPath.startsWith("characters/", ignoreCase = true) -> {
                    val rel = File(decodedPath.substringAfter('/')).name
                    File(File(userRoot, "characters"), rel)
                }
                decodedPath.startsWith("User Avatars/", ignoreCase = true) -> {
                    val rel = File(decodedPath.substringAfter('/')).name
                    File(File(userRoot, "User Avatars"), rel)
                }
                decodedPath.startsWith("backgrounds/", ignoreCase = true) -> {
                    val rel = File(decodedPath.substringAfter('/')).name
                    File(File(userRoot, "backgrounds"), rel)
                }
                decodedPath.startsWith("assets/", ignoreCase = true) -> {
                    val rel = decodedPath.substringAfter('/')
                    File(File(userRoot, "assets"), rel)
                }
                decodedPath.startsWith("img/", ignoreCase = true) -> {
                    val rel = File(decodedPath.substringAfter('/')).name
                    val candidateUser = File(File(userRoot, "img"), rel)
                    if (candidateUser.exists()) candidateUser
                    else File(File(AppPaths.defaultContentDir(context), "img"), rel)
                }
                else -> null
            }
        }

        private fun parseQueryParams(encodedQuery: String): Map<String, String> {
            if (encodedQuery.isBlank()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (pair in encodedQuery.split('&')) {
                if (pair.isBlank()) continue
                val eq = pair.indexOf('=')
                if (eq < 0) {
                    out[decodePercent(pair)] = ""
                } else {
                    val k = decodePercent(pair.substring(0, eq))
                    val v = decodePercent(pair.substring(eq + 1))
                    out[k] = v
                }
            }
            return out
        }

        private fun decodePercent(raw: String): String =
            runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }
}
