package io.github.sanitised.st.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import io.github.sanitised.st.api.CharacterUpload
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class PickedDocument(
    val fileName: String,
    val bytes: ByteArray
)

internal suspend fun Context.readPickedDocument(uri: Uri): PickedDocument {
    return withContext(Dispatchers.IO) {
        val fileName = documentDisplayName(uri)
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: throw IllegalStateException("Unable to read selected file")
        PickedDocument(fileName = fileName, bytes = bytes)
    }
}

internal suspend fun Context.writePickedDocument(uri: Uri, bytes: ByteArray) {
    withContext(Dispatchers.IO) {
        contentResolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
        } ?: throw IllegalStateException("Unable to write selected file")
    }
}

internal suspend fun Context.prepareCharacterAvatarUpload(
    upload: CharacterUpload,
    mode: CharacterAvatarProcessingMode
): CharacterUpload {
    return withContext(Dispatchers.IO) {
        if (!CharacterEditTools.shouldTranscodeAvatar(upload.fileName, mode)) {
            upload
        } else {
            CharacterUpload(
                fileName = CharacterEditTools.avatarOutputFileName(upload.fileName, mode),
                bytes = transcodeAvatarToPng(upload.bytes, mode)
            )
        }
    }
}

private fun transcodeAvatarToPng(bytes: ByteArray, mode: CharacterAvatarProcessingMode): ByteArray {
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalStateException("Unable to decode selected image")
    val processed = if (mode == CharacterAvatarProcessingMode.CENTER_CROP_PNG) {
        decoded.centerCropToAvatarRatio()
    } else {
        decoded
    }
    return ByteArrayOutputStream().use { output ->
        processed.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
}

private fun Bitmap.centerCropToAvatarRatio(): Bitmap {
    val targetRatio = 512f / 768f
    val sourceRatio = width.toFloat() / height.toFloat()
    val cropWidth: Int
    val cropHeight: Int
    if (sourceRatio > targetRatio) {
        cropHeight = height
        cropWidth = (height * targetRatio).roundToInt().coerceAtMost(width)
    } else {
        cropWidth = width
        cropHeight = (width / targetRatio).roundToInt().coerceAtMost(height)
    }
    val cropX = ((width - cropWidth) / 2).coerceAtLeast(0)
    val cropY = ((height - cropHeight) / 2).coerceAtLeast(0)
    val cropped = Bitmap.createBitmap(this, cropX, cropY, cropWidth, cropHeight)
    return Bitmap.createScaledBitmap(cropped, 512, 768, true)
}

private fun Context.documentDisplayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                val name = cursor.getString(index)
                if (!name.isNullOrBlank()) return name
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "character.png"
}
