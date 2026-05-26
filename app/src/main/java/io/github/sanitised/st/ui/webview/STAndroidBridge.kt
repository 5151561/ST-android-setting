package io.github.sanitised.st.ui.webview

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.JavascriptInterface
import io.github.sanitised.st.ThemeMode
import org.json.JSONObject

class STAndroidBridge(
    private val context: Context,
    private val portProvider: () -> Int,
    private val themeModeProvider: () -> ThemeMode
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun getAppInfo(): String {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        return JSONObject()
            .put("platform", "android")
            .put("packageName", context.packageName)
            .put("versionName", packageInfo?.versionName)
            .toString()
    }

    @JavascriptInterface
    fun getRuntimeInfo(): String {
        val port = portProvider()
        return JSONObject()
            .put("host", "127.0.0.1")
            .put("port", port)
            .put("baseUrl", "http://127.0.0.1:$port/")
            .toString()
    }

    @JavascriptInterface
    fun getThemeMode(): String {
        return when (themeModeProvider()) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.AUTO -> "auto"
        }
    }

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        mainHandler.post {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("SillyTavern", text))
        }
    }

    @JavascriptInterface
    fun shareText(text: String) {
        mainHandler.post {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @JavascriptInterface
    fun setKeepScreenOn(enabled: Boolean) {
        mainHandler.post {
            val activity = context as? Activity ?: return@post
            if (enabled) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}
