package io.github.sanitised.st.ui.navigation

import android.content.Context
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.res.stringResource
import io.github.sanitised.st.BackupCoverageStatus
import io.github.sanitised.st.BackupImportKind
import io.github.sanitised.st.BackupImportPreview
import io.github.sanitised.st.MainViewModel
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.R
import io.github.sanitised.st.ui.components.STConfirmDialog
import io.github.sanitised.st.ui.components.STDialogButtonStyle

internal sealed interface PendingDialog {
    object ResetToDefault : PendingDialog
    object RemoveUserData : PendingDialog
    data class RestoreSettingsSnapshot(val name: String) : PendingDialog
    data class CheckingImport(val uri: Uri) : PendingDialog
    data class ConfirmImport(
        val uri: Uri,
        val previewText: String,
        val canImport: Boolean
    ) : PendingDialog
}

internal fun pendingDialogStateSaver(): Saver<PendingDialog?, String> {
    return Saver(
        save = { dialog ->
            when (dialog) {
                null -> "none"
                PendingDialog.ResetToDefault -> "reset"
                PendingDialog.RemoveUserData -> "remove-data"
                is PendingDialog.RestoreSettingsSnapshot -> "restore-snapshot:${Uri.encode(dialog.name)}"
                is PendingDialog.CheckingImport -> "none"
                is PendingDialog.ConfirmImport -> "import:${Uri.encode(dialog.uri.toString())}:${dialog.canImport}:${Uri.encode(dialog.previewText)}"
            }
        },
        restore = { key ->
            when {
                key == "none" -> null
                key == "reset" -> PendingDialog.ResetToDefault
                key == "remove-data" -> PendingDialog.RemoveUserData
                key.startsWith("restore-snapshot:") -> PendingDialog.RestoreSettingsSnapshot(
                    Uri.decode(key.removePrefix("restore-snapshot:"))
                )
                key.startsWith("import:") -> PendingDialog.ConfirmImport(
                    uri = Uri.parse(Uri.decode(key.removePrefix("import:").split(":", limit = 3).getOrElse(0) { "" })),
                    canImport = key.removePrefix("import:").split(":", limit = 3).getOrElse(1) { "false" }.toBoolean(),
                    previewText = Uri.decode(key.removePrefix("import:").split(":", limit = 3).getOrElse(2) { "" })
                )
                else -> null
            }
        }
    )
}

/** 备份/恢复等全局确认对话框:独立作用域,弹窗状态变化不再牵动根作用域。 */
@Composable
internal fun STAppDialogs(
    pendingDialogState: MutableState<PendingDialog?>,
    statusState: State<NodeStatus>,
    viewModel: MainViewModel,
) {
    when (val dialog = pendingDialogState.value) {
        PendingDialog.ResetToDefault -> {
            STConfirmDialog(
                title = stringResource(R.string.dialog_reset_title),
                confirmLabel = stringResource(R.string.reset),
                onConfirm = {
                    pendingDialogState.value = null
                    viewModel.resetToDefault()
                },
                onDismiss = { pendingDialogState.value = null },
                body = { Text(text = stringResource(R.string.dialog_reset_body)) },
                buttonStyle = STDialogButtonStyle.FILLED
            )
        }

        PendingDialog.RemoveUserData -> {
            STConfirmDialog(
                title = stringResource(R.string.dialog_remove_data_title),
                confirmLabel = stringResource(R.string.remove),
                onConfirm = {
                    pendingDialogState.value = null
                    viewModel.removeUserData()
                },
                onDismiss = { pendingDialogState.value = null },
                body = { Text(text = stringResource(R.string.dialog_remove_data_body)) },
                buttonStyle = STDialogButtonStyle.FILLED
            )
        }

        is PendingDialog.RestoreSettingsSnapshot -> {
            STConfirmDialog(
                title = stringResource(R.string.settings_snapshot_restore_title),
                confirmLabel = stringResource(R.string.settings_snapshot_restore),
                onConfirm = {
                    val snapshotName = dialog.name
                    pendingDialogState.value = null
                    viewModel.restoreSettingsSnapshot(statusState.value.port, snapshotName)
                },
                onDismiss = { pendingDialogState.value = null },
                body = {
                    Text(text = stringResource(R.string.settings_snapshot_restore_body, dialog.name))
                },
                buttonStyle = STDialogButtonStyle.FILLED
            )
        }

        is PendingDialog.CheckingImport -> {
            STConfirmDialog(
                title = stringResource(R.string.dialog_import_title),
                confirmLabel = stringResource(R.string.import_action),
                onConfirm = {},
                onDismiss = { pendingDialogState.value = null },
                body = { Text(text = stringResource(R.string.dialog_import_checking_body)) },
                confirmEnabled = false,
                buttonStyle = STDialogButtonStyle.FILLED
            )
        }

        is PendingDialog.ConfirmImport -> {
            STConfirmDialog(
                title = stringResource(R.string.dialog_import_title),
                confirmLabel = stringResource(R.string.import_action),
                onConfirm = {
                    val importUri = dialog.uri
                    pendingDialogState.value = null
                    viewModel.import(importUri)
                },
                onDismiss = { pendingDialogState.value = null },
                body = { Text(text = dialog.previewText) },
                confirmEnabled = dialog.canImport,
                buttonStyle = STDialogButtonStyle.FILLED
            )
        }

        null -> Unit
    }
}

internal fun Context.backupImportPreviewText(preview: BackupImportPreview): String {
    val kindLabel = when (preview.kind) {
        BackupImportKind.APP_BACKUP -> getString(R.string.backup_precheck_kind_app)
        BackupImportKind.ST_UI_USER_BACKUP -> getString(R.string.backup_precheck_kind_ui)
    }
    val configLine = if (preview.hasConfig) {
        getString(R.string.backup_precheck_config_present)
    } else {
        getString(R.string.backup_precheck_config_missing)
    }
    val manifestLine = preview.manifest?.let { manifest ->
        getString(
            R.string.backup_precheck_manifest,
            manifest.appVersion.ifBlank { getString(R.string.unknown_short) },
            manifest.stCommit ?: getString(R.string.unknown_short)
        )
    }
    val coverageLines = preview.coverage.joinToString(separator = "\n") { item ->
        val label = backupCoverageLabel(item.path)
        when (item.status) {
            BackupCoverageStatus.PRESENT ->
                getString(R.string.backup_precheck_item_present, label, item.count)
            BackupCoverageStatus.MISSING ->
                getString(R.string.backup_precheck_item_missing, label)
        }
    }
    val warningLines = preview.warningMessages.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n") { warning ->
        val localized = if (warning.contains("secrets.json")) {
            getString(R.string.backup_precheck_warning_secrets)
        } else {
            warning
        }
        getString(R.string.backup_precheck_warning_item, localized)
    }

    return buildString {
        appendLine(getString(R.string.dialog_import_body))
        appendLine(getString(R.string.backup_precheck_snapshot_advice))
        appendLine()
        appendLine(getString(R.string.backup_precheck_type, kindLabel))
        appendLine(getString(R.string.backup_precheck_user, preview.userHandle))
        appendLine(configLine)
        if (manifestLine != null) appendLine(manifestLine)
        appendLine()
        appendLine(getString(R.string.backup_precheck_coverage))
        appendLine(coverageLines)
        if (warningLines != null) {
            appendLine()
            appendLine(getString(R.string.backup_precheck_warnings))
            append(warningLines)
        }
    }.trim()
}

private fun Context.backupCoverageLabel(path: String): String {
    return when (path) {
        "settings.json" -> getString(R.string.backup_precheck_path_settings)
        "characters" -> getString(R.string.backup_precheck_path_characters)
        "chats" -> getString(R.string.backup_precheck_path_chats)
        "worlds" -> getString(R.string.backup_precheck_path_worlds)
        "groups" -> getString(R.string.backup_precheck_path_groups)
        "User Avatars" -> getString(R.string.backup_precheck_path_user_avatars)
        "QuickReplies" -> getString(R.string.backup_precheck_path_quick_replies)
        "secrets.json" -> getString(R.string.backup_precheck_path_secrets)
        else -> path
    }
}
