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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.AppPaths
import io.github.sanitised.st.BackupOperationAnchor
import io.github.sanitised.st.CustomOperationAnchor
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.TauriTavernRuntime
import io.github.sanitised.st.api.SettingsSnapshot
import kotlinx.coroutines.delay

@Composable
fun STStCoreScreen(
    status: NodeStatus,
    stLabel: String = TauriTavernRuntime.CORE_LABEL,
    nodeLabel: String = TauriTavernRuntime.ENGINE_LABEL,
    isCustomInstalled: Boolean = false,
    customInstalledLabel: String? = null,
    busyMessage: String = "",
    settingsSnapshots: List<SettingsSnapshot> = emptyList(),
    settingsSnapshotsLoading: Boolean = false,
    settingsSnapshotMessage: String = "",
    showBackupOperationCard: Boolean = false,
    backupOperationTitle: String = "",
    backupOperationDetails: String = "",
    backupOperationProgressPercent: Int? = null,
    backupOperationAnchor: BackupOperationAnchor? = null,
    showCustomOperationCard: Boolean = false,
    customOperationTitle: String = "",
    customOperationDetails: String = "",
    customOperationProgressPercent: Int? = null,
    customOperationCancelable: Boolean = false,
    customOperationAnchor: CustomOperationAnchor? = null,
    onBack: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenBrowser: () -> Unit = {},
    onShowLogs: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRefreshSettingsSnapshots: () -> Unit = {},
    onCreateSettingsSnapshot: () -> Unit = {},
    onRestoreSettingsSnapshot: (String) -> Unit = {},
    onLoadCustomZip: () -> Unit = {},
    onResetToDefault: () -> Unit,
    onRemoveUserData: () -> Unit,
    onCancelCustomOperation: () -> Unit = {},
    autoStartService: Boolean = true,
    onAutoStartServiceChanged: (Boolean) -> Unit = {},
    autoOpenBrowser: Boolean = false,
    onAutoOpenBrowserChanged: (Boolean) -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dataDirPath = remember(context) { AppPaths.dataDir(context).absolutePath }
    val defaultContentPath = remember(context) { AppPaths.defaultContentDir(context).absolutePath }
    val running = status.state == NodeState.RUNNING
    val starting = status.state == NodeState.STARTING

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

    STBackRoot(title = "TauriTavern 核心", onBack = onBack, modifier = modifier, actions = {
        STIconButton(Icons.Filled.MoreVert, "更多", { onShowMessage("TauriTavern Rust 核心由进程内 JNI 直接驱动") })
    }) {
        STStStatusHero(
            status = status,
            stLabel = stLabel,
            nodeLabel = nodeLabel,
            onStart = onStartService,
            onStop = onStopService,
            onReinitDefault = onResetToDefault
        )

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
                    Text("运行架构", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Rust JNI (libtauritavern_jni.so)",
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
                    Text("宿主进程 PID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (running || starting) "${status.pid ?: "—"}" else "—",
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
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("数据目录 (DataDirectory)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = dataDirPath,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("内置模板目录 (DefaultContent)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = defaultContentPath,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("内核健康状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (running) Icons.Filled.CheckCircle else Icons.Filled.Face,
                            contentDescription = null,
                            tint = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = when (status.state) {
                                NodeState.RUNNING -> "JNI 已就绪"
                                NodeState.STARTING -> "正在初始化…"
                                NodeState.ERROR -> status.message.ifBlank { "初始化失败" }
                                else -> "未激活"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

        STSectionHeader("核心维护与数据")
        STSettingsGroup {
            STListItem(
                headline = "重新初始化默认模板",
                supporting = "释放内置 FirstHop.json、默认角色卡与预设到 DataDirectory",
                leading = { STTileIcon(Icons.Filled.RestartAlt, tint = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) },
                divider = true,
                onClick = onResetToDefault
            )
            STListItem(
                headline = "导出数据备份",
                supporting = "打包导出角色、世界书、聊天记录与设置",
                leading = { STTileIcon(Icons.Filled.Upload, tint = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) },
                divider = true,
                onClick = onExport
            )
            STListItem(
                headline = "导入数据备份",
                supporting = "从备份归档恢复并重载 TauriTavern 核心",
                leading = { STTileIcon(Icons.Filled.Download, tint = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) },
                divider = true,
                onClick = onImport
            )
            STListItem(
                headline = "运行日志",
                supporting = "查看 TauriTavern Rust 核心与备份操作日志",
                leading = { STTileIcon(Icons.Filled.Info, tint = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) },
                divider = true,
                onClick = onShowLogs
            )
            STListItem(
                headline = "清除用户数据",
                supporting = "危险操作：清空 DataDirectory 并恢复初始状态",
                leading = { STTileIcon(Icons.Filled.Delete, tint = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) },
                onClick = onRemoveUserData
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
private fun STStStatusHero(
    status: NodeStatus,
    stLabel: String,
    nodeLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReinitDefault: () -> Unit
) {
    val running = status.state == NodeState.RUNNING
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            STStatusDot(if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(10.dp))
            Text(if (running) "进程内运行中" else "未激活", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            STBadge("Rust JNI")
        }
        Text(
            if (running) "TauriTavern Rust 核心已就绪" else "TauriTavern Rust 核心未激活",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "$stLabel · $nodeLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.RestartAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("重载核心")
                }
                OutlinedButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("暂停")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("初始化核心")
                }
                OutlinedButton(onClick = onReinitDefault) {
                    Icon(Icons.Filled.Settings, null)
                    Spacer(Modifier.width(6.dp))
                    Text("重置模板")
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
