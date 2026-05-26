package io.github.sanitised.st.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sanitised.st.AppPaths
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.R
import io.github.sanitised.st.ThemeMode
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.formatServiceLogLine
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class ReadyState {
    IDLE,
    CHECKING,
    READY,
    UNAVAILABLE
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatWebViewScreen(
    status: NodeStatus,
    target: WebViewTarget,
    themeMode: ThemeMode,
    onStartService: () -> Unit,
    onShowLogs: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val diagnosticScope = rememberCoroutineScope()
    val baseUrl = remember(status.port) { "http://127.0.0.1:${status.port}/" }
    val currentPort = rememberUpdatedState(status.port)
    val currentThemeMode = rememberUpdatedState(themeMode)
    val currentTarget = rememberUpdatedState(target)
    val logWebView: (String) -> Unit = remember(context, diagnosticScope) {
        { message ->
            diagnosticScope.launch(Dispatchers.IO) {
                appendWebViewLog(context, message)
            }
        }
    }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var readyState by remember { mutableStateOf(ReadyState.IDLE) }
    var pageError by remember { mutableStateOf<WebViewErrorState?>(null) }
    var retryNonce by rememberSaveable { mutableStateOf(0) }
    var startRequested by rememberSaveable { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        pendingFileCallback?.onReceiveValue(uris.toTypedArray())
        pendingFileCallback = null
    }

    val webView = remember(context) {
        WebView(context).apply {
            if (context.isAppDebuggable()) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
            logWebView("webview: create target=$target baseUrl=$baseUrl")
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            settings.disableSystemDarkening()
            addJavascriptInterface(
                STAndroidBridge(
                    context = context,
                    portProvider = { currentPort.value },
                    themeModeProvider = { currentThemeMode.value }
                ),
                "STAndroid"
            )
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress == 100) {
                        logWebView("webview: progress=100 url=${view?.url}")
                    }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    if (consoleMessage != null) {
                        logWebView(
                            "webview-console: ${consoleMessage.messageLevel()} " +
                                "${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} " +
                                consoleMessage.message()
                        )
                    }
                    return super.onConsoleMessage(consoleMessage)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    pendingFileCallback?.onReceiveValue(null)
                    pendingFileCallback = filePathCallback
                    val mimeTypes = fileChooserParams
                        ?.acceptTypes
                        ?.filter { it.isNotBlank() }
                        ?.toTypedArray()
                        ?.takeIf { it.isNotEmpty() }
                        ?: arrayOf("*/*")
                    filePickerLauncher.launch(mimeTypes)
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    pageError = null
                    logWebView("webview: page-started url=$url")
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    logWebView("webview: page-commit-visible url=$url")
                    view?.logDocumentState(logWebView)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.isSafeForEmbeddedWebView(currentPort.value)) return false
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    runCatching { context.startActivity(intent) }
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val finishedView = view ?: return
                    pageError = null
                    logWebView("webview: page-finished url=$url current=${finishedView.url}")
                    finishedView.logDocumentState(logWebView)
                    WebViewNavigator.injectAndroidRuntimeFlags(finishedView)
                    WebViewNavigator.navigateToTarget(finishedView, currentTarget.value)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    logWebView(
                        "webview-error: mainFrame=${request?.isForMainFrame} " +
                            "url=${request?.url} code=${error?.errorCode} " +
                            "description=${error?.description}"
                    )
                    if (request?.isForMainFrame == true) {
                        pageError = WebViewErrorState(
                            kind = WebViewErrorKind.PAGE_LOAD_FAILED,
                            detail = error?.description?.toString()
                        )
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    logWebView(
                        "webview-http-error: mainFrame=${request?.isForMainFrame} " +
                            "url=${request?.url} status=${errorResponse?.statusCode} " +
                            "reason=${errorResponse?.reasonPhrase}"
                    )
                    if (request?.isForMainFrame == true) {
                        pageError = WebViewErrorState(
                            kind = WebViewErrorKind.PAGE_LOAD_FAILED,
                            detail = errorResponse?.reasonPhrase
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(status.state) {
        if (status.state == NodeState.STOPPED && !startRequested) {
            startRequested = true
            onStartService()
        }
    }

    LaunchedEffect(status.state, status.port, retryNonce) {
        if (status.state != NodeState.RUNNING) {
            readyState = ReadyState.IDLE
            return@LaunchedEffect
        }
        readyState = ReadyState.CHECKING
        pageError = null
        val client = TavernCoreClient(baseUrl = baseUrl)
        val deadline = System.currentTimeMillis() + 60_000L
        while (System.currentTimeMillis() < deadline) {
            if (client.healthCheck().ok) {
                readyState = ReadyState.READY
                return@LaunchedEffect
            }
            delay(1000)
        }
        readyState = ReadyState.UNAVAILABLE
    }

    LaunchedEffect(webView, readyState, baseUrl, target, retryNonce) {
        if (readyState != ReadyState.READY || pageError != null) return@LaunchedEffect
        if (!webView.hasLoadedBaseUrl(baseUrl)) {
            webView.loadUrl(baseUrl)
        } else {
            WebViewNavigator.navigateToTarget(webView, target)
        }
    }

    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            onBackToHome()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    val error = when {
        status.state == NodeState.ERROR -> WebViewErrorState(
            kind = WebViewErrorKind.SERVICE_ERROR,
            detail = status.message.ifBlank { null }
        )
        status.state == NodeState.STOPPED && startRequested -> WebViewErrorState(WebViewErrorKind.SERVICE_STOPPED)
        readyState == ReadyState.UNAVAILABLE -> WebViewErrorState(WebViewErrorKind.SERVER_UNAVAILABLE)
        pageError != null -> pageError
        else -> null
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (error == null) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )
            }

            when {
                error != null -> WebViewErrorPage(
                    error = error,
                    port = status.port,
                    onPrimaryAction = {
                        pageError = null
                        if (status.state == NodeState.STOPPED || status.state == NodeState.ERROR) {
                            startRequested = true
                            onStartService()
                        } else {
                            retryNonce += 1
                            webView.reload()
                        }
                    },
                    onShowLogs = onShowLogs,
                    modifier = Modifier.fillMaxSize()
                )
                readyState != ReadyState.READY -> WebViewLoadingOverlay(
                    text = when (status.state) {
                        NodeState.STARTING -> stringResource(R.string.webview_loading_starting)
                        NodeState.RUNNING -> stringResource(R.string.webview_loading_connecting)
                        else -> stringResource(R.string.webview_loading_starting)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun WebViewLoadingOverlay(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

private fun Uri.isSafeForEmbeddedWebView(expectedPort: Int): Boolean {
    if (scheme == "about" || scheme == "data" || scheme == "blob") return true
    return scheme == "http" &&
        (host == "127.0.0.1" || host == "localhost") &&
        (port == -1 || port == expectedPort)
}

private fun WebView.hasLoadedBaseUrl(baseUrl: String): Boolean {
    val currentUrl = url ?: return false
    return currentUrl == baseUrl || currentUrl.startsWith(baseUrl)
}

private fun WebView.logDocumentState(logWebView: (String) -> Unit) {
    evaluateJavascript(
        """
        (function() {
          const body = document.body;
          const preloader = document.getElementById('preloader');
          const sheld = document.getElementById('sheld');
          const styleOf = (node, prop) => node ? getComputedStyle(node)[prop] : null;
          return JSON.stringify({
            href: location.href,
            readyState: document.readyState,
            title: document.title,
            bodyChildren: body ? body.children.length : -1,
            bodyText: body ? body.innerText.slice(0, 160) : null,
            preloaderDisplay: styleOf(preloader, 'display'),
            preloaderVisibility: styleOf(preloader, 'visibility'),
            sheldDisplay: styleOf(sheld, 'display'),
            sheldVisibility: styleOf(sheld, 'visibility'),
            scriptCount: document.scripts.length,
            moduleScriptCount: Array.from(document.scripts).filter((script) => script.type === 'module').length
          });
        })();
        """.trimIndent()
    ) { result ->
        logWebView("webview: document-state $result")
    }
}

private fun appendWebViewLog(context: Context, message: String) {
    val logsDir = AppPaths(context).logsDir
    if (!logsDir.exists()) {
        logsDir.mkdirs()
    }
    File(logsDir, "service.log").appendText(
        formatServiceLogLine(message),
        Charsets.UTF_8
    )
}

private fun Context.isAppDebuggable(): Boolean {
    return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

@Suppress("DEPRECATION")
private fun WebSettings.disableSystemDarkening() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        isAlgorithmicDarkeningAllowed = false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        forceDark = WebSettings.FORCE_DARK_OFF
    }
}
