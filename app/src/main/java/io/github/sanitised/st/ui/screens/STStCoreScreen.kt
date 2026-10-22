package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.BackupOperationAnchor
import io.github.sanitised.st.CustomOperationAnchor
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.SettingsSnapshot
import kotlinx.coroutines.delay

@Composable
fun STStCoreScreen(
    status: NodeStatus,
    stLabel: String,
    nodeLabel: String,
    isCustomInstalled: Boolean,
    customInstalledLabel: String?,
    busyMessage: String,
    settingsSnapshots: List<SettingsSnapshot>,
    settingsSnapshotsLoading: Boolean,
    settingsSnapshotMessage: String,
    showBackupOperationCard: Boolean,
    backupOperationTitle: String,
    backupOperationDetails: String,
    backupOperationProgressPercent: Int?,
    backupOperationAnchor: BackupOperationAnchor?,
    showCustomOperationCard: Boolean,
    customOperationTitle: String,
    customOperationDetails: String,
    customOperationProgressPercent: Int?,
    customOperationCancelable: Boolean,
    customOperationAnchor: CustomOperationAnchor?,
    onBack: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenBrowser: () -> Unit,
    onShowLogs: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRefreshSettingsSnapshots: () -> Unit,
    onCreateSettingsSnapshot: () -> Unit,
    onRestoreSettingsSnapshot: (String) -> Unit,
    onLoadCustomZip: () -> Unit,
    onResetToDefault: () -> Unit,
    onRemoveUserData: () -> Unit,
    onCancelCustomOperation: () -> Unit,
    autoStartService: Boolean,
    onAutoStartServiceChanged: (Boolean) -> Unit,
    autoOpenBrowser: Boolean,
    onAutoOpenBrowserChanged: (Boolean) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val running = status.state == NodeState.RUNNING
    val starting = status.state == NodeState.STARTING

    // Live Uptime calculation
    var uptimeSec by remember(running) { mutableStateOf(0) }
    LaunchedEffect(running) {
        if (running) {
            uptimeSec = 0
            while (true) {
                delay(1000)
                uptimeSec++
            }
        } else {
            uptimeSec = 0
        }
    }
    val uptimeText = if (running) {
        val min = uptimeSec / 60
        val sec = uptimeSec % 60
        if (min > 0) "${min} 分 ${sec} 秒" else "${sec} 秒"
    } else {
        "—"
    }

    var allowBackgroundRun by remember { mutableStateOf(true) }

    STBackRoot(title = "ST 核心", onBack = onBack, modifier = modifier, actions = {
        STIconButton(Icons.Filled.MoreVert, "更多", { onShowMessage("更多内核操作功能开发中") })
    }) {
        STStStatusHero(status, stLabel, nodeLabel, onStartService, onStopService, onOpenBrowser)
        
        if (running || starting) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("进程 PID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (running) "${status.pid ?: "—"}" else "—",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("运行时长", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(uptimeText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("监听地址", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("127.0.0.1:${status.port}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("健康检查", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (running) Icons.Filled.CheckCircle else Icons.Filled.Face,
                                contentDescription = null,
                                tint = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (running) "已就绪" else "等待 HTTP 200…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (busyMessage.isNotBlank()) {
            STSystemInfoCard("正在处理", busyMessage)
        }
        if (showBackupOperationCard) {
            STOperationCard(backupOperationTitle, backupOperationDetails, backupOperationProgressPercent, backupOperationAnchor?.name.orEmpty())
        }
        if (showCustomOperationCard) {
            STOperationCard(customOperationTitle, customOperationDetails, customOperationProgressPercent, customOperationAnchor?.name.orEmpty()) {
                if (customOperationCancelable) onCancelCustomOperation()
            }
        }
        
        STSectionHeader("管理")
        STSettingsGroup {
            STListItem(
                headline = "内核版本",
                supporting = if (isCustomInstalled) "自定义 · ${customInstalledLabel.orEmpty()}" else "自带 · $stLabel",
                leading = { STTileIcon(Icons.Filled.Settings, tint = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) },
                divider = true,
                onClick = onLoadCustomZip
            )
            STListItem(
                headline = "数据备份与快照",
                supporting = "导出或导入完整数据",
                leading = { STTileIcon(Icons.Filled.Upload, tint = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) },
                divider = true,
                onClick = onExport
            )
            STListItem(
                headline = "运行日志",
                supporting = "stdout · stderr · service",
                leading = { STTileIcon(Icons.Filled.Info, tint = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) },
                divider = true,
                onClick = onShowLogs
            )
            STListItem(
                headline = "清除用户数据",
                supporting = "危险操作，清除全部数据",
                leading = { STTileIcon(Icons.Filled.Delete, tint = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) },
                divider = true,
                onClick = onRemoveUserData
            )
            STListItem(
                headline = "恢复自带内核",
                supporting = "清理自定义 ST 版本",
                leading = { STTileIcon(Icons.Filled.RestartAlt, tint = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface) },
                onClick = onResetToDefault
            )
        }

        STSectionHeader("自动化")
        STListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
            STListItem(
                headline = "启动 App 时自动唤起服务",
                supporting = "进入主屏后立即拉起 Node 进程",
                trailing = { Switch(checked = autoStartService, onCheckedChange = onAutoStartServiceChanged) },
                divider = true,
                onClick = { onAutoStartServiceChanged(!autoStartService) }
            )
            STListItem(
                headline = "服务就绪后自动打开浏览器",
                supporting = "对 :8000 进行 TCP 探测后跳转",
                trailing = { Switch(checked = autoOpenBrowser, onCheckedChange = onAutoOpenBrowserChanged) },
                divider = true,
                onClick = { onAutoOpenBrowserChanged(!autoOpenBrowser) }
            )
            STListItem(
                headline = "允许后台持续运行",
                supporting = "加入电池白名单后更稳定",
                trailing = { Switch(checked = allowBackgroundRun, onCheckedChange = { allowBackgroundRun = it }) },
                onClick = { allowBackgroundRun = !allowBackgroundRun }
            )
        }

        STSectionHeader("设置快照", trailing = {
            Text(if (settingsSnapshotsLoading) "加载中" else "${settingsSnapshots.size} 个", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        })
        STListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
            STListItem(
                headline = "创建设置快照",
                supporting = settingsSnapshotMessage.ifBlank { "保存当前设置，方便回滚" },
                leading = { STTileIcon(Icons.Filled.Backup) },
                divider = settingsSnapshots.isNotEmpty(),
                onClick = onCreateSettingsSnapshot
            )
            settingsSnapshots.take(5).forEachIndexed { index, snap ->
                STListItem(
                    headline = snap.name,
                    supporting = "${snap.size} bytes",
                    leading = { STTileIcon(Icons.Filled.CheckCircle) },
                    divider = index != settingsSnapshots.take(5).lastIndex,
                    onClick = { onRestoreSettingsSnapshot(snap.name) }
                )
            }
        }
    }
}

@Composable
private fun STStStatusHero(status: NodeStatus, stLabel: String, nodeLabel: String, onStart: () -> Unit, onStop: () -> Unit, onOpen: () -> Unit) {
    val running = status.state == NodeState.RUNNING
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            STStatusDot(if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(10.dp))
            Text(if (running) "运行中" else "已停止", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            STBadge(":${status.port}")
        }
        Text(if (running) "SillyTavern 正在为你运行" else "SillyTavern 已停止", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp))
        Text("$stLabel · $nodeLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("在浏览器打开")
                }
                OutlinedButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("停止")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("启动服务")
                }
            }
        }
    }
}

@Composable
private fun STOperationCard(title: String, details: String, progress: Int?, anchor: String, onCancel: (() -> Unit)? = null) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title.ifBlank { anchor.ifBlank { "正在处理" } }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            if (onCancel != null) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) { Text("取消") }
            }
        }
    }
}
