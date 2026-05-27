package io.github.sanitised.st.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.R

internal enum class STDialogButtonStyle {
    TEXT,
    FILLED
}

@Composable
internal fun STInfoCard(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun STSectionCard(
    title: String? = null,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    contentSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(contentSpacing)) {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
internal fun STConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: @Composable () -> Unit,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    buttonStyle: STDialogButtonStyle = STDialogButtonStyle.TEXT
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body,
        confirmButton = {
            when (buttonStyle) {
                STDialogButtonStyle.TEXT -> {
                    TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                        Text(confirmLabel)
                    }
                }

                STDialogButtonStyle.FILLED -> {
                    Button(onClick = onConfirm, enabled = confirmEnabled) {
                        Text(confirmLabel)
                    }
                }
            }
        },
        dismissButton = {
            when (buttonStyle) {
                STDialogButtonStyle.TEXT -> {
                    TextButton(onClick = onDismiss, enabled = dismissEnabled) {
                        Text(stringResource(R.string.cancel))
                    }
                }

                STDialogButtonStyle.FILLED -> {
                    Button(onClick = onDismiss, enabled = dismissEnabled) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    )
}

@Composable
internal fun STOperationProgressCard(
    title: String,
    details: String,
    progressPercent: Int?,
    showCancel: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            if (details.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            STProgressBlock(progressPercent = progressPercent, contentColor = contentColor)
            if (showCancel) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onCancel) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
internal fun STProgressBlock(
    progressPercent: Int?,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    if (progressPercent == null) {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { progressPercent.coerceIn(0, 100) / 100f },
            modifier = modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.percent_value, progressPercent),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor
        )
    }
}
