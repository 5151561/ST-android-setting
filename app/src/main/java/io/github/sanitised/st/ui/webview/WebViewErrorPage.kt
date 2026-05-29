package io.github.sanitised.st.ui.webview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.R

enum class WebViewErrorKind {
    SERVICE_STOPPED,
    SERVICE_ERROR,
    SERVER_UNAVAILABLE,
    PAGE_LOAD_FAILED,
    RENDER_PROCESS_GONE
}

data class WebViewErrorState(
    val kind: WebViewErrorKind,
    val detail: String? = null
)

@Composable
fun WebViewErrorPage(
    error: WebViewErrorState,
    port: Int,
    onPrimaryAction: () -> Unit,
    onShowLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val title = when (error.kind) {
        WebViewErrorKind.SERVICE_STOPPED -> stringResource(R.string.webview_error_service_stopped_title)
        WebViewErrorKind.SERVICE_ERROR -> stringResource(R.string.webview_error_service_error_title)
        WebViewErrorKind.SERVER_UNAVAILABLE -> stringResource(R.string.webview_error_server_unavailable_title)
        WebViewErrorKind.PAGE_LOAD_FAILED -> stringResource(R.string.webview_error_page_failed_title)
        WebViewErrorKind.RENDER_PROCESS_GONE -> "运行时进程异常"
    }
    val body = when (error.kind) {
        WebViewErrorKind.SERVICE_STOPPED -> stringResource(R.string.webview_error_service_stopped_body)
        WebViewErrorKind.SERVICE_ERROR -> error.detail ?: stringResource(R.string.webview_error_service_error_body)
        WebViewErrorKind.SERVER_UNAVAILABLE -> stringResource(R.string.webview_error_server_unavailable_body, port)
        WebViewErrorKind.PAGE_LOAD_FAILED -> error.detail ?: stringResource(R.string.webview_error_page_failed_body)
        WebViewErrorKind.RENDER_PROCESS_GONE -> error.detail ?: "WebView 渲染进程异常终止，点击重试恢复聊天。"
    }
    val primaryLabel = when (error.kind) {
        WebViewErrorKind.SERVICE_STOPPED,
        WebViewErrorKind.SERVICE_ERROR -> stringResource(R.string.webview_start_service)
        WebViewErrorKind.SERVER_UNAVAILABLE,
        WebViewErrorKind.PAGE_LOAD_FAILED,
        WebViewErrorKind.RENDER_PROCESS_GONE -> stringResource(R.string.webview_retry)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = colors.secondary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = primaryLabel)
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(
                onClick = onShowLogs,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.webview_view_logs))
            }
        }
    }
}
