package io.github.sanitised.st.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import io.github.sanitised.st.api.CharacterUpload
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

internal suspend fun Context.prepareCharacterAvatarUpload(upload: CharacterUpload): CharacterUpload {
    return withContext(Dispatchers.IO) {
        if (!CharacterEditTools.shouldTranscodeAvatar(upload.fileName)) {
            upload
        } else {
            CharacterUpload(
                fileName = CharacterEditTools.avatarOutputFileName(upload.fileName),
                bytes = transcodeAvatarToPng(upload.bytes)
            )
        }
    }
}

private fun transcodeAvatarToPng(bytes: ByteArray): ByteArray {
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalStateException("Unable to decode selected image")
    return ByteArrayOutputStream().use { output ->
        decoded.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
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
