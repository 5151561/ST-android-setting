package io.github.sanitised.st.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.ui.theme.STTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun CharacterAvatarImage(
    baseUrl: String,
    avatar: String?,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    localBytes: ByteArray? = null
) {
    val colors = STTheme.colors
    val image by produceState<ImageBitmap?>(initialValue = null, baseUrl, avatar, localBytes) {
        value = withContext(Dispatchers.IO) {
            localBytes?.decodeBitmap() ?: loadAvatarBitmap(baseUrl, avatar)
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surfaceWarm),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.fg2
            )
        }
    }
}

private fun ByteArray.decodeBitmap(): ImageBitmap? =
    BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()

private fun loadAvatarBitmap(baseUrl: String, avatar: String?): ImageBitmap? {
    if (avatar.isNullOrBlank()) return null
    return runCatching {
        val url = "${baseUrl.trimEnd('/')}/characters/${Uri.encode(avatar)}"
        val request = Request.Builder().url(url).get().build()
        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            bytes.decodeBitmap()
        }
    }.getOrNull()
}
